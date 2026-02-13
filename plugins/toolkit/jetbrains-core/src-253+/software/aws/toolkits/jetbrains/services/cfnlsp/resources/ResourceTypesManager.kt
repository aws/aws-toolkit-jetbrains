// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.resources

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
@State(name = "cfnResourceTypes", storages = [Storage("awsToolkit.xml", roamingType = RoamingType.DISABLED)])
internal class ResourceTypesManager(
    private val project: Project,
) : PersistentStateComponent<ResourceTypesManagerState> {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }

    private var state = ResourceTypesManagerState()
    private var availableTypes: List<String> = emptyList()
    private var typesLoaded: Boolean = false
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<ResourceTypesChangeListener>()

    override fun getState(): ResourceTypesManagerState = state

    override fun loadState(state: ResourceTypesManagerState) { this.state = state }

    fun addListener(listener: ResourceTypesChangeListener) {
        listeners.add(listener)
    }

    fun getAvailableResourceTypes(): List<String> = availableTypes.toList()

    fun areTypesLoaded(): Boolean = typesLoaded

    fun getSelectedResourceTypes(): Set<String> = state.selectedTypes.toSet()

    fun addResourceType(typeName: String) {
        if (typeName !in state.selectedTypes) {
            state.selectedTypes.add(typeName)
            notifyListeners()
        }
    }

    fun removeResourceType(typeName: String) {
        if (typeName in state.selectedTypes) {
            state.selectedTypes.remove(typeName)
            notifyListeners()

            // Send async request to server to clear cache
            LOG.info { "Removing resource type from LSP server: $typeName" }
            clientServiceProvider().removeResourceType(typeName)
                .thenAccept {
                    LOG.info { "Successfully removed resource type: $typeName" }
                }
                .exceptionally { error ->
                    LOG.warn(error) { "Failed to remove resource type from LSP server: $typeName" }
                    null
                }
        }
    }

    fun loadAvailableTypes(): CompletableFuture<Unit> {
        LOG.info { "Loading available resource types" }

        return clientServiceProvider().listResourceTypes()
            .thenApply { result ->
                if (result != null) {
                    LOG.info { "Loaded ${result.resourceTypes.size} resource types" }
                    availableTypes = result.resourceTypes
                    typesLoaded = true
                } else {
                    LOG.warn { "Failed to load resource types - null result" }
                }
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to load resource types" }
            }
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    companion object {
        private val LOG = getLogger<ResourceTypesManager>()
        fun getInstance(project: Project): ResourceTypesManager = project.service()
    }
}

internal data class ResourceTypesManagerState(
    var selectedTypes: MutableSet<String> = mutableSetOf(),
)

typealias ResourceTypesChangeListener = () -> Unit
