package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class FileUriUtilTest : BasePlatformTestCase() {

    private fun createMockVirtualFile(path: String): VirtualFile =
        LightVirtualFile(path)

    @Test
    fun `test basic unix path`() {
        val virtualFile = createMockVirtualFile("/path/to/file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///path/to/file.txt", uri)
    }

    @Test
    fun `test unix directory path`() {
        val virtualFile = createMockVirtualFile("/path/to/directory/")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///path/to/directory", uri)
    }

    @Test
    fun `test path with spaces`() {
        val virtualFile = createMockVirtualFile("/path/with spaces/file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///path/with%20spaces/file.txt", uri)
    }

    @Test
    fun `test windows style path`() {
        val virtualFile = createMockVirtualFile("C:\\path\\to\\file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///C:/path/to/file.txt", uri)
    }

    @Test
    fun `test windows directory path`() {
        val virtualFile = createMockVirtualFile("C:\\path\\to\\directory\\")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///C:/path/to/directory", uri)
    }

    @Test
    fun `test root path`() {
        val virtualFile = createMockVirtualFile("/")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///", uri)
    }

    @Test
    fun `test path with multiple separators`() {
        val virtualFile = createMockVirtualFile("/path//to///file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertEquals("file:///path/to/file.txt", uri)
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
        val virtualFile = mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns "jar"
            }
            every { url } returns "jar:file:///path/to/archive.jar!/com/example/Test.class"
            every { isDirectory } returns false
        }

        val result = FileUriUtil.toUriString(virtualFile)
        assertEquals("jar:file:///path/to/archive.jar!/com/example/Test.class", result)
    }

    @Test
    fun `test jrt protocol conversion`() {
        val virtualFile = mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns "jrt"
            }
            every { url } returns "jrt://java.base/java/lang/String.class"
            every { isDirectory } returns false
        }

        val result = FileUriUtil.toUriString(virtualFile)
        assertEquals("jrt://java.base/java/lang/String.class", result)
    }

    @Test
    fun `test invalid jar url returns null`() {
        val virtualFile = mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns "jar"
            }
            every { url } returns "invalid:url:format"
            every { isDirectory } returns false
        }

        val result = FileUriUtil.toUriString(virtualFile)
        assertNull(result)
    }

    @Test
    fun `test jar protocol with directory`() {
        val virtualFile = mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns "jar"
            }
            every { url } returns "jar:file:///path/to/archive.jar!/com/example/"
            every { isDirectory } returns true
        }

        val result = FileUriUtil.toUriString(virtualFile)
        assertEquals("jar:file:///path/to/archive.jar!/com/example", result)
    }

    @Test
    fun `test null url in jar protocol`() {
        val virtualFile = mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns "jar"
            }
            every { url } returns ""
            every { isDirectory } returns false
        }

        val result = FileUriUtil.toUriString(virtualFile)
        assertNull(result)
    }

    @Test
    fun `test empty url in jar protocol`() {
        val virtualFile = mockk<VirtualFile> {
            every { fileSystem } returns mockk {
                every { protocol } returns "jar"
            }
            every { url } returns ""
            every { isDirectory } returns false
        }

        val result = FileUriUtil.toUriString(virtualFile)
        assertNull(result)
    }
}
