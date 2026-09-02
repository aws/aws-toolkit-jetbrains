// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class BaseLspInstallerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val lspName = "test-lsp"

    @Test
    fun `withRetries succeeds immediately when no error`() {
        var calls = 0
        val installer = createInstaller(tempFolder.root.toPath())
        val result = installer.withRetries("test") {
            calls++
            42
        }
        assertThat(result).isEqualTo(42)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `withRetries retries exactly 3 times`() {
        var calls = 0
        val installer = createInstaller(tempFolder.root.toPath())
        assertThatThrownBy {
            installer.withRetries("test") {
                calls++
                throw RuntimeException("always fails")
            }
        }.isInstanceOf(RuntimeException::class.java)
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `withRetries succeeds on third attempt`() {
        var calls = 0
        val installer = createInstaller(tempFolder.root.toPath())
        val result = installer.withRetries("test") {
            calls++
            if (calls < 3) throw RuntimeException("fail $calls")
            "recovered"
        }
        assertThat(result).isEqualTo("recovered")
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `withRetries propagates InterruptedException immediately`() {
        var calls = 0
        val installer = createInstaller(tempFolder.root.toPath())
        assertThatThrownBy {
            installer.withRetries("test") {
                calls++
                throw InterruptedException("interrupted")
            }
        }.isInstanceOf(InterruptedException::class.java)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `withRetries uses 500 then 1000 backoff via injectable sleeper`() {
        val sleepCalls = mutableListOf<Long>()
        val sleep: (Long) -> Unit = { millis -> sleepCalls.add(millis) }
        val installer = createInstaller(tempFolder.root.toPath(), sleep = sleep)

        var calls = 0
        assertThatThrownBy {
            installer.withRetries("test") {
                calls++
                throw RuntimeException("fail")
            }
        }
        assertThat(calls).isEqualTo(3)
        assertThat(sleepCalls).containsExactly(500L, 1000L)
    }

    @Test
    fun `resolveRelease selects highest compatible version with matching target`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot, resolvePlatform = { "darwin" }, resolveArch = { "arm64" })

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.0.0", targets = targets("darwin", "arm64")),
                LspManifestVersion(serverVersion = "1.5.0", targets = targets("darwin", "arm64")),
                LspManifestVersion(serverVersion = "2.0.0", targets = targets("darwin", "arm64")),
            )
        )
        val release = installer.resolveRelease(manifest)
        assertThat(release.version).isEqualTo("2.0.0")
    }

    @Test
    fun `resolveRelease skips higher version without matching platform target`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot, resolvePlatform = { "darwin" }, resolveArch = { "arm64" })

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.3.0", targets = targets("darwin", "arm64")),
                LspManifestVersion(serverVersion = "1.5.0", targets = targets("linux", "x64")),
                LspManifestVersion(serverVersion = "1.4.0", targets = targets("darwin", "arm64")),
            )
        )
        val release = installer.resolveRelease(manifest)
        assertThat(release.version).isEqualTo("1.4.0")
    }

    @Test
    fun `resolveRelease does not prefer older latest flag over higher semver with target`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot, resolvePlatform = { "darwin" }, resolveArch = { "arm64" })

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.2.0", latest = true, targets = targets("darwin", "arm64")),
                LspManifestVersion(serverVersion = "1.5.0", latest = false, targets = targets("darwin", "arm64")),
            )
        )
        val release = installer.resolveRelease(manifest)
        assertThat(release.version).isEqualTo("1.5.0")
    }

    @Test
    fun `resolveRelease skips delisted versions`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot, resolvePlatform = { "darwin" }, resolveArch = { "arm64" })

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.5.0", isDelisted = true, targets = targets("darwin", "arm64")),
                LspManifestVersion(serverVersion = "1.3.0", targets = targets("darwin", "arm64")),
            )
        )
        val release = installer.resolveRelease(manifest)
        assertThat(release.version).isEqualTo("1.3.0")
    }

    @Test
    fun `resolveRelease throws when no version has matching target`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot, resolvePlatform = { "darwin" }, resolveArch = { "arm64" })

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.5.0", targets = targets("linux", "x64")),
                LspManifestVersion(serverVersion = "1.3.0", targets = targets("win32", "x64")),
            )
        )
        assertThatThrownBy { installer.resolveRelease(manifest) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("No target found")
    }

    @Test
    fun `resolveRelease throws when all compatible versions are delisted`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot, resolvePlatform = { "darwin" }, resolveArch = { "arm64" })

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.5.0", isDelisted = true, targets = targets("darwin", "arm64")),
            )
        )
        assertThatThrownBy { installer.resolveRelease(manifest) }
            .isInstanceOf(LspInstallException::class.java)
            .hasMessageContaining("No compatible version")
    }

    @Test
    fun `findServerFileInDir finds server at root level`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.createFile(versionDir.resolve("srv.js"))

        val installer = createInstaller(baseRoot)
        assertThat(installer.findServerFileInDir(versionDir)).isNotNull
    }

    @Test
    fun `findServerFileInDir finds server in subdirectory`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.0.0")
        val subDir = versionDir.resolve("extracted")
        Files.createDirectories(subDir)
        Files.createFile(subDir.resolve("srv.js"))

        val installer = createInstaller(baseRoot)
        assertThat(installer.findServerFileInDir(versionDir)).isNotNull
    }

    @Test
    fun `findServerFileInDir returns null when file missing`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)

        val installer = createInstaller(baseRoot)
        assertThat(installer.findServerFileInDir(versionDir)).isNull()
    }

    @Test
    fun `findServerFileInDir returns null for nonexistent dir`() {
        val (baseRoot, _) = createLayout()
        val installer = createInstaller(baseRoot)
        assertThat(installer.findServerFileInDir(Path.of("/nonexistent/dir"))).isNull()
    }

    @Test
    fun `validateAllRequiredFiles checks all required files not just server`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.0.0")
        Files.createDirectories(versionDir)
        Files.createFile(versionDir.resolve("srv.js"))

        val config = testConfig(baseRoot, requiredFiles = listOf("srv.js", "extras"))
        val installer = InstallerTestHelper(config)
        assertThat(installer.validateAllRequiredFiles(versionDir)).isFalse()

        Files.createDirectories(versionDir.resolve("extras"))
        assertThat(installer.validateAllRequiredFiles(versionDir)).isTrue()
    }

    @Test
    fun `validateAllRequiredFiles checks semver range`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("99.0.0")
        Files.createDirectories(versionDir)
        Files.createFile(versionDir.resolve("srv.js"))

        val config = testConfig(baseRoot, versionRange = "<2.0.0")
        val installer = InstallerTestHelper(config)
        assertThat(installer.validateAllRequiredFiles(versionDir)).isFalse()
    }

    @Test
    fun `cleanupOldVersions retains current plus one valid fallback`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.0.0", "srv.js")
        createVersion(storageDir, "1.1.0", "srv.js")
        createVersion(storageDir, "1.2.0", "srv.js")
        createVersion(storageDir, "1.3.0", "srv.js")

        val installer = createInstaller(baseRoot)
        installer.cleanupOldVersions("1.3.0")

        assertThat(Files.exists(storageDir.resolve("1.3.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.1.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
    }

    @Test
    fun `cleanupOldVersions only keeps valid fallback with all required files`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.0.0", "srv.js")
        val invalidDir = storageDir.resolve("1.2.0")
        Files.createDirectories(invalidDir)
        createVersion(storageDir, "1.1.0", "srv.js")
        createVersion(storageDir, "1.3.0", "srv.js")

        val installer = createInstaller(baseRoot)
        installer.cleanupOldVersions("1.3.0")

        assertThat(Files.exists(storageDir.resolve("1.3.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.1.0"))).isTrue()
        assertThat(Files.exists(storageDir.resolve("1.2.0"))).isFalse()
        assertThat(Files.exists(storageDir.resolve("1.0.0"))).isFalse()
    }

    @Test
    fun `findCachedServer selects highest version within range`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.0.0", "srv.js")
        createVersion(storageDir, "1.5.0", "srv.js")
        createVersion(storageDir, "1.9.0", "srv.js")

        val installer = createInstaller(baseRoot)
        val result = installer.findCachedServer()
        assertThat(result?.parent?.fileName.toString()).isEqualTo("1.9.0")
    }

    @Test
    fun `findCachedServer validates all required files`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.3.0", "srv.js")
        val invalidDir = storageDir.resolve("1.5.0")
        Files.createDirectories(invalidDir)
        Files.createFile(invalidDir.resolve("srv.js"))

        val config = testConfig(baseRoot, requiredFiles = listOf("srv.js", "extras"))
        val installer = InstallerTestHelper(config)
        assertThat(installer.findCachedServer()).isNull()

        Files.createDirectories(storageDir.resolve("1.3.0").resolve("extras"))
        assertThat(installer.findCachedServer()).isNotNull
    }

    @Test
    fun `postInstall verifies configured files after the hook`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.4.0", "srv.js")
        Files.createDirectories(storageDir.resolve("1.4.0").resolve("extras"))
        val installer = InstallerTestHelper(
            testConfig(baseRoot, requiredFiles = listOf("extras")),
            postInstallAction = { versionDir -> versionDir.resolve("extras").toFile().deleteRecursively() },
        )

        assertThatThrownBy { installer.findCachedServer() }
            .hasMessageContaining("Required file 'extras' not found after install")
    }

    @Test
    fun `invalidateResolvedInstallation clears state and deletes directory`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.4.0", "srv.js")

        val installer = createInstaller(baseRoot)
        installer.findCachedServer()
        assertThat(installer.resolvedDir).isNotNull

        installer.invalidateResolvedInstallation()

        assertThat(installer.resolvedDir).isNull()
        assertThat(Files.exists(storageDir.resolve("1.4.0"))).isFalse()
    }

    @Test
    fun `invalidateResolvedInstallation is idempotent`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.4.0", "srv.js")

        val installer = createInstaller(baseRoot)
        installer.findCachedServer()

        installer.invalidateResolvedInstallation()
        installer.invalidateResolvedInstallation()
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `local bundle root returns exact server file and bypasses manifest and network`() {
        val bundleRoot = tempFolder.newFolder("bundle-${System.nanoTime()}").toPath()
        val serverFile = bundleRoot.resolve("srv.js")
        Files.createFile(serverFile)

        val installer = InstallerTestHelper(
            testConfig(tempFolder.root.toPath(), localBundleRoot = bundleRoot),
            httpGetText = { error("manifest fetch must not happen for a local bundle") },
            httpGetBytes = { error("download must not happen for a local bundle") },
        )

        val resolved = installer.getServerPath()

        assertThat(resolved).isEqualTo(serverFile)
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `local bundle root missing server file fails clearly`() {
        val bundleRoot = tempFolder.newFolder("bundle-empty-${System.nanoTime()}").toPath()

        val installer = InstallerTestHelper(
            testConfig(tempFolder.root.toPath(), localBundleRoot = bundleRoot),
            httpGetText = { error("manifest fetch must not happen for a local bundle") },
            httpGetBytes = { error("download must not happen for a local bundle") },
        )

        assertThatThrownBy { installer.getServerPath() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("srv.js")
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `getServerPath rejects manifest versions containing path separators`() {
        val baseRoot = tempFolder.newFolder("unsafe-versions-${System.nanoTime()}").toPath()
        val unsafeVersions = listOf(
            "1.2.3+/../../../outside",
            """1.2.3+\..\..\outside""",
        )

        unsafeVersions.forEach { version ->
            var downloads = 0
            val manifest = LspManifest(
                versions = listOf(
                    LspManifestVersion(serverVersion = version, targets = targets("darwin", "arm64")),
                )
            )
            val installer = InstallerTestHelper(
                testConfig(baseRoot),
                httpGetText = { "{}" },
                httpGetBytes = {
                    downloads++
                    error("download must not happen for an unsafe version")
                },
                manifestAdapter = FixedManifestAdapter(manifest),
                sleep = {},
            )

            assertThatThrownBy { installer.getServerPath() }
                .isInstanceOfSatisfying(LspInstallException::class.java) {
                    assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.NO_COMPATIBLE_VERSION)
                }
                .hasMessageContaining("safe as a single directory name")
            assertThat(downloads).isEqualTo(0)
        }
    }

    @Test
    fun `getServerPath accepts a safe version with build metadata`() {
        val (baseRoot, storageDir) = createLayout()
        val version = "1.9.0+build.7"
        createVersion(storageDir, version, "srv.js")
        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = version, targets = targets("darwin", "arm64")),
            )
        )
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { "{}" },
            httpGetBytes = { error("download must not happen for an installed safe version") },
            manifestAdapter = FixedManifestAdapter(manifest),
            sleep = {},
        )

        val resolved = installer.getServerPath()

        assertThat(resolved).isEqualTo(storageDir.resolve(version).resolve("srv.js"))
        assertThat(installer.resolvedDir).isEqualTo(storageDir.resolve(version))
    }

    @Test
    fun `downloadAndInstall rejects an unsafe version before download or cleanup`() {
        val (baseRoot, _) = createLayout()
        val outsideDir = baseRoot.resolve("outside")
        val sentinel = outsideDir.resolve("sentinel.txt")
        Files.createDirectories(outsideDir)
        Files.writeString(sentinel, "preserve")

        val payload = "payload".toByteArray()
        var downloads = 0
        val installer = InstallerTestHelper(
            testConfig(baseRoot),
            httpGetBytes = {
                downloads++
                payload
            },
            sleep = {},
        )
        val release = LspRelease(
            version = "1.2.3+/../../../outside",
            contents = listOf(
                LspContentEntry("wrong.js", "https://example.com/wrong.js", emptyList(), payload.size.toLong()),
            ),
        )

        assertThatThrownBy { installer.downloadAndInstall(release) }
            .isInstanceOfSatisfying(LspInstallException::class.java) {
                assertThat(it.errorCode).isEqualTo(LspInstallException.ErrorCode.NO_COMPATIBLE_VERSION)
            }
            .hasMessageContaining("safe as a single directory name")

        assertThat(downloads).isEqualTo(0)
        assertThat(Files.readString(sentinel)).isEqualTo("preserve")
        assertThat(Files.exists(outsideDir.resolve("wrong.js"))).isFalse()
    }

    @Test
    fun `getServerPath propagates NO_COMPATIBLE_VERSION from a fresh manifest without falling back to a cached server`() {
        val (baseRoot, storageDir) = createLayout()
        // A valid cached server exists and WOULD satisfy an offline fallback...
        createVersion(storageDir, "1.0.0", "srv.js")

        // ...but a freshly fetched, successfully parsed manifest offers only an out-of-range version,
        // so the typed NO_COMPATIBLE_VERSION must propagate rather than reusing the cached server.
        val freshManifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "99.0.0", targets = targets("darwin", "arm64")),
            )
        )
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { "{}" },
            manifestAdapter = FixedManifestAdapter(freshManifest),
            sleep = {},
        )

        val thrown = catchThrowable { installer.getServerPath() }
        assertThat(thrown).isInstanceOf(LspInstallException::class.java)
        assertThat((thrown as LspInstallException).errorCode).isEqualTo(LspInstallException.ErrorCode.NO_COMPATIBLE_VERSION)
        assertThat(installer.resolvedDir).isNull()
    }

    @Test
    fun `getServerPath reuses a complete selected installation without downloading`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.9.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("srv.js"), "CACHED")

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.9.0", targets = targets("darwin", "arm64")),
            )
        )
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { "{}" },
            httpGetBytes = { error("download must not happen when the selected version is already complete") },
            manifestAdapter = FixedManifestAdapter(manifest),
            sleep = {},
        )

        val resolved = installer.getServerPath()

        assertThat(resolved).isEqualTo(versionDir.resolve("srv.js"))
        assertThat(Files.readString(resolved)).isEqualTo("CACHED")
        assertThat(installer.resolvedDir).isEqualTo(versionDir)
    }

    @Test
    fun `getServerPath runs postInstall when reusing a complete selected installation`() {
        val (baseRoot, storageDir) = createLayout()
        val versionDir = storageDir.resolve("1.9.0")
        Files.createDirectories(versionDir)
        Files.createFile(versionDir.resolve("srv.js"))

        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.9.0", targets = targets("darwin", "arm64")),
            )
        )
        val postInstalledDirs = mutableListOf<Path>()
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { "{}" },
            httpGetBytes = { error("download must not happen when the selected version is already complete") },
            manifestAdapter = FixedManifestAdapter(manifest),
            sleep = {},
            postInstallAction = { postInstalledDirs.add(it) },
        )

        val resolved = installer.getServerPath()

        assertThat(postInstalledDirs).containsExactly(versionDir)
        assertThat(resolved).isEqualTo(versionDir.resolve("srv.js"))
        assertThat(installer.resolvedDir).isEqualTo(versionDir)
    }

    @Test
    fun `getServerPath downloads and installs when the selected version directory is incomplete`() {
        val (baseRoot, storageDir) = createLayout()
        // A stale, incomplete 1.9.0 directory with no server file must not be reused.
        val versionDir = storageDir.resolve("1.9.0")
        Files.createDirectories(versionDir)
        Files.writeString(versionDir.resolve("leftover.txt"), "PARTIAL")

        val content = "FRESH".toByteArray()
        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(
                    serverVersion = "1.9.0",
                    targets = listOf(
                        LspManifestTarget(
                            platform = "darwin",
                            arch = "arm64",
                            contents = listOf(
                                LspContentEntry(
                                    filename = "srv.js",
                                    url = "https://example.com/srv.js",
                                    hashes = emptyList(),
                                    bytes = content.size.toLong(),
                                )
                            ),
                        )
                    ),
                )
            )
        )
        var downloads = 0
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { "{}" },
            httpGetBytes = {
                downloads++
                content
            },
            manifestAdapter = FixedManifestAdapter(manifest),
            sleep = {},
        )

        val resolved = installer.getServerPath()

        assertThat(downloads).isEqualTo(1)
        assertThat(resolved).isEqualTo(versionDir.resolve("srv.js"))
        assertThat(Files.readString(resolved)).isEqualTo("FRESH")
        assertThat(installer.resolvedDir).isEqualTo(versionDir)
    }

    @Test
    fun `getServerPath removes a failed install directory before falling back to a cached server`() {
        val (baseRoot, storageDir) = createLayout()
        // A complete lower version is available as the fallback target.
        val fallbackDir = storageDir.resolve("1.5.0")
        Files.createDirectories(fallbackDir.resolve("extras"))
        Files.createFile(fallbackDir.resolve("srv.js"))

        // The selected 1.9.0 download succeeds but fails final validation (missing required 'extras'),
        // so its partially written directory must be removed before falling back.
        val content = "FRESH".toByteArray()
        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(
                    serverVersion = "1.9.0",
                    targets = listOf(
                        LspManifestTarget(
                            platform = "darwin",
                            arch = "arm64",
                            contents = listOf(
                                LspContentEntry(
                                    filename = "srv.js",
                                    url = "https://example.com/srv.js",
                                    hashes = emptyList(),
                                    bytes = content.size.toLong(),
                                )
                            ),
                        )
                    ),
                )
            )
        )
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0", requiredFiles = listOf("srv.js", "extras")),
            httpGetText = { "{}" },
            httpGetBytes = { content },
            manifestAdapter = FixedManifestAdapter(manifest),
            sleep = {},
        )

        val resolved = installer.getServerPath()

        assertThat(resolved).isEqualTo(fallbackDir.resolve("srv.js"))
        assertThat(installer.resolvedDir).isEqualTo(fallbackDir)
        assertThat(Files.exists(storageDir.resolve("1.9.0"))).isFalse()
    }

    @Test
    fun `getServerPath falls back to a cached server when the manifest fetch fails`() {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.5.0", "srv.js")

        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { throw java.io.IOException("network unavailable") },
            manifestAdapter = FixedManifestAdapter(LspManifest(emptyList())),
            sleep = {},
        )

        val resolved = installer.getServerPath()

        assertThat(resolved).isEqualTo(storageDir.resolve("1.5.0").resolve("srv.js"))
        assertThat(installer.resolvedDir).isEqualTo(storageDir.resolve("1.5.0"))
    }

    @Test
    fun `getServerPath falls back to the highest cached server when asset download fails`() {
        assertFallsBackToHighestCachedServer(1) { throw java.io.IOException("asset unavailable") }
    }

    @Test
    fun `getServerPath falls back to the highest cached server when asset extraction fails`() {
        val invalidZip = "not a zip".toByteArray()
        assertFallsBackToHighestCachedServer(invalidZip.size.toLong()) { invalidZip }
    }

    @Test
    fun `withRetries preserves interrupt status when the sleeper is interrupted`() {
        val installer = createInstaller(
            tempFolder.root.toPath(),
            sleep = { throw InterruptedException("interrupted while sleeping") },
        )

        var calls = 0
        assertThatThrownBy {
            installer.withRetries("test") {
                calls++
                throw RuntimeException("fail to trigger backoff sleep")
            }
        }.isInstanceOf(InterruptedException::class.java)

        // Thread.interrupted() both asserts and clears the flag so it does not leak to other tests.
        assertThat(Thread.interrupted()).isTrue()
        assertThat(calls).isEqualTo(1)
    }

    private fun assertFallsBackToHighestCachedServer(
        assetSize: Long,
        httpGetBytes: (String) -> ByteArray,
    ) {
        val (baseRoot, storageDir) = createLayout()
        createVersion(storageDir, "1.5.0", "srv.js")
        createVersion(storageDir, "1.10.0", "srv.js")
        val manifest = LspManifest(
            versions = listOf(
                LspManifestVersion(serverVersion = "1.9.0", targets = targets("darwin", "arm64", assetSize))
            )
        )
        val installer = InstallerTestHelper(
            testConfig(baseRoot, versionRange = "<2.0.0"),
            httpGetText = { "{}" },
            httpGetBytes = httpGetBytes,
            manifestAdapter = FixedManifestAdapter(manifest),
            sleep = {},
        )

        val resolved = installer.getServerPath()

        assertThat(resolved).isEqualTo(storageDir.resolve("1.10.0").resolve("srv.js"))
        assertThat(installer.resolvedDir).isEqualTo(storageDir.resolve("1.10.0"))
        assertThat(Files.exists(storageDir.resolve("1.9.0"))).isFalse()
    }

    private fun createLayout(): Pair<Path, Path> {
        val baseRoot = tempFolder.newFolder("base-${System.nanoTime()}").toPath()
        val storageDir = baseRoot.resolve("language-servers").resolve(lspName)
        Files.createDirectories(storageDir)
        return baseRoot to storageDir
    }

    private fun createVersion(storageDir: Path, version: String, filename: String) {
        val dir = storageDir.resolve(version)
        Files.createDirectories(dir)
        Files.createFile(dir.resolve(filename))
    }

    private fun testConfig(
        baseRoot: Path,
        versionRange: String = "<100.0.0",
        requiredFiles: List<String> = emptyList(),
        localBundleRoot: Path? = null,
    ): LspInstallerConfig = LspInstallerConfig(
        name = lspName,
        supportedVersionRange = SemVerParser.parseRange(versionRange),
        manifestUrl = "https://example.com/manifest.json",
        serverFilename = "srv.js",
        requiredFiles = requiredFiles,
        storageDir = baseRoot.resolve("language-servers").resolve(lspName),
        localBundleRoot = localBundleRoot,
    )

    private fun createInstaller(
        baseRoot: Path,
        sleep: (Long) -> Unit = {},
        resolvePlatform: () -> String = { "darwin" },
        resolveArch: () -> String = { "arm64" },
    ): InstallerTestHelper = InstallerTestHelper(
        testConfig(baseRoot),
        resolvePlatform = resolvePlatform,
        resolveArch = resolveArch,
        sleep = sleep,
    )

    private fun targets(platform: String, arch: String, bytes: Long = 50_000_000): List<LspManifestTarget> = listOf(
        LspManifestTarget(
            platform = platform,
            arch = arch,
            contents = listOf(LspContentEntry("server.zip", "https://example.com/server.zip", emptyList(), bytes))
        )
    )
}

private class InstallerTestHelper(
    config: LspInstallerConfig,
    httpGetText: (String) -> String = { error("network not expected in test: $it") },
    httpGetBytes: (String) -> ByteArray = { error("network not expected in test: $it") },
    manifestAdapter: ManifestAdapter = InstallerTestNoOpAdapter,
    resolvePlatform: () -> String = { "darwin" },
    resolveArch: () -> String = { "arm64" },
    sleep: (Long) -> Unit = {},
    private val postInstallAction: (Path) -> Unit = {},
) : BaseLspInstaller(
    config = config,
    manifestAdapter = manifestAdapter,
    httpGetText = httpGetText,
    httpGetBytes = httpGetBytes,
    resolvePlatform = resolvePlatform,
    resolveArch = resolveArch,
    sleep = sleep,
) {
    val resolvedDir: Path?
        get() = resolvedVersionDir.get()

    public override fun postInstall(versionDir: Path) = postInstallAction(versionDir)
}

private object InstallerTestNoOpAdapter : ManifestAdapter {
    override fun parseManifest(json: String): LspManifest = error("Not expected in test")
}

private class FixedManifestAdapter(private val manifest: LspManifest) : ManifestAdapter {
    override fun parseManifest(json: String): LspManifest = manifest
}
