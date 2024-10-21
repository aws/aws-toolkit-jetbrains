// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.FeatureValue
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableCustomizationsRequest
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.utils.isQExpired

@Service
class CodeWhispererFeatureConfigService {
    private val featureConfigs = mutableMapOf<String, FeatureContext>()

    @RequiresBackgroundThread
    fun fetchFeatureConfigs(project: Project) {
        if (isQExpired(project)) return

        LOG.debug { "Fetching feature configs" }
        try {
            val response = project.awsClient<CodeWhispererRuntimeClient>().listFeatureEvaluations {
                it.userContext(codeWhispererUserContext())
            }

            // Simply force overwrite feature configs from server response, no needed to check existing values.
            response.featureEvaluations().forEach {
                featureConfigs[it.feature()] = FeatureContext(it.feature(), it.variation(), it.value())
            }

            // Only apply new auto-trigger UX to BID users
            val isNewAutoTriggerUX = getNewAutoTriggerUX()
            if (isNewAutoTriggerUX) {
                calculateIfIamIdentityCenterConnection(project) {
                    featureConfigs.remove(NEW_AUTO_TRIGGER_UX)
                }
            }

            val customizationArnOverride = featureConfigs[CUSTOMIZATION_ARN_OVERRIDE_NAME]?.value?.stringValue()
            if (customizationArnOverride != null) {
                // Double check if server-side wrongly returns a customizationArn to BID users
                calculateIfBIDConnection(project) {
                    featureConfigs.remove(CUSTOMIZATION_ARN_OVERRIDE_NAME)
                }
                val availableCustomizations =
                    calculateIfIamIdentityCenterConnection(project) {
                        try {
                            project.awsClient<CodeWhispererRuntimeClient>().listAvailableCustomizationsPaginator(
                                ListAvailableCustomizationsRequest.builder().build()
                            )
                                .stream()
                                .toList()
                                .flatMap { resp ->
                                    resp.customizations().map {
                                        it.arn()
                                    }
                                }
                        } catch (e: Exception) {
                            LOG.debug(e) { "Failed to list available customizations" }
                            null
                        }
                    }

                // If customizationArn from A/B is not available in listAvailableCustomizations response, don't use this value
                if (availableCustomizations?.contains(customizationArnOverride) == false) {
                    LOG.debug {
                        "Customization arn $customizationArnOverride not available in listAvailableCustomizations, not using"
                    }
                    featureConfigs.remove(CUSTOMIZATION_ARN_OVERRIDE_NAME)
                }
            }
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

    fun getIsDataCollectionEnabled(): Boolean =
        getFeatureValueForKey(DATA_COLLECTION_FEATURE).stringValue() == "data-collection"

    fun getCustomizationArnOverride(): String = getFeatureValueForKey(CUSTOMIZATION_ARN_OVERRIDE_NAME).stringValue()

    fun getNewAutoTriggerUX(): Boolean = getFeatureValueForKey(NEW_AUTO_TRIGGER_UX).stringValue() == "TREATMENT"

    fun getInlineCompletion(): Boolean = getFeatureValueForKey(INLINE_COMPLETION).stringValue() == "TREATMENT"

    // Get the feature value for the given key.
    // In case of a misconfiguration, it will return a default feature value of Boolean false.
    private fun getFeatureValueForKey(name: String): FeatureValue =
        featureConfigs[name]?.value ?: FEATURE_DEFINITIONS[name]?.value
            ?: FeatureValue.builder().boolValue(false).build()

    companion object {
        fun getInstance(): CodeWhispererFeatureConfigService = service()
        private const val TEST_FEATURE_NAME = "testFeature"
        private const val INLINE_COMPLETION = "ProjectContextV2"
        private const val DATA_COLLECTION_FEATURE = "IDEProjectContextDataCollection"
        const val CUSTOMIZATION_ARN_OVERRIDE_NAME = "customizationArnOverride"
        private const val NEW_AUTO_TRIGGER_UX = "newAutoTriggerUX"
        private val LOG = getLogger<CodeWhispererFeatureConfigService>()

        // TODO: add real feature later
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
            )
        )
    }
}

data class FeatureContext(
    val name: String,
    val variation: String,
    val value: FeatureValue,
)
