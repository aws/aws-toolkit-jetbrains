// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.context.CodeScanIssueDetailsDisplayType
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.additionBackgroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.additionForegroundColor
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.applyFix
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
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.truncateIssueTitle
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getHexString
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Component
import java.awt.BorderLayout
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

internal class CodeWhispererCodeScanIssueDetailsPanel(
    private val project: Project,
    issue: CodeWhispererCodeScanIssue,
) : JPanel(BorderLayout()) {
    private val kit = HTMLEditorKit()
    private val doc = kit.createDefaultDocument()
    private val codeScanManager = CodeWhispererCodeScanManager.getInstance(project)

    private val editorPane = JEditorPane().apply {
        contentType = "text/html"
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(3, 10, 8, 11)
        )
        val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
        background = editorColorsScheme.defaultBackground
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
                    }
                    he.description.startsWith("amazonq://issue/openFile-") -> {
                        runInEdt {
                            FileEditorManager.getInstance(project).openTextEditor(
                                OpenFileDescriptor(project, issue.file),
                                true
                            )
                        }
                    }
                    else -> {
                        BrowserUtil.browse(he.url)
                    }
                }
            }
        }
        editorKit = kit
        document = doc
        text = getCodeScanIssueDetailsHtml(issue, CodeScanIssueDetailsDisplayType.DetailsPane, project = project)
        caretPosition = 0
    }

    private val scrollPane = JBScrollPane(editorPane).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }
    private val severityLabel = JLabel(truncateIssueTitle(issue.title)).apply {
        icon = getSeverityIcon(issue)
        horizontalTextPosition = JLabel.LEFT
        font = font.deriveFont(16f)
    }
    private val applyFixButton = JButton(message("codewhisperer.codescan.apply_fix_button_label")).apply {
        addActionListener {
            applyFix(issue)
        }
    }
    private val explainIssueButton = JButton(message("codewhisperer.codescan.explain_button_label")).apply {
        addActionListener {
            explainIssue(issue)
        }
    }
    private val ignoreIssueButton = JButton(message("codewhisperer.codescan.ignore_button")).apply {
        addActionListener {
            codeScanManager.ignoreSingleIssue(issue)
            ApplicationManager.getApplication().executeOnPooledThread {
                CodeWhispererTelemetryService.getInstance().sendCodeScanIssueIgnore(Component.Webview, issue, false)
            }
        }
    }
    private val ignoreIssuesButton = JButton(message("codewhisperer.codescan.ignore_all_button")).apply {
        addActionListener {
            codeScanManager.ignoreAllIssues(issue)
            ApplicationManager.getApplication().executeOnPooledThread {
                CodeWhispererTelemetryService.getInstance().sendCodeScanIssueIgnore(Component.Webview, issue, true)
            }
        }
    }
    private val closeDetailsButton = JButton(AllIcons.Actions.CloseDarkGrey).apply {
        border = null
        margin = null
        isContentAreaFilled = false
        putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
        addActionListener {
            hideIssueDetails()
        }
    }
    private val titlePane = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        preferredSize = Dimension(this.width, 30)
        add(Box.createHorizontalStrut(10))
        add(severityLabel)
        add(Box.createHorizontalGlue())
        add(closeDetailsButton)
    }
    private val buttonPane = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        preferredSize = Dimension(this.width, 30)
        add(explainIssueButton)
        add(applyFixButton)
        add(ignoreIssueButton)
        add(ignoreIssuesButton)
        add(Box.createHorizontalGlue())
    }
    private fun hideIssueDetails() {
        isVisible = false
        revalidate()
        repaint()
    }

    init {
        removeAll()
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

        add(BorderLayout.NORTH, titlePane)
        add(BorderLayout.CENTER, scrollPane)
        add(BorderLayout.SOUTH, buttonPane)
        isVisible = true
        revalidate()
    }
}
