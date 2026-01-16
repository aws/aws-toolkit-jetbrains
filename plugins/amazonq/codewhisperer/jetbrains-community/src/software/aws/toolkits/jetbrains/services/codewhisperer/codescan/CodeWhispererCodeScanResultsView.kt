// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import icons.AwsIcons
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueGroupingStrategy
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueSeverity
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.layout.CodeWhispererLayoutConfig.addHorizontalGlue
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.INACTIVE_TEXT_COLOR
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.resources.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

/**
 * Create a Code Scan results view that displays the code scan results.
 */
internal class CodeWhispererCodeScanResultsView(private val project: Project) : JPanel(BorderLayout()) {

    private fun isGroupedBySeverity() = CodeWhispererCodeScanManager.getInstance(project).getGroupingStrategySelected() == IssueGroupingStrategy.SEVERITY

    private val codeScanTree: Tree = Tree().apply {
        isRootVisible = false

        addTreeSelectionListener { e ->
            val issueNode = e.path.lastPathComponent as? DefaultMutableTreeNode
            val issue = issueNode?.userObject as? CodeWhispererCodeScanIssue ?: return@addTreeSelectionListener

            showIssueDetails(issue)

            synchronized(issueNode) {
                if (issueNode.userObject !is CodeWhispererCodeScanIssue) return@addTreeSelectionListener
                navigateToIssue(issueNode.userObject as CodeWhispererCodeScanIssue)
            }
        }
        cellRenderer = ColoredTreeCellRenderer()
    }

    private fun expandItems() {
        if (!isGroupedBySeverity()) {
            return
        }
        val criticalTreePath = TreePath(arrayOf(codeScanTree.model.root, codeScanTree.model.getChild(codeScanTree.model.root, 0)))
        val highTreePath = TreePath(arrayOf(codeScanTree.model.root, codeScanTree.model.getChild(codeScanTree.model.root, 1)))
        codeScanTree.expandPath(criticalTreePath)
        codeScanTree.expandPath(highTreePath)
    }

    private val scrollPane = ScrollPaneFactory.createScrollPane(codeScanTree, true)
    private val splitter = OnePixelSplitter(CODE_SCAN_SPLITTER_PROPORTION_KEY, 1.0f).apply {
        firstComponent = scrollPane
    }

    private val toolbar = createToolbar().apply {
        setTargetComponent(this@CodeWhispererCodeScanResultsView)
        component.border = BorderFactory.createCompoundBorder(
            CustomLineBorder(JBUI.insetsRight(1)),
            component.border
        )
    }

    private val infoLabelInitialText = message("codewhisperer.codescan.run_scan_info")
    private val infoLabelPrefix = JLabel(infoLabelInitialText, JLabel.LEFT).apply {
        icon = AllIcons.General.BalloonInformation
    }
    private val scannedFilesLabelLink = ActionLink().apply {
        border = BorderFactory.createEmptyBorder(0, 7, 0, 0)
        addActionListener {
            showScannedFiles(scannedFiles)
        }
    }

    private val filtersAppliedToResultsLabel = JLabel(message("codewhisperer.codescan.scan_results_hidden_by_filters")).apply {
        border = BorderFactory.createEmptyBorder(7, 7, 7, 7)
    }
    private val clearFiltersLink = ActionLink(message("codewhisperer.codescan.clear_filters")).apply {
        addActionListener {
            CodeWhispererCodeScanManager.getInstance(project).clearFilters()
        }
    }
    private val filtersAppliedIndicator = JPanel(GridBagLayout()).apply {
        add(filtersAppliedToResultsLabel, GridBagConstraints())
        add(clearFiltersLink, GridBagConstraints().apply { gridy = 1 })
    }

    private val learnMoreLabelLink = ActionLink().apply {
        border = BorderFactory.createEmptyBorder(0, 7, 0, 0)
    }

    private val completeInfoLabel = JPanel(GridBagLayout()).apply {
        layout = GridBagLayout()
        border = BorderFactory.createCompoundBorder(
            CustomLineBorder(JBUI.insetsBottom(1)),
            BorderFactory.createEmptyBorder(7, 11, 8, 11)
        )
        add(infoLabelPrefix, CodeWhispererLayoutConfig.inlineLabelConstraints)
        add(scannedFilesLabelLink, CodeWhispererLayoutConfig.inlineLabelConstraints)
        add(learnMoreLabelLink, CodeWhispererLayoutConfig.inlineLabelConstraints)
        addHorizontalGlue()
    }

    private val progressIndicatorLabel = JLabel(message("codewhisperer.codescan.scan_in_progress"), AnimatedIcon.Default(), JLabel.CENTER).apply {
        border = BorderFactory.createEmptyBorder(7, 7, 7, 7)
    }

    private val progressIndicator = JPanel(GridBagLayout()).apply {
        add(progressIndicatorLabel, GridBagConstraints())
    }

    // Results panel containing info label and progressIndicator/scrollPane
    private val resultsPanel = JPanel(BorderLayout()).apply {
        add(BorderLayout.NORTH, completeInfoLabel)
    }

    private var scannedFiles: List<VirtualFile> = listOf()

    init {
        add(BorderLayout.WEST, toolbar.component)
        add(BorderLayout.CENTER, resultsPanel)
    }

    fun getCodeScanTree() = codeScanTree

    /**
     * Updates the [codeScanTree] with the new tree model root and displays the same on the UI.
     */
    fun updateAndDisplayScanResults(
        scanTreeModel: CodeWhispererCodeScanTreeModel,
        scannedFiles: List<VirtualFile>,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ) {
        codeScanTree.apply {
            model = scanTreeModel
            repaint()
        }
        expandItems()

        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            this.scannedFiles = scannedFiles
        }

        resultsPanel.apply {
            if (components.contains(progressIndicator)) remove(progressIndicator)
            add(BorderLayout.CENTER, splitter)
            splitter.proportion = 1.0f
            splitter.secondComponent = null
            revalidate()
            repaint()
        }

        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            changeInfoLabelToDisplayScanCompleted(scannedFiles.size)
        }
    }

    fun refreshUIWithUpdatedModel(scanTreeModel: CodeWhispererCodeScanTreeModel) {
        changeInfoLabelToDisplayScanCompleted(scannedFiles.size)
        val codeScanManager = CodeWhispererCodeScanManager.getInstance(project)
        if (scanTreeModel.getTotalIssuesCount() == 0 && codeScanManager.hasCodeScanIssues()) {
            resultsPanel.apply {
                if (components.contains(splitter)) remove(splitter)
                add(BorderLayout.CENTER, filtersAppliedIndicator)
                revalidate()
                repaint()
            }
        } else {
            codeScanTree.apply {
                model = scanTreeModel
                repaint()
            }
            expandItems()
            resultsPanel.apply {
                if (components.contains(filtersAppliedIndicator)) remove(filtersAppliedIndicator)
                add(BorderLayout.CENTER, splitter)
                splitter.proportion = 1.0f
                splitter.secondComponent = null
                revalidate()
                repaint()
            }
        }
    }

    fun setStoppingCodeScan() {
        completeInfoLabel.isVisible = false
        resultsPanel.apply {
            if (components.contains(splitter)) remove(splitter)
            progressIndicatorLabel.apply {
                text = message("codewhisperer.codescan.stopping_scan")
                revalidate()
                repaint()
            }
            add(BorderLayout.CENTER, progressIndicator)
            revalidate()
            repaint()
        }
    }

    fun setDefaultUI() {
        completeInfoLabel.isVisible = true
        infoLabelPrefix.apply {
            text = infoLabelInitialText
            icon = AllIcons.General.BalloonInformation
            isVisible = true
        }
        scannedFilesLabelLink.apply {
            text = ""
            isVisible = false
        }
        learnMoreLabelLink.apply {
            text = ""
            isVisible = false
        }
        resultsPanel.apply {
            removeAll()
            add(BorderLayout.NORTH, completeInfoLabel)
            revalidate()
            repaint()
        }
    }

    /**
     * Shows in progress indicator indicating that the scan is in progress.
     */
    fun showInProgressIndicator() {
        completeInfoLabel.isVisible = false

        progressIndicatorLabel.text = message("codewhisperer.codescan.scan_in_progress")
        resultsPanel.apply {
            if (components.contains(splitter)) remove(splitter)
            add(BorderLayout.CENTER, progressIndicator)
            revalidate()
            repaint()
        }
    }

    /**
     * Sets info label to show error in case a runtime exception is encountered while running a code scan.
     */
    fun showError(errorMsg: String) {
        completeInfoLabel.isVisible = true
        infoLabelPrefix.apply {
            text = errorMsg
            icon = AllIcons.General.Error
            isVisible = true
            repaint()
        }
        scannedFilesLabelLink.text = ""
        learnMoreLabelLink.text = ""
        resultsPanel.apply {
            if (components.contains(splitter)) remove(splitter)
            if (components.contains(progressIndicator)) remove(progressIndicator)
            revalidate()
            repaint()
        }
    }
    private fun showScannedFiles(files: List<VirtualFile>) {
        val scannedFilesViewPanel = CodeWhispererCodeScanHighlightingFilesPanel(project, files)
        scannedFilesViewPanel.apply {
            isVisible = true
            revalidate()
        }
        splitter.apply {
            secondComponent = scannedFilesViewPanel
            proportion = 0.5f
            revalidate()
            repaint()
        }
    }

    private fun showIssueDetails(issue: CodeWhispererCodeScanIssue) {
        val issueDetailsViewPanel = CodeWhispererCodeScanIssueDetailsPanel(project, issue)
        issueDetailsViewPanel.apply {
            isVisible = true
            revalidate()
        }
        splitter.apply {
            secondComponent = issueDetailsViewPanel
            proportion = 0.5f
            revalidate()
            repaint()
        }
    }

    private fun changeInfoLabelToDisplayScanCompleted(numScannedFiles: Int) {
        completeInfoLabel.isVisible = true
        infoLabelPrefix.icon = AllIcons.Actions.Commit
        infoLabelPrefix.text = message(
            "codewhisperer.codescan.run_scan_complete",
            numScannedFiles,
            (codeScanTree.model as CodeWhispererCodeScanTreeModel).getTotalIssuesCount(),
            project.name,
            INACTIVE_TEXT_COLOR,
            DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        )
        infoLabelPrefix.repaint()
        infoLabelPrefix.isVisible = true
        scannedFilesLabelLink.text = message("codewhisperer.codescan.view_scanned_files", numScannedFiles)
        scannedFilesLabelLink.isVisible = true
    }

    private fun createToolbar(): ActionToolbar {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction("aws.toolkit.codewhisperer.toolbar.security") as ActionGroup
        return actionManager.createActionToolbar(ACTION_PLACE, group, false)
    }

    private inner class ColoredTreeCellRenderer : TreeCellRenderer {
        private fun getSeverityIcon(severity: String): Icon? = when (severity) {
            IssueSeverity.LOW.displayName -> AwsIcons.Resources.CodeWhisperer.SEVERITY_INITIAL_LOW
            IssueSeverity.MEDIUM.displayName -> AwsIcons.Resources.CodeWhisperer.SEVERITY_INITIAL_MEDIUM
            IssueSeverity.HIGH.displayName -> AwsIcons.Resources.CodeWhisperer.SEVERITY_INITIAL_HIGH
            IssueSeverity.CRITICAL.displayName -> AwsIcons.Resources.CodeWhisperer.SEVERITY_INITIAL_CRITICAL
            IssueSeverity.INFO.displayName -> AwsIcons.Resources.CodeWhisperer.SEVERITY_INITIAL_INFO
            else -> null
        }

        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            value as DefaultMutableTreeNode
            val cell = JLabel()
            synchronized(value) {
                when (val obj = value.userObject) {
                    is String -> {
                        cell.text = message("codewhisperer.codescan.severity_issues_count", obj, value.childCount, INACTIVE_TEXT_COLOR)
                        cell.icon = this.getSeverityIcon(obj)
                    }
                    is VirtualFile -> {
                        cell.text = message("codewhisperer.codescan.file_name_issues_count", obj.name, obj.path, value.childCount, INACTIVE_TEXT_COLOR)
                        cell.icon = obj.fileType.icon
                    }
                    is CodeWhispererCodeScanIssue -> {
                        val cellText = obj.title.trimEnd('.')
                        val cellDescription = if (this@CodeWhispererCodeScanResultsView.isGroupedBySeverity()) {
                            "${obj.file.name} ${obj.displayTextRange()}"
                        } else {
                            obj.displayTextRange()
                        }
                        if (obj.isInvalid) {
                            cell.text = message("codewhisperer.codescan.scan_recommendation_invalid", obj.title, cellDescription, INACTIVE_TEXT_COLOR)
                            cell.toolTipText = message("codewhisperer.codescan.scan_recommendation_invalid.tooltip_text")
                            cell.icon = AllIcons.General.Information
                        } else {
                            cell.text = message("codewhisperer.codescan.scan_recommendation", cellText, cellDescription, INACTIVE_TEXT_COLOR)
                            cell.toolTipText = cellText
                            cell.icon = if (this@CodeWhispererCodeScanResultsView.isGroupedBySeverity()) {
                                obj.issueSeverity.icon
                            } else {
                                getSeverityIcon(obj.severity)
                            }
                        }
                    }
                }
            }
            return cell
        }
    }

    private fun navigateToIssue(codeScanIssue: CodeWhispererCodeScanIssue) {
        val textRange = codeScanIssue.textRange ?: return
        val startOffset = textRange.startOffset

        if (codeScanIssue.isInvalid) return

        runInEdt {
            val editor = FileEditorManager.getInstance(project).openTextEditor(
                OpenFileDescriptor(project, codeScanIssue.file, startOffset),
                false
            )
            if (editor == null) {
                LOG.error { "Cannot fetch editor for the file ${codeScanIssue.file.path}" }
                return@runInEdt
            }
            if (codeScanIssue.rangeHighlighter == null) {
                codeScanIssue.rangeHighlighter = codeScanIssue.addRangeHighlighter(editor.markupModel)
            }
        }
    }

    private companion object {
        const val ACTION_PLACE = "CodeScanResultsPanel"
        const val CODE_SCAN_SPLITTER_PROPORTION_KEY = "CODE_SCAN_SPLITTER_PROPORTION"
        private val LOG = getLogger<CodeWhispererCodeScanResultsView>()
    }
}
