// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.UploadObjectAction
import software.aws.toolkits.jetbrains.utils.delegateMock
import software.aws.toolkits.jetbrains.utils.rules.JavaCodeInsightTestFixtureRule
import java.io.ByteArrayInputStream
import java.time.Instant

class UploadObjectTest {

    @Rule
    @JvmField
    val projectRule = JavaCodeInsightTestFixtureRule()

    @Rule
    @JvmField
    val mockClientManager = MockClientManagerRule { projectRule.project }

    @Test
    fun uploadObjectTest() {

        val uploadCaptor = argumentCaptor<PutObjectRequest>()
        val s3Client = delegateMock<S3Client> {
            on { putObject(uploadCaptor.capture(), any<RequestBody>()) } doReturn PutObjectResponse.builder()
                    .versionId("VersionFoo")
                    .build()
        }

        mockClientManager.register(S3Client::class, s3Client)

        val vfsMock = S3VFS(s3Client)
        val virtualBucket = S3VirtualBucket(vfsMock, S3Bucket("TestBucket", s3Client, Instant.parse("1995-10-23T10:12:35Z")))
        val mockDialog = delegateMock<FileChooserDialog>()
        val treeTableMock = delegateMock<S3TreeTable> { on { getValueAt(any(), any()) } doReturn "testKey" }
        val mockFileChooser = delegateMock<FileChooserFactory> { on { createFileChooser(any(), any(), anyOrNull()) } doReturn mockDialog }

        val testFile = delegateMock<VirtualFile> { on { name } doReturn "TestFile" }
        val input = ByteArrayInputStream("Hello".toByteArray())
        testFile.stub { on { length } doReturn 341 }
        testFile.stub { on { inputStream } doReturn input }

        val arrayFile = arrayOf(testFile)

        mockDialog.stub {
            on { choose(any<Project>(), anyOrNull()) } doReturn (arrayFile)
        }
        val uploadObject = UploadObjectAction(virtualBucket, treeTableMock, mockFileChooser)

        runInEdtAndWait {

            uploadObject.uploadObjectAction(TestActionEvent(DataContext { projectRule.project }), mockDialog)

            verify(mockDialog).choose(any<Project>(), anyOrNull())
            verify(s3Client).putObject(any<PutObjectRequest>(), any<RequestBody>())

            val uploadRequestCapture = uploadCaptor.firstValue
            Assertions.assertThat(uploadRequestCapture.bucket()).isEqualTo("TestBucket")
            Assertions.assertThat(uploadRequestCapture.key()).isEqualTo("TestFile")
        }
    }
}