// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ClearStackEventsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackEventsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackEventsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackEvent
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ConsoleUrlGenerator
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.IconUtils
import software.aws.toolkits.jetbrains.utils.notifyInfo
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
    private val eventsPerPage = 50
    private var currentStackId: String? = null
    private var isLoading = false // Prevents rapid-fire clicks from triggering multiple concurrent operations

    private val eventTable = StackPanelLayoutBuilder.createEventsTable { operationId ->
        currentStackId?.let { stackId ->
            val consoleUrl = ConsoleUrlGenerator.generateOperationUrl(stackId, operationId)
            BrowserUtil.browse(consoleUrl)
        }
    }

    private val consoleLink = JBLabel(IconUtils.createBlueIcon(AllIcons.Ide.External_link_arrow)).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isVisible = false
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                currentStackId?.let { stackId ->
                    val consoleUrl = ConsoleUrlGenerator.generateStackEventsUrl(stackId)
                    BrowserUtil.browse(consoleUrl)
                }
            }
        })
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

    val component: JComponent = StackPanelLayoutBuilder.createTableWithPaginationPanel(
        "Stack: $stackName",
        consoleLink,
        pageLabel,
        prevButton,
        nextButton,
        eventTable,
        eventCountLabel
    )

    init {
        disposables.add(coordinator.addPollingListener(stackArn, this))
        setupTableClickHandler()
        loadEvents()
    }

    private fun setupTableClickHandler() {
        eventTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val row = eventTable.rowAtPoint(e.point)
                    val col = eventTable.columnAtPoint(e.point)
                    if (row >= 0 && col == StackEventsTableComponents.ARROW_COLUMN) { // Operation ID column should be clickable link to console
                        val operationId = eventTable.getValueAt(row, 0) as? String
                        if (!operationId.isNullOrEmpty() && operationId != "-") {
                            currentStackId?.let { stackId ->
                                val consoleUrl = ConsoleUrlGenerator.generateOperationUrl(stackId, operationId)
                                BrowserUtil.browse(consoleUrl)
                            }
                        }
                    }
                }
            }
        })

        eventTable.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val row = eventTable.rowAtPoint(e.point)
                val col = eventTable.columnAtPoint(e.point)
                if (row >= 0 && col == StackEventsTableComponents.OPERATION_COLUMN) {
                    val operationId = eventTable.getValueAt(row, 1) as? String
                    if (!operationId.isNullOrEmpty() && operationId.trim() != "-") {
                        eventTable.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    } else {
                        eventTable.cursor = Cursor.getDefaultCursor()
                    }
                } else {
                    eventTable.cursor = Cursor.getDefaultCursor()
                }
            }
        })
    }

    override fun onStackPolled() {
        refresh()
    }

    private fun loadEvents(): CompletableFuture<Void?> {
        if (isLoading) return CompletableFuture.completedFuture(null)
        isLoading = true

        return cfnClientService.getStackEvents(GetStackEventsParams(stackName, nextToken))
            .thenAccept { result: GetStackEventsResult? ->
                ApplicationManager.getApplication().invokeLater {
                    isLoading = false
                    result?.let { handleLoadResult(it) }
                }
            }
            .exceptionally { error: Throwable ->
                ApplicationManager.getApplication().invokeLater {
                    isLoading = false
                    renderError("Failed to load events: ${error.message}")
                }
                null
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
                        renderError("Failed to refresh events: ${error.message}")
                    } else {
                        result?.let { handleRefreshResult(it) }
                    }
                }
            }
    }

    private fun handleLoadResult(result: GetStackEventsResult) {
        if (nextToken == null) {
            allEvents = result.events
            currentStackId = result.events.firstOrNull()?.stackId
        } else {
            allEvents = allEvents + result.events
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

        consoleLink.isVisible = currentStackId != null
    }

    private fun renderError(message: String) {
        allEvents = emptyList()
        StackPanelLayoutBuilder.updateEventsTable(eventTable, allEvents)
        updateUIComponents()
        LOG.warn(message)
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
