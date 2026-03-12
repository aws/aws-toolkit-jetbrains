// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ClearStackEventsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackEventsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackEventsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackEvent
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ConsoleUrlGenerator
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.IconUtils
import software.aws.toolkits.jetbrains.utils.notifyInfo
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.math.ceil

internal class StackEventsPanel(
    private val project: Project,
    coordinator: StackViewCoordinator,
    stackArn: String,
    private val stackName: String,
) : Disposable, StackPollingListener {

    private val cfnClientService = CfnClientService.getInstance(project)
    private val disposables = mutableListOf<Disposable>()

    private var allEvents: List<StackEvent> = emptyList()
    private var currentPage: Int = 0
    private var nextToken: String? = null
    private val eventsPerPage = StackEventsTableComponents.EVENTS_PER_PAGE
    private var isLoading = false // Prevents rapid-fire clicks from triggering multiple concurrent operations

    private val eventTable = StackPanelLayoutBuilder.createEventsTable { operationId ->
        val consoleUrl = ConsoleUrlGenerator.generateOperationUrl(stackArn, operationId)
        BrowserUtil.browse(consoleUrl)
    }

    private val eventCountLabel = JBLabel("0 events").apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    internal val pageLabel = JBLabel("Page 1 of 1")
    internal val prevButton = JButton("Previous").apply {
        addActionListener { loadPrevPage() }
        isEnabled = false
    }
    internal val nextButton = JButton("Next").apply {
        addActionListener { loadNextPage() }
        isEnabled = false
    }
    internal val consoleLink = IconUtils.createConsoleLinkIcon {
        ConsoleUrlGenerator.generateStackEventsUrl(stackArn)
    }.apply {
        isVisible = false // Start hidden until successful load
    }

    val component: JComponent = StackPanelLayoutBuilder.createStackTablePanel(
        stackName,
        eventTable,
        eventCountLabel,
        consoleLink,
        PaginationControls(pageLabel, prevButton, nextButton)
    )

    init {
        disposables.add(coordinator.addPollingListener(stackArn, this))
        loadEvents()
    }

    override fun onStackPolled() {
        refresh()
    }

    private fun loadEvents(): CompletableFuture<Unit?> {
        if (isLoading) return CompletableFuture.completedFuture(null)
        isLoading = true

        return cfnClientService.getStackEvents(GetStackEventsParams(stackName, nextToken))
            .thenApply { result: GetStackEventsResult? ->
                ApplicationManager.getApplication().invokeLater {
                    isLoading = false
                    result?.let { handleLoadResult(it) }
                }
            }
            .exceptionally { error: Throwable ->
                ApplicationManager.getApplication().invokeLater {
                    isLoading = false
                    handleError("Failed to load events: ${error.message}")
                }
            }
    }

    private fun refresh() {
        if (isLoading) return
        isLoading = true

        // Fetch only new events and prepend them (smart updates)
        cfnClientService.getStackEvents(GetStackEventsParams(stackName, refresh = true))
            .whenComplete { result: GetStackEventsResult?, error: Throwable? ->
                ApplicationManager.getApplication().invokeLater {
                    isLoading = false
                    if (error != null) {
                        handleError("Failed to refresh events: ${error.message}")
                    } else {
                        result?.let { handleRefreshResult(it) }
                    }
                }
            }
    }

    private fun handleLoadResult(result: GetStackEventsResult) {
        consoleLink.isVisible = true
        allEvents = if (nextToken == null) {
            result.events
        } else {
            allEvents + result.events
        }
        nextToken = result.nextToken
        renderEvents()
    }

    private fun handleRefreshResult(result: GetStackEventsResult) {
        if (result.gapDetected == true) {
            cfnClientService.getStackEvents(GetStackEventsParams(stackName))
                .whenComplete { initialResult, error ->
                    ApplicationManager.getApplication().invokeLater {
                        if (error == null && initialResult != null) {
                            allEvents = initialResult.events
                            nextToken = initialResult.nextToken
                            currentPage = 0
                            renderEvents("Event history reloaded due to high activity")
                        }
                    }
                }
        } else if (result.events.isNotEmpty()) {
            allEvents = result.events + allEvents
            currentPage = 0
            renderEvents()
        }
    }

    private fun loadNextPage() {
        if (isLoading) return

        val totalPages = ceil(allEvents.size.toDouble() / eventsPerPage).toInt()
        val nextPageIndex = currentPage + 1

        if (nextPageIndex < totalPages) {
            currentPage = nextPageIndex
            renderEvents()
        } else if (nextToken != null) {
            loadEvents().thenRun {
                ApplicationManager.getApplication().invokeLater {
                    currentPage = nextPageIndex
                    renderEvents()
                }
            }
        }
    }

    private fun loadPrevPage() {
        if (isLoading) return

        if (currentPage > 0) {
            currentPage--
            renderEvents()
        } else {
            refresh()
        }
    }

    private fun renderEvents(notification: String? = null) {
        StackPanelLayoutBuilder.updateEventsTable(eventTable, allEvents)
        StackPanelLayoutBuilder.updateEventsTablePage(eventTable, currentPage)
        updateUIComponents()

        // Show notification if provided (gap detection warning)
        notification?.let {
            notifyInfo("CloudFormation Events", it, this.project)
        }
    }

    private fun updateUIComponents() {
        val hasMore = nextToken != null
        eventCountLabel.text = "${allEvents.size} events${if (hasMore) " loaded" else ""}"

        val totalPages = ceil(allEvents.size.toDouble() / eventsPerPage).toInt().coerceAtLeast(1)
        pageLabel.text = "Page ${currentPage + 1} of $totalPages"

        prevButton.isEnabled = currentPage > 0 && !isLoading

        val isAtLastPage = currentPage >= totalPages - 1
        nextButton.text = if (isAtLastPage && hasMore) "Load More" else "Next"
        nextButton.isEnabled = (!isAtLastPage || hasMore) && !isLoading
    }

    private fun handleError(message: String) {
        consoleLink.isVisible = false
        allEvents = emptyList()
        StackPanelLayoutBuilder.updateEventsTable(eventTable, allEvents, message)
        updateUIComponents()
        LOG.warn { message }
    }

    override fun dispose() {
        if (stackName.isNotEmpty()) {
            cfnClientService.clearStackEvents(ClearStackEventsParams(stackName))
        }
        disposables.forEach { it.dispose() }
        disposables.clear()
    }

    companion object {
        private val LOG = getLogger<StackEventsPanel>()
    }
}
