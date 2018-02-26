package software.aws.toolkits.core.telemetry

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient
import software.amazon.awssdk.services.cognitoidentity.model.Credentials
import software.amazon.awssdk.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import software.amazon.awssdk.services.cognitoidentity.model.GetCredentialsForIdentityResponse
import software.amazon.awssdk.services.cognitoidentity.model.GetIdRequest
import software.amazon.awssdk.services.cognitoidentity.model.GetIdResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.Mockito.`when` as whenever

class AWSCognitoCredentialsProviderTest {

    @Rule
    @JvmField
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    private lateinit var cognitoClient: CognitoIdentityClient

    @Mock
    private lateinit var storage: CachedIdentityStorage

    @Captor
    private lateinit var getCredentialsRequestCaptor: ArgumentCaptor<GetCredentialsForIdentityRequest>

    @Captor
    private lateinit var getIdRequestCaptor: ArgumentCaptor<GetIdRequest>

    @Test fun testGetCredentials() {
        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(CREDENTIALS)
            .build()

        whenever(cognitoClient.getId(any<GetIdRequest>()))
            .thenReturn(GET_ID_RESULT)
        whenever(cognitoClient.getCredentialsForIdentity(any<GetCredentialsForIdentityRequest>()))
            .thenReturn(getCredentialsResult)

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, cognitoClient)

        val awsCredentials = provider.credentials

        verify(cognitoClient).getId(getIdRequestCaptor.capture())
        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())

        assert(getIdRequestCaptor.value.identityPoolId()).isEqualTo(IDENTITY_POOL_ID)
        assert(getCredentialsRequestCaptor.value.identityId()).isEqualTo(IDENTITY_ID)

        assert(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY)
        assert(awsCredentials.secretAccessKey()).isEqualTo(SECRET_KEY)
        assert(awsCredentials.sessionToken()).isEqualTo(SESSION_TOKEN)
    }

    @Test fun testGetCredentialsNotExpired() {
        val notExpiredCredentials = Credentials.builder()
            .accessKeyId(ACCESS_KEY)
            .secretKey(SECRET_KEY)
            .sessionToken(SESSION_TOKEN)
            .expiration(Instant.now().plus(1, ChronoUnit.HOURS))
            .build()

        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(notExpiredCredentials)
            .build()

        whenever(cognitoClient.getId(any<GetIdRequest>()))
            .thenReturn(GET_ID_RESULT)
        whenever(cognitoClient.getCredentialsForIdentity(any(GetCredentialsForIdentityRequest::class.java)))
            .thenReturn(getCredentialsResult)

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, cognitoClient)

        provider.credentials
        provider.credentials // Try to get them again to check for a refresh

        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())
    }

    @Test fun testGetCredentialsExpired() {
        val expiredCredentials = Credentials.builder()
            .accessKeyId(ACCESS_KEY)
            .secretKey(SECRET_KEY)
            .sessionToken(SESSION_TOKEN)
            .expiration(Instant.now().minus(1, ChronoUnit.HOURS))
            .build()

        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(expiredCredentials)
            .build()

        whenever(cognitoClient.getId(any<GetIdRequest>()))
            .thenReturn(GET_ID_RESULT)
        whenever(cognitoClient.getCredentialsForIdentity(any<GetCredentialsForIdentityRequest>()))
            .thenReturn(getCredentialsResult)

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, cognitoClient)

        provider.credentials
        provider.credentials

        verify(cognitoClient, times(2)).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())
    }

    @Test fun testGetCredentialsWithEmptyCache() {
        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(CREDENTIALS)
            .build()

        whenever(storage.loadIdentity(anyString())).thenReturn(null)
        whenever(cognitoClient.getId(any<GetIdRequest>()))
            .thenReturn(GET_ID_RESULT)
        whenever(cognitoClient.getCredentialsForIdentity(any<GetCredentialsForIdentityRequest>()))
            .thenReturn(getCredentialsResult)

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, cognitoClient, storage)

        val awsCredentials = provider.credentials

        verify(storage).loadIdentity(IDENTITY_POOL_ID)
        verify(cognitoClient).getId(getIdRequestCaptor.capture())
        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())
        verify(storage).storeIdentity(IDENTITY_POOL_ID, IDENTITY_ID)

        assert(getIdRequestCaptor.value.identityPoolId()).isEqualTo(IDENTITY_POOL_ID)
        assert(getCredentialsRequestCaptor.value.identityId()).isEqualTo(IDENTITY_ID)

        assert(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY)
        assert(awsCredentials.secretAccessKey()).isEqualTo(SECRET_KEY)
        assert(awsCredentials.sessionToken()).isEqualTo(SESSION_TOKEN)
    }

    @Test fun testGetCredentialsWithValidCache() {
        val getCredentialsResult = GetCredentialsForIdentityResponse.builder()
            .credentials(CREDENTIALS)
            .build()

        whenever(storage.loadIdentity(anyString())).thenReturn(IDENTITY_ID)
        whenever(cognitoClient.getCredentialsForIdentity(any<GetCredentialsForIdentityRequest>()))
            .thenReturn(getCredentialsResult)

        val provider = AWSCognitoCredentialsProvider(IDENTITY_POOL_ID, cognitoClient, storage)
        val awsCredentials = provider.credentials

        verify(storage).loadIdentity(IDENTITY_POOL_ID)
        verify(cognitoClient).getCredentialsForIdentity(getCredentialsRequestCaptor.capture())

        assert(getCredentialsRequestCaptor.value.identityId()).isEqualTo(IDENTITY_ID)

        assert(awsCredentials.accessKeyId()).isEqualTo(ACCESS_KEY)
        assert(awsCredentials.secretAccessKey()).isEqualTo(SECRET_KEY)
        assert(awsCredentials.sessionToken()).isEqualTo(SESSION_TOKEN)
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