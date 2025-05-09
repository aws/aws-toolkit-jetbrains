// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationExtension
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApplicationExtension::class)
class FileUriUtilTest {

    private fun createMockVirtualFile(path: String, mockProtocol: String = "file", mockIsDirectory: Boolean = false): VirtualFile =
        mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns mockProtocol
            }
            every { url } returns path
            every { isDirectory } returns mockIsDirectory
        }

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

    @Test
    fun `test basic unix path`() {
        val virtualFile = createMockVirtualFile("/path/to/file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/file.txt")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test unix directory path`() {
        val virtualFile = createMockVirtualFile("/path/to/directory/", mockIsDirectory = true)
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/directory")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test path with spaces`() {
        val virtualFile = createMockVirtualFile("/path/with spaces/file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/with%20spaces/file.txt")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test root path`() {
        val virtualFile = createMockVirtualFile("/")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test path with multiple separators`() {
        val virtualFile = createMockVirtualFile("/path//to///file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/file.txt")
        assertThat(uri).isEqualTo(expected)
    }

    @Test
    fun `test very long path`() {
        val longPath = "/a".repeat(256) + "/file.txt"
        val virtualFile = createMockVirtualFile(longPath)
        val uri = LspEditorUtil.toUriString(virtualFile)
        if (uri != null) {
            assertThat(uri.startsWith("file:///")).isTrue
            assertThat(uri.endsWith("/file.txt")).isTrue
        }
    }

    @Test
    fun `test relative path`() {
        val virtualFile = createMockVirtualFile("./relative/path/file.txt")
        val uri = LspEditorUtil.toUriString(virtualFile)
        if (uri != null) {
            assertThat(uri.contains("file.txt")).isTrue
            assertThat(uri.startsWith("file:///")).isTrue
        }
    }

    @Test
    fun `test jar protocol conversion`() {
        val virtualFile = createMockVirtualFile(
            "jar:file:///path/to/archive.jar!/com/example/Test.class",
            "jar"
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jar:file:///path/to/archive.jar!/com/example/Test.class")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test jrt protocol conversion`() {
        val virtualFile = createMockVirtualFile(
            "jrt://java.base/java/lang/String.class",
            "jrt"
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jrt://java.base/java/lang/String.class")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test invalid jar url returns null`() {
        val virtualFile = createMockVirtualFile(
            "invalid:url:format",
            "jar"
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        assertThat(result).isNull()
    }

    @Test
    fun `test jar protocol with directory`() {
        val virtualFile = createMockVirtualFile(
            "jar:file:///path/to/archive.jar!/com/example/",
            "jar",
            true
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jar:file:///path/to/archive.jar!/com/example")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `test empty url in jar protocol`() {
        val virtualFile = createMockVirtualFile(
            "",
            "jar",
            true
        )
        val result = LspEditorUtil.toUriString(virtualFile)
        assertThat(result).isNull()
    }
}
