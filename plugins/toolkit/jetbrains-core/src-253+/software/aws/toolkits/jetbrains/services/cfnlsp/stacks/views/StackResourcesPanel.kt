// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackResourceSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ConsoleUrlGenerator
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.IconUtils
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel
import kotlin.math.ceil

internal class StackResourcesPanel(
    project: Project,
    private val coordinator: StackViewCoordinator,
    private val stackArn: String,
    private val stackName: String,
    private val autoRefreshDelayMs: Int = 5000,
) : Disposable, StackPanelListener {

    private val cfnClientService = CfnClientService.getInstance(project)
    private val disposables = mutableListOf<Disposable>()

    private var autoRefreshAlarm: Alarm? = null

    // Resource data management
    private var allResources: List<StackResourceSummary> = emptyList()
    private var currentPage: Int = 0
    private var nextToken: String? = null
    private val resourcesPerPage = 50
    private var isLoading = false // Prevents rapid-fire clicks from triggering multiple concurrent operations

    private val tableModel = DefaultTableModel(
        arrayOf("Logical ID", "Physical ID", "Type", "Status"),
        0
    )

    private val resourceTable = JBTable(tableModel).apply {
        setDefaultEditor(Any::class.java, null) // Make table non-editable
    }

    internal val pageLabel = JBLabel("Page 1")
    internal val prevButton = JButton("Previous").apply { addActionListener { loadPrevPage() } }
    internal val nextButton = JButton("Next").apply { addActionListener { loadNextPage() } }

    private val consoleLink = JBLabel(IconUtils.createBlueIcon(AllIcons.Ide.External_link_arrow)).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val consoleUrl = ConsoleUrlGenerator.generateStackResourcesUrl(stackArn)
                BrowserUtil.browse(consoleUrl)
            }
        })
    }

    val component: JComponent = StackPanelLayoutBuilder.createTableWithPaginationPanel(
        stackName,
        consoleLink,
        pageLabel,
        prevButton,
        nextButton,
        resourceTable
    )

    init {
        loadResources()
        disposables.add(coordinator.addListener(stackArn, this))
    }

    override fun onStackUpdated() {
        val stackState = coordinator.getStackState(stackArn)
        val currentStatus = stackState?.status

        currentPage = 0
        allResources = emptyList()
        nextToken = null
        loadResources()

        if (currentStatus?.let { StackStatusUtils.isInTransientState(it) } == true) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    private fun loadResources(): CompletableFuture<Void> {
        if (isLoading) return CompletableFuture.completedFuture(null)
        isLoading = true

        val tokenToUse = nextToken
        val params = GetStackResourcesParams(stackName, tokenToUse)

        return cfnClientService.getStackResources(params).whenComplete { result, error ->
            ApplicationManager.getApplication().invokeLater {
                isLoading = false
                if (error != null) {
                    LOG.warn("Failed to load stack resources", error)
                } else {
                    result?.let { response ->
                        if (tokenToUse == null) {
                            // Fresh load - replace all resources
                            allResources = response.resources
                            currentPage = 0
                        } else {
                            // Pagination load - append resources
                            allResources = allResources + response.resources
                        }
                        nextToken = response.nextToken
                        updateTable()
                    } ?: LOG.warn("No result received from getStackResources")
                }
            }
        }.thenApply { null }
    }

    private fun updateTable() {
        tableModel.rowCount = 0
        val startIndex = currentPage * resourcesPerPage
        val endIndex = minOf(startIndex + resourcesPerPage, allResources.size)
        val pageResources = allResources.subList(startIndex, endIndex)

        pageResources.forEach { resource ->
            tableModel.addRow(
                arrayOf(
                    resource.logicalResourceId,
                    resource.physicalResourceId ?: "N/A",
                    resource.resourceType,
                    resource.resourceStatus
                )
            )
        }

        // Update pagination controls
        pageLabel.text = "Page ${currentPage + 1}"
        prevButton.isEnabled = currentPage > 0 && !isLoading
        nextButton.isEnabled = hasMorePages() && !isLoading

        // Update button text based on whether NEXT click will need to load more from server
        nextButton.text = if ((currentPage + 1) * resourcesPerPage >= allResources.size && nextToken != null) {
            "Load More"
        } else {
            "Next"
        }
    }

    private fun loadNextPage() {
        if (isLoading) return

        val totalPages = ceil(allResources.size.toDouble() / resourcesPerPage).toInt()
        val nextPageIndex = currentPage + 1

        if (nextPageIndex >= totalPages && nextToken == null) {
            return
        }

        if (nextPageIndex < totalPages) {
            currentPage = nextPageIndex
            updateTable()
        } else {
            loadResources().whenComplete { _, _ ->
                ApplicationManager.getApplication().invokeLater {
                    if (allResources.size > nextPageIndex * resourcesPerPage) {
                        currentPage = nextPageIndex
                        updateTable()
                    }
                }
            }
        }
    }

    private fun loadPrevPage() {
        if (isLoading) return
        if (currentPage > 0) {
            currentPage--
            updateTable()
        }
    }

    private fun hasMorePages(): Boolean =
        (currentPage + 1) * resourcesPerPage < allResources.size || nextToken != null

    private fun startAutoRefresh() {
        if (autoRefreshAlarm != null) {
            return
        }

        LOG.info("Starting auto-refresh for stack $stackName with ${autoRefreshDelayMs}ms interval")
        autoRefreshAlarm = Alarm(this).apply {
            fun scheduleNext() {
                addRequest({
                    val stackState = coordinator.getStackState(stackArn)

                    if (stackState?.status?.let { StackStatusUtils.isInTransientState(it) } == true) {
                        currentPage = 0
                        allResources = emptyList()
                        nextToken = null
                        loadResources()
                        scheduleNext()
                    } else {
                        stopAutoRefresh()
                    }
                }, autoRefreshDelayMs)
            }
            scheduleNext()
        }
    }

    private fun stopAutoRefresh() {
        if (autoRefreshAlarm != null) {
            LOG.info("Stopping auto-refresh for stack $stackName")
            autoRefreshAlarm?.cancelAllRequests()
            autoRefreshAlarm = null
        }
    }

    override fun dispose() {
        stopAutoRefresh()
        disposables.forEach { it.dispose() }
        disposables.clear()
    }

    companion object {
        private val LOG = getLogger<StackResourcesPanel>()
    }
}
