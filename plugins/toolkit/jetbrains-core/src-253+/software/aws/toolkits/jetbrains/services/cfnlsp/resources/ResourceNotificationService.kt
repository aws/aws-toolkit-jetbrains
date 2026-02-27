// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStatePurpose
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.utils.notifyWarn
import software.aws.toolkits.resources.message
import java.awt.datatransfer.StringSelection

internal class ResourceNotificationService(private val project: Project) {
    fun showResultNotification(successCount: Int, failureCount: Int, purpose: ResourceStatePurpose) {
        val actionKey = purpose.name.lowercase()
        val titleKey = "cloudformation.explorer.resources.$actionKey"
        val title = message(titleKey).removeSuffix(" Resource State")
        val resourcePlural = if (successCount == 1 || failureCount == 1) "" else "s"

        when {
            successCount > 0 && failureCount == 0 -> {
                notifyInfo(
                    title,
                    message("cloudformation.explorer.resources.$actionKey.success", successCount, resourcePlural),
                    project
                )
            }
            successCount > 0 && failureCount > 0 -> {
                val successPlural = if (successCount == 1) "" else "s"
                notifyWarn(
                    title,
                    message("cloudformation.explorer.resources.$actionKey.partial", successCount, successPlural, failureCount),
                    project
                )
            }
            failureCount > 0 -> {
                notifyError(
                    title,
                    message("cloudformation.explorer.resources.$actionKey.failed", failureCount, resourcePlural),
                    project
                )
            }
            else -> {
                notifyInfo(
                    title,
                    message("cloudformation.explorer.resources.$actionKey.none"),
                    project
                )
            }
        }
    }

    fun showStackManagementInfo(result: ResourceStackManagementResult) {
        val messageText = if (result.managedByStack == true) {
            message("cloudformation.explorer.resources.stack_info.managed", result.stackName ?: "Unknown")
        } else {
            message("cloudformation.explorer.resources.stack_info.not_managed")
        }

        val actions = mutableListOf<AnAction>()

        if (result.managedByStack == true && result.stackName != null) {
            actions.add(object : AnAction(message("cloudformation.explorer.resources.stack_info.copy_name")) {
                override fun actionPerformed(e: AnActionEvent) {
                    CopyPasteManager.getInstance().setContents(StringSelection(result.stackName))
                }
            })

            if (result.stackId != null) {
                actions.add(object : AnAction(message("cloudformation.explorer.resources.stack_info.copy_arn")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        CopyPasteManager.getInstance().setContents(StringSelection(result.stackId))
                    }
                })
            }
        }

        notifyInfo(
            message("cloudformation.explorer.resources.stack_info.title"),
            messageText,
            project,
            actions
        )
    }
}
