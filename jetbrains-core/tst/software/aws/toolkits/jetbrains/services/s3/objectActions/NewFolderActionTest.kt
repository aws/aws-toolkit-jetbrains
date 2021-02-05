// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.ui.TestDialog
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeDirectoryNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectVersionNode
import software.aws.toolkits.jetbrains.ui.TestDialogService
import java.time.Instant

class NewFolderActionTest : ObjectActionTestBase() {
    private val sut = NewFolderAction()

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
    fun `new folder on an object uses its parent directory as key prefix`() {
    }

    @Test
    fun `new folder on directory uses its key as prefix`() {
    }

    @Test
    fun `new folder with no select uses no prefix`() {
    }
}
