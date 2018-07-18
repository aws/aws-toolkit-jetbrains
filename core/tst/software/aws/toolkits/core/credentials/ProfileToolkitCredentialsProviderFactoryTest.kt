package software.aws.toolkits.core.credentials

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.profiles.ProfileProperties.AWS_ACCESS_KEY_ID
import software.amazon.awssdk.profiles.ProfileProperties.AWS_SECRET_ACCESS_KEY
import software.amazon.awssdk.profiles.ProfileProperties.AWS_SESSION_TOKEN
import software.amazon.awssdk.services.sts.STSClient
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.region.ToolkitRegionProvider
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

class ProfileToolkitCredentialsProviderFactoryTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var profileFile: File
    private lateinit var mockClientManager: ToolkitClientManager
    private lateinit var mockRegionProvider: ToolkitRegionProvider

    @Before
    fun setUp() {
        profileFile = temporaryFolder.newFile("config")
        mockClientManager = mock()
        mockRegionProvider = mock()
    }

    @Test
    fun testLoadingWithEmptyProfiles() {
        val providerFactory = createProviderFactory()
        assertThat(providerFactory.listCredentialProviders()).isEmpty()
    }

    @Test
    fun testLoadingWithExpectedProfiles() {
        profileFile.writeText(TEST_PROFILE_FILE_CONTENTS)

        val providerFactory = createProviderFactory()

        assertThat(providerFactory.listCredentialProviders())
            .hasSize(2)
            .has(correctProfile(FOO_PROFILE))
            .has(correctProfile(BAR_PROFILE))
    }

    @Test
    fun testCreationOfBasicCredentials() {
        profileFile.writeText(TEST_PROFILE_FILE_CONTENTS)

        val providerFactory = createProviderFactory()

        val credentialsProvider = providerFactory.get("profile:bar")
        assertThat(credentialsProvider).isNotNull
        assertThat(credentialsProvider!!.credentials).isInstanceOf(AwsCredentials::class.java)
    }

    @Test
    fun testCreationOfStaticSessionCredentials() {
        profileFile.writeText(TEST_PROFILE_FILE_CONTENTS)

        val providerFactory = createProviderFactory()

        val credentialsProvider = providerFactory.get("profile:foo")
        assertThat(credentialsProvider).isNotNull
        assertThat(credentialsProvider!!.credentials).isInstanceOf(AwsCredentials::class.java)
    }

    @Test
    fun testAssumingRoles() {
        profileFile.writeText(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            source_profile=source_profile
            external_id=externalId
            source_profile=source_profile

            [profile source_profile]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
        """.trimIndent()
        )

        val stsClient = mock<STSClient> {
            on {
                assumeRole(check<AssumeRoleRequest> {
                    assertThat(it.roleArn()).isEqualTo("arn1")
                    assertThat(it.roleSessionName()).isEqualTo("testSession")
                    assertThat(it.externalId()).isEqualTo("externalId")
                })
            }.doReturn(
                AssumeRoleResponse.builder()
                    .credentials {
                        it.accessKeyId("AccessKey")
                        it.secretAccessKey("SecretKey")
                        it.sessionToken("SessionToken")
                        it.expiration(Instant.now().plus(1, ChronoUnit.HOURS))
                    }
                    .build()
            )
        }

        mockClientManager.stub {
            on {
                getClient(eq(STSClient::class), any(), any())
            }.thenReturn(stsClient)
        }

        val providerFactory = createProviderFactory()

        val credentialsProvider = providerFactory.get("profile:role")
        assertThat(credentialsProvider).isNotNull
        assertThat(credentialsProvider!!.credentials).isInstanceOf(AwsSessionCredentials::class.java).satisfies {
            val sessionCredentials = it as AwsSessionCredentials
            assertThat(sessionCredentials.accessKeyId()).isEqualTo("AccessKey")
            assertThat(sessionCredentials.secretAccessKey()).isEqualTo("SecretKey")
            assertThat(sessionCredentials.sessionToken()).isEqualTo("SessionToken")
        }
    }

    @Test
    fun testAssumingRoleChained() {
        profileFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile

            [profile source_profile]
            role_arn=arn2
            source_profile=source_profile2

            [profile source_profile2]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
        """.trimIndent()
        )

        val stsClientRoleProfile = mock<STSClient> {
            on {
                assumeRole(check<AssumeRoleRequest> {
                    assertThat(it.roleArn()).isEqualTo("arn1")
                })
            }.doReturn(
                AssumeRoleResponse.builder()
                    .credentials {
                        it.accessKeyId("AccessKey")
                        it.secretAccessKey("SecretKey")
                        it.sessionToken("SessionToken")
                        it.expiration(Instant.now().plus(1, ChronoUnit.HOURS))
                    }
                    .build()
            )
        }

        val stsClientSourceProfile = mock<STSClient> {
            on {
                assumeRole(check<AssumeRoleRequest> {
                    assertThat(it.roleArn()).isEqualTo("arn2")
                })
            }.doReturn(
                AssumeRoleResponse.builder()
                    .credentials {
                        it.accessKeyId("AccessKey2")
                        it.secretAccessKey("SecretKey2")
                        it.sessionToken("SessionToken2")
                        it.expiration(Instant.now().plus(1, ChronoUnit.HOURS))
                    }
                    .build()
            )
        }

        mockClientManager.stub {
            on {
                getClient(eq(STSClient::class), check {
                    it.credentials // Hack to replicate the STS client getting the credentials for the assume role call
                }, any())
            }.thenReturn(stsClientSourceProfile, stsClientRoleProfile) // Assuming role happens bottom up
        }

        val providerFactory = createProviderFactory()

        val credentialsProvider = providerFactory.get("profile:role")
        assertThat(credentialsProvider).isNotNull
        assertThat(credentialsProvider!!.credentials).isInstanceOf(AwsSessionCredentials::class.java).satisfies {
            val sessionCredentials = it as AwsSessionCredentials
            assertThat(sessionCredentials.accessKeyId()).isEqualTo("AccessKey")
            assertThat(sessionCredentials.secretAccessKey()).isEqualTo("SecretKey")
            assertThat(sessionCredentials.sessionToken()).isEqualTo("SessionToken")
        }
    }

    @Test
    fun testSourceProfileDoesNotExist() {
        profileFile.writeText(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            source_profile=source_profile
            external_id=externalId
        """.trimIndent()
        )

        assertThatThrownBy {
            ProfileToolkitCredentialsProvider(
                profiles(),
                profiles()["role"]!!,
                mockClientManager,
                mockRegionProvider
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Profile `role` references source profile `source_profile` which does not exist")
    }

    @Test
    fun testCircularChainProfiles() {
        profileFile.writeText(
            """
            [profile role]
            role_arn=arn1
            source_profile=source_profile

            [profile source_profile]
            role_arn=arn2
            source_profile=source_profile2

            [profile source_profile2]
            role_arn=arn3
            source_profile=source_profile3

            [profile source_profile3]
            role_arn=arn4
            source_profile=source_profile
        """.trimIndent()
        )

        assertThatThrownBy {
            ProfileToolkitCredentialsProvider(
                profiles(),
                profiles()["role"]!!,
                mockClientManager,
                mockRegionProvider
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("A circular profile dependency was found between role->source_profile->source_profile2->source_profile3->source_profile")
    }

    private fun profiles(): MutableMap<String, Profile> {
        return ProfileFile.builder()
            .content(profileFile.toPath())
            .type(ProfileFile.Type.CONFIGURATION)
            .build()
            .profiles()
    }

    private fun correctProfile(expectedProfile: Profile): Condition<Iterable<ToolkitCredentialsProvider>> {
        return object : Condition<Iterable<ToolkitCredentialsProvider>>(expectedProfile.toString()) {
            override fun matches(value: Iterable<ToolkitCredentialsProvider>): Boolean {
                return value.filterIsInstance<ProfileToolkitCredentialsProvider>()
                    .any { it.profile == expectedProfile }
            }
        }
    }

    private fun createProviderFactory() =
        ProfileToolkitCredentialsProviderFactory(mockRegionProvider, mockClientManager, profileFile.toPath())

    companion object {
        val TEST_PROFILE_FILE_CONTENTS = """
            [profile bar]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey

            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
        """.trimIndent()

        private const val FOO_PROFILE_NAME = "foo"
        private const val FOO_ACCESS_KEY = "FooAccessKey"
        private const val FOO_SECRET_KEY = "FooSecretKey"
        private const val FOO_SESSION_TOKEN = "FooSessionToken"

        private const val BAR_PROFILE_NAME = "bar"
        private const val BAR_ACCESS_KEY = "BarAccessKey"
        private const val BAR_SECRET_KEY = "BarSecretKey"

        private val FOO_PROFILE: Profile = Profile.builder()
            .name(FOO_PROFILE_NAME)
            .properties(
                mapOf(
                    AWS_ACCESS_KEY_ID to FOO_ACCESS_KEY,
                    AWS_SECRET_ACCESS_KEY to FOO_SECRET_KEY,
                    AWS_SESSION_TOKEN to FOO_SESSION_TOKEN
                )
            )
            .build()

        private val BAR_PROFILE: Profile = Profile.builder()
            .name(BAR_PROFILE_NAME)
            .properties(
                mapOf(
                    AWS_ACCESS_KEY_ID to BAR_ACCESS_KEY,
                    AWS_SECRET_ACCESS_KEY to BAR_SECRET_KEY
                )
            )
            .build()
    }
}