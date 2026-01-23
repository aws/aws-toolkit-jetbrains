// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkit.jetbrains.core
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.testFramework.replaceService
import migration.software.aws.toolkit.core.ToolkitClientManager
import migration.software.aws.toolkit.core.region.ToolkitRegionProvider
import migration.software.aws.toolkit.jetbrains.core.AwsResourceCache
import migration.software.aws.toolkit.jetbrains.core.coroutines.PluginCoroutineScopeTracker
import migration.software.aws.toolkit.jetbrains.core.credentials.CredentialManager
import migration.software.aws.toolkit.jetbrains.core.credentials.sso.SsoLoginCallbackProvider
import migration.software.aws.toolkit.jetbrains.services.telemetry.TelemetryService
import migration.software.aws.toolkit.jetbrains.settings.AwsSettings
import org.mockito.kotlin.mock
import software.aws.toolkit.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkit.jetbrains.core.credentials.sso.MockSsoLoginCallbackProvider
import software.aws.toolkit.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkit.jetbrains.services.telemetry.NoOpTelemetryService
import software.aws.toolkit.jetbrains.settings.MockAwsSettings

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
            AwsSettings::class.java,
            MockAwsSettings(),
            disposable
        )

        app.replaceService(
            ToolkitClientManager::class.java,
            mock<ToolkitClientManager>(),
            disposable
        )

        app.replaceService(
            TelemetryService::class.java,
            NoOpTelemetryService(),
            disposable
        )

        app.replaceService(
            ToolkitRegionProvider::class.java,
            AwsRegionProvider(),
            disposable
        )

        app.replaceService(
            CredentialManager::class.java,
            MockCredentialsManager(),
            disposable
        )

        app.replaceService(
            AwsResourceCache::class.java,
            MockResourceCache(),
            disposable
        )

        app.replaceService(
            SsoLoginCallbackProvider::class.java,
            MockSsoLoginCallbackProvider(),
            disposable
        )

        app.replaceService(
            PluginCoroutineScopeTracker::class.java,
            PluginCoroutineScopeTracker(),
            disposable
        )
    }
}
