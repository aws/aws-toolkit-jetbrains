// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.ui.TestDialog
import com.intellij.testFramework.DisposableRule
import com.intellij.util.io.createFile
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.aws.toolkits.core.utils.test.retryableAssert
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeDirectoryNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectVersionNode
import software.aws.toolkits.jetbrains.services.s3.objectActions.DownloadObjectAction.ConflictResolution
import software.aws.toolkits.jetbrains.ui.TestDialogService
import software.aws.toolkits.jetbrains.utils.createMockFileChooser
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DownloadObjectActionTest : ObjectActionTestBase() {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val mockClientManager = MockClientManagerRule()

    @Rule
    @JvmField
    val testDisposable = DisposableRule()

    private val sut = DownloadObjectAction()

    @After
    fun tearDown() {
        TestDialogService.setTestDialog(TestDialog.DEFAULT)
    }

    @Test
    fun downloadSingleFileToFile() {
        val destinationFile = tempFolder.newFile().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFile)

        val testData = listOf(
            TestData("testFile-1")
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFile).hasContent("testFile-1-content")
    }

    @Test
    fun downloadSingleFileToFolder() {
        val destinationFolder = tempFolder.newFolder().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1")
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
    }

    @Test
    fun downloadSingleFileToFolderWithConflictSkip() {
        val destinationFolder = tempFolder.newFolder().toPath()
        destinationFolder.resolve("testFile-1").createFile()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        setUpConflictResolutionResponses(
            ConflictResolution.SINGLE_FILE_RESOLUTIONS,
            ConflictResolution.SKIP
        )

        val testData = listOf(
            TestData("testFile-1")
        )

        // 0 due to ee skipped our only file
        val countDownLatch = setUpS3Mock(testData, numberOfDownloads = 0)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("")
    }

    @Test
    fun downloadSingleFileToFolderWithConflictOverwrite() {
        val destinationFolder = tempFolder.newFolder().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        setUpConflictResolutionResponses(
            ConflictResolution.SINGLE_FILE_RESOLUTIONS,
            ConflictResolution.OVERWRITE
        )

        val testData = listOf(
            TestData("testFile-1")
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
    }

    @Test
    fun downloadMultipleFiles() {
        val destinationFolder = tempFolder.newFolder().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1"),
            TestData("testFile-2"),
            TestData("testFile-3")
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
        assertThat(destinationFolder.resolve("testFile-2")).hasContent("testFile-2-content")
        assertThat(destinationFolder.resolve("testFile-3")).hasContent("testFile-3-content")
    }

    @Test
    fun downloadMultipleFilesConflictSkipSome() {
        val destinationFolder = tempFolder.newFolder().toPath()
        destinationFolder.resolve("testFile-2").createFile()
        destinationFolder.resolve("testFile-4").createFile()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1"),
            TestData("testFile-2"),
            TestData("testFile-3"),
            TestData("testFile-4"),
            TestData("testFile-5")
        )

        setUpConflictResolutionResponses(
            ConflictResolution.MULTIPLE_FILE_RESOLUTIONS,
            ConflictResolution.SKIP,
            ConflictResolution.SKIP
        )

        val countDownLatch = setUpS3Mock(testData, numberOfDownloads = 3)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
        assertThat(destinationFolder.resolve("testFile-2")).hasContent("")
        assertThat(destinationFolder.resolve("testFile-3")).hasContent("testFile-3-content")
        assertThat(destinationFolder.resolve("testFile-4")).hasContent("")
        assertThat(destinationFolder.resolve("testFile-5")).hasContent("testFile-5-content")
    }

    @Test
    fun downloadMultipleFilesConflictSkipThenOverwriteRest() {
        val destinationFolder = tempFolder.newFolder().toPath()
        destinationFolder.resolve("testFile-2").createFile()
        destinationFolder.resolve("testFile-4").createFile()
        destinationFolder.resolve("testFile-5").createFile()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1"),
            TestData("testFile-2"),
            TestData("testFile-3"),
            TestData("testFile-4"),
            TestData("testFile-5")
        )

        setUpConflictResolutionResponses(
            ConflictResolution.MULTIPLE_FILE_RESOLUTIONS,
            ConflictResolution.SKIP,
            ConflictResolution.OVERWRITE_ALL
        )

        val countDownLatch = setUpS3Mock(testData, numberOfDownloads = 4)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
        assertThat(destinationFolder.resolve("testFile-2")).hasContent("")
        assertThat(destinationFolder.resolve("testFile-3")).hasContent("testFile-3-content")
        assertThat(destinationFolder.resolve("testFile-4")).hasContent("testFile-4-content")
        assertThat(destinationFolder.resolve("testFile-5")).hasContent("testFile-5-content")
    }

    @Test
    fun downloadMultipleFilesConflictSkipAll() {
        val destinationFolder = tempFolder.newFolder().toPath()
        destinationFolder.resolve("testFile-2").createFile()
        destinationFolder.resolve("testFile-4").createFile()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1"),
            TestData("testFile-2"),
            TestData("testFile-3"),
            TestData("testFile-4"),
            TestData("testFile-5")
        )

        setUpConflictResolutionResponses(
            ConflictResolution.MULTIPLE_FILE_RESOLUTIONS,
            ConflictResolution.SKIP_ALL
        )

        val countDownLatch = setUpS3Mock(testData, numberOfDownloads = 3)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
        assertThat(destinationFolder.resolve("testFile-2")).hasContent("")
        assertThat(destinationFolder.resolve("testFile-3")).hasContent("testFile-3-content")
        assertThat(destinationFolder.resolve("testFile-4")).hasContent("")
        assertThat(destinationFolder.resolve("testFile-5")).hasContent("testFile-5-content")
    }

    @Test
    fun singleExceptionLeadsToPartialDownload() {
        val destinationFolder = tempFolder.newFolder().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1"),
            TestData("testFile-2", downloadError = true),
            TestData("testFile-3")
        )

        val countDownLatch = setUpS3Mock(testData, numberOfDownloads = 2)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
        retryableAssert { // We delete the file after we get the failure
            assertThat(destinationFolder.resolve("testFile-2")).doesNotExist()
        }
        assertThat(destinationFolder.resolve("testFile-3")).doesNotExist()
    }

    @Test
    fun cancelOnPromptIsSkip() {
        val destinationFolder = tempFolder.newFolder().toPath()
        destinationFolder.resolve("testFile-1").createFile()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1")
        )

        TestDialogService.setTestDialog {
            -1 // Means cancel (esc)
        }

        val countDownLatch = setUpS3Mock(testData, numberOfDownloads = 0)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("")
    }

    @Test
    fun downloadSingleVersionFileToFile() {
        val destinationFile = tempFolder.newFile().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFile)

        val testData = listOf(
            TestData("testFile-1", isVersion = true)
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFile).hasContent("testFile-1-content-old-version")
    }

    @Test
    fun downloadSingleVersionFileToFolder() {
        val destinationFolder = tempFolder.newFolder().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1", isVersion = true)
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1@testVersionId")).hasContent("testFile-1-content-old-version")
    }

    @Test
    fun downloadMixOfVersionedFilesAndNormalFilesToFolder() {
        val destinationFolder = tempFolder.newFolder().toPath()

        createMockFileChooser(testDisposable.disposable, destinationFolder)

        val testData = listOf(
            TestData("testFile-1"),
            TestData("testFile-1", isVersion = true),
            TestData("testFile-2"),
            TestData("testFile-3", isVersion = true)
        )

        val countDownLatch = setUpS3Mock(testData)
        val nodes = testData.convertToNodes()

        sut.executeAction(nodes)

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(destinationFolder.resolve("testFile-1")).hasContent("testFile-1-content")
        assertThat(destinationFolder.resolve("testFile-1@testVersionId")).hasContent("testFile-1-content-old-version")
        assertThat(destinationFolder.resolve("testFile-2")).hasContent("testFile-2-content")
        assertThat(destinationFolder.resolve("testFile-3@testVersionId")).hasContent("testFile-3-content-old-version")
    }

    private fun setUpConflictResolutionResponses(choices: List<ConflictResolution>, vararg responses: ConflictResolution) {
        var responseNum = 0
        TestDialogService.setTestDialog {
            choices.indexOf(responses[responseNum++])
        }
    }

    private fun setUpS3Mock(testData: List<TestData>, numberOfDownloads: Int = testData.size): CountDownLatch {
        val countDownLatch = CountDownLatch(numberOfDownloads)

        s3Client.stub {
            on {
                getObject(any<GetObjectRequest>(), any<ResponseTransformer<GetObjectResponse, GetObjectResponse>>())
            } doAnswer { invoke ->
                val request = invoke.arguments[0] as GetObjectRequest

                if (testData.first { it.key == request.key() }.downloadError) {
                    countDownLatch.countDown()
                    throw S3Exception.builder().message("Test Error for ${request.key()}").build()
                }

                @Suppress("UNCHECKED_CAST")
                val transformer = invoke.arguments[1] as ResponseTransformer<GetObjectResponse, GetObjectResponse>

                val contentPostfix = if (request.versionId() != null) "-old-version" else ""
                val content = "${request.key()}-content$contentPostfix".toByteArray()

                val delegate = object : ByteArrayInputStream(content) {
                    override fun close() {
                        super.close()
                        countDownLatch.countDown()
                    }
                }

                transformer.transform(
                    GetObjectResponse.builder()
                        .eTag("1111")
                        .lastModified(Instant.parse("1995-10-23T10:12:35Z"))
                        .contentLength(content.size.toLong())
                        .build(),
                    AbortableInputStream.create(delegate)
                )
            }
        }

        return countDownLatch
    }

    private data class TestData(val key: String, val isVersion: Boolean = false, val downloadError: Boolean = false)

    private fun List<TestData>.convertToNodes(): List<S3TreeNode> {
        val parent = S3TreeDirectoryNode(s3Bucket(), null, "")

        return this.map {
            if (it.isVersion) {
                val obj = S3TreeObjectNode(parent, it.key, 1, java.time.Instant.now())
                S3TreeObjectVersionNode(obj, "testVersionId", 1, Instant.now())
            } else {
                S3TreeObjectNode(parent, it.key, 1, Instant.now())
            }
        }
    }
}
