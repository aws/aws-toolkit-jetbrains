// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.workspace.context

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import software.aws.toolkits.jetbrains.services.amazonq.project.findWorkspaceRoot
import software.aws.toolkits.jetbrains.services.amazonq.project.isContentInWorkspace
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists

class WorkspaceTest : LightPlatformTestCase() {
    private lateinit var tempDir: Path

    override fun setUp() {
        super.setUp()
        tempDir = Files.createTempDirectory("workspace-test")
    }

    override fun tearDown() {
        if (tempDir.exists()) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { it.deleteExisting() }
        }
        super.tearDown()
    }

    private fun createDir(relativePath: String): VirtualFile {
        val normalizedPath = relativePath.removePrefix("/")
        return if (normalizedPath.isEmpty()) {
            tempDir.refreshAndFindVirtualDirectory() ?: error("Failed to create directory")
        } else {
            tempDir.resolve(normalizedPath).createDirectories().refreshAndFindVirtualDirectory() ?: error("Failed to create directory")
        }
    }

    private fun createFile(path: String): VirtualFile {
        val filePath = tempDir.resolve(path.removePrefix("/"))
        Files.createDirectories(filePath.parent)
        return Files.createFile(filePath).refreshAndFindVirtualFile() ?: error("Failed to create file")
    }

    fun `testFindWorkspaceRoot returns null when no projects`() {
        assertNull(findWorkspaceRoot(emptySet()))
    }

    fun `test findWorkspaceRoot returns project path for single project`() {
        val projectPath = createDir("test/project")

        assertEquals(projectPath, findWorkspaceRoot(setOf(projectPath)))
    }

    fun `test findWorkspaceRoot returns project path for project with inner modules`() {
        val path1 = createDir("test/projects/project1")
        val path2 = createDir("test/projects/project1/module")

        assertEquals(path1, findWorkspaceRoot(setOf(path1, path2)))
    }

    fun `test findWorkspaceRoot returns common root of multiple projects`() {
        val path1 = createDir("test/projects/project1")
        val path2 = createDir("test/projects/project2")

        assertEquals(createDir("test/projects"), findWorkspaceRoot(setOf(path1, path2)))
    }

    fun `test findWorkspaceRoot returns common root of a project and external modules`() {
        val projectPath = createDir("test/project")
        val modulePath = createDir("test/external/module")

        assertEquals(createDir("test"), findWorkspaceRoot(setOf(projectPath, modulePath)))
    }

    fun `test isContentInWorkspace returns false when workspace has no directories`() {
        assertFalse(isContentInWorkspace(createDir("any/path"), emptySet()))
    }

    fun `test isContentInWorkspace returns true for path in project`() {
        val projectPath = createDir("test/project")
        val testPath = createFile("test/project/src/file.txt")

        assertTrue(isContentInWorkspace(testPath, setOf(projectPath)))
    }

    fun `test isContentInWorkspace returns true for path in external module`() {
        val projectPath = createDir("test/project")
        val modulePath = createDir("test/external/module")

        val testPath = createFile("test/external/module/src/file.txt")

        assertTrue(isContentInWorkspace(testPath, setOf(projectPath, modulePath)))
    }

    fun `test isContentInWorkspace returns false for path outside project and modules`() {
        val projectPath = createDir("test/project")
        val modulePath = createDir("test/external/module")

        val testPath = createFile("other/path/file.txt")

        assertFalse(isContentInWorkspace(testPath, setOf(projectPath, modulePath)))
    }
}
