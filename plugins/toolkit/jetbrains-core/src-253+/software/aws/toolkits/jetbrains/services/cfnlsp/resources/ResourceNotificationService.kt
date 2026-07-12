// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import software.aws.toolkit.jetbrains.utils.createShowMoreInfoDialogAction
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkit.jetbrains.utils.notifyWarn
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStackManagementResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceStatePurpose
import software.aws.toolkits.resources.message
import java.awt.datatransfer.StringSelection

internal class ResourceNotificationService(private val project: Project) {
    fun showResultNotification(
        successCount: Int,
        failureCount: Int,
        purpose: ResourceStatePurpose,
        failureReasons: Map<String, Map<String, String>>? = null,
    ) {
        val actionKey = purpose.name.lowercase()
        val titleKey = "cloudformation.explorer.resources.$actionKey"
        val title = message(titleKey).removeSuffix(" Resource State")
        val resourcePlural = if (successCount == 1 || failureCount == 1) "" else "s"
        val reasonsSuffix = formatFailureReasons(failureReasons)

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
                if (reasonsSuffix.isNotEmpty()) {
                    notifyWarn(
                        title,
                        message("cloudformation.explorer.resources.$actionKey.partial.with_reasons", successCount, successPlural, failureCount, reasonsSuffix),
                        project,
                        listOf(viewDetailsAction(failureReasons))
                    )
                } else {
                    notifyWarn(
                        title,
                        message("cloudformation.explorer.resources.$actionKey.partial", successCount, successPlural, failureCount),
                        project
                    )
                }
            }
            failureCount > 0 -> {
                if (reasonsSuffix.isNotEmpty()) {
                    notifyError(
                        title,
                        message("cloudformation.explorer.resources.$actionKey.failed.with_reasons", failureCount, resourcePlural, reasonsSuffix),
                        project,
                        listOf(viewDetailsAction(failureReasons))
                    )
                } else {
                    notifyError(
                        title,
                        message("cloudformation.explorer.resources.$actionKey.failed", failureCount, resourcePlural),
                        project
                    )
                }
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

    /**
     * Formats up to [MAX_DISPLAYED_FAILURE_REASONS] failure reasons into a suffix string, e.g.
     * `: [ResourceNotFoundException: ... not found], [AccessDeniedException: ... not authorized]`. Any beyond the
     * limit are summarized as "and N more"; the complete set of reasons is written to the IDE log (see the
     * "view log" action on the notification). Returns an empty string when there are no reasons.
     */
    internal fun formatFailureReasons(failureReasons: Map<String, Map<String, String>>?): String {
        if (failureReasons.isNullOrEmpty()) {
            return ""
        }
        val reasons = failureReasons.values.flatMap { it.values }
        if (reasons.isEmpty()) {
            return ""
        }

        val displayed = reasons.take(MAX_DISPLAYED_FAILURE_REASONS)
        val remaining = reasons.size - displayed.size
        val joined = displayed.joinToString(", ") { "[$it]" }
        return if (remaining > 0) {
            ": $joined, ${message("cloudformation.explorer.resources.failure_reasons.more", remaining)}"
        } else {
            ": $joined"
        }
    }

    /**
     * Notification action that opens a scrollable dialog listing every failure reason (not just the truncated set
     * shown in the balloon). The full set is also written to the IDE log.
     */
    private fun viewDetailsAction(failureReasons: Map<String, Map<String, String>>?): AnAction =
        createShowMoreInfoDialogAction(
            message("cloudformation.explorer.resources.failure_reasons.view_details"),
            message("cloudformation.explorer.resources.failure_reasons.view_details.title"),
            message("cloudformation.explorer.resources.failure_reasons.view_details.message"),
            formatAllFailureReasons(failureReasons)
        )

    /**
     * Builds the complete list of failures (`<identifier>: <reason>`) for the details dialog, separated by a blank
     * line so long, wrapped entries remain readable.
     */
    private fun formatAllFailureReasons(failureReasons: Map<String, Map<String, String>>?): String =
        failureReasons
            ?.values
            ?.flatMap { identifiers -> identifiers.entries.map { (id, reason) -> "$id: $reason" } }
            ?.joinToString(System.lineSeparator() + System.lineSeparator())
            .orEmpty()

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

    private companion object {
        const val MAX_DISPLAYED_FAILURE_REASONS = 2
    }
}
