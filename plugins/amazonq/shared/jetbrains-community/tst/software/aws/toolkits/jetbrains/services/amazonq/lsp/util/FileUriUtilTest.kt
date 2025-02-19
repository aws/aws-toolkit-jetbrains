package software.aws.toolkits.jetbrains.services.amazonq.lsp.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
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
        assertTrue(uri.startsWith("file:///"))
        assertTrue(uri.endsWith("/file.txt"))
    }

    @Test
    fun `test relative path`() {
        val virtualFile = createMockVirtualFile("./relative/path/file.txt")
        val uri = FileUriUtil.toUriString(virtualFile)
        assertTrue(uri.contains("file.txt"))
        assertTrue(uri.startsWith("file:///"))
    }
}
