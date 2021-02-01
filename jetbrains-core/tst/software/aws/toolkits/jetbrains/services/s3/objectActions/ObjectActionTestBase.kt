// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import software.amazon.awssdk.services.s3.model.Bucket
import software.aws.toolkits.core.utils.delegateMock
import software.aws.toolkits.core.utils.test.aString
import software.aws.toolkits.jetbrains.services.s3.editor.S3EditorDataKeys
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3VirtualBucket

open class ObjectActionTestBase {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    protected val bucketName = aString()
    protected val virtualBucket = S3VirtualBucket(
        Bucket.builder().name(bucketName).build(),
        delegateMock()
    )

    protected fun AnAction.executeAction(nodes: List<S3TreeNode>) {
        val event = createEventFor(this, nodes)
        actionPerformed(event)
    }

    protected fun AnAction.updateAction(nodes: List<S3TreeNode>): Presentation {
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
