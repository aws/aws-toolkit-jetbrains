// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.DeleteObjectAction
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.time.Instant

class DeleteObjectTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    private val s3Client: S3Client by lazy { mockClientManagerRule.register(S3Client::class, delegateMock()) }

    @Test
    fun deleteObjectTest() {

        val deleteCaptor = argumentCaptor<DeleteObjectRequest>()
        val vfsMock = S3VFS(s3Client)
        val treeTableMock = delegateMock<S3TreeTable> { on { getValueAt(any(), any()) } doReturn "testKey" }
        val virtualBucket = S3VirtualBucket(vfsMock, S3Bucket("TestBucket", s3Client, Instant.parse("1995-10-23T10:12:35Z")))
        val deleteObject = DeleteObjectAction(treeTableMock, virtualBucket)

        s3Client.stub {
            on { deleteObject(deleteCaptor.capture()) } doReturn (DeleteObjectResponse.builder().versionId("1223").deleteMarker(true).requestCharged("yes").build())
            Messages.setTestDialog(TestDialog.OK)
        }

        runInEdtAndWait {
            deleteObject.deleteObjectAction(TestActionEvent(DataContext { projectRule.project }))
            val deleteRequest = deleteCaptor.firstValue
            Assertions.assertThat(deleteRequest.bucket()).isEqualTo("TestBucket")
            Assertions.assertThat(deleteRequest.key()).isEqualTo("testKey")
        }
    }
}