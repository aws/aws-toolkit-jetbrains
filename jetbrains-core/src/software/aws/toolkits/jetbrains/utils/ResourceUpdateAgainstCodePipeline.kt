// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showYesNoDialog
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesRequest
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.TagFilter
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.resources.message

const val CODEPIPELINE_SYSTEM_TAG_KEY = "aws1:codepipeline:pipelineArn"
const val LAMBDA_RESOURCE_TYPE_FILTER = "lambda:function"
const val STACK_RESOURCE_TYPE_FILTER = "cloudformation:stack"

fun warnLambdaUpdateAgainstCodePipeline(project: Project, functionName: String, functionArn: String, operation: String): Boolean {
    val codePipelineArn = getCodePipelineArnForResource(project, functionArn, LAMBDA_RESOURCE_TYPE_FILTER)
    return warnResourceUpdateAgainstCodePipeline(project, codePipelineArn, functionName, message("codepipeline.lambda.resource_type"), operation)
}

fun warnStackUpdateAgainstCodePipeline(project: Project, stackName: String, stackArn: String, operation: String): Boolean {
    val codePipelineArn = getCodePipelineArnForResource(project, stackArn, STACK_RESOURCE_TYPE_FILTER)
    return warnResourceUpdateAgainstCodePipeline(project, codePipelineArn, stackName, message("codepipeline.stack.resource_type"), operation)
}

fun getCodePipelineArnForResource(project: Project, resourceArn: String, resourceTypeFilter: String): String? {
    val client: ResourceGroupsTaggingApiClient = AwsClientManager.getInstance(project).getClient()

    val tagFilter = TagFilter.builder().key(CODEPIPELINE_SYSTEM_TAG_KEY).build()
    val request = GetResourcesRequest.builder().tagFilters(tagFilter).resourceTypeFilters(resourceTypeFilter).build()
    val response = client.getResources(request)

    for (resourceTagMapping in response.resourceTagMappingList().filterNotNull()) {
        if (resourceTagMapping.resourceARN() == resourceArn) {
            val tagValue = resourceTagMapping.tags().filterNotNull().find { it.key() == CODEPIPELINE_SYSTEM_TAG_KEY }?.value()
            if (tagValue != null) {
                return tagValue
            }
        }
    }
    return null
}

fun warnResourceUpdateAgainstCodePipeline(project: Project, codePipelineArn: String?, resourceName: String, resourceType: String, operation: String): Boolean {
    if (codePipelineArn != null) {
        val title = message("codepipeline.resource.update.warning.title")
        val message = message("codepipeline.resource.update.warning.message", resourceType, resourceName, codePipelineArn, operation)
        val yesText = message("codepipeline.resource.update.warning.yes_text")
        val noText = message("codepipeline.resource.update.warning.no_text")

        return showYesNoDialog(title, message, project, yesText, noText, Messages.getWarningIcon())
    }
    return false
}
