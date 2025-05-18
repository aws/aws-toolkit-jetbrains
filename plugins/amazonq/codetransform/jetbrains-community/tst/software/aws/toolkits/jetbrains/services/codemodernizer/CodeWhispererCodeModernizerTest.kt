// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog
import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.codewhispererstreaming.model.TransformationDownloadArtifactType
import software.amazon.awssdk.services.ssooidc.model.SsoOidcException
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenAuthState
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerArtifact
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeTransformType
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DownloadArtifactResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.DownloadFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.InvalidTelemetryReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ParseZipFailureReason
import software.aws.toolkits.jetbrains.services.codemodernizer.model.ValidationResult
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.downloadClientInstructions
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.filterOnlyParentFiles
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.unzipFile
import software.aws.toolkits.telemetry.CodeTransformPreValidationError
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists

class CodeWhispererCodeModernizerTest : CodeWhispererCodeModernizerTestBase() {

    @Before
    override fun setup() {
        super.setup()
    }

    @Test
    fun `ArtifactHandler notifies users if patch does not exist`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(project, clientAdaptorSpy))
        val expectedError = ParseZipFailureReason(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS, "test error")
        doNothing().whenever(handler).notifyUnableToApplyPatch(any())
        val result = DownloadArtifactResult.ParseZipFailure(expectedError)
        doReturn(result).whenever(handler).downloadArtifact(any(), eq(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS), eq(false))
        handler.displayDiff(jobId)
        verify(handler, times(1)).notifyUnableToApplyPatch(any())
    }

    @Test
    fun `ArtifactHandler notifies proxy wildcard error`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(project, clientAdaptorSpy))

        doThrow(RuntimeException("Dangling meta character '*' near index 0"))
            .whenever(clientAdaptorSpy).downloadExportResultArchive(jobId)
        val expectedResult =
            DownloadArtifactResult.DownloadFailure(DownloadFailureReason.PROXY_WILDCARD_ERROR(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS))
        val result = handler.downloadArtifact(jobId, TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS, false)
        verify(clientAdaptorSpy, times(1)).downloadExportResultArchive(jobId)
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ArtifactHandler notifies ssl handshake error`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(projectRule.project, clientAdaptorSpy))
        doThrow(RuntimeException("Unable to execute HTTP request: javax.net.ssl.SSLHandshakeException: PKIX path building failed"))
            .whenever(clientAdaptorSpy).downloadExportResultArchive(jobId)
        val expectedResult =
            DownloadArtifactResult.DownloadFailure(DownloadFailureReason.SSL_HANDSHAKE_ERROR(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS))
        val result = handler.downloadArtifact(jobId, TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS, false)
        verify(clientAdaptorSpy, times(1)).downloadExportResultArchive(jobId)
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ArtifactHandler do not notify error in prefetch`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(project, clientAdaptorSpy))
        doThrow(RuntimeException("Unable to execute HTTP request: javax.net.ssl.SSLHandshakeException: PKIX path building failed"))
            .whenever(clientAdaptorSpy).downloadExportResultArchive(jobId)
        val expectedResult = DownloadArtifactResult.Skipped
        val result = handler.downloadArtifact(jobId, TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS, true)
        verify(clientAdaptorSpy, times(1)).downloadExportResultArchive(jobId)
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ArtifactHandler catches invalid grant exceptions`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(projectRule.project, clientAdaptorSpy))
        doThrow(SsoOidcException.builder().build())
            .whenever(clientAdaptorSpy).downloadExportResultArchive(jobId)
        val expectedResult =
            DownloadArtifactResult.DownloadFailure(DownloadFailureReason.CREDENTIALS_EXPIRED(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS))
        val result = handler.downloadArtifact(jobId, TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS, false)
        verify(clientAdaptorSpy, times(1)).downloadExportResultArchive(jobId)
        assertEquals(expectedResult, result)
    }

    @Test
    fun `ArtifactHandler checks if credentials need refresh before calling ExportResultArchive api`() = runBlocking {
        listOf(BearerTokenAuthState.NEEDS_REFRESH, BearerTokenAuthState.NOT_AUTHENTICATED).forEach {
            setupConnection(it)
            val handler = spy(ArtifactHandler(projectRule.project, clientAdaptorSpy))
            val expectedResult =
                DownloadArtifactResult.DownloadFailure(DownloadFailureReason.CREDENTIALS_EXPIRED(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS))
            val result = handler.downloadArtifact(jobId, TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS, false)
            verify(clientAdaptorSpy, times(0)).downloadExportResultArchive(jobId)
            assertEquals(expectedResult, result)
        }
    }

    @Test
    fun `ArtifactHandler displays patch`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(project, clientAdaptorSpy))
        val path = testCodeModernizerArtifact.zipPath
        val result = DownloadArtifactResult.Success(testCodeModernizerArtifact, path)
        doReturn(result).whenever(handler).downloadArtifact(any(), eq(TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS), eq(false))
        val mockDialog = mock<ApplyPatchDifferentiatedDialog>()
        whenever(mockDialog.showAndGet()).thenReturn(true)
        doAnswer {
            mockDialog.showAndGet()
            mockDialog
        }.whenever(handler).displayDiffUsingPatch(any(), any())
        handler.displayDiff(jobId)
        verify(handler, never()).notifyUnableToApplyPatch(any())
        verify(handler, times(1)).displayDiffUsingPatch(
            testCodeModernizerArtifact.patch,
            jobId,
        )
    }

    @Test
    fun `ArtifactHandler could not find build log from path`() = runBlocking {
        setupConnection(BearerTokenAuthState.AUTHORIZED)
        val handler = spy(ArtifactHandler(project, clientAdaptorSpy))
        val expected =
            DownloadArtifactResult.ParseZipFailure(
                ParseZipFailureReason(TransformationDownloadArtifactType.LOGS, "Could not find build log")
            )
        val mockZipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("some-other-file.txt"))
                zos.write("mock content".toByteArray())
                zos.closeEntry()
            }
            bos.toByteArray()
        }
        doReturn(listOf(mockZipBytes)).whenever(clientAdaptorSpy)
            .downloadExportResultArchive(jobId, null, TransformationDownloadArtifactType.LOGS)
        val result = handler.downloadArtifact(jobId, TransformationDownloadArtifactType.LOGS, false)
        verify(clientAdaptorSpy, times(1)).downloadExportResultArchive(jobId, null, TransformationDownloadArtifactType.LOGS)
        assertEquals(expected, result)
    }

    @Test
    fun `downloadClientInstructions downloads and extracts patch file`() = runBlocking {
        val jobId = JobId("test-job-id")
        val artifactId = "test-artifact-id"
        val mockZipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("diff.patch"))
                zos.write("mock content".toByteArray())
                zos.closeEntry()
            }
            bos.toByteArray()
        }
        doReturn(listOf(mockZipBytes)).whenever(clientAdaptorSpy).downloadExportResultArchive(
            jobId,
            artifactId,
            TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS
        )
        val result = downloadClientInstructions(jobId, artifactId, project)
        verify(clientAdaptorSpy).downloadExportResultArchive(jobId, artifactId, TransformationDownloadArtifactType.CLIENT_INSTRUCTIONS)
        assertEquals(result.fileName.toString(), "diff.patch")
    }

    @Test
    fun `CodeModernizerArtifact can process a valid zip file`() {
        val artifact = CodeModernizerArtifact.create(exampleZipPath.toAbsolutePath().toString())
        assertEquals(validManifest, artifact.manifest)
        assertEquals(validMetrics.linesOfCodeChanged, artifact.metrics?.linesOfCodeChanged)
    }

    @Test
    fun `can unzip a file`() {
        val tempDir = createTempDirectory()
        val result = unzipFile(exampleZipPath, tempDir)
        assert(result)
        assert(tempDir.resolve(validZipManifestPath).exists())
        assert(tempDir.resolve(validZipPatchFilePath).exists())
    }

    @Test
    fun `can unzip only sources folder`() {
        val tempDir = createTempDirectory("test_zip")
        val destDir = createTempDirectory("test_unzip")
        // create test zip file with both source and non-source files
        val zipPath = tempDir.resolve("test.zip")
        ZipOutputStream(FileOutputStream(zipPath.toFile())).use { zos ->
            zos.putNextEntry(ZipEntry("sources/Test.kt"))
            zos.write("test content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("other/Other.kt"))
            zos.write("other content".toByteArray())
            zos.closeEntry()
        }

        val result = unzipFile(zipPath, destDir, extractOnlySources = true)
        assertTrue(result)
        assertTrue(destDir.resolve("sources/Test.kt").exists())
        assertFalse(destDir.resolve("other/Other.kt").exists())
        tempDir.toFile().deleteRecursively()
        destDir.toFile().deleteRecursively()
    }

    @Test
    fun `returns False when unable to unzip file`() {
        assertFalse(unzipFile(Path("dummy1"), Path("dummy2")))
    }

    @Test
    fun `able to filter roots correctly`() {
        val tests = listOf(
            setOf<String>() to listOf(),
            setOf("foo/bar") to listOf("foo/bar"),
            setOf("foo/bar", "foo/baz", "foo/bar/qux") to listOf("foo/bar", "foo/baz"),
            setOf("foos", "foo/bar", "foo/baz", "foo/bar/qux") to listOf("foos", "foo/bar", "foo/baz"),
            setOf("foo", "foo/bar", "foo/baz", "foo/bar/qux") to listOf("foo"),
        ).map { (input, expected) -> input.map { "$it/pom.xml" } to expected.map { "$it/pom.xml" } }
        tests.map { (input, expected) -> Pair(input.map { LightVirtualFile(it) }.toSet(), expected) }
            .forEach { (input, expected) ->
                assertEquals(expected, filterOnlyParentFiles(input).map { it.name })
            }
    }

    @Test
    fun `able to filter roots correctly when multiple on same level`() {
        val tests = listOf(
            setOf("foo/tmp0.txt", "foo/bar/tmp.txt", "foo/bar/tmp2.txt", "foo/bar/qux/tmp3.txt") to listOf("foo/tmp0.txt"),
            setOf("foo/bar/tmp.txt", "foo/bar/tmp2.txt", "foo/bar/qux/tmp3.txt") to listOf("foo/bar/tmp.txt", "foo/bar/tmp2.txt"),
        )
        tests.map { (input, expected) -> Pair(input.map { LightVirtualFile(it) }.toSet(), expected) }
            .forEach { (input, expected) ->
                assertEquals(expected, filterOnlyParentFiles(input).map { it.name })
            }
    }

    @Test
    fun `stopping job before JobId has been created notifies users that job can be stopped`() {
        codeModernizerManagerSpy.userInitiatedStopCodeModernization()
        verify(codeModernizerManagerSpy, times(1)).notifyTransformationStartStopping()
    }

    @Test
    fun `start transformation without IdC connection`() {
        val result = codeModernizerManagerSpy.validate(project, CodeTransformType.LANGUAGE_UPGRADE)
        val expectedResult = ValidationResult(
            false,
            InvalidTelemetryReason(
                CodeTransformPreValidationError.NonSsoLogin
            )
        )
        assertEquals(expectedResult, result)
    }
}
