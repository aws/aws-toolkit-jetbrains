// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks.views

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.GetStackResourcesParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackResourceSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ConsoleUrlGenerator
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.IconUtils
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.table.DefaultTableModel
import kotlin.math.ceil

internal class StackResourcesPanel(
    project: Project,
    coordinator: StackViewCoordinator,
    private val stackArn: String,
    private val stackName: String,
) : Disposable, StackPollingListener {

    private val cfnClientService = CfnClientService.getInstance(project)
    private val disposables = mutableListOf<Disposable>()

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
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    internal val pageLabel = JBLabel("Page 1")
    internal val prevButton = JButton("Previous").apply { addActionListener { loadPrevPage() } }
    internal val nextButton = JButton("Next").apply { addActionListener { loadNextPage() } }
    internal val resourceCountLabel = JBLabel("0 resources").apply {
        foreground = UIUtil.getContextHelpForeground()
    }
    internal val consoleLink = IconUtils.createConsoleLinkIcon {
        ConsoleUrlGenerator.generateStackResourcesUrl(stackArn)
    }.apply {
        isVisible = false // Start hidden until successful load
    }

    val component: JComponent = StackPanelLayoutBuilder.createStackTablePanel(
        stackName,
        resourceTable,
        resourceCountLabel,
        consoleLink,
        PaginationControls(pageLabel, prevButton, nextButton)
    )

    init {
        loadResources()
        disposables.add(coordinator.addPollingListener(stackArn, this))
    }

    override fun onStackPolled() {
        currentPage = 0
        allResources = emptyList()
        nextToken = null
        isLoading = false // Reset loading flag to ensure refresh happens
        loadResources()
    }

    private fun loadResources(): CompletableFuture<Unit> {
        if (isLoading) return CompletableFuture.completedFuture(null)
        isLoading = true

        val tokenToUse = nextToken
        val params = GetStackResourcesParams(stackName, tokenToUse)

        return cfnClientService.getStackResources(params).whenComplete { result, error ->
            ApplicationManager.getApplication().invokeLater {
                isLoading = false
                if (error != null) {
                    handleError("Failed to load resources: ${error.message}")
                } else {
                    result?.let { response ->
                        consoleLink.isVisible = true
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
                    } ?: run {
                        handleError("No stack data found")
                    }
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

        // Update resource count
        val hasMore = nextToken != null
        resourceCountLabel.text = "${allResources.size} resource${if (allResources.size != 1) "s" else ""}${if (hasMore) " loaded" else ""}"

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

    private fun handleError(message: String) {
        consoleLink.isVisible = false
        allResources = emptyList()
        currentPage = 0
        nextToken = null

        tableModel.rowCount = 0
        tableModel.addRow(arrayOf(message, "", "", ""))

        // Update pagination controls for error state
        pageLabel.text = "Page 1"
        prevButton.isEnabled = false
        nextButton.isEnabled = false
        resourceCountLabel.text = "0 resources"
        LOG.warn { message }
    }

    override fun dispose() {
        disposables.forEach { it.dispose() }
        disposables.clear()
    }

    companion object {
        private val LOG = getLogger<StackResourcesPanel>()
    }
}
