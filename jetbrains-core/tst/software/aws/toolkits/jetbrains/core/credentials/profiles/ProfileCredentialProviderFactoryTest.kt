// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import org.assertj.core.api.Assertions.STRING
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.sts.StsClient
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.core.credentials.CredentialsChangeEvent
import software.aws.toolkits.core.credentials.CredentialsChangeListener
import software.aws.toolkits.core.rules.SystemPropertyHelper
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import software.aws.toolkits.jetbrains.core.credentials.InteractiveCredential
import software.aws.toolkits.jetbrains.core.credentials.ToolkitCredentialProcessProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.SsoCache
import software.aws.toolkits.jetbrains.core.region.getDefaultRegion
import software.aws.toolkits.jetbrains.utils.isInstanceOf
import software.aws.toolkits.jetbrains.utils.isInstanceOfSatisfying
import software.aws.toolkits.jetbrains.utils.rules.NotificationListenerRule
import java.io.File
import java.util.function.Function

class ProfileCredentialProviderFactoryTest {
    private val temporaryFolder = TemporaryFolder()
    private val systemPropertyHelper = SystemPropertyHelper()
    private val disposableRule = DisposableRule()
    private val notificationListener = NotificationListenerRule(disposableRule.disposable)
    private val clientManager = MockClientManagerRule()

    @Rule
    @JvmField
    val ruleChain = RuleChain(
        temporaryFolder,
        ApplicationRule(),
        systemPropertyHelper,
        notificationListener,
        disposableRule,
        clientManager
    )

    private lateinit var profileFile: File

    private val mockProfileWatcher = MockProfileWatcher()
    private val profileLoadCallback = mock<CredentialsChangeListener>()
    private val credentialChangeEvent = argumentCaptor<CredentialsChangeEvent>()

    @Before
    fun setUp() {
        reset(profileLoadCallback)

        val awsFolder = temporaryFolder.newFolder(".aws")
        profileFile = File(awsFolder, "config")

        System.getProperties().setProperty("aws.configFile", profileFile.absolutePath)
        System.getProperties().setProperty("aws.sharedCredentialsFile", File(awsFolder, "credentials").absolutePath)

        profileLoadCallback.stub {
            on { profileLoadCallback.invoke(credentialChangeEvent.capture()) }.thenReturn(Unit)
        }

        ApplicationManager.getApplication().replaceService(ProfileWatcher::class.java, mockProfileWatcher, disposableRule.disposable)

        TestDialogManager.setTestInputDialog { MFA_TOKEN }
    }

    @After
    fun tearDown() {
        TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
        mockProfileWatcher.reset()
    }

    @Test
    fun `empty profiles report nothing found`() {
        createProviderFactory()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback).invoke(capture())

            assertThat(firstValue.added).isEmpty()
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()
        }
    }

    @Test
    fun `loading supported profiles are reported as added`() {
        writeProfileFile(
            """
            [profile noRegion]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey

            [profile regionalized]
            aws_access_key_id=RegionAccessKey
            aws_secret_access_key=RegionSecretKey
            region=us-west-2
            """.trimIndent()
        )

        createProviderFactory()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback).invoke(capture())

            assertThat(firstValue.added).hasSize(2)
                .has(profileName("noRegion"))
                .has(profileName("regionalized", defaultRegion = "us-west-2"))

            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()
        }
    }

    @Test
    fun `failing to load a profile shows a notification`() {
        writeProfileFile(
            """
            [profile bar]
            aws_access_key_id
            """.trimIndent()
        )

        createProviderFactory()

        assertThat(notificationListener.notifications)
            .extracting(Function { t -> t.content })
            .singleElement(STRING)
            .contains("Expected an '=' sign defining a property on line 2")
    }

    @Test
    fun `an error does not prevent loading of other profiles and is reported`() {
        writeProfileFile(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            external_id=externalId
            source_profile=doNotExist

            [profile another_profile]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
            """.trimIndent()
        )

        createProviderFactory()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback).invoke(capture())

            assertThat(firstValue.added).hasSize(1).has(profileName("another_profile"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()
        }

        assertThat(notificationListener.notifications)
            .extracting(Function { t -> t.content })
            .singleElement(STRING)
            .contains("2 profiles found. Failed to load 1 profile.")
    }

    @Test
    fun `modifying a profile gets reported as a modification`() {
        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            """.trimIndent()
        )

        createProviderFactory()

        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey2
            aws_secret_access_key=FooSecretKey2
            aws_session_token=FooSessionToken2
            """.trimIndent()
        )

        mockProfileWatcher.triggerListeners()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).hasSize(1).has(profileName("foo"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).isEmpty()
            assertThat(secondValue.modified).hasSize(1).has(profileName("foo"))
            assertThat(secondValue.removed).isEmpty()
        }
    }

    @Test
    fun `deleting a profile gets reported as a deletion`() {
        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            """.trimIndent()
        )

        createProviderFactory()

        writeProfileFile("")

        mockProfileWatcher.triggerListeners()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).hasSize(1).has(profileName("foo"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).isEmpty()
            assertThat(secondValue.modified).isEmpty()
            assertThat(secondValue.removed).hasSize(1).has(profileName("foo"))
        }
    }

    @Test
    fun `adding a profile gets reported as an addition`() {
        writeProfileFile("")

        createProviderFactory()

        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            """.trimIndent()
        )

        mockProfileWatcher.triggerListeners()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).isEmpty()
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).hasSize(1).has(profileName("foo"))
            assertThat(secondValue.modified).isEmpty()
            assertThat(secondValue.removed).isEmpty()
        }
    }

    @Test
    fun `modifying a parent credential provider reports modification for it and its children`() {
        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            
            [profile bar]
            source_profile=foo
            role_arn=SomeArn
            
            [profile baz]
            source_profile=bar
            role_arn=SomeArn
            """.trimIndent()
        )

        createProviderFactory()

        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            
            [profile bar]
            source_profile=foo
            role_arn=DifferentArn
            
            [profile baz]
            source_profile=bar
            role_arn=SomeArn
            """.trimIndent()
        )

        mockProfileWatcher.triggerListeners()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).hasSize(3).has(profileName("foo")).has(profileName("bar")).has(profileName("baz"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).isEmpty()
            assertThat(secondValue.modified).hasSize(2).has(profileName("bar")).has(profileName("baz"))
            assertThat(secondValue.removed).isEmpty()
        }
    }

    @Test
    fun `removing a parent credential provider reports removal for it and its children`() {
        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            
            [profile bar]
            source_profile=foo
            role_arn=SomeArn
            
            [profile baz]
            source_profile=bar
            role_arn=SomeArn
            """.trimIndent()
        )

        createProviderFactory()

        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            
            [profile baz]
            source_profile=bar
            role_arn=SomeArn
            """.trimIndent()
        )

        mockProfileWatcher.triggerListeners()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).hasSize(3).has(profileName("foo")).has(profileName("bar")).has(profileName("baz"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).isEmpty()
            assertThat(secondValue.modified).isEmpty()
            assertThat(secondValue.removed).hasSize(2).has(profileName("bar")).has(profileName("baz"))
        }
    }

    @Test
    fun `only modified profiles are reported`() {
        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            
            [profile bar]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            """.trimIndent()
        )

        createProviderFactory()

        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey2
            aws_secret_access_key=FooSecretKey2
            
            [profile bar]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            """.trimIndent()
        )

        mockProfileWatcher.triggerListeners()

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).hasSize(2).has(profileName("foo")).has(profileName("bar"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).isEmpty()
            assertThat(secondValue.modified).hasSize(1).has(profileName("foo"))
            assertThat(secondValue.removed).isEmpty()
        }
    }

    @Test
    fun `a deleted profile throws error when trying to be retrieved`() {
        writeProfileFile(
            """
            [profile foo]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            """.trimIndent()
        )

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("foo")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider.resolveCredentials()).isInstanceOfSatisfying(AwsSessionCredentials::class.java) {
            assertThat(it.accessKeyId()).isEqualTo("FooAccessKey")
            assertThat(it.secretAccessKey()).isEqualTo("FooSecretKey")
            assertThat(it.sessionToken()).isEqualTo("FooSessionToken")
        }

        FileUtil.delete(profileFile)

        mockProfileWatcher.triggerListeners()

        assertThatThrownBy {
            providerFactory.createProvider(validProfile)
        }.isInstanceOf(IllegalStateException::class.java)

        argumentCaptor<CredentialsChangeEvent> {
            verify(profileLoadCallback, times(2)).invoke(capture())

            assertThat(firstValue.added).hasSize(1).has(profileName("foo"))
            assertThat(firstValue.modified).isEmpty()
            assertThat(firstValue.removed).isEmpty()

            assertThat(secondValue.added).isEmpty()
            assertThat(secondValue.modified).isEmpty()
            assertThat(secondValue.removed).hasSize(1).has(profileName("foo"))
        }
    }

    @Test
    fun `static credentials profile creates a provider`() {
        writeProfileFile(
            """
            [profile static]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
            """.trimIndent()
        )

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("static")
        val credentialsProvider = providerFactory.createProvider(validProfile).resolveCredentials()

        assertThat(credentialsProvider).isInstanceOfSatisfying<AwsBasicCredentials> {
            assertThat(it.accessKeyId()).isEqualTo("BarAccessKey")
            assertThat(it.secretAccessKey()).isEqualTo("BarSecretKey")
        }
    }

    @Test
    fun `static session credential profile creates a provider`() {
        writeProfileFile(
            """
            [profile staticSession]
            aws_access_key_id=FooAccessKey
            aws_secret_access_key=FooSecretKey
            aws_session_token=FooSessionToken
            """.trimIndent()
        )

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("staticSession")
        val credentialsProvider = providerFactory.createProvider(validProfile).resolveCredentials()

        assertThat(credentialsProvider).isInstanceOfSatisfying<AwsSessionCredentials> {
            assertThat(it.accessKeyId()).isEqualTo("FooAccessKey")
            assertThat(it.secretAccessKey()).isEqualTo("FooSecretKey")
            assertThat(it.sessionToken()).isEqualTo("FooSessionToken")
        }
    }

    @Test
    fun `assume role profile with a source_profile creates a provider`() {
        writeProfileFile(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            external_id=externalId
            source_profile=source_profile

            [profile source_profile]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
            """.trimIndent()
        )

        clientManager.create<StsClient>()

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("role")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider).isInstanceOfSatisfying<ProfileAssumeRoleProvider> {
            assertThat(it.parentProvider).isInstanceOf<StaticCredentialsProvider>()
        }
    }

    @Test
    fun `assume role profile with a credential_source of ec2 creates a provider`() {
        writeProfileFile(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            credential_source=Ec2InstanceMetadata
            """.trimIndent()
        )

        clientManager.create<StsClient>()

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("role")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider).isInstanceOfSatisfying<ProfileAssumeRoleProvider> {
            assertThat(it.parentProvider).isInstanceOf<InstanceProfileCredentialsProvider>()
        }
    }

    @Test
    fun `assume role profile with a credential_source of ecs creates a provider`() {
        writeProfileFile(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            credential_source=EcsContainer
            """.trimIndent()
        )

        clientManager.create<StsClient>()

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("role")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider).isInstanceOfSatisfying<ProfileAssumeRoleProvider> {
            assertThat(it.parentProvider).isInstanceOf<ContainerCredentialsProvider>()
        }
    }

    @Test
    fun `assume role profile with a credential_source of env vars creates a provider`() {
        writeProfileFile(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            credential_source=Environment
            """.trimIndent()
        )

        clientManager.create<StsClient>()

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("role")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider).isInstanceOfSatisfying<ProfileAssumeRoleProvider> {
            assertThat(it.parentProvider).isInstanceOf<AwsCredentialsProviderChain>()
        }
    }

    @Test
    fun `sso profile creates a provider`() {
        writeProfileFile(
            """
            [profile sso]
            sso_start_url=ValidUrl
            sso_region=us-east-2
            sso_account_id=111222333444
            sso_role_name=RoleName
            """.trimIndent()
        )

        clientManager.create<SsoClient>()
        clientManager.create<SsoOidcClient>()

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("sso")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider).isInstanceOf<ProfileSsoProvider>()
    }

    @Test
    fun `credential process profile creates a provider`() {
        writeProfileFile(
            """
            [profile credProcess]
            credential_process=echo
            """.trimIndent()
        )

        val providerFactory = createProviderFactory()
        val validProfile = findCredentialIdentifier("credProcess")
        val credentialsProvider = providerFactory.createProvider(validProfile)

        assertThat(credentialsProvider).isInstanceOf<ToolkitCredentialProcessProvider>()
    }

    @Test
    fun `MFA profiles always require user action`() {
        writeProfileFile(
            """
            [profile role]
            role_arn=arn1
            role_session_name=testSession
            external_id=externalId
            mfa_serial=someSerialArn
            source_profile=source_profile

            [profile source_profile]
            aws_access_key_id=BarAccessKey
            aws_secret_access_key=BarSecretKey
            """.trimIndent()
        )

        createProviderFactory()

        assertThat((findCredentialIdentifier("role") as InteractiveCredential).userActionRequired()).isTrue
    }

    @Test
    fun `sso profiles only need user action if the token is invalid`() {
        writeProfileFile(
            """
            [profile valid]
            sso_start_url=ValidUrl
            sso_region=us-east-2
            sso_account_id=111222333444
            sso_role_name=RoleName
            
            [profile expired]
            sso_start_url=ExpiredUrl
            sso_region=us-east-2
            sso_account_id=111222333444
            sso_role_name=RoleName
            
            [profile validChain]
            source_profile = valid
            role_arn = AssumedRoleArn
            """.trimIndent()
        )

        val ssoCache = mock<SsoCache> {
            on { loadAccessToken(("ValidUrl")) }.thenReturn(mock())
            on { loadAccessToken(("ExpiredUrl")) }.thenReturn(null)
        }

        createProviderFactory(ssoCache)

        assertThat((findCredentialIdentifier("valid") as InteractiveCredential).userActionRequired()).isFalse
        assertThat((findCredentialIdentifier("expired") as InteractiveCredential).userActionRequired()).isTrue
        assertThat((findCredentialIdentifier("validChain") as InteractiveCredential).userActionRequired()).isFalse
    }

    private fun writeProfileFile(content: String) {
        FileUtil.createIfDoesntExist(profileFile)
        FileUtil.writeToFile(profileFile, content)
    }

    private fun profileName(expectedProfileName: String, defaultRegion: String? = null): Condition<Iterable<CredentialIdentifier>> =
        object : Condition<Iterable<CredentialIdentifier>>(expectedProfileName) {
            override fun matches(value: Iterable<CredentialIdentifier>): Boolean = value.any {
                it.id == "profile:$expectedProfileName" && defaultRegion?.let { dr -> it.defaultRegionId == dr } ?: true
            }
        }

    private fun createProviderFactory(ssoCache: SsoCache = mock()): ProfileCredentialProviderFactory {
        val factory = ProfileCredentialProviderFactory(ssoCache)
        factory.setUp(profileLoadCallback)

        return factory
    }

    private fun findCredentialIdentifier(profileName: String) = credentialChangeEvent.allValues.flatMap { it.added }.first { it.id == "profile:$profileName" }

    private fun ProfileCredentialProviderFactory.createProvider(validProfile: CredentialIdentifier) = this.createAwsCredentialProvider(
        validProfile,
        getDefaultRegion(),
    )

    private class MockProfileWatcher : ProfileWatcher {
        private val listeners = mutableListOf<() -> Unit>()

        override fun addListener(listener: () -> Unit) {
            listeners.add(listener)
        }

        fun reset() {
            listeners.clear()
        }

        fun triggerListeners() {
            listeners.forEach { it() }
        }
    }

    private companion object {
        const val MFA_TOKEN = "MfaToken"
    }
}
