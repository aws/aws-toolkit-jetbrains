// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import com.intellij.openapi.util.SystemInfo
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class LspInstallerDownloadTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val lspName = "test-lsp"

    @Test
    fun `downloads all content entries`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir)
        val zip1 = createZipWithFile("srv.js", "server content")
        val zip2 = createZipWithFile("extra.dat", "extra content")
        val downloadedUrls = mutableListOf<String>()

        var callCount = 0
        val installer = createInstaller(
            baseRoot,
            httpGetBytes = { url ->
                downloadedUrls.add(url)
                if (callCount++ == 0) zip1 else zip2
            }
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip1.size.toLong()),
                LspContentEntry("extras.zip", "https://example.com/extras.zip", emptyList(), zip2.size.toLong()),
            )
        )

        installer.downloadAndInstall(release)

        assertThat(downloadedUrls).containsExactly(
            "https://example.com/server.zip",
            "https://example.com/extras.zip"
        )
    }

    @Test
    fun `retries each content exactly 3 times on failure`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir)
        val zip = createZipWithFile("srv.js", "content")
        var attempts = 0

        val installer = createInstaller(
            baseRoot,
            httpGetBytes = {
                attempts++
                if (attempts <= 2) throw RuntimeException("Network error")
                zip
            },
            sleep = {},
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        installer.downloadAndInstall(release)
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun `verifies hash for each content entry`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir)

        val zipData = createZipWithFile("srv.js", "hello")
        val correctHash = BaseLspInstaller.computeHash(zipData, "sha256")

        val installer = createInstaller(baseRoot, httpGetBytes = { zipData })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", listOf("sha256:$correctHash"), zipData.size.toLong()),
            )
        )

        val path = installer.downloadAndInstall(release)
        assertThat(path).isNotNull()
    }

    @Test
    fun `fails on hash mismatch`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir)

        val zip = createZipWithFile("srv.js", "content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", listOf("sha256:badhash"), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("Hash verification failed")
    }

    @Test
    fun `fails closed when manifest hashes are declared but all malformed`() {
        val (baseRoot, storageDir) = createLayout()

        val zip = createZipWithFile("srv.js", "content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", listOf("not-a-valid-hash"), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("Hash verification failed")

        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
    }

    @Test
    fun `fails with DOWNLOAD_FAILED when byte count does not match manifest`() {
        val (baseRoot, storageDir) = createLayout()

        val zip = createZipWithFile("srv.js", "server content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                // Declared size is deliberately one byte larger than the actual download.
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong() + 1),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("size mismatch")

        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
    }

    @Test
    fun `rejects non-zip content whose filename escapes the install root`() {
        val (baseRoot, storageDir) = createLayout()

        val payload = "malicious".toByteArray()
        val installer = createInstaller(baseRoot, httpGetBytes = { payload })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("../../evil.txt", "https://example.com/evil", emptyList(), payload.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("outside install root")

        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
        assertThat(Files.exists(storageDir.parent.resolve("evil.txt"))).isFalse()
        assertThat(Files.exists(baseRoot.resolve("evil.txt"))).isFalse()
        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
    }

    @Test
    fun `rejects a zip entry that traverses outside the install root`() {
        val (baseRoot, storageDir) = createLayout()

        val zip = createZipWithFile("../evil.txt", "malicious")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.EXTRACTION_FAILED)
            }

        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("evil.txt"))).isFalse()
        assertThat(Files.exists(storageDir.parent.resolve("evil.txt"))).isFalse()
        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
    }

    @Test
    fun `a preflight failure removes the version directory including pre-existing content`() {
        val (baseRoot, storageDir) = createLayout()

        // A pre-existing (partial) version directory from an earlier attempt holds a sentinel.
        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("sentinel.txt"), "SENTINEL")

        // The archive's first entry is safe; the second escapes the version directory. Every entry is
        // preflighted before any write, and a failed install removes the whole version directory.
        val zip = createZipWithEntries("good.txt" to "safe", "../evil.txt" to "pwned")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.EXTRACTION_FAILED)
            }

        assertThat(Files.exists(versionDir)).isFalse()
        assertThat(Files.exists(storageDir.resolve("evil.txt"))).isFalse()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `preflight prevents an earlier archive from writing when a later archive escapes`() {
        val (baseRoot, storageDir) = createLayout()

        val safeZip = createZipWithFile("safe.js", "safe")
        val maliciousZip = createZipWithEntries("../evil.txt" to "pwned")
        var callCount = 0
        val installer = createInstaller(
            baseRoot,
            httpGetBytes = { if (callCount++ == 0) safeZip else maliciousZip }
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("safe.zip", "https://example.com/safe.zip", emptyList(), safeZip.size.toLong()),
                LspContentEntry("evil.zip", "https://example.com/evil.zip", emptyList(), maliciousZip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.EXTRACTION_FAILED)
            }

        // The entire content set is preflighted before any write, so the safe archive's entry is
        // never written once the later archive is found to escape.
        val versionDir = storageDir.resolve("1.0.0")
        assertThat(Files.exists(versionDir.resolve("safe.js"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("evil.txt"))).isFalse()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `extracts all zip contents without retaining downloaded archives`() {
        val (baseRoot, storageDir) = createLayout()

        val zip1 = createZipWithFile("srv.js", "server content")
        val zip2 = createZipWithFile("extra.dat", "extra content")
        var callCount = 0
        val installer = createInstaller(
            baseRoot,
            httpGetBytes = { if (callCount++ == 0) zip1 else zip2 }
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip1.size.toLong()),
                LspContentEntry("extras.zip", "https://example.com/extras.zip", emptyList(), zip2.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)
        assertThat(serverPath).isNotNull()

        val versionDir = storageDir.resolve("1.0.0")
        assertThat(Files.exists(versionDir.resolve("srv.js"))).isTrue()
        assertThat(Files.exists(versionDir.resolve("extra.dat"))).isTrue()

        val names = allEntryNames(storageDir)
        assertThat(names).noneMatch { it.endsWith(".zip", ignoreCase = true) }
        assertThat(names).noneMatch { it.contains(".tmp.") }
    }

    @Test
    fun `preserves zip entry POSIX permissions`() {
        assumeTrue(SystemInfo.isUnix)
        val (baseRoot, _) = createLayout()
        val zip = createZipWithFile("srv.js", "server content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })
        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)

        assertThat(Files.getPosixFilePermissions(serverPath)).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ,
        )
    }

    @Test
    fun `installs directly into the version directory`() {
        val (baseRoot, storageDir) = createLayout()

        val zip = createZipWithFile("srv.js", "server content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)

        val versionDir = storageDir.resolve("1.0.0")
        assertThat(serverPath).isEqualTo(versionDir.resolve("srv.js"))
        assertThat(Files.readString(serverPath)).isEqualTo("server content")
        assertThat(installer.resolvedDir).isEqualTo(versionDir)

        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
    }

    @Test
    fun `always overwrites a complete existing install and retains unrelated files`() {
        val (baseRoot, storageDir) = createLayout()

        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("unrelated.txt"), "KEEP")
        Files.writeString(versionDir.resolve("srv.js"), "STALE")

        val zip = createZipWithFile("srv.js", "FRESH")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)

        assertThat(serverPath).isEqualTo(versionDir.resolve("srv.js"))
        assertThat(Files.readString(serverPath)).isEqualTo("FRESH")
        assertThat(Files.readString(versionDir.resolve("unrelated.txt"))).isEqualTo("KEEP")
    }

    @Test
    fun `overwrites stale server file when version directory already exists`() {
        val (baseRoot, storageDir) = createLayout()

        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("srv.js"), "STALE")

        val config = downloadTestConfig(baseRoot, requiredFiles = listOf("srv.js", "extra.dat"))
        val zip = createZipWithNestedStructure(
            "srv.js" to "FRESH",
            "extra.dat" to "data",
        )
        val installer = DownloadTestInstaller(config, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)

        assertThat(serverPath).isEqualTo(versionDir.resolve("srv.js"))
        assertThat(Files.readString(serverPath)).isEqualTo("FRESH")
        assertThat(Files.readString(versionDir.resolve("extra.dat"))).isEqualTo("data")
    }

    @Test
    fun `overwrites stale nested files when version directory already exists`() {
        val (baseRoot, storageDir) = createLayout()

        val versionDir = storageDir.resolve("1.0.0")
        val nestedDir = versionDir.resolve("cloudformation-language-server")
        Files.createDirectories(nestedDir)
        Files.writeString(nestedDir.resolve("srv.js"), "STALE")

        val config = downloadTestConfig(baseRoot, requiredFiles = listOf("srv.js", "lib"))
        val zip = createZipWithNestedStructure(
            "cloudformation-language-server/srv.js" to "FRESH",
            "cloudformation-language-server/lib/util.js" to "FRESH-LIB",
        )
        val installer = DownloadTestInstaller(config, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)

        assertThat(serverPath).isEqualTo(nestedDir.resolve("srv.js"))
        assertThat(Files.readString(serverPath)).isEqualTo("FRESH")
        assertThat(Files.readString(nestedDir.resolve("lib").resolve("util.js"))).isEqualTo("FRESH-LIB")
    }

    @Test
    fun `removes the version directory and leaves resolved state unset when the server file is missing`() {
        val (baseRoot, storageDir) = createLayout()

        val zip = createZipWithFile("wrong.js", "content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("Server file")

        val versionDir = storageDir.resolve("1.0.0")
        assertThat(Files.exists(versionDir)).isFalse()
        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `removes a stale version directory when the download fails`() {
        val (baseRoot, storageDir) = createLayout()

        // A leftover partial directory from an earlier attempt must be cleared when a re-download fails.
        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("stale.txt"), "STALE")

        val installer = createInstaller(
            baseRoot,
            httpGetBytes = { throw java.io.IOException("network down") },
            sleep = {},
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), 10L),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.DOWNLOAD_FAILED)
            }

        assertThat(Files.exists(versionDir)).isFalse()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `removes a pre-existing stale version directory when hash verification fails`() {
        val (baseRoot, storageDir) = createLayout()

        // A leftover partial directory from an earlier attempt must be cleared when a re-download's
        // content fails hash verification, and the HASH_VERIFICATION_FAILED code must be preserved.
        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("stale.txt"), "STALE")

        val zip = createZipWithFile("srv.js", "content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", listOf("sha256:badhash"), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.HASH_VERIFICATION_FAILED)
            }

        assertThat(Files.exists(versionDir)).isFalse()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `a failed reinstall of the resolved version clears the now-deleted resolved directory`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.0.0")

        val zip = createZipWithFile("srv.js", "content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val goodRelease = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )
        installer.downloadAndInstall(goodRelease)
        assertThat(installer.resolvedDir).isEqualTo(versionDir)

        // A repeated install of the same, already-resolved version fails and deletes its directory, so
        // the resolved pointer must not keep referencing the removed install.
        val corruptRelease = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", listOf("sha256:badhash"), zip.size.toLong()),
            )
        )
        assertThatThrownBy { installer.downloadAndInstall(corruptRelease) }
            .isInstanceOf(LspInstallException::class.java)

        assertThat(Files.exists(versionDir)).isFalse()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `a failed install of a different version preserves the resolved fallback directory`() {
        val (baseRoot, storageDir) = createLayout()
        val fallbackDir = storageDir.resolve("1.5.0")

        val zip = createZipWithFile("srv.js", "content")
        val installer = createInstaller(baseRoot, httpGetBytes = { zip })

        val fallbackRelease = LspRelease(
            version = "1.5.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )
        installer.downloadAndInstall(fallbackRelease)
        assertThat(installer.resolvedDir).isEqualTo(fallbackDir)

        // A later install of a different version fails; the resolved fallback must be left intact.
        val corruptRelease = LspRelease(
            version = "1.9.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", listOf("sha256:badhash"), zip.size.toLong()),
            )
        )
        assertThatThrownBy { installer.downloadAndInstall(corruptRelease) }
            .isInstanceOf(LspInstallException::class.java)

        assertThat(Files.exists(storageDir.resolve("1.9.0"))).isFalse()
        assertThat(installer.resolvedDir).isEqualTo(fallbackDir)
        assertThat(Files.exists(fallbackDir)).isTrue()
    }

    @Test
    fun `removes the version directory when postInstall fails`() {
        val (baseRoot, storageDir) = createLayout()

        val zip = createZipWithFile("srv.js", "content")
        val installer = DownloadTestInstaller(
            downloadTestConfig(baseRoot),
            httpGetBytes = { zip },
            postInstallAction = { error("postInstall failed") },
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.EXTRACTION_FAILED)
            }

        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `a cleanup failure does not mask the original install error`() {
        val (baseRoot, _) = createLayout()

        // The archive has no server file, so final validation fails first...
        val zip = createZipWithFile("wrong.js", "content")
        val cleanupError = RuntimeException("cleanup boom")
        val installer = DownloadTestInstaller(
            downloadTestConfig(baseRoot),
            httpGetBytes = { zip },
            removeDirectory = { throw cleanupError },
        )

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val thrown = catchThrowable { installer.downloadAndInstall(release) }

        // ...so the validation failure propagates and the cleanup error is only attached as suppressed.
        assertThat(thrown)
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("Server file")
        assertThat(thrown.suppressed).contains(cleanupError)
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `validates nested zip with server in subdirectory`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir)

        val config = downloadTestConfig(baseRoot, requiredFiles = listOf("srv.js", "bin", "node_modules"))
        val zip = createZipWithNestedStructure(
            "cloudformation-language-server/srv.js" to "server content",
            "cloudformation-language-server/bin/cfn-init" to "init script",
            "cloudformation-language-server/node_modules/.package-lock.json" to "{}",
        )
        val installer = DownloadTestInstaller(config, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        val serverPath = installer.downloadAndInstall(release)
        assertThat(serverPath).isNotNull()
        assertThat(serverPath.fileName.toString()).isEqualTo("srv.js")
        assertThat(Files.exists(serverPath.parent.resolve("bin"))).isTrue()
        assertThat(Files.exists(serverPath.parent.resolve("node_modules"))).isTrue()
    }

    @Test
    fun `fails when nested zip missing required files relative to server`() {
        val (baseRoot, storageDir) = createLayout()
        Files.createDirectories(storageDir)

        val config = downloadTestConfig(baseRoot, requiredFiles = listOf("srv.js", "bin", "node_modules"))
        val zip = createZipWithNestedStructure(
            "nested/srv.js" to "server content",
            "nested/node_modules/.package-lock.json" to "{}",
        )
        val installer = DownloadTestInstaller(config, httpGetBytes = { zip })

        val release = LspRelease(
            version = "1.0.0",
            contents = listOf(
                LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), zip.size.toLong()),
            )
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("bin")

        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
        assertThat(allEntryNames(storageDir)).noneMatch { it.contains(".tmp.") }
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `parseHashString splits algorithm and digest`() {
        val result = BaseLspInstaller.parseHashString("sha256:abcdef0123456789")
        assertThat(result).isEqualTo("sha256" to "abcdef0123456789")
    }

    @Test
    fun `parseHashString returns null when no colon present`() {
        assertThat(BaseLspInstaller.parseHashString("nocolon")).isNull()
    }

    @Test
    fun `parseHashString returns null for empty algorithm or digest`() {
        assertThat(BaseLspInstaller.parseHashString(":abc")).isNull()
        assertThat(BaseLspInstaller.parseHashString("sha256:")).isNull()
    }

    @Test
    fun `computeHash produces correct sha256`() {
        val hash = BaseLspInstaller.computeHash("hello".toByteArray(), "sha256")
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    }

    @Test
    fun `computeHash produces correct sha512`() {
        val hash = BaseLspInstaller.computeHash("hello".toByteArray(), "sha512")
        assertThat(hash).hasSize(128)
    }

    @Test
    fun `computeHash produces correct sha384`() {
        val hash = BaseLspInstaller.computeHash("test data".toByteArray(), "sha384")
        assertThat(hash).hasSize(96)
    }

    private fun createLayout(): Pair<Path, Path> {
        val baseRoot = tempFolder.newFolder("base-${System.nanoTime()}").toPath()
        val storageDir = baseRoot.resolve("language-servers").resolve(lspName)
        Files.createDirectories(storageDir)
        return baseRoot to storageDir
    }

    private fun allEntryNames(root: Path): List<String> = root.toFile().walkTopDown().map { it.name }.toList()

    private fun downloadTestConfig(baseRoot: Path, requiredFiles: List<String> = emptyList()) = LspInstallerConfig(
        name = lspName,
        supportedVersionRange = SemVerParser.parseRange("<100.0.0"),
        manifestUrl = "https://example.com/manifest.json",
        serverFilename = "srv.js",
        requiredFiles = requiredFiles,
        storageDir = baseRoot.resolve("language-servers").resolve(lspName),
    )

    private fun createInstaller(
        baseRoot: Path,
        httpGetBytes: (String) -> ByteArray = { error("network not expected in test: $it") },
        sleep: (Long) -> Unit = {},
    ): DownloadTestInstaller =
        DownloadTestInstaller(downloadTestConfig(baseRoot), httpGetBytes = httpGetBytes, sleep = sleep)

    private fun createZipWithFile(filename: String, content: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zos ->
            zos.putArchiveEntry(ZipArchiveEntry(filename).apply { unixMode = FILE_MODE })
            zos.write(content.toByteArray())
            zos.closeArchiveEntry()
        }
        return baos.toByteArray()
    }

    private fun createZipWithEntries(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putArchiveEntry(ZipArchiveEntry(name).apply { unixMode = FILE_MODE })
                zos.write(content.toByteArray())
                zos.closeArchiveEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun createZipWithNestedStructure(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipArchiveOutputStream(baos).use { zos ->
            val createdDirs = mutableSetOf<String>()
            for ((path, content) in entries) {
                val parts = path.split("/")
                for (i in 1 until parts.size) {
                    val dirPath = parts.subList(0, i).joinToString("/") + "/"
                    if (dirPath !in createdDirs) {
                        zos.putArchiveEntry(ZipArchiveEntry(dirPath).apply { unixMode = DIR_MODE })
                        zos.closeArchiveEntry()
                        createdDirs.add(dirPath)
                    }
                }
                zos.putArchiveEntry(ZipArchiveEntry(path).apply { unixMode = FILE_MODE })
                zos.write(content.toByteArray())
                zos.closeArchiveEntry()
            }
        }
        return baos.toByteArray()
    }

    private companion object {
        // Archive entry modes so the decompressor grants readable/searchable POSIX permissions to
        // the extracted files and directories (entries default to mode 0, i.e. no permissions).
        val FILE_MODE = "644".toInt(8)
        val DIR_MODE = "755".toInt(8)
    }
}

private class DownloadTestInstaller(
    config: LspInstallerConfig,
    httpGetBytes: (String) -> ByteArray = { error("network not expected in test: $it") },
    sleep: (Long) -> Unit = {},
    removeDirectory: (Path) -> Unit = { LspFileUtils.deleteRecursively(it) },
    private val postInstallAction: (Path) -> Unit = {},
) : BaseLspInstaller(
    config = config,
    manifestAdapter = DownloadTestNoOpAdapter,
    httpGetBytes = httpGetBytes,
    sleep = sleep,
    removeDirectory = removeDirectory,
) {
    val resolvedDir: Path?
        get() = resolvedVersionDir.get()

    public override fun postInstall(versionDir: Path) = postInstallAction(versionDir)
}

private object DownloadTestNoOpAdapter : ManifestAdapter {
    override fun parseManifest(json: String): LspManifest = error("Not expected in test")
}
