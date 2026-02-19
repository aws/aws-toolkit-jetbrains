// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtilsTest {

    @Test
    fun `generateUrl creates correct AWS console URL`() {
        val stackId = "arn:aws:cloudformation:us-east-1:123456789012:stack/my-stack/" +
            "12345678-1234-1234-1234-123456789012"
        val result = ConsoleUrlGenerator.generateUrl(stackId)

        val expectedUrl = "https://console.aws.amazon.com/go/view?arn=" +
            "arn%3Aaws%3Acloudformation%3Aus-east-1%3A123456789012%3Astack%2Fmy-stack%2F" +
            "12345678-1234-1234-1234-123456789012"
        assertThat(result).isEqualTo(expectedUrl)
    }

    @Test
    fun `generateUrl handles special characters in stack name`() {
        val stackId = "arn:aws:cloudformation:us-west-2:123456789012:stack/" +
            "my-stack-with-dashes_and_underscores/12345"
        val result = ConsoleUrlGenerator.generateUrl(stackId)

        val expectedUrl = "https://console.aws.amazon.com/go/view?arn=" +
            "arn%3Aaws%3Acloudformation%3Aus-west-2%3A123456789012%3Astack%2F" +
            "my-stack-with-dashes_and_underscores%2F12345"
        assertThat(result).isEqualTo(expectedUrl)
    }

    @Test
    fun `generateUrl handles different regions`() {
        val stackId = "arn:aws:cloudformation:eu-west-1:123456789012:stack/test-stack/abcdef"
        val result = ConsoleUrlGenerator.generateUrl(stackId)

        val expectedUrl = "https://console.aws.amazon.com/go/view?arn=" +
            "arn%3Aaws%3Acloudformation%3Aeu-west-1%3A123456789012%3Astack%2Ftest-stack%2Fabcdef"
        assertThat(result).isEqualTo(expectedUrl)
    }

    @Test
    fun `generateUrl handles empty string`() {
        val result = ConsoleUrlGenerator.generateUrl("")
        assertThat(result).isEqualTo("https://console.aws.amazon.com/go/view?arn=")
    }

    @Test
    fun `generateUrl handles spaces and special characters`() {
        val stackId = "arn:aws:cloudformation:us-east-1:123456789012:stack/" +
            "stack with spaces & symbols/12345"
        val result = ConsoleUrlGenerator.generateUrl(stackId)

        val expectedUrl = "https://console.aws.amazon.com/go/view?arn=" +
            "arn%3Aaws%3Acloudformation%3Aus-east-1%3A123456789012%3Astack%2F" +
            "stack+with+spaces+%26+symbols%2F12345"
        assertThat(result).isEqualTo(expectedUrl)
    }
}
