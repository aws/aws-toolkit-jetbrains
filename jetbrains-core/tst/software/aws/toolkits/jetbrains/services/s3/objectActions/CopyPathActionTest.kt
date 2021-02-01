// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.s3.model.Bucket
import software.aws.toolkits.core.utils.delegateMock
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.services.s3.editor.S3EditorDataKeys
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeDirectoryNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeObjectVersionNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3VirtualBucket
import java.awt.datatransfer.DataFlavor
import java.time.Instant

class CopyPathActionTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    private val sut = CopyPathAction()
    private val bucketName = aString()
    private val virtualBucket = S3VirtualBucket(
        Bucket.builder().name(bucketName).build(),
        delegateMock()
    )

    @Test
    fun `copy path disabled with no nodes`() {
        assertThat(sut.updateAction(emptyList()).isEnabled).isFalse
    }

    @Test
    fun `copy path disabled with on multiple nodes`() {
        val nodes = listOf(
            S3TreeDirectoryNode(virtualBucket, null, "path1"),
            S3TreeDirectoryNode(virtualBucket, null, "path2")
        )
        assertThat(sut.updateAction(nodes).isEnabled).isFalse
    }

    @Test
    fun `copy path enabled with on single node`() {
        val nodes = listOf(
            S3TreeDirectoryNode(virtualBucket, null, "path1"),
        )
        assertThat(sut.updateAction(nodes).isEnabled).isTrue
    }

    @Test
    fun `copy path for dir value is correct`() {
        val nodes = listOf(
            S3TreeDirectoryNode(virtualBucket, null, "path1"),
        )
        sut.executeAction(nodes)

        val data = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertThat(data).isEqualTo("path1")
    }

    @Test
    fun `copy path for obj value is correct`() {
        val dir = S3TreeDirectoryNode(virtualBucket, null, "path1")
        val nodes = listOf(
            S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        )
        sut.executeAction(nodes)

        val data = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertThat(data).isEqualTo("path1/obj1")
    }

    @Test
    fun `copy path for obj version value is correct`() {
        val dir = S3TreeDirectoryNode(virtualBucket, null, "path1")
        val obj = S3TreeObjectNode(dir, "path1/obj1", 1, Instant.now())
        val nodes = listOf(
            S3TreeObjectVersionNode(obj, "version", 1, Instant.now())
        )
        sut.executeAction(nodes)

        val data = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertThat(data).isEqualTo("path1/obj1")
    }

    private fun AnAction.executeAction(nodes: List<S3TreeNode>) {
        val event = createEventFor(this, nodes)
        actionPerformed(event)
    }

    private fun AnAction.updateAction(nodes: List<S3TreeNode>): Presentation {
        val event = createEventFor(this, nodes)
        update(event)
        return event.presentation
    }

    private fun createEventFor(action: AnAction, nodes: List<S3TreeNode>): AnActionEvent {
        val projectContext = SimpleDataContext.getProjectContext(projectRule.project)
        val dc = SimpleDataContext.getSimpleContext(
            mapOf(
                S3EditorDataKeys.BUCKET.name to virtualBucket,
                S3EditorDataKeys.SELECTED_NODES.name to nodes
            ),
            projectContext
        )
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dc)
    }
}
