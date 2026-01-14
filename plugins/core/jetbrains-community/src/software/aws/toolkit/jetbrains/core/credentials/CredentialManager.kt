// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import software.aws.toolkit.jetbrains.core.credentials.sso.bearer.BearerTokenProviderListener
import software.aws.toolkit.core.credentials.CredentialProviderFactory
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.tryOrNull
import software.aws.toolkits.telemetry.AwsTelemetry
import java.util.concurrent.atomic.AtomicInteger

typealias CredentialManager = migration.software.aws.toolkit.jetbrains.core.credentials.CredentialManager

class DefaultCredentialManager : CredentialManager(), Disposable {
    private val extensionMap: Map<String, CredentialProviderFactory>
        get() = EP_NAME.extensionList.associateBy {
            it.id
        }

    init {
        extensionMap.values.forEach { providerFactory ->
            val count = AtomicInteger(0)
            LOG.tryOrNull("Failed to set up $providerFactory") {
                providerFactory.setUp { change ->
                    change.added.forEach {
                        addProvider(it)
                        count.incrementAndGet()
                    }

                    change.modified.forEach {
                        modifyProvider(it)
                    }

                    change.removed.forEach {
                        removeProvider(it)
                        count.decrementAndGet()
                    }

                    change.ssoAdded.forEach {
                        addSsoSession(it)
                    }

                    change.ssoModified.forEach {
                        modifySsoSession(it)
                    }

                    change.ssoRemoved.forEach {
                        removeSsoSession(it)
                    }

                    AwsTelemetry.loadCredentials(
                        credentialSourceId = providerFactory.credentialSourceId.toTelemetryCredentialSourceId(),
                        value = count.get().toDouble()
                    )
                }
            }
        }

        // sync bearer changes back to any profiles with a dependency
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            BearerTokenProviderListener.Companion.TOPIC,
            object : BearerTokenProviderListener {
                override fun onProviderChange(providerId: String, newScopes: List<String>?) {
                    modifyDependentProviders(providerId)
                }

                override fun invalidate(providerId: String) {
                    modifyDependentProviders(providerId)
                }
            }
        )
    }

    override fun dispose() {}

    override fun factoryMapping(): Map<String, CredentialProviderFactory> = extensionMap

    companion object {
        val EP_NAME = ExtensionPointName.create<CredentialProviderFactory>("aws.toolkit.credentialProviderFactory")
        private val LOG = getLogger<DefaultCredentialManager>()
    }
}
