// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.Messages
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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.CopyObjectResponse
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.services.s3.BucketEditor.S3TreeTable
import software.aws.toolkits.jetbrains.services.s3.ObjectActions.RenameObjectAction
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.time.Instant

class RenameObjectTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    private val s3Client: S3Client by lazy { mockClientManagerRule.register(S3Client::class, delegateMock()) }
    @Test
    fun renameObjectTest() {

        val deleteCaptor = argumentCaptor<DeleteObjectRequest>()
        val copyCaptor = argumentCaptor<CopyObjectRequest>()

        val vfsMock = S3VirtualFileSystem(s3Client)
        val treeTableMock = delegateMock<S3TreeTable> { on { getValueAt(any(), any()) } doReturn "testKey" }
        val virtualBucket = S3VirtualBucket(vfsMock, S3Bucket("TestBucket", s3Client, Instant.parse("1995-10-23T10:12:35Z")))
        val renameObject = RenameObjectAction(treeTableMock, virtualBucket)

        s3Client.stub {
            on { copyObject(copyCaptor.capture()) } doReturn(CopyObjectResponse.builder().versionId("1223").build())
        }
        s3Client.stub {
            on { deleteObject(deleteCaptor.capture()) } doReturn(DeleteObjectResponse.builder().versionId("1223").deleteMarker(true).requestCharged("yes").build())
        }

        Messages.setTestInputDialog { TEST_RENAME_KEY }


        runInEdtAndWait {
            renameObject.renameObjectAction(TestActionEvent(DataContext { projectRule.project }), TEST_RENAME_KEY)
            val copyRequestCapture = copyCaptor.firstValue
            Assertions.assertThat(copyRequestCapture.bucket()).isEqualTo("TestBucket")
            Assertions.assertThat(copyRequestCapture.copySource()).isEqualTo("TestBucket/testKey")

            val deleteRequestCapture = deleteCaptor.firstValue
            Assertions.assertThat(deleteRequestCapture.bucket()).isEqualTo("TestBucket")
            Assertions.assertThat(deleteRequestCapture.key()).isEqualTo("testKey")
        }
    }

    companion object {
        val TEST_RENAME_KEY = "RenameKey"
    }
}