// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("BannedImports")
package software.aws.toolkits.jetbrains.services.amazonq

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.FeatureValue
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.getLogger
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.amazon.q.jetbrains.utils.isQExpired
@Service
class CodeWhispererFeatureConfigService {
    private val featureConfigs = mutableMapOf<String, FeatureContext>()

    @RequiresBackgroundThread
    fun fetchFeatureConfigs(project: Project) {
        if (isQExpired(project)) return
        val connection = connection(project)
        if (connection == null) {
            LOG.error { "No connection found even after validating Q connection" }
            return
        }

        LOG.debug { "Fetching feature configs" }
        try {
            val response = QRegionProfileManager.getInstance().getQClient<CodeWhispererRuntimeClient>(project).listFeatureEvaluations {
                it.userContext(codeWhispererUserContext())
                it.profileArn(QRegionProfileManager.getInstance().activeProfile(project)?.arn)
            } ?: return

            // Simply force overwrite feature configs from server response, no needed to check existing values.
            response.featureEvaluations().forEach {
                featureConfigs[it.feature()] = FeatureContext(it.feature(), it.variation(), it.value())
            }

            validateNewAutoTriggerUX(project)

            CodeWhispererFeatureConfigListener.notifyUiFeatureConfigsAvailable()
        } catch (e: Exception) {
            LOG.debug(e) { "Error when fetching feature configs" }
        }
        LOG.debug { "Current feature configs: ${getFeatureConfigsTelemetry()}" }
    }

    fun getFeatureConfigsTelemetry(): String =
        "{${
            featureConfigs.entries.joinToString(", ") { (name, context) ->
                "$name: ${context.variation}"
            }
        }}"

    // TODO: for all feature variations, define a contract that can be enforced upon the implementation of
    // the business logic.
    // When we align on a new feature config, client-side will implement specific business logic to utilize
    // these values by:
    // 1) Add an entry in FEATURE_DEFINITIONS, which is <feature_name> to <feature_context>.
    // 2) Add a function with name `getXXX`, where XXX refers to the feature name.
    // 3) Specify the return type: One of the return type String/Boolean/Long/Double should be used here.
    // 4) Specify the key for the `getFeatureValueForKey` helper function which is the feature name.
    // 5) Specify the corresponding type value getter for the `FeatureValue` class. For example,
    // if the return type is Long, then the corresponding type value getter is `longValue()`.
    // 6) Add a test case for this feature.
    fun getTestFeature(): String = getFeatureValueForKey(TEST_FEATURE_NAME).stringValue()

    fun getCustomizationFeature(): FeatureContext? = getFeature(CUSTOMIZATION_ARN_OVERRIDE_NAME)

    fun getHighlightCommandFeature(): FeatureContext? = getFeature(HIGHLIGHT_COMMAND_NAME)

    fun getNewAutoTriggerUX(): Boolean = getFeatureValueForKey(NEW_AUTO_TRIGGER_UX).stringValue() == "TREATMENT"

    fun getInlineCompletion(): Boolean = getFeatureValueForKey(INLINE_COMPLETION).stringValue() == "TREATMENT"

    fun getChatWSContext(): Boolean = getFeatureValueForKey(CHAT_WS_CONTEXT).stringValue() == "TREATMENT"

    // convert into mynahUI parsable string
    // format: '[["key1", {"name":"Feature1","variation":"A","value":true}]]'
    fun getFeatureConfigJsonString(): String {
        val jsonString = featureConfigs.entries.map { (key, value) ->
            "[\"$key\",${Gson().toJson(value)}]"
        }
        return """
            '$jsonString'
        """.trimIndent()
    }

    // Get the feature value for the given key.
    // In case of a misconfiguration, it will return a default feature value of Boolean false.
    private fun getFeatureValueForKey(name: String): FeatureValue =
        getFeature(name)?.value ?: FEATURE_DEFINITIONS[name]?.value
            ?: FeatureValue.builder().boolValue(false).build()

    // Gets the feature context for a given feature name.
    private fun getFeature(name: String): FeatureContext? = featureConfigs[name]

    private fun connection(project: Project) =
        ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())

    private fun validateNewAutoTriggerUX(project: Project) {
        // Only apply new auto-trigger UX to BID users
        val isNewAutoTriggerUX = getNewAutoTriggerUX()
        if (isNewAutoTriggerUX) {
            calculateIfIamIdentityCenterConnection(project) {
                featureConfigs.remove(NEW_AUTO_TRIGGER_UX)
            }
        }
    }

    fun validateCustomizationOverride(project: Project, featOverrideContext: FeatureContext): CodeWhispererCustomization? {
        val customizationArnOverride = featOverrideContext.value.stringValue()
        connection(project) ?: return null
        customizationArnOverride ?: return null

        // Double check if server-side wrongly returns a customizationArn to BID users
        calculateIfBIDConnection(project) {
            featureConfigs.remove(CUSTOMIZATION_ARN_OVERRIDE_NAME)
        }
        val availableCustomizations =
            calculateIfIamIdentityCenterConnection(project) {
                try {
                    val profiles = QRegionProfileManager.getInstance().listRegionProfiles(project)
                        ?: error("Attempted to fetch profiles while there does not exist")

                    val customs = profiles.flatMap { profile ->
                        QRegionProfileManager.getInstance().getQClient<CodeWhispererRuntimeClient>(project)
                            .listAvailableCustomizations { it.profileArn(profile.arn) }.customizations().map { originalCustom ->
                                CodeWhispererCustomization(
                                    arn = originalCustom.arn(),
                                    name = originalCustom.name(),
                                    description = originalCustom.description(),
                                    profile = profile
                                )
                            }
                    }

                    customs
                } catch (e: Exception) {
                    LOG.debug(e) { "encountered error while validating customization override" }
                    null
                }
            }

        val isValidOverride = availableCustomizations != null && availableCustomizations.any { it.arn == customizationArnOverride }

        // If customizationArn from A/B is not available in listAvailableCustomizations response, don't use this value
        return if (!isValidOverride) {
            LOG.debug { "Customization arn $customizationArnOverride not available in listAvailableCustomizations, not using" }
            featureConfigs.remove(CUSTOMIZATION_ARN_OVERRIDE_NAME)
            null
        } else {
            availableCustomizations?.find { it.arn == customizationArnOverride }
        }
    }

    companion object {
        fun getInstance(): CodeWhispererFeatureConfigService = service()
        private const val TEST_FEATURE_NAME = "testFeature"
        private const val INLINE_COMPLETION = "ProjectContextV2"
        private const val CUSTOMIZATION_ARN_OVERRIDE_NAME = "customizationArnOverride"
        private const val HIGHLIGHT_COMMAND_NAME = "highlightCommand"
        private const val NEW_AUTO_TRIGGER_UX = "newAutoTriggerUX"
        private const val CHAT_WS_CONTEXT = "WorkspaceContext"
        private val LOG = getLogger<CodeWhispererFeatureConfigService>()

        // Also serve as default values in case server-side config isn't there yet
        val FEATURE_DEFINITIONS = mapOf(
            TEST_FEATURE_NAME to FeatureContext(
                TEST_FEATURE_NAME,
                "CONTROL",
                FeatureValue.builder().stringValue("testValue").build()
            ),
            // For BuilderId and error cases return an empty string "arn" to handle
            CUSTOMIZATION_ARN_OVERRIDE_NAME to FeatureContext(
                CUSTOMIZATION_ARN_OVERRIDE_NAME,
                "customizationARN",
                FeatureValue.builder().stringValue("").build()
            ),
            NEW_AUTO_TRIGGER_UX to FeatureContext(
                NEW_AUTO_TRIGGER_UX,
                "CONTROL",
                FeatureValue.builder().stringValue("CONTROL").build()
            ),
            INLINE_COMPLETION to FeatureContext(
                INLINE_COMPLETION,
                "CONTROL",
                FeatureValue.builder().stringValue("CONTROL").build()
            ),
            CHAT_WS_CONTEXT to FeatureContext(
                CHAT_WS_CONTEXT,
                "CONTROL",
                FeatureValue.builder().stringValue("CONTROL").build()
            ),
        )
    }
}

data class FeatureContext(
    val name: String,
    val variation: String,
    val value: FeatureValue,
)
