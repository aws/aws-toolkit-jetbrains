// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.ide.actions.CopyContentRootPathProvider
import com.intellij.openapi.application.ApplicationManager
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.annotations.VisibleForTesting
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererC
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCpp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererCsharp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererGo
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJava
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJavaScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererJsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererKotlin
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPhp
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPython
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRuby
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererRust
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererScala
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererShell
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTsx
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererTypeScript
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.Chunk
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SupplementalContextResult
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroup
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererUserGroupSettings
import software.aws.toolkits.jetbrains.utils.assertIsNonDispatchThread
import java.io.DataInput
import java.io.DataOutput
import java.util.Collections
import kotlin.Exception

private val contentRootPathProvider = CopyContentRootPathProvider()

private val codewhispererCodeChunksIndex = GistManager.getInstance()
    .newPsiFileGist("psi to code chunk index", 0, CodeWhispererCodeChunkExternalizer) { psiFile ->
        runBlocking {
            FileContextProvider.getInstance(psiFile.project).extractCodeChunksForFile(psiFile)
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

    suspend fun extractSupplementalFileContext(psiFile: PsiFile, fileContext: FileContextInfo, timeout: Long): SupplementalContextResult

    suspend fun extractCodeChunksForFile(psiFile: PsiFile): List<Chunk>

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
    override suspend fun extractSupplementalFileContext(psiFile: PsiFile, targetContext: FileContextInfo, timeout: Long): SupplementalContextResult {
        assertIsNonDispatchThread()
        val startFetchingTimestamp = System.currentTimeMillis()
        val isTst = isTestFile(psiFile)
        return try {
            withTimeout(timeout) {
                val language = targetContext.programmingLanguage
                val group = CodeWhispererUserGroupSettings.getInstance().getUserGroup()

                // if utg is not supported, use crossfile context as fallback
                val supplementalContext = if (isTst && language.isUTGSupported()) {
                    when (shouldFetchUtgContext(language, group)) {
                        true -> extractSupplementalFileContextForTst(psiFile, targetContext)
                        false -> SupplementalContextResult.NotSupported(isTst, language, targetContext.filename)
                    }
                } else {
                    when (shouldFetchCrossfileContext(language, group)) {
                        true -> extractSupplementalFileContextForSrc(psiFile, targetContext)
                        false -> SupplementalContextResult.NotSupported(isTst, language, targetContext.filename)
                    }
                }

                when (supplementalContext) {
                    is SupplementalContextResult.Success -> run {
                        supplementalContext.latency = System.currentTimeMillis() - startFetchingTimestamp
                        val logStr = buildString {
                            append("Successfully fetched supplemental context.")
                            supplementalContext.contents.forEachIndexed { index, chunk ->
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
                    }

                    is SupplementalContextResult.Failure -> run {
                        supplementalContext.latency = System.currentTimeMillis() - startFetchingTimestamp
                        LOG.warn { "Failed to fetch supplemental context, error message: ${supplementalContext.error.message}" }
                    }

                    is SupplementalContextResult.NotSupported -> run {
                        LOG.debug { "${if (isTst) "UTG" else "Crossfile"} is not supporting ${targetContext.programmingLanguage.languageId}" }
                    }
                }

                return@withTimeout supplementalContext
            }
        } catch (e: TimeoutCancellationException) {
            LOG.debug {
                "Supplemental context fetch timed out in ${System.currentTimeMillis() - startFetchingTimestamp}ms"
            }
            SupplementalContextResult.Failure(
                isUtg = isTst,
                error = e,
                latency = System.currentTimeMillis() - startFetchingTimestamp,
                targetFileName = targetContext.filename
            )
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun extractCodeChunksForFile(psiFile: PsiFile): List<Chunk> {
        val fileCrawler = psiFile.programmingLanguage().fileCrawler
        val files = fileCrawler.listCrossFileCandidate(psiFile)
        return extractCodeChunksFromFiles(files)
    }

    suspend fun extractCodeChunksFromFiles(files: List<VirtualFile>): List<Chunk> {
        val parseFilesStart = System.currentTimeMillis()
        val hasUsed = Collections.synchronizedSet(mutableSetOf<VirtualFile>())
        val chunks = mutableListOf<Chunk>()

        files.forEach { file ->
            yield()
            if (hasUsed.contains(file)) {
                return@forEach
            }
            val relativePath = runReadAction { contentRootPathProvider.getPathToElement(project, file, null) ?: file.path }
            chunks.addAll(file.toCodeChunk(relativePath))
            hasUsed.add(file)
            if (chunks.size > CodeWhispererConstants.CrossFile.CHUNK_SIZE) {
                LOG.debug { "finish fetching ${CodeWhispererConstants.CrossFile.CHUNK_SIZE} chunks in ${System.currentTimeMillis() - parseFilesStart} ms" }
                return chunks.take(CodeWhispererConstants.CrossFile.CHUNK_SIZE)
            }
        }

        LOG.debug { "finish fetching ${CodeWhispererConstants.CrossFile.CHUNK_SIZE} chunks in ${System.currentTimeMillis() - parseFilesStart} ms" }
        return chunks.take(CodeWhispererConstants.CrossFile.CHUNK_SIZE)
    }

    override fun isTestFile(psiFile: PsiFile) = psiFile.programmingLanguage().fileCrawler.isTestFile(psiFile.virtualFile, psiFile.project)

    @VisibleForTesting
    suspend fun extractSupplementalFileContextForSrc(psiFile: PsiFile, targetContext: FileContextInfo): SupplementalContextResult {
        if (!targetContext.programmingLanguage.isSupplementalContextSupported()) {
            return SupplementalContextResult.NotSupported(false, targetContext.programmingLanguage, targetContext.filename)
        }

        // takeLast(11) will extract 10 lines (exclusing current line) of left context as the query parameter
        val query = targetContext.caretContext.leftFileContext.split("\n").takeLast(11).joinToString("\n")

        // step 1: prepare data
        val first60Chunks: List<Chunk> = try {
            readAction {
                if (ApplicationManager.getApplication().isUnitTestMode) {
                    // TODO: hacky way to make test work, in test env, psiFile.virtualFile will be null with gist
                    runBlocking {
                        extractCodeChunksForFile(psiFile)
                    }
                } else {
                    codewhispererCodeChunksIndex.getFileData(psiFile)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        }

        yield()

        if (first60Chunks.isEmpty()) {
            LOG.warn {
                "0 chunks was found for supplemental context, fileName=${targetContext.filename}, " +
                    "programmingLanaugage: ${targetContext.programmingLanguage}"
            }
            return SupplementalContextResult.Failure(isUtg = false, Exception("No code chunk was found from crossfile candidates"), targetContext.filename)
        }

        // we need to keep the reference to Chunk object because we will need to get "nextChunk" later after calculation
        val contentToChunk = first60Chunks.associateBy { it.content }

        // BM250 only take list of string as argument
        // step 2: bm25 calculation
        val timeBeforeBm25 = System.currentTimeMillis()
        val top3Chunks: List<BM25Result> = BM250kapi(first60Chunks.map { it.content }).topN(query)
        LOG.info { "Time ellapsed for BM25 algorithm: ${System.currentTimeMillis() - timeBeforeBm25} ms" }

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

        return SupplementalContextResult.Success(
            isUtg = false,
            contents = crossfileContext,
            targetFileName = targetContext.filename,
            strategy = CrossFileStrategy.OpenTabsBM25,
        )
    }

    @VisibleForTesting
    fun extractSupplementalFileContextForTst(psiFile: PsiFile, targetContext: FileContextInfo): SupplementalContextResult {
        if (!targetContext.programmingLanguage.isUTGSupported()) {
            return SupplementalContextResult.NotSupported(true, targetContext.programmingLanguage, targetContext.filename)
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

                SupplementalContextResult.Success(
                    isUtg = true,
                    contents = utgContext,
                    targetFileName = targetContext.filename,
                    strategy = strategy
                )
            }
        } ?: run {
            return SupplementalContextResult.Failure(isUtg = true, Exception("Focal file not found"), targetContext.filename)
        }
    }

    companion object {
        private val LOG = getLogger<DefaultCodeWhispererFileContextProvider>()

        fun shouldFetchUtgContext(language: CodeWhispererProgrammingLanguage, userGroup: CodeWhispererUserGroup): Boolean {
            if (!language.isUTGSupported()) {
                return false
            }

            return when (language) {
                is CodeWhispererJava -> true
                else -> userGroup == CodeWhispererUserGroup.CrossFile
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun shouldFetchCrossfileContext(language: CodeWhispererProgrammingLanguage, userGroup: CodeWhispererUserGroup): Boolean {
            if (!language.isSupplementalContextSupported()) {
                return false
            }

            return when (language) {
                is CodeWhispererJava,
                is CodeWhispererPython,
                is CodeWhispererJavaScript,
                is CodeWhispererTypeScript,
                is CodeWhispererJsx,
                is CodeWhispererTsx -> true

                is CodeWhispererC,
                is CodeWhispererGo,
                is CodeWhispererPhp,
                is CodeWhispererRust,
                is CodeWhispererKotlin,
                is CodeWhispererCpp,
                is CodeWhispererCsharp,
                is CodeWhispererRuby,
                is CodeWhispererShell,
                is CodeWhispererScala -> CodeWhispererFeatureConfigService.getInstance().getCrossfileConfig()

                // TODO: languages under A/B, should read feature flag from [CodeWhispererFeatureConfigService]
                else -> false
            }
        }
    }
}
