// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.Customization
import software.amazon.awssdk.services.codewhispererruntime.model.FeatureEvaluation
import software.amazon.awssdk.services.codewhispererruntime.model.FeatureValue
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableCustomizationsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableCustomizationsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ListFeatureEvaluationsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListFeatureEvaluationsResponse
import software.amazon.awssdk.services.codewhispererruntime.paginators.ListAvailableCustomizationsIterable
import software.amazon.q.core.TokenConnectionSettings
import software.amazon.q.core.region.AwsRegion
import software.amazon.q.jetbrains.core.MockClientManagerRule
import software.amazon.q.jetbrains.core.credentials.LegacyManagedBearerSsoConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.sono.SONO_URL
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfile
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import kotlin.reflect.full.memberFunctions
import kotlin.test.Test

class CodeWhispererFeatureConfigServiceTest {
    @JvmField
    @Rule
    val applicationRule = ApplicationRule()

    @JvmField
    @Rule
    val disposableRule = DisposableRule()

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule()

    @Test
    fun `test FEATURE_DEFINITIONS is not empty`() {
        assertThat(CodeWhispererFeatureConfigService.FEATURE_DEFINITIONS).isNotEmpty
        assertThat(CodeWhispererFeatureConfigService.FEATURE_DEFINITIONS).containsKeys("testFeature")
    }

    @Test
    fun `test highlightCommand returns non-empty`() {
        val mockClient = mockClientManagerRule.create<CodeWhispererRuntimeClient>().stub {
            on { listFeatureEvaluations(any<ListFeatureEvaluationsRequest>()) } doReturn ListFeatureEvaluationsResponse.builder().featureEvaluations(
                listOf(
                    FeatureEvaluation.builder()
                        .feature("highlightCommand")
                        .variation("a new command")
                        .value(FeatureValue.fromStringValue("@highlight"))
                        .build()
                )
            ).build()
        }

        projectRule.project.replaceService(
            QRegionProfileManager::class.java,
            mock<QRegionProfileManager> { on { getQClient(any<Project>(), eq(QRegionProfile()), eq(CodeWhispererRuntimeClient::class)) } doReturn mockClient },
            disposableRule.disposable
        )

        val mockTokenSettings = mock<TokenConnectionSettings> {
            on { providerId } doReturn "mock"
            on { region } doReturn AwsRegion.GLOBAL
            on { withRegion(any()) } doReturn this.mock
        }

        val mockSsoConnection = mock<LegacyManagedBearerSsoConnection> {
            on { startUrl } doReturn "fake sso url"
            on { getConnectionSettings() } doReturn mockTokenSettings
        }

        projectRule.project.replaceService(
            ToolkitConnectionManager::class.java,
            mock { on { activeConnectionForFeature(eq(QConnection.getInstance())) } doReturn mockSsoConnection },
            disposableRule.disposable
        )

        runBlocking {
            CodeWhispererFeatureConfigService.getInstance().fetchFeatureConfigs(projectRule.project)
        }

        assertThat(CodeWhispererFeatureConfigService.getInstance().getHighlightCommandFeature()?.value?.stringValue()).isEqualTo("@highlight")
        assertThat(CodeWhispererFeatureConfigService.getInstance().getHighlightCommandFeature()?.variation).isEqualTo("a new command")
    }

    @Test
    fun `test customizationArnOverride returns empty for BID users`() {
        testCustomizationArnOverrideABHelper(isIdc = false, isInListAvailableCustomizations = false)
        testCustomizationArnOverrideABHelper(isIdc = false, isInListAvailableCustomizations = true)
    }

    @Test
    fun `test customizationArnOverride returns empty for IdC users if arn not in listAvailableCustomizations`() {
        testCustomizationArnOverrideABHelper(isIdc = true, isInListAvailableCustomizations = false)
    }

    @Test
    @Ignore("Test setup isn't correctly for connection().create<Client>()")
    fun `test customizationArnOverride returns non-empty for IdC users if arn in listAvailableCustomizations`() {
        testCustomizationArnOverrideABHelper(isIdc = true, isInListAvailableCustomizations = true)
    }

    private fun testCustomizationArnOverrideABHelper(isIdc: Boolean, isInListAvailableCustomizations: Boolean) {
        mockClientManagerRule.create<CodeWhispererRuntimeClient>().stub {
            on { listFeatureEvaluations(any<ListFeatureEvaluationsRequest>()) } doReturn ListFeatureEvaluationsResponse.builder().featureEvaluations(
                listOf(
                    FeatureEvaluation.builder()
                        .feature("customizationArnOverride")
                        .variation("customization-name")
                        .value(FeatureValue.fromStringValue("test arn"))
                        .build()
                )
            ).build()

            val mockResponseIterable: ListAvailableCustomizationsIterable = mock()
            mockResponseIterable.stub {
                if (isInListAvailableCustomizations) {
                    on { stream() } doReturn listOf(
                        ListAvailableCustomizationsResponse.builder()
                            .customizations(
                                Customization.builder().arn("test arn").name("Test Arn").build()
                            ).build()
                    ).stream()
                } else {
                    on { stream() } doReturn listOf(
                        ListAvailableCustomizationsResponse.builder()
                            .customizations(
                                emptyList()
                            ).build()
                    ).stream()
                }
            }
            on { listAvailableCustomizationsPaginator(any<ListAvailableCustomizationsRequest>()) } doReturn mockResponseIterable
        }

        val mockSsoConnection = mock<LegacyManagedBearerSsoConnection> {
            on { this.startUrl } doReturn if (isIdc) "fake sso url" else SONO_URL
        }

        projectRule.project.replaceService(
            ToolkitConnectionManager::class.java,
            mock { on { activeConnectionForFeature(eq(QConnection.getInstance())) } doReturn mockSsoConnection },
            disposableRule.disposable
        )

        runBlocking {
            CodeWhispererFeatureConfigService.getInstance().fetchFeatureConfigs(projectRule.project)
        }

        if (!isIdc || !isInListAvailableCustomizations) {
            assertThat(CodeWhispererFeatureConfigService.getInstance().getCustomizationFeature()).isNull()
        } else {
            assertThat(CodeWhispererFeatureConfigService.getInstance().getCustomizationFeature()?.value?.stringValue()).isEqualTo("test arn")
            assertThat(CodeWhispererFeatureConfigService.getInstance().getCustomizationFeature()?.variation).isEqualTo("customization-name")
        }
    }

    @Test
    @Ignore("This test has incorrect setup that the a/b value used in codebase doesn't need to be the value type received from the service")
    fun `test service has getters for all the features`() {
        val typeMap = mapOf(
            "kotlin.Boolean" to FeatureValue.Type.BOOL_VALUE,
            "kotlin.String" to FeatureValue.Type.STRING_VALUE,
            "kotlin.Long" to FeatureValue.Type.LONG_VALUE,
            "kotlin.Double" to FeatureValue.Type.DOUBLE_VALUE,
        )
        CodeWhispererFeatureConfigService.FEATURE_DEFINITIONS.forEach { (name, context) ->
            val methodName = "get${name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"

            // Find the member function with the specified name
            val method = CodeWhispererFeatureConfigService::class.memberFunctions.find { it.name == methodName }
            assertThat(method).isNotNull
            val kotlinType = method?.returnType.toString()
            assertThat(context.value.type()).isEqualTo(typeMap[kotlinType])
        }
    }
}
