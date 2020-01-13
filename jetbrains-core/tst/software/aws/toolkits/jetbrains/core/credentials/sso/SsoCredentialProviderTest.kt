// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.sso.model.GetRoleCredentialsRequest
import software.amazon.awssdk.services.sso.model.GetRoleCredentialsResponse
import software.amazon.awssdk.services.sso.model.RoleCredentials
import software.aws.toolkits.jetbrains.utils.delegateMock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SsoCredentialProviderTest {
    private val accessToken = "access123"
    private val accountId = "111222333444"
    private val roleName = "role123"
    private val accessKey = "accessKey"
    private val secretKey = "secretKey"
    private val sessionToken = "sessionToken"

    private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

    private lateinit var ssoClient: SsoClient

    @Before
    fun setUp() {
        ssoClient = delegateMock()
    }

    @Test
    fun cachingDoesNotApplyToExpiredSession() {
        createSsoResponse(Instant.now().minusSeconds(10))

        verify(ssoClient).getRoleCredentials(any<GetRoleCredentialsRequest>())
    }

    private fun createSsoResponse(expirationTime: Instant) {
        ssoClient.stub {
            on(
                ssoClient.getRoleCredentials(
                    GetRoleCredentialsRequest.builder()
                        .accessToken(accessToken)
                        .accountId(accountId)
                        .roleName(roleName)
                        .build()
                )
            ).thenReturn(
                GetRoleCredentialsResponse.builder()
                    .roleCredentials(
                        RoleCredentials.builder()
                            .accessKeyId(accessKey)
                            .secretAccessKey(secretKey)
                            .sessionToken(sessionToken)
                            .expiration(expirationTime.toEpochMilli())
                            .build())
                    .build()
            )
        }
    }
}
