// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class FileUriUtilTest : BasePlatformTestCase() {

    private fun createMockVirtualFile(path: String, protocol: String = "file", isDirectory: Boolean = false): VirtualFile =
        mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { this@mockk.protocol } returns protocol
            }
            every { url } returns path
            every { this@mockk.isDirectory } returns isDirectory
        }

    private fun normalizeFileUri(uri: String) : String {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            return uri
        }

        if  (!uri.startsWith("file:///")) {
            return uri
        }

        val path = uri.substringAfter("file:///")
        return "file:///C:/$path"
    }

    @Test
    fun `test basic unix path`() {
        val virtualFile = createMockVirtualFile("/path/to/file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        val expected =  normalizeFileUri("file:///path/to/file.txt")
        assertEquals(expected, uri)
    }

    @Test
    fun `test unix directory path`() {
        val virtualFile = createMockVirtualFile("/path/to/directory/", isDirectory = true)
        val uri = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/directory")
        assertEquals(expected, uri)
    }

    @Test
    fun `test path with spaces`() {
        val virtualFile = createMockVirtualFile("/path/with spaces/file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/with%20spaces/file.txt")
        assertEquals(expected, uri)
    }

    @Test
    fun `test root path`() {
        val virtualFile = createMockVirtualFile("/")
        val uri = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///")
        assertEquals(expected , uri)
    }

    @Test
    fun `test path with multiple separators`() {
        val virtualFile = createMockVirtualFile("/path//to///file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("file:///path/to/file.txt")
        assertEquals(expected, uri)
    }

    @Test
    fun `test very long path`() {
        val longPath = "/a".repeat(256) + "/file.txt"
        val virtualFile = createMockVirtualFile(longPath)
        val uri = FileUriUtil.toUriString(virtualFile)
        if (uri != null) {
            assertTrue(uri.startsWith("file:///"))
            assertTrue(uri.endsWith("/file.txt"))
        }
    }

    @Test
    fun `test relative path`() {
        val virtualFile = createMockVirtualFile("./relative/path/file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        if (uri != null) {
            assertTrue(uri.contains("file.txt"))
            assertTrue(uri.startsWith("file:///"))
        }
    }

    @Test
    fun `test jar protocol conversion`() {
        val virtualFile = createMockVirtualFile(
            "jar:file:///path/to/archive.jar!/com/example/Test.class",
            "jar"
        )
        val result = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jar:file:///path/to/archive.jar!/com/example/Test.class")
        assertEquals(expected, result)
    }

    @Test
    fun `test jrt protocol conversion`() {
        val virtualFile = createMockVirtualFile(
            "jrt://java.base/java/lang/String.class",
            "jrt"
        )
        val result = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jrt://java.base/java/lang/String.class")
        assertEquals(expected, result)
    }

    @Test
    fun `test invalid jar url returns null`() {
        val virtualFile = createMockVirtualFile(
            "invalid:url:format",
            "jar"
        )
        val result = FileUriUtil.toUriString(virtualFile)
        assertNull(result)
    }

    @Test
    fun `test jar protocol with directory`() {
        val virtualFile = createMockVirtualFile(
            "jar:file:///path/to/archive.jar!/com/example/",
            "jar",
            true
        )
        val result = FileUriUtil.toUriString(virtualFile)
        val expected = normalizeFileUri("jar:file:///path/to/archive.jar!/com/example")
        assertEquals(expected, result)
    }

    @Test
    fun `test empty url in jar protocol`() {
        val virtualFile = createMockVirtualFile(
            "",
            "jar",
            true
        )
        val result = FileUriUtil.toUriString(virtualFile)
        assertNull(result)
    }
}
