// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.testFramework.ProjectRule
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Rule
import org.junit.Test
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesRequest
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesResponse
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.ResourceTagMapping
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.Tag
import software.aws.toolkits.jetbrains.core.MockClientManagerRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceUpdateAgainstCodePipelineTest {

    @Rule
    @JvmField
    val projectRule = ProjectRule()

    @JvmField
    @Rule
    val mockClientManagerRule = MockClientManagerRule(projectRule)

    private val mockClient by lazy {
        mockClientManagerRule.register(ResourceGroupsTaggingApiClient::class, delegateMock())
    }

    private val RESOURCE_ARN = "resourceARN"
    private val RESOURCE_TYPE_FILTER = "resourceTypeFilter"
    private val CODEPIPELINE_ARN = "codePipelineArn"
    private val RESOURCE_NAME = "resourceName"
    private val RESOURCE_TYPE = "resourceType"
    private val OPERATION = "operation"

    @Test
    fun getCodePipelineArnForResource_resourceTagMappingNotFound() {
        whenever(mockClient.getResources(any<GetResourcesRequest>()))
                .thenReturn(
                        GetResourcesResponse.builder().build()
                )

        assertNull(getCodePipelineArnForResource(projectRule.project, RESOURCE_ARN, RESOURCE_TYPE_FILTER))
    }

    @Test
    fun getCodePipelineArnForResource_resourceArnNotFound() {
        whenever(mockClient.getResources(any<GetResourcesRequest>()))
                .thenReturn(
                        GetResourcesResponse.builder()
                                .resourceTagMappingList(
                                        getResourceTagMapping("arn", "key", "value")
                                )
                                .build()
                )

        assertNull(getCodePipelineArnForResource(projectRule.project, RESOURCE_ARN, RESOURCE_TYPE_FILTER))
    }

    @Test
    fun getCodePipelineArnForResource_pipelineTagNotFound() {
        whenever(mockClient.getResources(any<GetResourcesRequest>()))
                .thenReturn(
                        GetResourcesResponse.builder()
                                .resourceTagMappingList(
                                        getResourceTagMapping(RESOURCE_ARN, "key", "value")
                                )
                                .build()
                )

        assertNull(getCodePipelineArnForResource(projectRule.project, RESOURCE_ARN, RESOURCE_TYPE_FILTER))
    }

    @Test
    fun getCodePipelineArnForResource_pipelineTagFound() {
        whenever(mockClient.getResources(any<GetResourcesRequest>()))
                .thenReturn(
                        GetResourcesResponse.builder()
                                .resourceTagMappingList(
                                        getResourceTagMapping(RESOURCE_ARN, CODEPIPELINE_SYSTEM_TAG_KEY, CODEPIPELINE_ARN)
                                )
                                .build()
                )

        assertEquals(CODEPIPELINE_ARN, getCodePipelineArnForResource(projectRule.project, RESOURCE_ARN, RESOURCE_TYPE_FILTER))
    }

    @Test
    fun warnResourceUpdateAgainstCodePipeline_codePipelineArnIsNull() {
        assertFalse(warnResourceUpdateAgainstCodePipeline(projectRule.project, null, RESOURCE_NAME, RESOURCE_TYPE, OPERATION))
    }

    @Test
    fun warnResourceUpdateAgainstCodePipeline_userClicksYes() {
        Messages.setTestDialog(TestDialog.OK)
        assertTrue(warnResourceUpdateAgainstCodePipeline(projectRule.project, CODEPIPELINE_ARN, RESOURCE_NAME, RESOURCE_TYPE, OPERATION))
    }

    @Test
    fun warnResourceUpdateAgainstCodePipeline_userClicksNo() {
        Messages.setTestDialog(TestDialog.NO)
        assertFalse(warnResourceUpdateAgainstCodePipeline(projectRule.project, CODEPIPELINE_ARN, RESOURCE_NAME, RESOURCE_TYPE, OPERATION))
    }

    private fun getResourceTagMapping(resourceARN: String, tagKey: String, tagValue: String): ResourceTagMapping {
        return ResourceTagMapping.builder()
                .resourceARN(resourceARN)
                .tags(
                        Tag.builder()
                                .key(tagKey)
                                .value(tagValue)
                                .build()
                )
                .build()
    }
}
