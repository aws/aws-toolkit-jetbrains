// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import software.amazon.awssdk.core.SdkClient
import software.amazon.awssdk.http.SdkHttpClient
import software.aws.toolkits.core.ToolkitClientManager
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.core.credentials.CredentialProviderNotFoundException
import software.aws.toolkits.core.credentials.ToolkitCredentialsChangeListener
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.region.ToolkitRegionProvider
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.AwsToolkit
import software.aws.toolkits.jetbrains.core.credentials.ConnectionSettings
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider

open class AwsClientManager(project: Project) : ToolkitClientManager(), Disposable {

    private val accountSettingsManager = AwsConnectionManager.getInstance(project)
    private val regionProvider = AwsRegionProvider.getInstance()

    init {
        Disposer.register(project, Disposable { this.dispose() })

        val busConnection = ApplicationManager.getApplication().messageBus.connect(project)
        busConnection.subscribe(CredentialManager.CREDENTIALS_CHANGED, object : ToolkitCredentialsChangeListener {
            override fun providerRemoved(identifier: CredentialIdentifier) {
                invalidateSdks(identifier.id)
            }
        })
    }

    override fun dispose() {
        shutdown()
    }

    override val sdkHttpClient: SdkHttpClient
        get() = AwsSdkClient.getInstance().sdkHttpClient

    override val userAgent = AwsClientManager.userAgent

    override fun getCredentialsProvider(): ToolkitCredentialsProvider {
        try {
            return accountSettingsManager.activeCredentialProvider
        } catch (e: CredentialProviderNotFoundException) {
            // TODO: Notify user

            // Throw canceled exception to stop any task relying on this call
            throw ProcessCanceledException(e)
        }
    }

    override fun getRegion(): AwsRegion = accountSettingsManager.activeRegion

    override fun getRegionProvider(): ToolkitRegionProvider = regionProvider

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ToolkitClientManager = ServiceManager.getService(project, ToolkitClientManager::class.java)

        val userAgent: String by lazy {
            val platformName = tryOrNull { ApplicationNamesInfo.getInstance().fullProductNameWithEdition.replace(' ', '-') }
            val platformVersion = tryOrNull { ApplicationInfoEx.getInstanceEx().fullVersion.replace(' ', '-') }
            "AWS-Toolkit-For-JetBrains/${AwsToolkit.PLUGIN_VERSION} $platformName/$platformVersion"
        }
    }
}

inline fun <reified T : SdkClient> Project.awsClient(
    credentialsProviderOverride: ToolkitCredentialsProvider? = null,
    regionOverride: AwsRegion? = null
): T = AwsClientManager
    .getInstance(this)
    .getClient(credentialsProviderOverride = credentialsProviderOverride, regionOverride = regionOverride)

inline fun <reified T : SdkClient> Project.awsClient(connectionSettings: ConnectionSettings): T = AwsClientManager
    .getInstance(this)
    .getClient(connectionSettings.credentials, connectionSettings.region)
