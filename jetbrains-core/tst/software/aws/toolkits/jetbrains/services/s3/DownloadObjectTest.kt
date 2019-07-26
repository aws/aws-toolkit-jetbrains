// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.DownloadObjectAction
import software.aws.toolkits.jetbrains.utils.delegateMock
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import java.time.Instant
import org.assertj.core.api.Assertions
import java.nio.file.Path

class DownloadObjectTest {

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val mockClientManager = MockClientManagerRule { projectRule.project }

    @Test
    fun downloadObjectTest() {

        val downloadCaptor = argumentCaptor<GetObjectRequest>()
        val s3Client = delegateMock<S3Client> {
            on { getObject(downloadCaptor.capture(), any<Path>()) } doReturn GetObjectResponse.builder()
                    .eTag("1111").lastModified(Instant.parse("1995-10-23T10:12:35Z")).build()
        }

        mockClientManager.register(S3Client::class, s3Client)

        val vfsMock = S3VFS(s3Client)
        val treeTableMock = delegateMock<S3TreeTable> { on { getValueAt(any(), any()) } doReturn "testKey" }
        val virtualBucket = S3VirtualBucket(vfsMock, S3Bucket("TestBucket", s3Client, Instant.parse("1995-10-23T10:12:35Z")))

        val mockSave = delegateMock<FileSaverDialog>()
        val mockFileChooser = delegateMock<FileChooserFactory>
        { on { createSaveFileDialog(any<FileSaverDescriptor>(), any<Project>()) } doReturn mockSave }

        val testfile = FileUtil.createTempFile("myfile", ".txt")

        mockSave.stub {
            on { save(any<VirtualFile>(), any<String>()) } doReturn (VirtualFileWrapper(testfile))
        }

        val downloadObject = DownloadObjectAction(treeTableMock, virtualBucket, mockFileChooser)

        runInEdtAndWait {

            downloadObject.downloadObjectAction(TestActionEvent(DataContext { projectRule.project }), mockSave)

//            verify(mockFileChooser).createSaveFileDialog(any(), any<Project>())
            verify(s3Client).getObject(any<GetObjectRequest>(), any<Path>())

            val downloadRequestCapture = downloadCaptor.firstValue

            Assertions.assertThat(downloadRequestCapture.bucket()).isEqualTo("TestBucket")
            Assertions.assertThat(downloadRequestCapture.key()).isEqualTo("testKey")
        }
    }
}