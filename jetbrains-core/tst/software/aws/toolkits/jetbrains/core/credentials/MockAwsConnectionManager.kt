// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import org.junit.rules.ExternalResource
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.aws.toolkits.core.credentials.CredentialIdentifier
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.utils.spinUntil
import java.time.Duration

class MockAwsConnectionManager(project: Project) : AwsConnectionManager(project) {
    init {
        reset()
    }

    fun reset() {
        recentlyUsedRegions.clear()
        recentlyUsedProfiles.clear()
        val regionProvider = AwsRegionProvider.getInstance()
        changeConnectionSettings(MockCredentialsManager.DUMMY_PROVIDER_IDENTIFIER, regionProvider.defaultRegion())

        waitUntilConnectionStateIsStable()
    }

    fun changeRegionAndWait(region: AwsRegion) {
        changeRegion(region)
        waitUntilConnectionStateIsStable()
    }

    fun changeCredentialProviderAndWait(identifier: CredentialIdentifier) {
        changeCredentialProvider(identifier)
        waitUntilConnectionStateIsStable()
    }

    fun nullifyCredentialProviderAndWait() {
        changeConnectionSettings(null, selectedRegion)
        waitUntilConnectionStateIsStable()
    }

    fun setConnectionState(state: ConnectionState) {
        connectionState = state
    }

    override suspend fun validate(credentialsProvider: ToolkitCredentialsProvider, region: AwsRegion) {}

    companion object {
        fun getInstance(project: Project): MockAwsConnectionManager =
            ServiceManager.getService(project, AwsConnectionManager::class.java) as MockAwsConnectionManager
    }

    class ProjectAccountSettingsManagerRule(projectRule: ProjectRule) : ExternalResource() {
        val settingsManager by lazy {
            getInstance(projectRule.project)
        }

        override fun before() {
            settingsManager.reset()
        }

        override fun after() {
            settingsManager.reset()
        }
    }
}

fun <T> runUnderRealCredentials(project: Project, block: () -> T): T {
    val credentials = DefaultCredentialsProvider.create().resolveCredentials()

    val manager = MockAwsConnectionManager.getInstance(project)
    val credentialsManager = MockCredentialsManager.getInstance()
    val oldActive = manager.connectionSettings()?.credentials
    val realCredentialsProvider = credentialsManager.addCredentials("RealCredentials", credentials)
    try {
        println("Running using real credentials")

        manager.changeCredentialProviderAndWait(realCredentialsProvider)

        return block.invoke()
    } finally {
        oldActive?.let {
            credentialsManager.getCredentialIdentifierById(oldActive.id)?.let {
                manager.changeCredentialProviderAndWait(it)
            }
        }
        credentialsManager.removeCredentials(realCredentialsProvider)
    }
}

fun AwsConnectionManager.waitUntilConnectionStateIsStable() = spinUntil(Duration.ofSeconds(10)) {
    connectionState.isTerminal
}
