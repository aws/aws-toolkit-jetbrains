// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.bucketActions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import software.aws.toolkits.jetbrains.services.s3.S3BucketNode
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError
import java.awt.datatransfer.StringSelection

class CopyBucketName : CopyAction<S3BucketNode>("Copy Name") {

    override fun performCopy(selected: S3BucketNode) {
        val bucketName = selected.toString()
        val copyPasteManager = CopyPasteManager.getInstance()
        copyPasteManager.setContents(StringSelection(bucketName))
    }

    override fun actionPerformed(selected: S3BucketNode, e: AnActionEvent) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                performCopy(selected)
                TelemetryService.getInstance().record(e.project, "s3") {
                    datum("copybucketname") {
                        count()
                    }
                }
            } catch (e: Exception) {
                e.notifyError("Copy name failed")
            }
        }
    }
}