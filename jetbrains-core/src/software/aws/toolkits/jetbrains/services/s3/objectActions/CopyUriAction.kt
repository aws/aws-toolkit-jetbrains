// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.s3.objectActions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeNode
import software.aws.toolkits.jetbrains.services.s3.editor.S3TreeTable
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.S3Telemetry
import java.awt.datatransfer.StringSelection

class CopyUriAction(private val project: Project, private val treeTable: S3TreeTable) : SingleS3ObjectAction(message("s3.copy.uri")) {
    override fun performAction(dataContext: DataContext, node: S3TreeNode) {
        CopyPasteManager.getInstance().setContents(StringSelection("s3://${treeTable.bucket.name}/${node.key}"))
        S3Telemetry.copyUri(project, success = true)
    }
}
