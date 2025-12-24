// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core.credentials.sso

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AccessTokenTest {
    @Test
    fun `DeviceAuthorizationGrantToken#toString has redacted values`() {
        val sut = DeviceAuthorizationGrantToken(
            startUrl = "clearText",
            region = "clearText",
            accessToken = "hiddenText",
            refreshToken = "hiddenText",
            expiresAt = Instant.EPOCH,
            createdAt = Instant.EPOCH,
        )

        assertThat(sut.toString()).doesNotContain("hiddenText")
    }

    @Test
    fun `PKCEAuthorizationGrantToken#toString has redacted values`() {
        val sut = PKCEAuthorizationGrantToken(
            issuerUrl = "clearText",
            region = "clearText",
            accessToken = "hiddenText",
            refreshToken = "hiddenText",
            expiresAt = Instant.EPOCH,
            createdAt = Instant.EPOCH,
        )

        assertThat(sut.toString()).doesNotContain("hiddenText")
    }
}
