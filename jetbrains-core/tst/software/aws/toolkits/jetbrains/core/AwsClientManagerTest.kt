// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.awscore.client.builder.AwsDefaultClientBuilder
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.core.signer.Signer
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.LambdaClientBuilder
import software.aws.toolkits.core.ConnectionSettings
import software.aws.toolkits.core.region.Endpoint
import software.aws.toolkits.core.region.Service
import software.aws.toolkits.core.region.anAwsRegion
import software.aws.toolkits.jetbrains.core.AwsClientManager.Companion.CUSTOMIZER_EP
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.MockAwsConnectionManager.ProjectAccountSettingsManagerRule
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialManagerRule
import software.aws.toolkits.jetbrains.core.region.MockRegionProviderRule
import java.net.URI
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class AwsClientManagerTest {
    private val projectRule = ProjectRule()
    private val disposableRule = DisposableRule()
    private val temporaryDirectory = TemporaryFolder()
    private val regionProvider = MockRegionProviderRule()
    private val credentialManager = MockCredentialManagerRule()
    private val projectSettingsRule = ProjectAccountSettingsManagerRule(projectRule)

    @Rule
    @JvmField
    val wireMockRule = WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort())

    @Rule
    @JvmField
    val ruleChain = RuleChain(
        projectRule,
        temporaryDirectory,
        credentialManager,
        regionProvider,
        projectSettingsRule,
        disposableRule
    )

    @Test
    fun canGetAnInstanceOfAClient() {
        val sut = getClientManager()
        val client = sut.getClient<DummyServiceClient>(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion())
        assertThat(client.serviceName()).isEqualTo("dummyClient")
    }

    @Test
    fun clientsAreCached() {
        val sut = getClientManager()
        val credProvider = credentialManager.createCredentialProvider()
        val region = regionProvider.createAwsRegion()

        val fooClient = sut.getClient<DummyServiceClient>(credProvider, region)
        val barClient = sut.getClient<DummyServiceClient>(credProvider, region)

        assertThat(fooClient).isSameAs(barClient)
    }

    @Test
    fun oldClientsAreRemovedWhenCredentialsAreRemoved() {
        val sut = getClientManager()

        val credentialsIdentifier = credentialManager.addCredentials("profile:admin")
        val credentialProvider = credentialManager.getAwsCredentialProvider(credentialsIdentifier, regionProvider.defaultRegion())

        sut.getClient<DummyServiceClient>(credentialProvider, anAwsRegion())

        assertThat(sut.cachedClients().keys).anySatisfy {
            assertThat(it.credentialProviderId).isEqualTo("profile:admin")
        }

        ApplicationManager.getApplication().messageBus.syncPublisher(CredentialManager.CREDENTIALS_CHANGED).providerRemoved(credentialsIdentifier)

        assertThat(sut.cachedClients().keys).noneSatisfy {
            assertThat(it.credentialProviderId).isEqualTo("profile:admin")
        }
    }

    @Test
    fun clientsAreClosedWhenParentIsDisposed() {
        val sut = getClientManager()
        val client = Disposer.newDisposable().use { parent ->
            Disposer.register(parent, sut)

            sut.getClient<DummyServiceClient>(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion()).also {
                assertThat(it.closed).isFalse()
            }
        }

        assertThat(client.closed).isTrue
        assertThat(sut.cachedClients()).isEmpty()
    }

    @Test
    fun clientsAreClosedWhenCredentialProviderIsRemoved() {
        val sut = getClientManager()
        val credentialProviderId = credentialManager.addCredentials()
        val credentialProvider = credentialManager.getAwsCredentialProvider(credentialProviderId, anAwsRegion())
        val client = sut.getClient<DummyServiceClient>(credentialProvider, regionProvider.createAwsRegion())

        assertThat(client.closed).isFalse
        credentialManager.removeCredentials(credentialProviderId)
        assertThat(client.closed).isTrue

        assertThat(sut.cachedClients()).isEmpty()
    }

    @Test
    fun httpClientIsSharedAcrossClients() {
        val sut = getClientManager()
        val dummy = sut.getClient<DummyServiceClient>(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion())
        val secondDummy = sut.getClient<SecondDummyServiceClient>(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion())

        assertThat(dummy.httpClient.delegate).isSameAs(secondDummy.httpClient.delegate)
    }

    @Test
    fun clientWithoutBuilderFailsDescriptively() {
        val sut = getClientManager()

        assertThatThrownBy { sut.getClient<InvalidServiceClient>(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("builder()")
    }

    @Test
    fun clientInterfaceWithoutNameFieldFailsDescriptively() {
        val sut = getClientManager()

        assertThatThrownBy { sut.getClient<NoServiceNameClient>(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion()) }
            .isInstanceOf(NoSuchFieldException::class.java)
            .hasMessageContaining("SERVICE_METADATA_ID")
    }

    @Test
    fun clientsAreScopedToRegion() {
        val sut = getClientManager()
        val credProvider = credentialManager.createCredentialProvider()

        val firstRegion = sut.getClient<DummyServiceClient>(credProvider, regionProvider.createAwsRegion())
        val secondRegion = sut.getClient<DummyServiceClient>(credProvider, regionProvider.createAwsRegion())

        assertThat(secondRegion).isNotSameAs(firstRegion)
    }

    @Test
    fun globalServicesCanBeGivenAnyRegion() {
        val sut = getClientManager()
        regionProvider.addService(
            "DummyService",
            Service(
                endpoints = mapOf("global" to Endpoint()),
                isRegionalized = false,
                partitionEndpoint = "global"
            )
        )
        val credProvider = credentialManager.createCredentialProvider()

        val first = sut.getClient<DummyServiceClient>(credProvider, regionProvider.createAwsRegion(partitionId = "test"))
        val second = sut.getClient<DummyServiceClient>(credProvider, regionProvider.createAwsRegion(partitionId = "test"))

        assertThat(first.serviceName()).isEqualTo("dummyClient")
        assertThat(second).isSameAs(first)
    }

    @Test
    fun userAgentIsPassed() {
        wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)))

        val aConnection = ConnectionSettings(credentialManager.createCredentialProvider(), regionProvider.createAwsRegion())
        val customizer = AwsClientCustomizer { connection, builder ->
            assertThat(connection).isEqualTo(aConnection)
            if (builder is LambdaClientBuilder) {
                builder.endpointOverride(URI.create(wireMockRule.baseUrl()))
            }
        }
        ExtensionTestUtil.maskExtensions(CUSTOMIZER_EP, listOf(customizer), disposableRule.disposable)

        getClientManager().createNewClient(LambdaClient::class, aConnection).use {
            it.listFunctions()
        }

        wireMockRule.verify(anyRequestedFor(urlPathMatching("(.*)/functions/")).withHeader("User-Agent", ContainsPattern("AWS-Toolkit-For-JetBrains/")))
    }

    // Test against real version so bypass ServiceManager for the client manager
    private fun getClientManager() = AwsClientManager()

    class DummyServiceClient(val httpClient: SdkHttpClient) : TestClient() {
        companion object {
            @Suppress("unused")
            @JvmStatic
            fun builder() = DummyServiceClientBuilder()
        }
    }

    class DummyServiceClientBuilder : TestClientBuilder<DummyServiceClientBuilder, DummyServiceClient>() {
        override fun serviceName(): String = "DummyService"

        override fun signingName(): String = serviceName()

        override fun buildClient() = DummyServiceClient(syncClientConfiguration().option(SdkClientOption.SYNC_HTTP_CLIENT))
    }

    class SecondDummyServiceClient(val httpClient: SdkHttpClient) : TestClient() {
        companion object {
            @Suppress("unused")
            @JvmStatic
            fun builder() = SecondDummyServiceClientBuilder()
        }
    }

    class SecondDummyServiceClientBuilder :
        TestClientBuilder<SecondDummyServiceClientBuilder, SecondDummyServiceClient>() {
        override fun serviceName(): String = "SecondDummyService"

        override fun signingName(): String = serviceName()

        override fun buildClient() = SecondDummyServiceClient(syncClientConfiguration().option(SdkClientOption.SYNC_HTTP_CLIENT))
    }

    class InvalidServiceClient : TestClient() {
        override fun close() {}

        override fun serviceName() = "invalidClient"
    }

    class NoServiceNameClient : SdkClient {
        override fun close() {}

        override fun serviceName() = "invalidClient"
    }

    abstract class TestClient : SdkClient, AutoCloseable {
        var closed = false

        override fun serviceName() = "dummyClient"

        override fun close() {
            closed = true
        }

        companion object {
            @Suppress("unused", "MayBeConstant")
            @JvmField
            val SERVICE_METADATA_ID = "DummyService"
        }
    }

    abstract class TestClientBuilder<B : AwsClientBuilder<B, C>, C> : AwsDefaultClientBuilder<B, C>() {
        init {
            overrideConfiguration {
                it.advancedOptions(mapOf(SdkAdvancedClientOption.SIGNER to Signer { _, _ -> throw NotImplementedError() }))
            }
        }

        override fun serviceEndpointPrefix() = "dummyClient"
    }

    private val SdkHttpClient.delegate: SdkHttpClient
        get() {
            val delegateProperty = this::class.declaredMemberProperties.find { it.name == "delegate" }
                ?: throw IllegalArgumentException(
                    "Expected instance of software.amazon.awssdk.core.client.builder.SdkDefaultClientBuilder.NonManagedSdkHttpClient"
                )
            delegateProperty.isAccessible = true
            return delegateProperty.call(this) as SdkHttpClient
        }
}
