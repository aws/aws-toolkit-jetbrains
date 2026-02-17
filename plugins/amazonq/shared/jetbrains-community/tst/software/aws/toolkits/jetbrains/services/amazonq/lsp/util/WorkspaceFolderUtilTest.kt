// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

class WorkspaceFolderUtilTest {

    @Test
    fun `createWorkspaceFolders returns empty list when project is default`() {
        val mockProject = mockk<Project>()
        every { mockProject.isDefault } returns true

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result).isEmpty()
    }

    @Test
    fun `createWorkspaceFolders returns workspace folders for non-default project with modules`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val mockModule1 = mockk<Module>()
        val mockModule2 = mockk<Module>()
        val mockModuleRootManager1 = mockk<ModuleRootManager>()
        val mockModuleRootManager2 = mockk<ModuleRootManager>()

        val mockContentRoot1 = createMockVirtualFile(
            URI("file:///path/to/root1"),
            name = "root1"
        )
        val mockContentRoot2 = createMockVirtualFile(
            URI("file:///path/to/root2"),
            name = "root2"
        )

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(mockModule1, mockModule2)
        every { mockModule1.name } returns "module1"
        every { mockModule2.name } returns "module2"
        every { ModuleRootManager.getInstance(mockModule1) } returns mockModuleRootManager1
        every { ModuleRootManager.getInstance(mockModule2) } returns mockModuleRootManager2
        every { mockModuleRootManager1.contentRoots } returns arrayOf(mockContentRoot1)
        every { mockModuleRootManager2.contentRoots } returns arrayOf(mockContentRoot2)

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result).hasSize(2)
        assertThat(result[0].uri).isEqualTo(normalizeFileUri("file:///path/to/root1"))
        assertThat(result[1].uri).isEqualTo(normalizeFileUri("file:///path/to/root2"))
        assertThat(result[0].name).isEqualTo("module1")
        assertThat(result[1].name).isEqualTo("module2")
    }

    @Test
    fun `createWorkspaceFolders handles modules with no content roots`() {
        val mockProject = mockk<Project>()
        val mockModuleManager = mockk<ModuleManager>()
        val mockModule = mockk<Module>()
        val mockModuleRootManager = mockk<ModuleRootManager>()

        mockkStatic(ModuleManager::class, ModuleRootManager::class)

        every { mockProject.isDefault } returns false
        every { ModuleManager.getInstance(mockProject) } returns mockModuleManager
        every { mockModuleManager.modules } returns arrayOf(mockModule)
        every { ModuleRootManager.getInstance(mockModule) } returns mockModuleRootManager
        every { mockModuleRootManager.contentRoots } returns emptyArray()

        val result = WorkspaceFolderUtil.createWorkspaceFolders(mockProject)

        assertThat(result).isEmpty()
    }

    private fun createMockVirtualFile(uri: URI, name: String): VirtualFile =
        mockk<VirtualFile> {
            every { url } returns uri.toString()
            every { getName() } returns name
            every { isDirectory } returns false
            every { fileSystem } returns mockk {
                every { protocol } returns "file"
            }
        }

    // for windows unit tests
    private val windowsDrive: String
        get() = java.nio.file.Paths.get("").toAbsolutePath().root
            ?.toString()?.firstOrNull()?.uppercaseChar()?.toString() ?: "C"

    private fun normalizeFileUri(uri: String): String {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return uri
        }
        if (!uri.startsWith("file:///")) {
            return uri
        }
        val path = uri.substringAfter("file:///")
        return "file:///$windowsDrive:/$path"
    }
}
