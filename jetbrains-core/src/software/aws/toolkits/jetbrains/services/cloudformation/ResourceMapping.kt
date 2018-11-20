// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cloudformation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.MultiMap
import software.amazon.awssdk.services.cloudformation.CloudFormationClient
import software.amazon.awssdk.services.cloudformation.model.ListStackResourcesRequest
import software.amazon.awssdk.services.cloudformation.model.ListStacksRequest
import software.amazon.awssdk.services.cloudformation.model.StackResourceSummary
import software.amazon.awssdk.services.cloudformation.model.StackStatus
import software.amazon.awssdk.services.cloudformation.model.StackSummary
import software.aws.toolkits.jetbrains.core.AwsClientManager
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.stream.Stream

interface ResourceMapping {
    fun listAllSummaries(logicalResource: String): Collection<StackResourceSummary>

    fun guessPhysicalResourceId(logicalResource: String): String?

    fun isEmpty(): Boolean

    object EMPTY : ResourceMapping {
        override fun listAllSummaries(logicalResource: String): Collection<StackResourceSummary> = Collections.emptyList()

        override fun guessPhysicalResourceId(logicalResource: String): String? = null

        override fun isEmpty(): Boolean = true
    }
}

internal class ResourceMappingImpl : ResourceMapping {
    private val logicalToSummary: MultiMap<String, StackResourceSummary> = MultiMap.createLinked()

    override fun listAllSummaries(logicalResource: String): Collection<StackResourceSummary> = logicalToSummary.get(logicalResource)

    override fun guessPhysicalResourceId(logicalResource: String): String? =
            listAllSummaries(logicalResource).firstOrNull()?.physicalResourceId()

    private fun registerResource(resourceSummary: StackResourceSummary) =
            resourceSummary.logicalResourceId()?.let { logicalToSummary.putValue(it, resourceSummary) }

    override fun isEmpty(): Boolean = logicalToSummary.isEmpty

    companion object {
        @JvmStatic
        fun promiseMapping(project: Project): CompletionStage<ResourceMapping> {
            val client: CloudFormationClient = AwsClientManager.getInstance(project).getClient()
            return promiseMapping(client)
        }

        @JvmStatic
        fun promiseMapping(client: CloudFormationClient): CompletionStage<ResourceMapping> {
            val promise = CompletableFuture<ResourceMapping>()

            ApplicationManager.getApplication().executeOnPooledThread {
                val result = ResourceMappingImpl()
                try {
                    val stackRequest = ListStacksRequest.builder()
                            .stackStatusFilters(StackStatus.CREATE_COMPLETE, StackStatus.UPDATE_COMPLETE)
                            .build()
                    client.listStacksPaginator(stackRequest).stream()
                            .flatMap { it.stackSummaries().stream() }
                            .flatMap { client.listResourcesForStack(it) }
                            .forEach { result.registerResource(it) }

                    promise.complete(result)
                } catch (e: Exception) {
                    promise.completeExceptionally(e)
                }
            }
            return promise
        }

        private fun CloudFormationClient.listResourcesForStack(stackSummary: StackSummary): Stream<StackResourceSummary> {
            val request = ListStackResourcesRequest.builder().stackName(stackSummary.stackId()).build()
            return this.listStackResourcesPaginator(request).stream()
                    .flatMap { it -> it.stackResourceSummaries().stream() }
        }
    }
}