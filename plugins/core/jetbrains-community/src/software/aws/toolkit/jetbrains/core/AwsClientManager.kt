// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core

import com.intellij.ide.plugins.PluginManager
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
import software.aws.toolkit.core.ClientConnectionSettings
import software.aws.toolkit.core.ConnectionSettings
import software.aws.toolkit.core.TokenConnectionSettings
import software.aws.toolkit.core.ToolkitClientCustomizer
import software.aws.toolkit.core.ToolkitClientManager
import software.aws.toolkit.core.credentials.CredentialIdentifier
import software.aws.toolkit.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkit.core.region.ToolkitRegionProvider
import software.aws.toolkit.core.utils.tryOrNull
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import software.aws.toolkit.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkit.jetbrains.settings.AwsSettings

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
            BearerTokenProviderListener.Companion.TOPIC,
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

    override fun getRegionProvider(): ToolkitRegionProvider = AwsRegionProvider.Companion.getInstance()

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
            val platformName = tryOrNull { ApplicationNamesInfo.getInstance().fullProductNameWithEdition.replace(' ', '-') }
            val platformVersion = tryOrNull { ApplicationInfoEx.getInstanceEx().fullVersion.replace(' ', '-') }
            val pluginVersion = tryOrNull { PluginManager.getPluginByClass(this::class.java)?.version }
            return "AWS-Toolkit-For-JetBrains/$pluginVersion $platformName/$platformVersion ClientId/${AwsSettings.getInstance().clientId}"
        }

        private val platformName = tryOrNull { ApplicationNamesInfo.getInstance().fullProductNameWithEdition.replace(' ', '-') }
        private val platformVersion = tryOrNull { ApplicationInfoEx.getInstanceEx().fullVersion.replace(' ', '-') }

        val CUSTOMIZER_EP = ExtensionPointName<ToolkitClientCustomizer>("aws.toolkit.sdk.clientCustomizer")
    }
}

inline fun <reified T : SdkClient> Project.awsClient(): T {
    val accountSettingsManager = AwsConnectionManager.Companion.getInstance(this)

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
