// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.feedback

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkits.resources.message

class FeedbackTest {
    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @Test
    fun panelInitiallyNegative() {
        val panel = SubmitFeedbackPanel(initiallyPositive = false)
        assertThat(panel.sentiment).isEqualTo(Sentiment.NEGATIVE)
    }

    @Test
    fun panelInitiallyPositive() {
        val panel = SubmitFeedbackPanel(initiallyPositive = true)
        assertThat(panel.sentiment).isEqualTo(Sentiment.POSITIVE)
    }

    @Test
    fun noSentimentSet() {
        runInEdtAndWait {
            val dialog = FeedbackDialog(projectRule.project)
            val panel = dialog.getViewForTesting()

            assertThat(panel.sentiment).isEqualTo(Sentiment.POSITIVE)

            panel.clearSentimentSelection()
            assertThat(panel.sentiment).isEqualTo(null)
            assertThat(dialog.doValidate()).isInstanceOfSatisfying(ValidationInfo::class.java) {
                it.message.contains(message("feedback.validation.no_sentiment"))
            }
        }
    }

    @Test
    fun noCommentSet() {
        runInEdtAndWait {
            val dialog = FeedbackDialog(projectRule.project)
            val panel = dialog.getViewForTesting()

            listOf(
                "",
                "      ",
                "\n"
            ).forEach { case ->
                panel.comment = case
                assertThat(dialog.doValidate()).isInstanceOfSatisfying(ValidationInfo::class.java) {
                    it.message.contains(message("feedback.validation.empty_comment"))
                }
            }
        }
    }

    @Test
    fun commentTooLong() {
        runInEdtAndWait {
            val dialog = FeedbackDialog(projectRule.project)
            val panel = dialog.getViewForTesting()

            panel.comment = "string".repeat(2000)
            assertThat(dialog.doValidate()).isInstanceOfSatisfying(ValidationInfo::class.java) {
                it.message.contains(message("feedback.validation.comment_too_long"))
            }
        }
    }
}
