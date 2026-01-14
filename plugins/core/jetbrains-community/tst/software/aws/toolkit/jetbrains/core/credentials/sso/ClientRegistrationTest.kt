// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials.sso

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ClientRegistrationTest {
    @Test
    fun `DeviceAuthorizationClientRegistration#toString has redacted values`() {
        val sut = DeviceAuthorizationClientRegistration(
            clientId = "clearText",
            clientSecret = "hiddenText",
            expiresAt = Instant.EPOCH,
            scopes = listOf("clearText"),
        )

        assertThat(sut.toString()).doesNotContain("hiddenText")
    }

    @Test
    fun `PKCEClientRegistration#toString has redacted values`() {
        val sut = PKCEClientRegistration(
            clientId = "clearText",
            clientSecret = "hiddenText",
            expiresAt = Instant.EPOCH,
            scopes = listOf("clearText"),
            issuerUrl = "clearText",
            region = "clearText",
            clientType = "clearText",
            grantTypes = listOf("clearText"),
            redirectUris = listOf("clearText")
        )

        assertThat(sut.toString()).doesNotContain("hiddenText")
    }
}
