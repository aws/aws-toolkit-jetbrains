// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codemodernizer

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codemodernizer.constants.buildTransformResultChatContent
import software.aws.toolkits.jetbrains.services.codemodernizer.messages.CodeTransformButtonId
import software.aws.toolkits.jetbrains.services.codemodernizer.model.CodeModernizerJobCompletedResult
import software.aws.toolkits.jetbrains.services.codemodernizer.model.JobId
import software.aws.toolkits.resources.message
import kotlin.test.assertEquals

class CodeTransformChatTest {

    @Test
    fun `test that transform result chat item includes view build log button and message when pre-build fails`() {
        val result = CodeModernizerJobCompletedResult.JobFailedInitialBuild(JobId("dummy-job-id-123"), "Build failed in Java 8 sandbox", true)
        val chatItem = buildTransformResultChatContent(result)
        assertEquals(chatItem.message, message("codemodernizer.chat.message.result.fail_initial_build"))
        assertNotNull(chatItem.buttons)
        assertEquals(chatItem.buttons!!.size, 1)
        assertEquals(chatItem.buttons!![0].id, CodeTransformButtonId.ViewBuildLog.id)
    }

    @Test
    fun `test that transform result chat item includes view summary button and view diff button with correct label when job fully succeeded with 5 patch files`() {
        val result = CodeModernizerJobCompletedResult.JobCompletedSuccessfully(JobId("dummy-job-id-123"))
        val chatItem = buildTransformResultChatContent(result, 5)
        assertEquals(chatItem.message, message("codemodernizer.chat.message.result.success.multiple_diffs"))
        assertNotNull(chatItem.buttons)
        assertEquals(chatItem.buttons!!.size, 2)
        assertEquals(chatItem.buttons!![0].id, CodeTransformButtonId.ViewDiff.id)
        assertEquals(chatItem.buttons!![0].text, "View diff 1/5")
        assertEquals(chatItem.buttons!![1].id, CodeTransformButtonId.ViewSummary.id)
    }

    @Test
    fun `test that transform result chat item includes view summary button and view diff button with correct label when job partially succeeded with 1 patch file`() {
        val result = CodeModernizerJobCompletedResult.JobPartiallySucceeded(JobId("dummy-job-id-123"))
        val chatItem = buildTransformResultChatContent(result, 1)
        assertEquals(chatItem.message, message("codemodernizer.chat.message.result.partially_success"))
        assertNotNull(chatItem.buttons)
        assertEquals(chatItem.buttons!!.size, 2)
        assertEquals(chatItem.buttons!![0].id, CodeTransformButtonId.ViewDiff.id)
        assertEquals(chatItem.buttons!![0].text, "View diff")
        assertEquals(chatItem.buttons!![1].id, CodeTransformButtonId.ViewSummary.id)
    }

    @Test
    fun `test that transform result chat item does not include any buttons when job failed with known reason`() {
        val result = CodeModernizerJobCompletedResult.JobFailed(JobId("dummy-job-id-123"), message("codemodernizer.file.invalid_pom_version"))
        val chatItem = buildTransformResultChatContent(result)
        assertEquals(chatItem.message, message("codemodernizer.chat.message.result.fail_with_known_reason", result.failureReason))
        assertNull(chatItem.buttons)
    }
}
