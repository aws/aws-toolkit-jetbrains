// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.isDeveloperMode
import software.aws.toolkits.jetbrains.services.codewhisperer.model.ListUtgCandidateResult
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CrossFile.NEIGHBOR_FILES_DISTANCE

/**
 * An interface define how do we parse and fetch files provided a psi file or project
 * since different language has its own way importing other files or its own naming style for test file
 */
interface FileCrawler {
    fun listFilesUnderProjectRoot(project: Project): List<VirtualFile>

    /**
     * should be invoked at test files e.g. MainTest.java, or test_main.py
     * @param target psi of the test file we are searching with, e.g. MainTest.java
     * @return its source file e.g. Main.java, main.py or most relevant file if any
     */
    fun listUtgCandidate(target: PsiFile): ListUtgCandidateResult

    /**
     * List files opened in the editors and sorted by file distance @see [CodeWhispererFileCrawler.getFileDistance]
     * @return opened files and satisfy the following conditions
     * (1) not the input file
     * (2) with the same file extension as the input file has
     * (3) non-test file which will be determined by [FileCrawler.isTestFile]
     * (4) writable file
     */
    fun listCrossFileCandidate(target: PsiFile): List<VirtualFile>

    /**
     * Determine if the file given is test file or not based on its path and file name
     */
    fun isTestFile(target: VirtualFile, project: Project): Boolean
}

class NoOpFileCrawler : FileCrawler {
    override fun listFilesUnderProjectRoot(project: Project): List<VirtualFile> = emptyList()

    override fun listUtgCandidate(target: PsiFile) = ListUtgCandidateResult(null, UtgStrategy.Empty)

    override fun listCrossFileCandidate(target: PsiFile): List<VirtualFile> = emptyList()

    override fun isTestFile(target: VirtualFile, project: Project): Boolean = false
}

abstract class CodeWhispererFileCrawler : FileCrawler {
    abstract val fileExtension: String
    abstract val dialects: Set<String>
    abstract val testFileNamingPatterns: List<Regex>

    override fun isTestFile(target: VirtualFile, project: Project): Boolean {
        val filePath = target.path

        // if file path itself explicitly explains the file is under test sources
        if (TestSourcesFilter.isTestSources(target, project) ||
            filePath.contains("""test/""", ignoreCase = true) ||
            filePath.contains("""tst/""", ignoreCase = true) ||
            filePath.contains("""tests/""", ignoreCase = true)
        ) {
            return true
        }

        // no explicit clue from the file path, use regexes based on naming conventions
        return testFileNamingPatterns.any { it.matches(target.name) }
    }

    override fun listFilesUnderProjectRoot(project: Project): List<VirtualFile> = project.guessProjectDir()?.let { rootDir ->
        VfsUtil.collectChildrenRecursively(rootDir).filter {
            // TODO: need to handle cases js vs. jsx, ts vs. tsx when we enable js/ts utg since we likely have different file extensions
            it.path.endsWith(fileExtension)
        }
    }.orEmpty()

    override fun listCrossFileCandidate(target: PsiFile): List<VirtualFile> = if (isDeveloperMode()) {
        val previousSelected = listPreviousSelectedFile(target)
        val neighbors = neighborFiles(target).mapNotNull { it.virtualFile }
        val result = previousSelected + neighbors
        println("MRU list: ${previousSelected.map { it.name }}")
        println("neighbors: ${neighbors.map { it.name }}")
        result.distinctBy { it.name }
    } else {
        listAllOpenedFilesSortedByDist(target)
    }.also {
        val logStr = it.map { file -> file.name }
        println("crossfile candidates: $logStr")
    }

    /**
     * Default strategy will return all opened files sorted with file distance against the target
     */
    private fun listAllOpenedFilesSortedByDist(target: PsiFile): List<VirtualFile> {
        val targetFile = target.virtualFile

        val openedFiles = runReadAction {
            FileEditorManager.getInstance(target.project).openFiles.toList().filter {
                it.name != target.virtualFile.name &&
                    isSameDialect(it.extension) &&
                    !isTestFile(it, target.project)
            }
        }

        val fileToFileDistanceList = runReadAction {
            openedFiles.map {
                return@map it to CodeWhispererFileCrawler.getFileDistance(fileA = targetFile, fileB = it)
            }
        }

        return fileToFileDistanceList.sortedBy { it.second }.map { it.first }
    }

    /**
     * New strategy will return opened files sorted by timeline the file is used (Most Recently Used), which should be as same as JB's file switcher (ctrl + tab)
     * Note: test file is included here unlike the default strategy (thus different predicate inside filter)
     */
    private fun listPreviousSelectedFile(target: PsiFile): List<VirtualFile> {
        val targetVFile = target.virtualFile
        return runReadAction {
            (FileEditorManager.getInstance(target.project) as FileEditorManagerImpl).getSelectionHistory()
                .map { it.first }
                .filter {
                    it.name != targetVFile.name &&
                        isSameDialect(it.extension)
                }
        }
    }

    override fun listUtgCandidate(target: PsiFile): ListUtgCandidateResult {
        val byName = findSourceFileByName(target)
        if (byName != null) {
            return ListUtgCandidateResult(byName, UtgStrategy.ByName)
        }

        val byContent = findSourceFileByContent(target)
        if (byContent != null) {
            return ListUtgCandidateResult(byContent, UtgStrategy.ByContent)
        }

        return ListUtgCandidateResult(null, UtgStrategy.Empty)
    }

    abstract fun findSourceFileByName(target: PsiFile): VirtualFile?

    abstract fun findSourceFileByContent(target: PsiFile): VirtualFile?

    // TODO: may need to update when we enable JS/TS UTG, since we have to factor in .jsx/.tsx combinations
    fun guessSourceFileName(tstFileName: String): String? {
        val srcFileName = tryOrNull {
            testFileNamingPatterns.firstNotNullOf { regex ->
                regex.find(tstFileName)?.groupValues?.let { groupValues ->
                    groupValues.get(1) + groupValues.get(2)
                }
            }
        }

        return srcFileName
    }

    private fun isSameDialect(fileExt: String?): Boolean = fileExt?.let {
        dialects.contains(fileExt)
    } ?: false

    /**
     *     1. A: root/util/context/a.ts
     *     2. B: root/util/b.ts
     *     3. C: root/util/service/c.ts
     *     4. D: root/d.ts
     *     5. E: root/util/context/e.ts
     *     6. F: root/util/foo/bar/baz/f.ts
     *
     *   neighborfiles(A) = [B, E]
     *   neighborfiles(B) = [A, C, D, E]
     *   neighborfiles(C) = [B,]
     *   neighborfiles(D) = [B,]
     *   neighborfiles(E) = [A, B]
     *   neighborfiles(F) = []
     *
     *      A B C D E F
     *   A  x 1 2 2 0 4
     *   B  1 x 1 1 1 3
     *   C  2 1 x 2 2 4
     *   D  2 1 2 x 2 4
     *   E  0 1 2 2 x 4
     *   F  4 3 4 4 4 x
     */
    fun neighborFiles(psiFile: PsiFile): Set<PsiFile> = search(psiFile, NEIGHBOR_FILES_DISTANCE).filterNot { it == psiFile }.toSet()

    private fun search(psiFile: PsiFile, distance: Int): Set<PsiFile> = runReadAction {
        search(psiFile.containingDirectory, distance, true) +
            search(psiFile.containingDirectory, distance, false)
    }

    private fun search(psiDir: PsiDirectory, distance: Int, goDown: Boolean): Set<PsiFile> {
        var d = distance
        val res = mutableListOf<PsiFile>()
        var pendingVisit = listOf(psiDir)

        while (d >= 0 && pendingVisit.isNotEmpty()) {
            val toVisit = mutableListOf<PsiDirectory>()
            for (dir in pendingVisit) {
                val fs = dir.files
                res.addAll(fs)

                val dirs = if (goDown) {
                    dir.subdirectories.toList()
                } else {
                    dir.parentDirectory?.let {
                        listOf(it)
                    }.orEmpty()
                }

                toVisit.addAll(dirs)
            }

            pendingVisit = toVisit
            d--
        }

        return res.toSet()
    }

    companion object {
        // TODO: move to CodeWhispererUtils.kt
        /**
         * @param target will be the source of keywords
         * @param keywordProducer defines how we generate keywords from the target
         * @return return the file with the highest substring matching from all opened files with the same file extension
         */
        fun searchRelevantFileInEditors(target: PsiFile, keywordProducer: (psiFile: PsiFile) -> List<String>): VirtualFile? {
            val project = target.project
            val targetElements = keywordProducer(target)

            return runReadAction {
                FileEditorManager.getInstance(project).openFiles
                    .filter { openedFile ->
                        openedFile.name != target.virtualFile.name && openedFile.extension == target.virtualFile.extension
                    }
                    .mapNotNull { openedFile -> PsiManager.getInstance(project).findFile(openedFile) }
                    .maxByOrNull {
                        val elementsToCheck = keywordProducer(it)
                        countSubstringMatches(targetElements, elementsToCheck)
                    }?.virtualFile
            }
        }

        // TODO: move to CodeWhispererUtils.kt
        /**
         * how many elements in elementsToCheck is contained (as substring) in targetElements
         */
        fun countSubstringMatches(targetElements: List<String>, elementsToCheck: List<String>): Int = elementsToCheck.fold(0) { acc, elementToCheck ->
            val hasTarget = targetElements.any { it.contains(elementToCheck, ignoreCase = true) }
            if (hasTarget) {
                acc + 1
            } else {
                acc
            }
        }

        /**
         * For [LocalFileSystem](implementation of virtual file system), the path will be an absolute file path with file separator characters replaced
         * by forward slash "/"
         * @see [VirtualFile.getPath]
         */
        fun getFileDistance(fileA: VirtualFile, fileB: VirtualFile): Int {
            val targetFilePaths = fileA.path.split("/").dropLast(1)
            val candidateFilePaths = fileB.path.split("/").dropLast(1)

            var i = 0
            while (i < minOf(targetFilePaths.size, candidateFilePaths.size)) {
                val dir1 = targetFilePaths[i]
                val dir2 = candidateFilePaths[i]

                if (dir1 != dir2) {
                    break
                }

                i++
            }

            return targetFilePaths.subList(fromIndex = i, toIndex = targetFilePaths.size).size +
                candidateFilePaths.subList(fromIndex = i, toIndex = candidateFilePaths.size).size
        }
    }
}
