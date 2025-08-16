// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.CodeWhispererCodeScanManager
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.context.CodeScanIssueDetailsDisplayType
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.additionBackgroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.additionForegroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.applyFix
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.applySuggestedFix
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.codeBlockBackgroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.codeBlockBorderColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.codeBlockForegroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.deletionBackgroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.deletionForegroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.explainIssue
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.getCodeScanIssueDetailsHtml
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.getSeverityIcon
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.metaBackgroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.metaForegroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.openDiff
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.sendCodeRemediationTelemetryToServiceApi
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.truncateIssueTitle
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getHexString
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodeFixAction
import software.aws.toolkits.telemetry.MetricResult
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class CodeWhispererCodeScanEditorMouseMotionListener(private val project: Project) : EditorMouseMotionListener {
    /**
     * Current context for popup is still being shown.
     */
    private var currentPopupContext: ScanIssuePopupContext? = null

    private fun hidePopup() {
        currentPopupContext?.popup?.cancel()
        currentPopupContext = null
    }

    private fun showPopup(issues: List<CodeWhispererCodeScanIssue>, e: EditorMouseEvent, issueIndex: Int = 0) {
        if (issues.isEmpty()) {
            LOG.debug {
                "Unable to show popup issue at ${e.logicalPosition} as there are no issues"
            }
            return
        }

        val issue = issues[issueIndex]
        val content = getCodeScanIssueDetailsHtml(issue, CodeScanIssueDetailsDisplayType.EditorPopup, project = project)
        val kit = HTMLEditorKit()
        kit.styleSheet.apply {
            addRule("h1, h3 { margin-bottom: 0 }")
            addRule("th { text-align: left; }")
            addRule(".code-block { background-color: ${codeBlockBackgroundColor.getHexString()}; border: 1px solid ${codeBlockBorderColor.getHexString()}; }")
            addRule(".code-block pre { margin: 0; }")
            addRule(".code-block div { color: ${codeBlockForegroundColor.getHexString()}; }")
            addRule(
                ".code-block div.deletion { background-color: ${deletionBackgroundColor.getHexString()}; color: ${deletionForegroundColor.getHexString()}; }"
            )
            addRule(
                ".code-block div.addition { background-color: ${additionBackgroundColor.getHexString()}; color: ${additionForegroundColor.getHexString()}; }"
            )
            addRule(".code-block div.meta { background-color: ${metaBackgroundColor.getHexString()}; color: ${metaForegroundColor.getHexString()}; }")
        }
        val doc = kit.createDefaultDocument()
        val editorPane = JEditorPane().apply {
            contentType = "text/html"
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(),
                BorderFactory.createEmptyBorder(7, 11, 8, 11)
            )
            isEditable = false
            addHyperlinkListener { he ->
                if (he.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    when {
                        he.description.startsWith("amazonq://issue/openDiff-") -> {
                            openDiff(issue)
                        }
                        he.description.startsWith("amazonq://issue/copyDiff-") -> {
                            text = getCodeScanIssueDetailsHtml(
                                issue,
                                CodeScanIssueDetailsDisplayType.DetailsPane,
                                CodeWhispererConstants.FixGenerationState.COMPLETED,
                                true,
                                project = project
                            )
                            CopyPasteManager.getInstance().setContents(StringSelection(issue.suggestedFixes.first().code))
                            val alarm = Alarm()
                            alarm.addRequest({
                                ApplicationManager.getApplication().invokeLater {
                                    text = getCodeScanIssueDetailsHtml(
                                        issue,
                                        CodeScanIssueDetailsDisplayType.DetailsPane,
                                        CodeWhispererConstants.FixGenerationState.COMPLETED,
                                        false,
                                        project = project
                                    )
                                }
                            }, 500)
                            ApplicationManager.getApplication().executeOnPooledThread {
                                CodeWhispererTelemetryService.getInstance()
                                    .sendCodeScanIssueApplyFixEvent(issue, MetricResult.Succeeded, codeFixAction = CodeFixAction.CopyDiff)
                            }
                        }
                        else -> {
                            BrowserUtil.browse(he.url)
                        }
                    }
                    hidePopup()
                }
            }
            editorKit = kit
            document = doc
            text = content
            caretPosition = 0
        }
        val scrollPane = JBScrollPane(editorPane).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
        val label = JLabel(truncateIssueTitle(issue.title)).apply {
            icon = getSeverityIcon(issue)
            horizontalTextPosition = JLabel.LEFT
        }
        val button = JButton(message("codewhisperer.codescan.apply_fix_button_label")).apply {
            toolTipText = message("codewhisperer.codescan.apply_fix_button_tooltip")
            putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        }
        button.addActionListener {
            applySuggestedFix(project, issue)
            button.isVisible = false
        }
        val nextButton = JButton(AllIcons.Actions.ArrowExpand).apply {
            preferredSize = Dimension(30, this.height)
            addActionListener {
                hidePopup()
                showPopup(issues, e, (issueIndex + 1) % issues.size)
            }
        }
        val prevButton = JButton(AllIcons.Actions.ArrowCollapse).apply {
            preferredSize = Dimension(30, this.height)
            addActionListener {
                hidePopup()
                showPopup(issues, e, (issues.size - (issueIndex + 1)) % issues.size)
            }
        }

        val explainButton = JButton(
            message("codewhisperer.codescan.explain_button_label")
        ).apply {
            toolTipText = message("codewhisperer.codescan.explain_button_tooltip")
            addActionListener {
                hidePopup()
                explainIssue(issue)
            }
        }
        val applyFixButton = JButton(
            message("codewhisperer.codescan.apply_fix_button_label")
        ).apply {
            toolTipText = message("codewhisperer.codescan.apply_fix_button_tooltip")
            addActionListener {
                hidePopup()
                applyFix(issue)
            }
        }

        val titlePane = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            preferredSize = Dimension(this.width, 30)

            // Add buttons first if they exist
            if (issues.size > 1) {
                add(prevButton)
                add(JLabel("${issueIndex + 1} of ${issues.size}"))
                add(nextButton)
            }

            if (issue.suggestedFixes.isNotEmpty()) {
                add(button)
            }
            add(explainButton)
            add(applyFixButton)

            // Add glue before and after label to center it
            add(Box.createHorizontalGlue())
            add(label)
            add(Box.createHorizontalGlue())
        }

        val containerPane = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
            add(titlePane)
            add(scrollPane)
            preferredSize = Dimension(650, 350)
        }

        val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(containerPane, null).setFocusable(true).setResizable(true)
            .createPopup()
        // Set the currently shown issue popup context as this issue
        currentPopupContext = ScanIssuePopupContext(issue, popup)

        popup.show(RelativePoint(e.mouseEvent))

        CodeWhispererTelemetryService.getInstance().sendCodeScanIssueHoverEvent(issue)
        sendCodeRemediationTelemetryToServiceApi(
            project,
            issue.file.programmingLanguage(),
            "CODESCAN_ISSUE_HOVER",
            issue.detectorId,
            issue.findingId,
            issue.ruleId,
            null,
            null,
            null,
            issue.suggestedFixes.isNotEmpty()
        )
    }

    override fun mouseMoved(e: EditorMouseEvent) {
        val scanManager = CodeWhispererCodeScanManager.getInstance(project)
        if (e.area != EditorMouseEventArea.EDITING_AREA || !e.isOverText) {
            hidePopup()
            return
        }
        val offset = e.offset
        val file = FileDocumentManager.getInstance().getFile(e.editor.document) ?: return
        val issuesInRange = scanManager.getScanNodesInRange(file, offset).map {
            it.userObject as CodeWhispererCodeScanIssue
        }
        if (issuesInRange.isEmpty()) {
            hidePopup()
            return
        }
        if (issuesInRange.contains(currentPopupContext?.issue)) return

        // No popups should be visible at this point.
        hidePopup()
        // Show popup for only the first issue found.
        // Only add popup if the issue is still valid. If the issue has gone stale or invalid because
        // the user has made some edits, we don't need to show the popup for the stale or invalid issues.
        if (!issuesInRange.first().isInvalid) {
            showPopup(issuesInRange, e)
        }
    }

    private data class ScanIssuePopupContext(val issue: CodeWhispererCodeScanIssue, val popup: JBPopup)

    companion object {
        private val LOG = getLogger<CodeWhispererCodeScanEditorMouseMotionListener>()
    }
}
