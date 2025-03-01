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
        val mockContentRoot1 = mockk<VirtualFile>()
        val mockContentRoot2 = mockk<VirtualFile>()

        every { mockProject.isDefault } returns false
        every { ProjectRootManager.getInstance(mockProject) } returns mockProjectRootManager
        every { mockProjectRootManager.contentRoots } returns arrayOf(mockContentRoot1, mockContentRoot2)

        every { mockContentRoot1.name } returns "root1"
        every { mockContentRoot1.url } returns "file:///path/to/root1"
        every { mockContentRoot2.name } returns "root2"
        every { mockContentRoot2.url } returns "file:///path/to/root2"

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertEquals(2, result.size)
        assertEquals("file:///path/to/root1", result[0].uri)
        assertEquals("file:///path/to/root2", result[1].uri)
        assertEquals("root1", result[0].name)
        assertEquals("root2", result[1].name)
    }

    @Test
    fun `reateWorkspaceFolders returns empty list when project has no content roots`() {
        val mockProject = mockk<Project>()
        val mockProjectRootManager = mockk<ProjectRootManager>()

        every { mockProject.isDefault } returns false
        every { ProjectRootManager.getInstance(mockProject) } returns mockProjectRootManager
        every { mockProjectRootManager.contentRoots } returns emptyArray()

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertEquals(emptyList<VirtualFile>(), result)
    }
}
