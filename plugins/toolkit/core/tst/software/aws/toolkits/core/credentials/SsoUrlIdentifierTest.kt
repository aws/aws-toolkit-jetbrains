// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.stream.Stream

class SsoUrlIdentifierTest {
    companion object {
        class Args : ArgumentsProvider {
            override fun provideArguments(context: ExtensionContext?) = Stream.of(
                Arguments.of("https://test.awsapps.com/start", "test"),
                Arguments.of("https://identitycenter.amazonaws.com/ssoins-something", "ssoins-something"),
                Arguments.of("https://test.awsapps.com/start-something", "test"),
                Arguments.of("https://start.us-gov-home.awsapps.com/directory/test", "test"),
                Arguments.of("https://start.us-gov-east-1.us-gov-home.awsapps.com/directory/test", "test"),
                Arguments.of("https://start.home.awsapps.cn/directory/test", "test"),
                Arguments.of("https://start.cn-north-1.home.awsapps.cn/directory/test", "test"),
            )
        }
    }

    @ParameterizedTest
    @ArgumentsSource(Args::class)
    fun ssoIdentifierFromUrl(input: String, expectedIdentifier: String) {
        assertThat(validatedSsoIdentifierFromUrl(input)).isEqualTo(expectedIdentifier)
    }

    @Test
    fun `throws on malformed url`() {
        val exception = assertThrows<Exception> {
            validatedSsoIdentifierFromUrl("https://identitycenter.amazonaws.com/ssoins-something;;\\];\\;\\;12\\;3''1'\\'31\\23'\\\"\"\"|\\\\_{}}}]]")
        }

        assertThat(exception).isNotNull()
    }
}
