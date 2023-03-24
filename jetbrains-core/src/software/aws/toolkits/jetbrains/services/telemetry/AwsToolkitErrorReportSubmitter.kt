// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.telemetry

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.util.Consumer
import software.aws.toolkits.jetbrains.ui.feedback.buildGithubIssueUrl
import java.awt.Component

class AwsToolkitErrorReportSubmitter : ErrorReportSubmitter() {
    override fun getReportActionText(): String = "Report to AWS on GitHub"

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val body = events.map {
            """
                |${it.message?.let { m -> "$m:"} ?: ""}
                |```
                |${it.getThrowableText()}
                |```
            """.trimMargin("|")
        }.joinToString("\n")

        val url = buildGithubIssueUrl(body)

        try {
            BrowserUtil.browse(url)
            consumer.consume(SubmittedReportInfo(url, "issue template on GitHub", SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
            return true
        } catch (e: Exception) {
            consumer.consume(SubmittedReportInfo(null, e.message, SubmittedReportInfo.SubmissionStatus.FAILED))
            return false
        }
    }
}
