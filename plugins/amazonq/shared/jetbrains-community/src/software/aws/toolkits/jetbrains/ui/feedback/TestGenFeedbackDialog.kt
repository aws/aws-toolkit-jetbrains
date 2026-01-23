// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.feedback

import com.intellij.openapi.project.Project
import software.amazon.q.jetbrains.services.telemetry.TelemetryService
import software.amazon.q.jetbrains.ui.feedback.FeedbackDialog
import software.aws.toolkits.resources.message

class TestGenFeedbackDialog(
    project: Project,
    private val requestId: String? = null,
    private val jobId: String? = null,
) : FeedbackDialog(project) {
    override fun notificationTitle() = message("aws.notification.title.amazonq.test_generation")
    override fun feedbackPrompt() = message("feedback.comment.textbox.title.amazonq.test_generation")
    override fun productName() = "Amazon Q Test Generation"
    override suspend fun sendFeedback() {
        TelemetryService.getInstance().sendFeedback(sentiment, "UserComment: $commentText, RequestId: $requestId, TestGenerationJobId: $jobId")
    }

    init {
        title = message("feedback.title.amazonq.send_feedback")
    }
}
