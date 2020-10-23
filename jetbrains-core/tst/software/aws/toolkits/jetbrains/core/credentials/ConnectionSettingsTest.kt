// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.anAwsRegion
import software.aws.toolkits.core.utils.test.aString

class ConnectionSettingsTest {

    private val credentialsProvider = mock<ToolkitCredentialsProvider>()
    private val region = anAwsRegion()

    private val connection = ConnectionSettings(credentialsProvider, region)

    @Test
    fun `applySafely puts basic settings in the map`() {
        val map = mutableMapOf<String, String>()

        setupCredentials(AwsBasicCredentials.create("access", "secret"))

        connection.safelyApplyTo(map)

        assertThat(map)
            .hasSize(3)
            .containsEntry("AWS_REGION", region.id)
            .containsEntry("AWS_ACCESS_KEY_ID", "access")
            .containsEntry("AWS_SECRET_ACCESS_KEY", "secret")
    }

    @Test
    fun `applySafely puts settings in the map including session`() {
        val map = mutableMapOf<String, String>()

        setupCredentials()

        connection.safelyApplyTo(map)

        assertThat(map)
            .hasSize(4)
            .containsEntry("AWS_REGION", region.id)
            .containsEntry("AWS_ACCESS_KEY_ID", "access")
            .containsEntry("AWS_SECRET_ACCESS_KEY", "secret")
            .containsEntry("AWS_SESSION_TOKEN", "session")
    }

    @Test
    fun `does not replace environment variables if they're already there`() {
        val existingRegion = aString()
        val existingAccessKey = aString()
        val map = mutableMapOf(
            "AWS_REGION" to existingRegion,
            "AWS_ACCESS_KEY_ID" to existingAccessKey
        )

        assertThat(map)
            .hasSize(2)
            .containsEntry("AWS_REGION", existingRegion)
            .containsEntry("AWS_ACCESS_KEY_ID", existingAccessKey)
    }

    fun setupCredentials(credential: AwsCredentials = AwsSessionCredentials.create("access", "secret", "session")) {
        reset(credentialsProvider)
        whenever(credentialsProvider.resolveCredentials()).thenReturn(credential)
    }
}
