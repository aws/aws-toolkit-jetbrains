// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.ui.TestDialog
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse
import software.aws.toolkits.core.utils.test.retryableAssert
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeDirectoryNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectVersionNode
import software.aws.toolkits.jetbrains.ui.TestDialogService
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeleteObjectActionTest : ObjectActionTestBase() {
    private val sut = DeleteObjectAction()

    @After
    fun tearDown() {
        TestDialogService.setTestDialog(TestDialog.DEFAULT)
    }

    @Test
    fun `delete action is disabled on empty selection`() {
        assertThat(sut.updateAction(emptyList()).isEnabled).isFalse
    }

    @Test
    fun `delete action is disabled on directory selection`() {
        val nodes = listOf(
            S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `delete action is enabled on object selection`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now()),
            S3TreeObjectNode(dir, "path1/obj2", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isTrue
    }

    @Test
    fun `delete action is disabled on object version selection`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val obj = S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        val nodes = listOf(
            S3TreeObjectVersionNode(obj, "version", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `delete action is disabled on mix of object and directory selection`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val nodes = listOf(
            dir,
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `delete denied confirmation is no-op`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )

        TestDialogService.setTestDialog(TestDialog.NO)

        sut.executeAction(nodes)

        verify(s3Client, never()).deleteObjects(any<DeleteObjectsRequest>())
    }

    @Test
    fun `delete confirmation cancelled is no-op`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )

        TestDialogService.setTestDialog {
            -1 // means cancel
        }

        sut.executeAction(nodes)

        verify(s3Client, never()).deleteObjects(any<DeleteObjectsRequest>())
    }

    @Test
    fun `delete confirmed confirmation deletes file`() {
        val dir = S3TreeDirectoryNode(s3Bucket(), null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now()),
            S3TreeObjectNode(dir, "path1/obj2", 1, Instant.now()),
            S3TreeObjectNode(dir, "path1/obj3", 1, Instant.now()),
        )

        val latch = CountDownLatch(nodes.size)

        s3Client.stub {
            on { deleteObjects(any<DeleteObjectsRequest>()) }.thenAnswer {
                val objects = it.getArgument<DeleteObjectsRequest>(0).delete().objects()
                repeat(objects.size) {
                    latch.countDown()
                }

                DeleteObjectsResponse.builder().build()
            }
        }

        TestDialogService.setTestDialog(TestDialog.OK)

        sut.executeAction(nodes)

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue

        // Happens async on a different thread
        retryableAssert {
            verify(treeTable, times(3)).invalidateLevel(any<S3TreeObjectNode>())
            verify(treeTable).refresh()
        }
    }
}
