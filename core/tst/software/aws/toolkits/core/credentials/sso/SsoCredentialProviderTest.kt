// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials.sso

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.sso.model.GetRoleCredentialsRequest
import software.amazon.awssdk.services.sso.model.GetRoleCredentialsResponse
import software.amazon.awssdk.services.sso.model.RoleCredentials
import software.amazon.awssdk.services.ssooidc.model.AccessDeniedException
import software.aws.toolkits.core.utils.delegateMock
import software.aws.toolkits.core.utils.test.aString
import java.time.Instant
import java.time.temporal.ChronoUnit

class SsoCredentialProviderTest {
    private val accessTokenId = aString()
    private val accountId = aString()
    private val roleName = aString()
    private val accessKey = aString()
    private val secretKey = aString()
    private val sessionToken = aString()
    private val credentials = AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
    private val accessToken = AccessToken(aString(), aString(), accessTokenId, Instant.now().plusSeconds(10))

    private lateinit var ssoClient: SsoClient
    private lateinit var ssoAccessTokenProvider: SsoAccessTokenProvider
    private lateinit var sut: SsoCredentialProvider

    @Before
    fun setUp() {
        ssoClient = delegateMock()
        ssoAccessTokenProvider = mock() {
            onBlocking {
                it.accessToken()
            }.thenReturn(
                accessToken
            )
        }
        sut = SsoCredentialProvider(accountId, roleName, ssoClient, ssoAccessTokenProvider)
    }

    @Test
    fun cachingDoesNotApplyToExpiredSession() {
        createSsoResponse(Instant.now().minusSeconds(5000))

        assertThat(sut.resolveCredentials()).usingRecursiveComparison().isEqualTo(credentials)

        // Resolve again
        assertThat(sut.resolveCredentials()).usingRecursiveComparison().isEqualTo(credentials)

        verify(ssoClient, times(2)).getRoleCredentials(any<GetRoleCredentialsRequest>())
    }

    @Test
    fun cachingDoesApplyToExpiredSession() {
        createSsoResponse(Instant.now().plus(2, ChronoUnit.HOURS))

        assertThat(sut.resolveCredentials()).usingRecursiveComparison().isEqualTo(credentials)

        // Resolve again
        assertThat(sut.resolveCredentials()).usingRecursiveComparison().isEqualTo(credentials)

        verify(ssoClient).getRoleCredentials(any<GetRoleCredentialsRequest>())
    }

    @Test
    fun rejectedAccessTokenInvalidatesIt() {
        ssoClient.stub {
            on(
                ssoClient.getRoleCredentials(any<GetRoleCredentialsRequest>())
            ).thenThrow(
                AccessDeniedException.builder().build()
            )
        }

        assertThatThrownBy { sut.resolveCredentials() }.isNotNull()

        verify(ssoAccessTokenProvider).invalidate()
    }

    private fun createSsoResponse(expirationTime: Instant) {
        ssoClient.stub {
            on(
                ssoClient.getRoleCredentials(
                    GetRoleCredentialsRequest.builder()
                        .accessToken(accessTokenId)
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
                            .build()
                    )
                    .build()
            )
        }
    }
}
