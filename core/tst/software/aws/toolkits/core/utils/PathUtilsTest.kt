package software.aws.toolkits.core.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectory
import kotlin.io.path.getPosixFilePermissions
import kotlin.test.assertNotNull

class PathUtilsTest {
    @Test
    fun `hasPosixFilePermissions()`(@TempDir temp: Path) {
        // no way to test other file systems at the moment
        val hasPosixFilePermissions = tryOrNull { temp.getPosixFilePermissions() } != null
        assertThat(temp.hasPosixFilePermissions()).isEqualTo(hasPosixFilePermissions)
    }

    @Test
    fun `touch() creates file`(@TempDir temp: Path) {
        val sut = temp.resolve("dasfasdfas")
        assertThat(sut).doesNotExist()

        sut.touch()

        assertThat(sut).exists()
        if (sut.hasPosixFilePermissions()) {
            assertThat(PosixFilePermissions.toString(sut.getPosixFilePermissions())).isEqualTo("rw-r--r--")
        }
    }

    @Test
    fun `touch() on existing file does not throw`(@TempDir temp: Path) {
        val sut = temp.resolve("dasfasdfas")
        assertThat(sut).doesNotExist()

        sut.touch()
        sut.touch()

        assertThat(sut).exists()
    }

    @Test
    fun `touch(restrictToOwner) works`(@TempDir temp: Path) {
        val sut = temp.resolve("sut")
        assertThat(sut).doesNotExist()

        sut.touch(restrictToOwner = true)

        assertThat(sut).exists()
        if (sut.hasPosixFilePermissions()) {
            assertThat(PosixFilePermissions.toString(sut.getPosixFilePermissions())).isEqualTo("rw-------")
        }
    }

    @Test
    fun `createParentDirectories() works`(@TempDir temp: Path) {
        val parent = temp.resolve("sut")
        val child = parent.resolve("child")
        assertThat(parent).doesNotExist()
        assertThat(child).doesNotExist()

        child.resolve("child2").createParentDirectories()

        assertThat(parent).exists()
        assertThat(child).exists()
        if (child.hasPosixFilePermissions()) {
            assertThat(PosixFilePermissions.toString(parent.getPosixFilePermissions())).isEqualTo("rwxr-xr-x")
            assertThat(PosixFilePermissions.toString(child.getPosixFilePermissions())).isEqualTo("rwxr-xr-x")
        }
    }

    @Test
    fun `createParentDirectories(restrictToOwner) works`(@TempDir temp: Path) {
        val parent = temp.resolve("sut")
        val child = parent.resolve("child")
        assertThat(parent).doesNotExist()
        assertThat(child).doesNotExist()

        child.resolve("child2").createParentDirectories(restrictToOwner = true)

        assertThat(parent).exists()
        assertThat(child).exists()
        if (child.hasPosixFilePermissions()) {
            assertThat(PosixFilePermissions.toString(parent.getPosixFilePermissions())).isEqualTo("rwx------")
            assertThat(PosixFilePermissions.toString(child.getPosixFilePermissions())).isEqualTo("rwx------")
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `builds useful exception message`(@TempDir temp: Path) {
        // need to come up with a non-painful way to setup this case to test posix...
        val sut = temp.resolve("sut")
        val aclView = Files.getFileAttributeView(temp, AclFileAttributeView::class.java)
        aclView.apply {
            acl = acl.map {
                if (it.principal() != owner) {
                    it
                } else {
                    AclEntry.newBuilder(it)
                        .setPermissions(it.permissions().minus(AclEntryPermission.ADD_SUBDIRECTORY))
                        .build()
                }
            }
        }

        assertThatExceptionOfType(java.nio.file.AccessDeniedException::class.java)
            .isThrownBy {
                sut.tryDirOp(mock()) {
                    createDirectory()
                }
            }
            .withMessageContaining("Potential issue is with $temp")
            .withMessageContaining("which has owner")
            .withMessageContaining("and ACL entries for")
            .withMessageNotContaining("POSIX permissions")
            .satisfies {
                assertThat(it.stackTraceToString()).doesNotContain("tryAugmentExceptionMessage")
                    .withFailMessage { "augmented exception does not reuse original stack trace" }
            }
    }
}
