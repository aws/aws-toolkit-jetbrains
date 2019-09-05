// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.execution.remote

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsResourceCache
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.services.lambda.resources.LambdaResources
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel

class RemoteLambdaRunSettingsEditor(project: Project) : SettingsEditor<RemoteLambdaRunConfiguration>() {
    private val view = RemoteLambdaRunSettingsEditorPanel(project)
    private val credentialManager = CredentialManager.getInstance()
    private val resourceCache = AwsResourceCache.getInstance(project)
    private val settings = AtomicReference<Pair<AwsRegion?, String?>>(null to null)
    private val functionName = AtomicReference<String?>()

    internal fun updateFunctions(region: AwsRegion?, credentialProvider: String?) {
        val (oldRegion, oldCredentialProvider) = settings.getAndUpdate { (_, _) -> region to credentialProvider }
        if (oldRegion == region && oldCredentialProvider == credentialProvider) return
        view.functionNames.selectedItem = null
        view.setFunctionNames(emptyList())

        credentialProvider ?: return
        region ?: return

        val credProvider = try {
            credentialManager.getCredentialProvider(credentialProvider)
        } catch (e: CredentialProviderNotFound) {
            return
        }

        view.functionNames.isEnabled = false

        resourceCache.getResource(LambdaResources.LIST_FUNCTIONS, region, credProvider).whenComplete { functions, error ->
            val functionNames = when (error) {
                null -> functions.mapNotNull { it.functionName() }.toList()
                else -> {
                    LOG.warn(error) { "Failed to load functions" }
                    emptyList()
                }
            }
            runInEdt(ModalityState.any()) {
                view.setFunctionNames(functionNames)
                view.functionNames.isEnabled = true
                val selected = functionName.get()
                if (functionNames.contains(selected)) {
                    view.functionNames.selectedItem = selected
                } else {
                    functionName.set(null)
                }
            }
        }
    }

    override fun createEditor(): JPanel = view.panel

    override fun resetEditorFrom(configuration: RemoteLambdaRunConfiguration) {
        functionName.set(configuration.functionName())
        if (configuration.isUsingInputFile()) {
            view.lambdaInput.inputFile = configuration.inputSource()
        } else {
            view.lambdaInput.inputText = configuration.inputSource()
        }
    }

    override fun applyEditorTo(configuration: RemoteLambdaRunConfiguration) {
        configuration.functionName(view.functionName)
        if (view.lambdaInput.isUsingFile) {
            configuration.useInputFile(view.lambdaInput.inputFile)
        } else {
            configuration.useInputText(view.lambdaInput.inputText)
        }
    }

    private companion object {
        val LOG = getLogger<RemoteLambdaRunSettingsEditor>()
    }
}
