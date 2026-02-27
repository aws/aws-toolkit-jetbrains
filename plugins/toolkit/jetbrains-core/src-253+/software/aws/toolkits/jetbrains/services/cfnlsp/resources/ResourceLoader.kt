// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.RefreshResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceRequest
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.SearchResourceParams
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

typealias ResourcesChangeListener = (String, List<String>) -> Unit

@Service(Service.Level.PROJECT)
internal class ResourceLoader(
    private val project: Project,
) : Disposable {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }

    private val cache = ResourceCache()
    private val listeners = CopyOnWriteArrayList<ResourcesChangeListener>()
    private val loadingTypes = ConcurrentHashMap.newKeySet<String>()

    fun addListener(listener: ResourcesChangeListener) {
        listeners.add(listener)
    }

    fun getResourceIdentifiers(resourceType: String): List<String> =
        cache.get(resourceType)?.resourceIdentifiers ?: emptyList()

    fun getCachedResources(resourceType: String): List<String>? =
        cache.get(resourceType)?.resourceIdentifiers

    fun hasMore(resourceType: String): Boolean =
        cache.get(resourceType)?.nextToken != null

    fun isLoaded(resourceType: String): Boolean =
        cache.get(resourceType)?.loaded ?: false

    fun getLoadedResourceTypes(): Set<String> = cache.keys()

    fun refreshResources(resourceType: String) {
        loadResources(resourceType, loadMore = false, useRefresh = true)
    }

    fun loadMoreResources(resourceType: String) {
        val currentData = cache.get(resourceType)
        if (currentData?.nextToken == null) return
        loadResources(resourceType, loadMore = true)
    }

    fun searchResource(resourceType: String, identifier: String): CompletableFuture<Boolean> {
        LOG.info { "Searching for resource $identifier in type $resourceType" }

        val params = SearchResourceParams(resourceType, identifier)
        return clientServiceProvider().searchResource(params)
            .thenApply { result ->
                if (result?.found == true) {
                    LOG.info { "Resource $identifier found in $resourceType" }

                    if (result.resource != null) {
                        val currentData = cache.get(resourceType)
                        val existingResources = currentData?.resourceIdentifiers ?: emptyList()

                        if (!existingResources.contains(identifier)) {
                            val updatedResources = existingResources + identifier
                            cache.put(
                                resourceType,
                                ResourceTypeData(
                                    resourceIdentifiers = updatedResources,
                                    nextToken = currentData?.nextToken,
                                    loaded = true
                                )
                            )
                            notifyListeners(resourceType, updatedResources)
                        }
                    } else {
                        if (cache.get(resourceType)?.loaded != true) {
                            refreshResources(resourceType)
                        }
                    }
                    true
                } else {
                    LOG.info { "Resource $identifier not found in $resourceType" }
                    false
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to search for resource $identifier in $resourceType" }
                false
            }
    }

    fun clear(resourceType: String?) {
        if (resourceType != null) {
            cache.remove(resourceType)
            notifyListeners(resourceType, emptyList())
        } else {
            val types = cache.keys()
            cache.clear()
            types.forEach { type ->
                notifyListeners(type, emptyList())
            }
        }
    }

    private fun loadResources(resourceType: String, loadMore: Boolean, useRefresh: Boolean = false) {
        if (!loadMore) {
            loadingTypes.add(resourceType)
        }

        LOG.info { "${if (useRefresh) "Refreshing" else "Loading"} resources for type $resourceType (loadMore=$loadMore)" }

        val currentData = cache.get(resourceType)
        val nextToken = if (loadMore) currentData?.nextToken else null

        if (useRefresh) {
            val params = RefreshResourcesParams(resources = listOf(ResourceRequest(resourceType)))
            clientServiceProvider().refreshResources(params)
                .thenAccept { result ->
                    handleResourceResult(resourceType, result?.resources, loadMore, currentData, useRefresh)
                }
                .exceptionally { error ->
                    handleResourceError(resourceType, error, loadMore, useRefresh)
                }
        } else {
            val params = ListResourcesParams(resources = listOf(ResourceRequest(resourceType, nextToken)))
            clientServiceProvider().listResources(params)
                .thenAccept { result ->
                    handleResourceResult(resourceType, result?.resources, loadMore, currentData, useRefresh)
                }
                .exceptionally { error ->
                    handleResourceError(resourceType, error, loadMore, useRefresh)
                }
        }
    }

    private fun handleResourceResult(
        resourceType: String,
        resources: List<ResourceSummary>?,
        loadMore: Boolean,
        currentData: ResourceTypeData?,
        useRefresh: Boolean,
    ) {
        loadingTypes.remove(resourceType)

        if (resources != null) {
            val resourceSummary = resources.firstOrNull { it.typeName == resourceType }
            if (resourceSummary != null) {
                // LSP server returns cumulative results, use them directly
                val allResources = resourceSummary.resourceIdentifiers

                cache.put(
                    resourceType,
                    ResourceTypeData(
                        resourceIdentifiers = allResources,
                        nextToken = resourceSummary.nextToken,
                        loaded = true
                    )
                )

                notifyListeners(resourceType, allResources)
                LOG.info { "${if (useRefresh) "Refreshed" else "Loaded"} ${resourceSummary.resourceIdentifiers.size} resources for $resourceType" }
            } else {
                LOG.info { "No resources found for $resourceType" }
                cache.put(
                    resourceType,
                    ResourceTypeData(
                        resourceIdentifiers = emptyList(),
                        nextToken = null,
                        loaded = true
                    )
                )
                notifyListeners(resourceType, emptyList())
            }
        }
    }

    private fun handleResourceError(
        resourceType: String,
        error: Throwable,
        loadMore: Boolean,
        useRefresh: Boolean,
    ): Nothing? {
        loadingTypes.remove(resourceType)
        LOG.warn(error) { "Failed to ${if (useRefresh) "refresh" else "load"} resources for $resourceType" }
        if (!loadMore) {
            cache.put(
                resourceType,
                ResourceTypeData(
                    resourceIdentifiers = emptyList(),
                    nextToken = null,
                    loaded = true
                )
            )
        }
        return null
    }

    private fun notifyListeners(resourceType: String, resources: List<String>) {
        listeners.forEach { it(resourceType, resources) }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        private val LOG = getLogger<ResourceLoader>()
        fun getInstance(project: Project): ResourceLoader = project.service()
    }
}
