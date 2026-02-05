// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackSummary

internal typealias StacksChangeListener = (List<StackSummary>) -> Unit

@Service(Service.Level.PROJECT)
internal class StacksManager(private val project: Project) : Disposable {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }

    private var stacks: List<StackSummary> = emptyList()

    private var nextToken: String? = null

    private var loaded = false

    private var loading = false
    private val listeners = mutableListOf<StacksChangeListener>()

    fun addListener(listener: StacksChangeListener) {
        listeners.add(listener)
    }

    fun get(): List<StackSummary> = stacks.toList()

    fun hasMore(): Boolean = nextToken != null

    fun isLoaded(): Boolean = loaded

    fun reload() {
        if (loading) return
        loadStacks(loadMore = false)
    }

    fun clear() {
        stacks = emptyList()
        nextToken = null
        loaded = false
        loading = false
        notifyListeners()
    }

    fun loadMoreStacks() {
        if (nextToken == null || loading) return
        loadStacks(loadMore = true)
    }

    private fun loadStacks(loadMore: Boolean) {
        loading = true
        LOG.info { "Loading stacks (loadMore=$loadMore)" }

        val params = ListStacksParams(
            statusToExclude = listOf("DELETE_COMPLETE"),
            loadMore = loadMore
        )

        clientServiceProvider().listStacks(params)
            .thenAccept { result ->
                loading = false
                if (result != null) {
                    LOG.info { "Loaded ${result.stacks.size} stacks" }
                    stacks = result.stacks
                    nextToken = result.nextToken
                    loaded = true
                    if (result.stacks.isEmpty() && !loadMore) {
                        notifyInfo("CloudFormation", "No stacks found in this region", project)
                    }
                } else {
                    LOG.warn { "Received null result from listStacks" }
                    if (!loadMore) {
                        stacks = emptyList()
                        nextToken = null
                        loaded = true
                    }
                }
                notifyListeners()
            }
            .exceptionally { error ->
                loading = false
                LOG.warn(error) { "Failed to load stacks" }
                notifyError("CloudFormation", "Failed to load stacks: ${error.message}", project)
                if (!loadMore) {
                    stacks = emptyList()
                    nextToken = null
                    loaded = true
                }
                notifyListeners()
                null
            }
    }

    private fun notifyListeners() {
        listeners.forEach { it(stacks) }
    }

    override fun dispose() {}

    companion object {
        private val LOG = getLogger<StacksManager>()
        fun getInstance(project: Project): StacksManager = project.service()
    }
}
