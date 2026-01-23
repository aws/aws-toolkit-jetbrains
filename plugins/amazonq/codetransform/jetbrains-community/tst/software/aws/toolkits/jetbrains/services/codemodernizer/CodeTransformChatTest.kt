// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.amazon.q.jetbrains.utils.satisfiesKt
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformResultChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformButtonId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.resources.message

class CodeTransformChatTest {

    @Test
    fun `test that transform result chat item includes view build log button and message when pre-build fails`() {
        val result = CodeModernizerJobCompletedResult.JobFailedInitialBuild(JobId("dummy-job-id-123"), "Build failed in Java 8 sandbox", true)
        val chatItem = buildTransformResultChatContent(result)
        assertThat(chatItem.message).isEqualTo(message("codemodernizer.chat.message.result.fail_initial_build"))
        assertThat(chatItem.buttons)
            .singleElement()
            .satisfiesKt {
                assertThat(it.id).isEqualTo(CodeTransformButtonId.ViewBuildLog.id)
            }
    }

    @Test
    fun `test that transform result chat item includes view summary button and view diff button with correct label when job fully succeeded`() {
        val result = CodeModernizerJobCompletedResult.JobCompletedSuccessfully(JobId("dummy-job-id-123"))
        val chatItem = buildTransformResultChatContent(result)
        assertThat(chatItem.message).contains("I successfully completed your transformation")
        assertThat(chatItem.buttons)
            .hasSize(2)
            .satisfiesKt { buttons ->
                assertThat(buttons[0].id).isEqualTo(CodeTransformButtonId.ViewDiff.id)
                assertThat(buttons[0].text).isEqualTo("View diff")
                assertThat(buttons[1].id).isEqualTo(CodeTransformButtonId.ViewSummary.id)
            }
    }

    @Test
    fun `test that transform result chat item includes view summary button and view diff button with correct label when job partially succeeded`() {
        val result = CodeModernizerJobCompletedResult.JobPartiallySucceeded(JobId("dummy-job-id-123"))
        val chatItem = buildTransformResultChatContent(result)
        assertThat(chatItem.message).isEqualTo(message("codemodernizer.chat.message.result.partially_success"))

        assertThat(chatItem.buttons)
            .hasSize(2)
            .satisfiesKt { buttons ->
                assertThat(buttons[0].id).isEqualTo(CodeTransformButtonId.ViewDiff.id)
                assertThat(buttons[0].text).isEqualTo("View diff")
                assertThat(buttons[1].id).isEqualTo(CodeTransformButtonId.ViewSummary.id)
            }
    }

    @Test
    fun `test that transform result chat item does not include any buttons when job failed with known reason`() {
        val result = CodeModernizerJobCompletedResult.JobFailed(JobId("dummy-job-id-123"), message("codemodernizer.file.invalid_pom_version"))
        val chatItem = buildTransformResultChatContent(result)

        assertThat(chatItem.message).isEqualTo(message("codemodernizer.chat.message.result.fail_with_known_reason", result.failureReason))
        assertThat(chatItem.buttons).isNull()
    }
}
