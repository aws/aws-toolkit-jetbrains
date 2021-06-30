// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.ui.TestDialog
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.core.utils.test.retryableAssert
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeContinuationNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeDirectoryNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeErrorNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectVersionNode
import software.aws.toolkits.jetbrains.ui.TestDialogService
import java.time.Instant

class NewFolderActionTest : ObjectActionTestBase() {
    override val sut = NewFolderAction()

    @After
    fun tearDown() {
        TestDialogService.setTestDialog(TestDialog.DEFAULT)
    }

    @Test
    fun `new folder action is enabled on empty selection`() {
        assertThat(sut.updateAction(emptyList()).isEnabled).isTrue
    }

    @Test
    fun `new folder action is enabled on directory selection`() {
        val nodes = listOf(
            S3TreeDirectoryNode(s3Bucket, null, "path1/")
        )

        assertThat(sut.updateAction(nodes).isEnabled).isTrue
    }

    @Test
    fun `new folder action is enabled on object selection`() {
        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isTrue
    }

    @Test
    fun `new folder action is disabled on object version selection`() {
        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val obj = S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        val nodes = listOf(
            S3TreeObjectVersionNode(obj, "version", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `new folder action is disabled on multiple selection`() {
        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val nodes = listOf(
            dir,
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `new folder action is disabled on error node`() {
        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val nodes = listOf(
            S3TreeErrorNode(s3Bucket, dir)
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `new folder action is disabled on continuation node`() {
        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val nodes = listOf(
            S3TreeContinuationNode(s3Bucket, dir, "path1/", "marker")
        )

        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `new folder on an object uses its parent directory as key prefix`() {
        val input = aString()

        TestDialogService.setTestInputDialog { input }

        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val obj = S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        val nodes = listOf(obj)

        sut.executeAction(nodes)

        retryableAssert {
            argumentCaptor<String>().apply {
                verifyBlocking(s3Bucket) { newFolder(capture()) }

                assertThat(allValues).hasSize(1)
                assertThat(firstValue).isEqualTo("path1/$input")
            }

            verify(treeTable).invalidateLevel(obj)
            verify(treeTable).refresh()
        }
    }

    @Test
    fun `new folder on directory uses its key as prefix`() {
        val input = aString()

        TestDialogService.setTestInputDialog { input }

        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val nodes = listOf(dir)

        sut.executeAction(nodes)

        retryableAssert {
            argumentCaptor<String>().apply {
                verifyBlocking(s3Bucket) { newFolder(capture()) }

                assertThat(allValues).hasSize(1)
                assertThat(firstValue).isEqualTo("path1/$input")
            }

            verify(treeTable).invalidateLevel(dir)
            verify(treeTable).refresh()
        }
    }

    @Test
    fun `new folder with no select uses no prefix`() {
        val input = aString()

        TestDialogService.setTestInputDialog { input }

        sut.executeAction(emptyList())

        retryableAssert {
            argumentCaptor<String>().apply {
                verifyBlocking(s3Bucket) { newFolder(capture()) }

                assertThat(allValues).hasSize(1)
                assertThat(firstValue).isEqualTo(input)
            }

            verify(treeTable).invalidateLevel(treeTable.rootNode)
            verify(treeTable).refresh()
        }
    }

    @Test
    fun `new folder action can be cancelled`() {
        TestDialogService.setTestInputDialog { null }

        val dir = S3TreeDirectoryNode(s3Bucket, null, "path1/")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )

        sut.executeAction(nodes)

        verifyBlocking(s3Bucket, never()) { newFolder(any()) }
        verify(treeTable, never()).invalidateLevel(any())
        verify(treeTable, never()).refresh()
    }
}
