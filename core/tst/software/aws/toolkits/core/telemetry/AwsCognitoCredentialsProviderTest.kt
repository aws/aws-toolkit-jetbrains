// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.telemetry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient
import software.amazon.awssdk.services.cognitoidentity.model.Credentials
import software.amazon.awssdk.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import software.amazon.awssdk.services.cognitoidentity.model.GetCredentialsForIdentityResponse
import software.amazon.awssdk.services.cognitoidentity.model.GetIdRequest
import software.amazon.awssdk.services.cognitoidentity.model.GetIdResponse
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.region.ToolkitRegionProvider
import software.aws.toolkits.core.utils.DelegateSdkConsumers
import java.time.Instant
import java.time.temporal.ChronoUnit

class AwsCognitoCredentialsProviderTest {
    private val cognitoClient = mock<CognitoIdentityClient>(defaultAnswer = DelegateSdkConsumers())
    private val clientManager = mock<ToolkitClientManager> {
        on { getClient(eq(CognitoIdentityClient::class), any(), any()) }.thenReturn(cognitoClient)
    }
    private val regionProvider = mock<ToolkitRegionProvider> {
        on { regions() }.thenReturn(mapOf("us-east-1" to AwsRegion("us-east-1", "US East")))
    }
    private val storage = mock<CachedIdentityStorage>()
    private val getCredentialsRequestCaptor = argumentCaptor<GetCredentialsForIdentityRequest>()
    private val getIdRequestCaptor = argumentCaptor<GetIdRequest>()

    @Test
    fun testGetCredentials() {
        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(CREDENTIALS)
            .build()

        cognitoClient.stub {
            on { getId(getIdRequestCaptor.capture()) }.thenReturn(GET_ID_RESULT)
            on { getCredentialsForIdentity(getCredentialsRequestCaptor.capture()) }.thenReturn(getCredentialsResult)
        }

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, clientManager, regionProvider)
        val awsCredentials = provider.resolveCredentials() as AwsSessionCredentials

        assertThat(getIdRequestCaptor.firstValue.identityPoolId()).isEqualTo(IDENTITY_POOL_ID)
        assertThat(getCredentialsRequestCaptor.firstValue.identityId()).isEqualTo(IDENTITY_ID)

        assertThat(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY)
        assertThat(awsCredentials.secretAccessKey()).isEqualTo(SECRET_KEY)
        assertThat(awsCredentials.sessionToken()).isEqualTo(SESSION_TOKEN)
    }

    @Test
    fun testGetCredentialsNotExpired() {
        val notExpiredCredentials = Credentials.builder()
            .accessKeyId(ACCESS_KEY)
            .secretKey(SECRET_KEY)
            .sessionToken(SESSION_TOKEN)
            .expiration(Instant.now().plus(1, ChronoUnit.HOURS))
            .build()

        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(notExpiredCredentials)
            .build()

        cognitoClient.stub {
            on { getId(getIdRequestCaptor.capture()) }.thenReturn(GET_ID_RESULT)
            on { getCredentialsForIdentity(getCredentialsRequestCaptor.capture()) }.thenReturn(getCredentialsResult)
        }

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, clientManager, regionProvider)

        provider.resolveCredentials()
        provider.resolveCredentials() // Try to get them again to check for a refresh

        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())
    }

    @Test
    fun testGetCredentialsExpired() {
        val expiredCredentials = Credentials.builder()
            .accessKeyId(ACCESS_KEY)
            .secretKey(SECRET_KEY)
            .sessionToken(SESSION_TOKEN)
            .expiration(Instant.now().minus(1, ChronoUnit.HOURS))
            .build()

        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(expiredCredentials)
            .build()

        cognitoClient.stub {
            on { getId(getIdRequestCaptor.capture()) }.thenReturn(GET_ID_RESULT)
            on { getCredentialsForIdentity(getCredentialsRequestCaptor.capture()) }.thenReturn(getCredentialsResult)
        }

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, clientManager, regionProvider)

        provider.resolveCredentials()
        provider.resolveCredentials()

        verify(cognitoClient, times(2)).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())
    }

    @Test
    fun testGetCredentialsWithEmptyCache() {
        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(CREDENTIALS)
            .build()

        storage.stub {
            on { loadIdentity(any()) }.thenReturn(null)
        }
        cognitoClient.stub {
            on { getId(getIdRequestCaptor.capture()) }.thenReturn(GET_ID_RESULT)
            on { getCredentialsForIdentity(getCredentialsRequestCaptor.capture()) }.thenReturn(getCredentialsResult)
        }

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, clientManager, regionProvider, storage)

        val awsCredentials = provider.resolveCredentials() as AwsSessionCredentials

        verify(storage).loadIdentity(IDENTITY_POOL_ID)
        verify(cognitoClient).getId(getIdRequestCaptor.capture())
        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())
        verify(storage).storeIdentity(IDENTITY_POOL_ID, IDENTITY_ID)

        assertThat(getIdRequestCaptor.firstValue.identityPoolId()).isEqualTo(IDENTITY_POOL_ID)
        assertThat(getCredentialsRequestCaptor.firstValue.identityId()).isEqualTo(IDENTITY_ID)

        assertThat(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY)
        assertThat(awsCredentials.secretAccessKey()).isEqualTo(SECRET_KEY)
        assertThat(awsCredentials.sessionToken()).isEqualTo(SESSION_TOKEN)
    }

    @Test
    fun testGetCredentialsWithValidCache() {
        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(CREDENTIALS)
            .build()

        storage.stub {
            on { loadIdentity(any()) }.thenReturn(IDENTITY_ID)
        }
        cognitoClient.stub {
            on { getCredentialsForIdentity(getCredentialsRequestCaptor.capture()) }.thenReturn(getCredentialsResult)
        }

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, clientManager, regionProvider, storage)
        val awsCredentials = provider.resolveCredentials() as AwsSessionCredentials

        verify(storage).loadIdentity(IDENTITY_POOL_ID)
        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())

        assertThat(getCredentialsRequestCaptor.firstValue.identityId()).isEqualTo(IDENTITY_ID)

        assertThat(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY)
        assertThat(awsCredentials.secretAccessKey()).isEqualTo(SECRET_KEY)
        assertThat(awsCredentials.sessionToken()).isEqualTo(SESSION_TOKEN)
    }

    companion object {
        private const val IDENTITY_POOL_ID = "IdentityPoolID"
        private const val IDENTITY_ID = "IdentityID"
        private const val ACCESS_KEY = "AccessKey"
        private const val SECRET_KEY = "SecretKey"
        private const val SESSION_TOKEN = "SessionToken"
        private val GET_ID_RESULT = GetIdResponse.builder().identityId(IDENTITY_ID).build()
        private val CREDENTIALS = Credentials.builder()
            .accessKeyId(ACCESS_KEY)
            .secretKey(SECRET_KEY)
            .sessionToken(SESSION_TOKEN)
            .expiration(Instant.now())
            .build()
    }
}