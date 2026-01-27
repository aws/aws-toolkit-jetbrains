// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ChangeSetInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class ChangeSetsManager(private val project: Project) {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }

    private val stackChangeSets = ConcurrentHashMap<String, StackChangeSets>()
    private val loadedStacks = ConcurrentHashMap.newKeySet<String>()
    private val listeners = mutableListOf<() -> Unit>()

    private data class StackChangeSets(
        val changeSets: List<ChangeSetInfo>,
        val nextToken: String? = null,
    )

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun isLoaded(stackName: String): Boolean = loadedStacks.contains(stackName)

    fun fetchChangeSets(stackName: String) {
        if (loadedStacks.contains(stackName)) return

        LOG.info { "Fetching change sets for $stackName" }

        clientServiceProvider().listChangeSets(ListChangeSetsParams(stackName))
            .thenAccept { result ->
                if (result != null) {
                    LOG.info { "Loaded ${result.changeSets.size} change sets for $stackName" }
                    stackChangeSets[stackName] = StackChangeSets(result.changeSets, result.nextToken)
                    loadedStacks.add(stackName)
                } else {
                    LOG.warn { "Received null result for change sets of $stackName" }
                    loadedStacks.add(stackName)
                }
                notifyListeners()
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to load change sets for $stackName" }
                loadedStacks.add(stackName)
                notifyListeners()
                null
            }
    }

    fun loadMoreChangeSets(stackName: String) {
        val current = stackChangeSets[stackName] ?: return
        val nextToken = current.nextToken ?: return

        LOG.info { "Loading more change sets for $stackName" }

        clientServiceProvider().listChangeSets(ListChangeSetsParams(stackName, nextToken))
            .thenAccept { result ->
                if (result != null) {
                    LOG.info { "Loaded ${result.changeSets.size} more change sets for $stackName" }
                    stackChangeSets[stackName] = StackChangeSets(
                        current.changeSets + result.changeSets,
                        result.nextToken
                    )
                }
                notifyListeners()
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to load more change sets for $stackName" }
                null
            }
    }

    fun get(stackName: String): List<ChangeSetInfo> =
        stackChangeSets[stackName]?.changeSets ?: emptyList()

    fun hasMore(stackName: String): Boolean =
        stackChangeSets[stackName]?.nextToken != null

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    companion object {
        private val LOG = getLogger<ChangeSetsManager>()
        fun getInstance(project: Project): ChangeSetsManager = project.service()
    }
}
