// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.q.core.ClientConnectionSettings
import software.amazon.q.core.ConnectionSettings
import software.amazon.q.core.TokenConnectionSettings
import software.amazon.q.core.ToolkitClientCustomizer
import software.amazon.q.core.ToolkitClientManager
import software.amazon.q.core.credentials.CredentialIdentifier
import software.amazon.q.core.credentials.ToolkitCredentialsChangeListener
import software.amazon.q.core.region.ToolkitRegionProvider
import software.amazon.q.core.utils.tryOrNull
import software.amazon.q.jetbrains.core.credentials.AwsConnectionManager
import software.amazon.q.jetbrains.core.credentials.CredentialManager
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.amazon.q.jetbrains.core.region.AwsRegionProvider
import software.amazon.q.jetbrains.services.telemetry.PluginResolver
import software.amazon.q.jetbrains.settings.AwsSettings

open class AwsClientManager : ToolkitClientManager(), Disposable {
    init {
        val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
        busConnection.subscribe(
            CredentialManager.CREDENTIALS_CHANGED,
            object : ToolkitCredentialsChangeListener {
                override fun providerRemoved(identifier: CredentialIdentifier) {
                    invalidateSdks(identifier.id)
                }

                override fun providerRemoved(providerId: String) {
                    invalidateSdks(providerId)
                }
            }
        )

        busConnection.subscribe(
            BearerTokenProviderListener.TOPIC,
            object : BearerTokenProviderListener {
                override fun onProviderChange(providerId: String, newScopes: List<String>?) {
                    invalidateSdks(providerId)
                }

                override fun invalidate(providerId: String) {
                    invalidateSdks(providerId)
                }
            }
        )
    }

    override fun userAgent() = getUserAgent()

    override fun dispose() {
        shutdown()
    }

    override fun sdkHttpClient(): SdkHttpClient = AwsSdkClient.getInstance().sharedSdkClient()

    override fun getRegionProvider(): ToolkitRegionProvider = AwsRegionProvider.getInstance()

    override fun globalClientCustomizer(
        credentialProvider: AwsCredentialsProvider?,
        tokenProvider: SdkTokenProvider?,
        regionId: String,
        builder: AwsClientBuilder<*, *>,
        clientOverrideConfiguration: ClientOverrideConfiguration.Builder,
    ) {
        CUSTOMIZER_EP.extensionList.forEach { it.customize(credentialProvider, tokenProvider, regionId, builder, clientOverrideConfiguration) }
    }

    companion object {
        @JvmStatic
        fun getInstance(): ToolkitClientManager = service()

        fun getUserAgent(): String {
            val pluginResolver = PluginResolver.fromCurrentThread()
            val pluginName = pluginResolver.product.toString().replace(" ", "-")
            val pluginVersion = pluginResolver.version
            return "$pluginName/$pluginVersion $platformName/$platformVersion ClientId/${AwsSettings.getInstance().clientId}"
        }

        private val platformName = tryOrNull { ApplicationNamesInfo.getInstance().fullProductNameWithEdition.replace(' ', '-') }
        private val platformVersion = tryOrNull { ApplicationInfoEx.getInstanceEx().fullVersion.replace(' ', '-') }

        val CUSTOMIZER_EP = ExtensionPointName<ToolkitClientCustomizer>("amazon.q.sdk.clientCustomizer")
    }
}

inline fun <reified T : SdkClient> Project.awsClient(): T {
    val accountSettingsManager = AwsConnectionManager.getInstance(this)

    return AwsClientManager
        .getInstance()
        .getClient(accountSettingsManager.activeCredentialProvider, accountSettingsManager.activeRegion)
}

inline fun <reified T : SdkClient> ConnectionSettings.awsClient(): T = AwsClientManager.getInstance().getClient(credentials, region)

inline fun <reified T : SdkClient> TokenConnectionSettings.awsClient(): T = AwsClientManager.getInstance().getClient(this)

inline fun <reified T : SdkClient> ClientConnectionSettings<*>.awsClient(): T = when (this) {
    is ConnectionSettings -> awsClient<T>()
    is TokenConnectionSettings -> awsClient<T>()
}
