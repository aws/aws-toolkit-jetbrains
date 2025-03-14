// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Path

class WorkspaceFolderUtilTest {

    @Test
    fun `createWorkspaceFolders returns empty list when no workspace folders`() {
        val mockProject = mockk<Project>()
        every { mockProject.isDefault } returns true

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertEquals(emptyList<VirtualFile>(), result)
    }

    @Test
    fun `createWorkspaceFolders returns workspace folders for non-default project`() {
        val mockProject = mockk<Project>()
        val mockProjectRootManager = mockk<ProjectRootManager>()
        val mockContentRoot1 = createMockVirtualFile(
            URI("file:///path/to/root1"),
            name = "root1"
        )
        val mockContentRoot2 = createMockVirtualFile(
            URI("file:///path/to/root2"),
            name = "root2"
        )

        every { mockProject.isDefault } returns false
        every { ProjectRootManager.getInstance(mockProject) } returns mockProjectRootManager
        every { mockProjectRootManager.contentRoots } returns arrayOf(mockContentRoot1, mockContentRoot2)

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertEquals(2, result.size)
        assertEquals(normalizeFileUri("file:///path/to/root1"), result[0].uri)
        assertEquals(normalizeFileUri("file:///path/to/root2"), result[1].uri)
        assertEquals("root1", result[0].name)
        assertEquals("root2", result[1].name)
    }

    @Test
    fun `createWorkspaceFolders returns empty list when project has no content roots`() {
        val mockProject = mockk<Project>()
        val mockProjectRootManager = mockk<ProjectRootManager>()

        every { mockProject.isDefault } returns false
        every { ProjectRootManager.getInstance(mockProject) } returns mockProjectRootManager
        every { mockProjectRootManager.contentRoots } returns emptyArray()

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertEquals(emptyList<VirtualFile>(), result)
    }

    private fun createMockVirtualFile(uri: URI, name: String): VirtualFile {
        val path = mockk<Path> {
            every { toUri() } returns uri
        }
        return mockk<VirtualFile> {
            every { url } returns uri.toString()
            every { getName() } returns name
            every { toNioPath() } returns path
            every { isDirectory } returns false
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
        }
    }

    // for windows unit tests
    private fun normalizeFileUri(uri: String): String {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return uri
        }

        if (!uri.startsWith("file:///")) {
            return uri
        }

        val path = uri.substringAfter("file:///")
        return "file:///C:/$path"
    }
}
