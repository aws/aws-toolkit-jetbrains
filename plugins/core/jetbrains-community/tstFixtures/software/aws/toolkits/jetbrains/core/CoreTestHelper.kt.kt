// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.core
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.testFramework.replaceService
import org.mockito.kotlin.mock
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.MockSsoLoginCallbackProvider
import software.aws.toolkits.jetbrains.services.telemetry.NoOpTelemetryService
import software.aws.toolkits.jetbrains.settings.MockAwsSettings
/**
Registers missing core services for tests that were previously registered in plugin.xml
 */
object CoreTestHelper {
    fun registerMissingServices(disposable: Disposable) {
        val app = ApplicationManager.getApplication()

        val extensionArea = app.extensionArea
        if (!extensionArea.hasExtensionPoint("aws.toolkit.core.startupAuthFactory")) {
            extensionArea.registerExtensionPoint(
                "aws.toolkit.core.startupAuthFactory",
                "software.aws.toolkits.jetbrains.core.credentials.ToolkitStartupAuthFactory",
                ExtensionPoint.Kind.INTERFACE
            )
        }

        app.replaceService(
            migration.software.aws.toolkits.jetbrains.settings.AwsSettings::class.java,
            MockAwsSettings(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.core.ToolkitClientManager::class.java,
            mock<migration.software.aws.toolkits.core.ToolkitClientManager>(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.jetbrains.services.telemetry.TelemetryService::class.java,
            NoOpTelemetryService(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.core.region.ToolkitRegionProvider::class.java,
            AwsRegionProvider(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.jetbrains.core.credentials.CredentialManager::class.java,
            MockCredentialsManager(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.jetbrains.core.AwsResourceCache::class.java,
            MockResourceCache(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.jetbrains.core.credentials.sso.SsoLoginCallbackProvider::class.java,
            MockSsoLoginCallbackProvider(),
            disposable
        )

        app.replaceService(
            migration.software.aws.toolkits.jetbrains.core.coroutines.PluginCoroutineScopeTracker::class.java,
            migration.software.aws.toolkits.jetbrains.core.coroutines.PluginCoroutineScopeTracker(),
            disposable
        )
    }
}
