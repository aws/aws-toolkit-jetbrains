package software.aws.toolkits.core.utils

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    @Test fun fileCanBeAddedToAZip() {
        val fileToAdd = tmpFolder.newFile()
        fileToAdd.writeText("hello world", StandardCharsets.UTF_8)
        val zipFile = tmpFolder.newFile("blah.zip")
        ZipOutputStream(Files.newOutputStream(zipFile.toPath())).use {
            it.putNextEntry("file.txt", fileToAdd.toPath())
        }

        assertZipContainsHelloWorldFile(zipFile)
    }

    @Test fun inputStreamCanBeAddedToAZip() {
        val zipFile = tmpFolder.newFile("blah.zip")
        ZipOutputStream(Files.newOutputStream(zipFile.toPath())).use {
            it.putNextEntry("file.txt", "hello world".byteInputStream(StandardCharsets.UTF_8))
        }

        assertZipContainsHelloWorldFile(zipFile)
    }

    private fun assertZipContainsHelloWorldFile(zipFile: File?) {
        val actualZip = ZipFile(zipFile)
        val actualEntry = actualZip.entries().toList().find { it.name == "file.txt" }

        assert(actualEntry).isNotNull()
        val contents = actualZip.getInputStream(actualEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        assert(contents).isEqualTo("hello world")
    }
}