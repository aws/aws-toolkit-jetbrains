// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.ide.actions.CopyContentRootPathProvider
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.gist.GistManager
import com.intellij.util.io.DataExternalizer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextController
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.Chunk
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SupplementalContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.SUPPLEMETAL_CONTEXT_BUFFER
import java.io.DataInput
import java.io.DataOutput
import java.util.Collections
import kotlin.coroutines.coroutineContext

private val contentRootPathProvider = CopyContentRootPathProvider()

private val codewhispererCodeChunksIndex = GistManager.getInstance()
    .newPsiFileGist("psi to code chunk index", 0, CodeWhispererCodeChunkExternalizer) { psiFile ->
        runBlocking {
            val fileCrawler = psiFile.programmingLanguage().fileCrawler
            val fileProducers = listOf<suspend (PsiFile) -> List<VirtualFile>> { psiFile -> fileCrawler.listCrossFileCandidate(psiFile) }
            FileContextProvider.getInstance(psiFile.project).extractCodeChunksFromFiles(psiFile, fileProducers)
        }
    }

private object CodeWhispererCodeChunkExternalizer : DataExternalizer<List<Chunk>> {
    override fun save(out: DataOutput, value: List<Chunk>) {
        out.writeInt(value.size)
        value.forEach { chunk ->
            out.writeUTF(chunk.path)
            out.writeUTF(chunk.content)
            out.writeUTF(chunk.nextChunk)
        }
    }

    override fun read(`in`: DataInput): List<Chunk> {
        val result = mutableListOf<Chunk>()
        val size = `in`.readInt()
        repeat(size) {
            result.add(
                Chunk(
                    path = `in`.readUTF(),
                    content = `in`.readUTF(),
                    nextChunk = `in`.readUTF()
                )
            )
        }

        return result
    }
}

/**
 * [extractFileContext] will extract the context from a psi file provided
 * [extractSupplementalFileContext] supplemental means file context extracted from files other than the provided one
 */
interface FileContextProvider {
    fun extractFileContext(editor: Editor, psiFile: PsiFile): FileContextInfo

    suspend fun extractSupplementalFileContext(psiFile: PsiFile, fileContext: FileContextInfo, timeout: Long): SupplementalContextInfo?

    suspend fun extractCodeChunksFromFiles(psiFile: PsiFile, fileProducers: List<suspend (PsiFile) -> List<VirtualFile>>): List<Chunk>

    /**
     * It will actually delegate to invoke corresponding [CodeWhispererFileCrawler.isTestFile] for each language
     * as different languages have their own naming conventions.
     */
    fun isTestFile(psiFile: PsiFile): Boolean

    companion object {
        fun getInstance(project: Project): FileContextProvider = project.service()
    }
}

class DefaultCodeWhispererFileContextProvider(private val project: Project) : FileContextProvider {
    override fun extractFileContext(editor: Editor, psiFile: PsiFile): FileContextInfo = CodeWhispererEditorUtil.getFileContextInfo(editor, psiFile)

    /**
     * codewhisperer extract the supplemental context with 2 different approaches depending on what type of file the target file is.
     * 1. source file -> explore files/classes imported from the target file + files within the same project root
     * 2. test file -> explore "focal file" if applicable, otherwise fall back to most "relevant" file.
     * for focal files, e.g. "MainTest.java" -> "Main.java", "test_main.py" -> "main.py"
     * for the most relevant file -> we extract "keywords" from files opened in editor then get the one with the highest similarity with target file
     */
    override suspend fun extractSupplementalFileContext(psiFile: PsiFile, targetContext: FileContextInfo, timeout: Long): SupplementalContextInfo? {
        val startFetchingTimestamp = System.currentTimeMillis()
        val isTst = readAction { isTestFile(psiFile) }
        return try {
            val language = targetContext.programmingLanguage

            val supplementalContext = if (isTst) {
                when (shouldFetchUtgContext(language)) {
                    true -> withTimeout(timeout) { extractSupplementalFileContextForTst(psiFile, targetContext) }
                    false -> SupplementalContextInfo.emptyUtgFileContextInfo(targetContext.filename)
                    null -> {
                        LOG.debug { "UTG is not supporting ${targetContext.programmingLanguage.languageId}" }
                        null
                    }
                }
            } else {
                when (shouldFetchCrossfileContext(language)) {
                    // we need this buffer 10ms as when project context timeout by 50ms,
                    // the entire [extractSupplementalFileContextForSrc] call will time out and not even return openTabsContext
                    true -> withTimeout(timeout + SUPPLEMETAL_CONTEXT_BUFFER) { extractSupplementalFileContextForSrc(psiFile, targetContext) }
                    false -> SupplementalContextInfo.emptyCrossFileContextInfo(targetContext.filename)
                    null -> {
                        LOG.debug { "Crossfile is not supporting ${targetContext.programmingLanguage.languageId}" }
                        null
                    }
                }
            }

            return supplementalContext?.let {
                if (it.contents.isNotEmpty()) {
                    val logStr = buildString {
                        append("Successfully fetched supplemental context with strategy ${it.strategy} with ${it.latency} ms")
                        it.contents.forEachIndexed { index, chunk ->
                            append(
                                """
                            |
                            | Chunk ${index + 1}:
                            |    path = ${chunk.path},
                            |    score = ${chunk.score},
                            |    contentLength = ${chunk.content.length}
                            |
                                """.trimMargin()
                            )
                        }
                    }

                    LOG.info { logStr }
                } else {
                    LOG.warn { "Failed to fetch supplemental context, empty list." }
                }

                // TODO: fix this latency
                it.copy(latency = System.currentTimeMillis() - startFetchingTimestamp)
            }
        } catch (e: TimeoutCancellationException) {
            LOG.debug {
                "Supplemental context fetch timed out in ${System.currentTimeMillis() - startFetchingTimestamp}ms"
            }
            SupplementalContextInfo(
                isUtg = isTst,
                contents = emptyList(),
                latency = System.currentTimeMillis() - startFetchingTimestamp,
                targetFileName = targetContext.filename,
                strategy = if (isTst) UtgStrategy.Empty else CrossFileStrategy.Empty
            )
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun extractCodeChunksFromFiles(psiFile: PsiFile, fileProducers: List<suspend (PsiFile) -> List<VirtualFile>>): List<Chunk> {
        val hasUsed = Collections.synchronizedSet(mutableSetOf<VirtualFile>())
        val chunks = mutableListOf<Chunk>()

        for (fileProducer in fileProducers) {
            yield()
            val files = fileProducer(psiFile)
            files.forEach { file ->
                yield()
                if (hasUsed.contains(file)) {
                    return@forEach
                }
                val relativePath = runReadAction { contentRootPathProvider.getPathToElement(project, file, null) ?: file.path }
                chunks.addAll(file.toCodeChunk(relativePath))
                hasUsed.add(file)
                if (chunks.size > CodeWhispererConstants.CrossFile.CHUNK_SIZE) {
                    return chunks.take(CodeWhispererConstants.CrossFile.CHUNK_SIZE)
                }
            }
        }

        return chunks.take(CodeWhispererConstants.CrossFile.CHUNK_SIZE)
    }

    override fun isTestFile(psiFile: PsiFile) = psiFile.programmingLanguage().fileCrawler.isTestFile(psiFile.virtualFile, psiFile.project)

    suspend fun extractSupplementalFileContextForSrc(psiFile: PsiFile, targetContext: FileContextInfo): SupplementalContextInfo {
        if (!targetContext.programmingLanguage.isSupplementalContextSupported()) {
            return SupplementalContextInfo.emptyCrossFileContextInfo(targetContext.filename)
        }

        val query = generateQuery(targetContext)

        val contexts = withContext(coroutineContext) {
            val projectContextDeferred1 = if (CodeWhispererFeatureConfigService.getInstance().getInlineCompletion()) {
                async {
                    val t0 = System.currentTimeMillis()
                    val r = fetchProjectContext(query, psiFile, targetContext)
                    val t1 = System.currentTimeMillis()
                    LOG.debug {
                        buildString {
                            append("time elapse for fetching project context=${t1 - t0}ms; ")
                            append("numberOfChunks=${r.contents.size}; ")
                            append("totalLength=${r.contentLength}")
                        }
                    }

                    r
                }
            } else {
                null
            }

            val openTabsContextDeferred1 = async {
                val t0 = System.currentTimeMillis()
                val r = fetchOpenTabsContext(query, psiFile, targetContext)
                val t1 = System.currentTimeMillis()
                LOG.debug {
                    buildString {
                        append("time elapse for open tabs context=${t1 - t0}ms; ")
                        append("numberOfChunks=${r.contents.size}; ")
                        append("totalLength=${r.contentLength}")
                    }
                }

                r
            }

            if (projectContextDeferred1 != null) {
                awaitAll(projectContextDeferred1, openTabsContextDeferred1)
            } else {
                awaitAll(openTabsContextDeferred1)
            }
        }

        val projectContext = contexts.find { it.strategy == CrossFileStrategy.ProjectContext }
        val openTabsContext = contexts.find { it.strategy == CrossFileStrategy.OpenTabsBM25 }

        return if (projectContext != null && projectContext.contents.isNotEmpty()) {
            projectContext
        } else {
            openTabsContext ?: SupplementalContextInfo.emptyCrossFileContextInfo(targetContext.filename)
        }
    }

    @VisibleForTesting
    suspend fun fetchProjectContext(query: String, psiFile: PsiFile, targetContext: FileContextInfo): SupplementalContextInfo {
        val response = ProjectContextController.getInstance(project).queryInline(query, psiFile.virtualFile?.path ?: "")

        return SupplementalContextInfo(
            isUtg = false,
            contents = response.map {
                Chunk(
                    content = it.content,
                    path = it.filePath,
                    nextChunk = it.content,
                    score = it.score
                )
            },
            targetFileName = targetContext.filename,
            strategy = CrossFileStrategy.ProjectContext
        )
    }

    @VisibleForTesting
    suspend fun fetchOpenTabsContext(query: String, psiFile: PsiFile, targetContext: FileContextInfo): SupplementalContextInfo {
        // step 1: prepare data
        val first60Chunks: List<Chunk> = try {
            runReadAction { codewhispererCodeChunksIndex.getFileData(psiFile) }
        } catch (e: TimeoutCancellationException) {
            throw e
        }

        yield()

        if (first60Chunks.isEmpty()) {
            LOG.warn {
                "0 chunks was found for supplemental context, fileName=${targetContext.filename}, " +
                    "programmingLanaugage: ${targetContext.programmingLanguage}"
            }
            return SupplementalContextInfo.emptyCrossFileContextInfo(targetContext.filename)
        }

        // we need to keep the reference to Chunk object because we will need to get "nextChunk" later after calculation
        val contentToChunk = first60Chunks.associateBy { it.content }

        // BM250 only take list of string as argument
        // step 2: bm25 calculation
        val top3Chunks: List<BM25Result> = BM250kapi(first60Chunks.map { it.content }).topN(query)

        yield()

        // we use nextChunk as supplemental context
        val crossfileContext = top3Chunks.mapNotNull { bm25Result ->
            contentToChunk[bm25Result.docString]?.let {
                if (it.nextChunk.isNotBlank()) {
                    Chunk(content = it.nextChunk, path = it.path, score = bm25Result.score)
                } else {
                    null
                }
            }
        }

        return SupplementalContextInfo(
            isUtg = false,
            contents = crossfileContext,
            targetFileName = targetContext.filename,
            strategy = CrossFileStrategy.OpenTabsBM25
        )
    }

    @VisibleForTesting
    fun extractSupplementalFileContextForTst(psiFile: PsiFile, targetContext: FileContextInfo): SupplementalContextInfo {
        if (!targetContext.programmingLanguage.isUTGSupported()) {
            return SupplementalContextInfo.emptyUtgFileContextInfo(targetContext.filename)
        }

        val utgCandidateResult = targetContext.programmingLanguage.fileCrawler.listUtgCandidate(psiFile)
        val focalFile = utgCandidateResult.vfile
        val strategy = utgCandidateResult.strategy

        return focalFile?.let { file ->
            runReadAction {
                val relativePath = contentRootPathProvider.getPathToElement(project, file, null) ?: file.path
                val content = file.content()

                val utgContext = if (content.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        Chunk(
                            content = CodeWhispererConstants.Utg.UTG_PREFIX + file.content().let {
                                it.substring(
                                    0,
                                    minOf(it.length, CodeWhispererConstants.Utg.UTG_SEGMENT_SIZE)
                                )
                            },
                            path = relativePath
                        )
                    )
                }

                SupplementalContextInfo(
                    isUtg = true,
                    contents = utgContext,
                    targetFileName = targetContext.filename,
                    strategy = strategy
                )
            }
        } ?: run {
            return SupplementalContextInfo.emptyUtgFileContextInfo(targetContext.filename)
        }
    }

    // takeLast(11) will extract 10 lines (exclusing current line) of left context as the query parameter
    fun generateQuery(fileContext: FileContextInfo) = fileContext.caretContext.leftFileContext.split("\n").takeLast(11).joinToString("\n")

    companion object {
        private val LOG = getLogger<DefaultCodeWhispererFileContextProvider>()

        fun shouldFetchUtgContext(language: CodeWhispererProgrammingLanguage): Boolean? {
            if (!language.isUTGSupported()) {
                return null
            }

            return when (language) {
                is CodeWhispererJava -> true
                else -> false
            }
        }

        fun shouldFetchCrossfileContext(language: CodeWhispererProgrammingLanguage): Boolean? {
            if (!language.isSupplementalContextSupported()) {
                return null
            }

            return when (language) {
                is CodeWhispererJava,
                is CodeWhispererPython,
                is CodeWhispererJavaScript,
                is CodeWhispererTypeScript,
                is CodeWhispererJsx,
                is CodeWhispererTsx,
                -> true

                else -> false
            }
        }
    }
}
