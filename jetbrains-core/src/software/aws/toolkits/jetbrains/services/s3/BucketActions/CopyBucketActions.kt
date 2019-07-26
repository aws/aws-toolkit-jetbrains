// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.s3.BucketActions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager

import java.awt.datatransfer.StringSelection
import software.aws.toolkits.jetbrains.services.s3.S3BucketNode
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.utils.notifyError

class CopyBucketName : CopyAction<S3BucketNode>("Copy Bucket Name") {

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
                e.notifyError("copy name failed")
            }
        }
    }
}

class CopyBucketARN : CopyAction<S3BucketNode>("Copy Bucket ARN") {
    override fun performCopy(selected: S3BucketNode) {
        val bucketName = selected.toString()
        val arn = "arn:aws:s3:::$bucketName"
        val copyPasteManager = CopyPasteManager.getInstance()
        copyPasteManager.setContents(StringSelection(arn))
    }

    override fun actionPerformed(selected: S3BucketNode, e: AnActionEvent) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                performCopy(selected)
                TelemetryService.getInstance().record(e.project, "s3") {
                    datum("copybucketarn") {
                        count()
                    }
                }
            } catch (e: Exception) {
                e.notifyError("copy arn failed")
            }
        }
    }
}