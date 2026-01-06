// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.icons.AllIcons
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.range
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.amazon.q.core.utils.WaiterTimeoutException
import software.amazon.q.core.utils.debug
import software.amazon.q.core.utils.error
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.info
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.coroutines.EDT
import software.amazon.q.jetbrains.core.coroutines.getCoroutineUiContext
import software.amazon.q.jetbrains.core.coroutines.projectCoroutineScope
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.utils.isQConnected
import software.amazon.q.jetbrains.utils.isQExpired
import software.amazon.q.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.jetbrains.ProblemsViewMutator
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners.CodeWhispererCodeScanDocumentListener
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners.CodeWhispererCodeScanEditorMouseMotionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners.CodeWhispererCodeScanFileListener
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.AmazonQCodeReviewGitUtils.isInsideWorkTree
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueGroupingStrategy
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.utils.IssueSeverity
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.overlaps
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isUserBuilderId
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererPlainText
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.INACTIVE_TEXT_COLOR
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.ISSUE_HIGHLIGHT_TEXT_ATTRIBUTES
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.amazonqIgnoreNextLine
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.scanResultsKey
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.scanScopeKey
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.coroutines.CoroutineContext

private val LOG = getLogger<CodeWhispererCodeScanManager>()

class CodeWhispererCodeScanManager(val project: Project, private val defaultScope: CoroutineScope) : Disposable {
    private val codeScanResultsPanel by lazy {
        CodeWhispererCodeScanResultsView(project)
    }

    private var autoScanIssues = emptyList<CodeWhispererCodeScanIssue>()
    private var ondemandScanIssues = emptyList<CodeWhispererCodeScanIssue>()
    private fun getCombinedScanIssues() = getUniqueIssues(autoScanIssues + ondemandScanIssues)
    private val severityNodeLookup = mapOf(
        IssueSeverity.CRITICAL.displayName to DefaultMutableTreeNode(IssueSeverity.CRITICAL.displayName),
        IssueSeverity.HIGH.displayName to DefaultMutableTreeNode(IssueSeverity.HIGH.displayName),
        IssueSeverity.MEDIUM.displayName to DefaultMutableTreeNode(IssueSeverity.MEDIUM.displayName),
        IssueSeverity.LOW.displayName to DefaultMutableTreeNode(IssueSeverity.LOW.displayName),
        IssueSeverity.INFO.displayName to DefaultMutableTreeNode(IssueSeverity.INFO.displayName)
    )
    private val fileNodeLookup = mutableMapOf<VirtualFile, DefaultMutableTreeNode>()
    private val scanNodesLookup = mutableMapOf<VirtualFile, MutableList<DefaultMutableTreeNode>>()
    private val selectedSeverityValues = IssueSeverity.entries.associate { it.displayName to true }.toMutableMap()
    private var selectedGroupingStrategy = IssueGroupingStrategy.SEVERITY

    private val documentListener = CodeWhispererCodeScanDocumentListener(project)
    private val editorMouseListener = CodeWhispererCodeScanEditorMouseMotionListener(project)
    private val fileListener = CodeWhispererCodeScanFileListener(project)

    private val isOnDemandScanInProgress = AtomicBoolean(false)

    private lateinit var codeScanJob: Job
    private lateinit var debouncedCodeScanJob: Job

    /**
     * Returns true if the code scan is in progress.
     * This function will return true for a cancelled code scan job which is in cancellation state.
     */
    fun isOnDemandScanInProgress(): Boolean = isOnDemandScanInProgress.get()

    fun getActionButtonIconForExplorerNode(): Icon = if (isOnDemandScanInProgress()) AllIcons.Actions.Suspend else AllIcons.Actions.Execute

    private fun isIgnoredIssueTitle(title: String) = getIgnoredIssueTitles().contains(title)

    fun isIgnoredIssue(title: String, document: Document, file: VirtualFile, startLine: Int) = isIgnoredIssueTitle(title) ||
        detectSingleIssueIgnored(document, file, startLine)

    private fun getIgnoredIssueTitles() = CodeWhispererSettings.getInstance().getIgnoredCodeReviewIssues().split(";").toMutableSet()

    fun ignoreAllIssues(issue: CodeWhispererCodeScanIssue) {
        val ignoredIssueTitles = getIgnoredIssueTitles()
        ignoredIssueTitles.add(issue.title)
        CodeWhispererSettings.getInstance().setIgnoredCodeReviewIssues(ignoredIssueTitles.joinToString(separator = ";"))
        // update the in memory copy and UI
        ondemandScanIssues = ondemandScanIssues.filter { it.title != issue.title }
        autoScanIssues = autoScanIssues.filter { it.title != issue.title }
        updateCodeScanIssuesTree()
    }

    private fun detectSingleIssueIgnored(document: Document, file: VirtualFile, startLine: Int): Boolean = runReadAction {
        try {
            if (startLine == 0) return@runReadAction false
            val commenter = getLanguageCommenter(document, project)
            val linePrefix: String? = commenter?.lineCommentPrefix ?: file.programmingLanguage().lineCommentPrefix()
            val blockPrefix: String? = commenter?.blockCommentPrefix ?: file.programmingLanguage().blockCommentPrefix()
            val blockSuffix: String? = commenter?.blockCommentSuffix ?: file.programmingLanguage().blockCommentSuffix()

            for (i in (startLine - 1) downTo 0) {
                val lineStart = document.getLineStartOffset(i)
                val lineEnd = document.getLineEndOffset(i)
                val targetRange = TextRange(lineStart, lineEnd)
                val lineText = document.getText(targetRange)
                if (lineText.isEmpty()) {
                    continue
                }
                if (linePrefix != null &&
                    lineText.trimIndent().startsWith(linePrefix) &&
                    lineText.contains(amazonqIgnoreNextLine)
                ) {
                    return@runReadAction true
                }
                if (blockPrefix != null &&
                    blockSuffix != null &&
                    lineText.trimIndent().startsWith(blockPrefix) &&
                    lineText.contains(amazonqIgnoreNextLine) &&
                    lineText.trimEnd().endsWith(blockSuffix)
                ) {
                    return@runReadAction true
                }
                return@runReadAction false
            }
            return@runReadAction false
        } catch (e: Exception) {
            LOG.warn { "Failed to detect if scan issue is ignored: ${e.stackTraceToString()}" }
            return@runReadAction false
        }
    }

    fun ignoreSingleIssue(issue: CodeWhispererCodeScanIssue) {
        val document = issue.document
        var commentString: String? = null
        var insertOffset: Int? = null
        try {
            runReadAction {
                if (!issue.isVisible) return@runReadAction
                val lineNumber = issue.startLine
                val issueRange = TextRange(document.getLineStartOffset(lineNumber - 1), document.getLineEndOffset(lineNumber - 1))
                val lineContent = document.getText(issueRange)
                val indentation = lineContent.takeWhile { it.isWhitespace() }
                insertOffset = document.getLineStartOffset(lineNumber - 1)
                val commenter = getLanguageCommenter(document, project)
                val linePrefix: String? = commenter?.lineCommentPrefix ?: issue.file.programmingLanguage().lineCommentPrefix()
                val blockPrefix: String? = commenter?.blockCommentPrefix ?: issue.file.programmingLanguage().blockCommentPrefix()
                val blockSuffix: String? = commenter?.blockCommentSuffix ?: issue.file.programmingLanguage().blockCommentSuffix()
                if (linePrefix != null) {
                    commentString = "$indentation$linePrefix $amazonqIgnoreNextLine\n"
                } else if (blockPrefix != null && blockSuffix != null) {
                    commentString = "$indentation$blockPrefix $amazonqIgnoreNextLine $blockSuffix\n"
                }
            }
            val finalOffset = insertOffset ?: return
            val finalCommentString = commentString ?: return
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    document.insertString(finalOffset, finalCommentString)
                }
            }
        } catch (e: Exception) {
            LOG.warn { "Failed to insert ignore comment ${e.stackTraceToString()}" }
        }
        ondemandScanIssues = ondemandScanIssues.filter { it.findingId != issue.findingId }
        autoScanIssues = autoScanIssues.filter { it.findingId != issue.findingId }
        removeIssueByFindingId(issue, issue.findingId)
    }

    private fun getLanguageCommenter(document: Document, project: Project): Commenter? {
        // TODO: need to implement fall back for languages not supported by IDE
        val language: Language = document
            .let { PsiDocumentManager.getInstance(project).getPsiFile(it) }
            ?.language ?: return null
        return LanguageCommenters.INSTANCE.forLanguage(language)
    }

    fun isSeveritySelected(severity: String): Boolean = selectedSeverityValues[severity] ?: true
    fun setSeveritySelected(severity: String, selected: Boolean) {
        selectedSeverityValues[severity] = selected
        updateCodeScanIssuesTree()
    }

    fun getGroupingStrategySelected() = selectedGroupingStrategy
    fun setGroupingStrategySelected(groupingStrategy: IssueGroupingStrategy) {
        selectedGroupingStrategy = groupingStrategy
        updateCodeScanIssuesTree()
    }

    /**
     * Returns true if there are any code scan issues.
     */
    fun hasCodeScanIssues(): Boolean = getCombinedScanIssues().isNotEmpty()

    /**
     * Clears all filters and updates the code scan issues tree.
     */
    fun clearFilters() {
        selectedSeverityValues.keys.forEach { selectedSeverityValues[it] = true }
        updateCodeScanIssuesTree()
    }

    /**
     * Triggers a code scan and displays results in the new tab in problems view panel.
     */
    fun runCodeScan(scope: CodeWhispererConstants.CodeAnalysisScope, isPluginStarting: Boolean = false, initiatedByChat: Boolean = false) {
        if (!isQConnected(project)) return

        // Return if a scan is already in progress.
        if (isOnDemandScanInProgress()) return

        val connectionExpired = if (isPluginStarting) {
            isQExpired(project)
        } else {
            promptReAuth(project)
        }
        if (connectionExpired) {
            isOnDemandScanInProgress.set(false)
            return
        }

        //  If scope is project
        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            // launch code scan coroutine
            try {
                codeScanJob = launchCodeScanCoroutine(CodeWhispererConstants.CodeAnalysisScope.PROJECT, initiatedByChat)
            } catch (e: CancellationException) {
                notifyChat(codeScanResponse = null, scope = scope)
            }
        } else {
            if (CodeWhispererExplorerActionManager.getInstance().isAutoEnabledForCodeScan() and !isUserBuilderId(project) || initiatedByChat) {
                // cancel if a file scan is running.
                if (!isOnDemandScanInProgress() && this::codeScanJob.isInitialized && codeScanJob.isActive) {
                    codeScanJob.cancel()
                }
                //  Add File Scan
                try {
                    codeScanJob = launchCodeScanCoroutine(CodeWhispererConstants.CodeAnalysisScope.FILE, initiatedByChat)
                } catch (e: CancellationException) {
                    notifyChat(codeScanResponse = null, scope = scope)
                }
            }
        }
    }

    /**
     * Creates a debounced code scan job with a delay.
     */
    fun createDebouncedRunCodeScan(
        scope: CodeWhispererConstants.CodeAnalysisScope,
        isPluginStarting: Boolean = false,
        waitMs: Long = CodeWhispererConstants.AUTO_SCAN_DEBOUNCE_DELAY_IN_SECONDS * 1000,
        coroutineScope: CoroutineScope = defaultScope,
    ) {
        if (this::debouncedCodeScanJob.isInitialized && debouncedCodeScanJob.isActive) {
            debouncedCodeScanJob.cancel()
        }
        debouncedCodeScanJob = coroutineScope.launch {
            delay(waitMs)
            runCodeScan(scope, isPluginStarting)
        }
    }

    fun stopCodeScan(scope: CodeWhispererConstants.CodeAnalysisScope) {
        // Return if code scan job is not active.
        if (!codeScanJob.isActive) {
            notifyChat(codeScanResponse = null, scope = scope)
            return
        }
        // TODO: need to check if we need to ask for user's confirmation again
        if (isOnDemandScanInProgress()) {
            LOG.info { "Code Review stopped by user..." }
            // Checking `codeScanJob.isActive` to ensure that the job is not already completed by the time user confirms.
            if (codeScanJob.isActive) {
                codeScanResultsPanel.setStoppingCodeScan()
                notifyChat(codeScanResponse = null, scope = scope)
                codeScanJob.cancel(CancellationException("User requested cancellation"))
            }
        }
    }

    private suspend fun isInValidFile(
        selectedFile: VirtualFile?,
        language: CodeWhispererProgrammingLanguage,
        codeScanSessionConfig: CodeScanSessionConfig,
    ): Boolean =
        selectedFile == null ||
            !language.isAutoFileScanSupported() ||
            !selectedFile.isFile ||
            selectedFile.fileSystem.protocol == "remoteDeploymentFS" ||
            readAction { codeScanSessionConfig.fileIndex.isInLibrarySource(selectedFile) }

    private fun launchCodeScanCoroutine(scope: CodeWhispererConstants.CodeAnalysisScope, initiatedByChat: Boolean) = projectCoroutineScope(project).launch {
        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT || initiatedByChat) beforeCodeScan()
        var codeScanStatus: Result = Result.Failed
        val startTime = Instant.now().toEpochMilli()
        var codeScanResponseContext = defaultCodeScanResponseContext()
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        var language: CodeWhispererProgrammingLanguage = CodeWhispererUnknownLanguage.INSTANCE
        var skipTelemetry = false
        try {
            val file =
                if (isRunningOnRemoteBackend()) {
                    FileEditorManager.getInstance(project).selectedEditorWithRemotes.firstOrNull()?.file
                } else {
                    FileEditorManager.getInstance(project).selectedEditor?.file
                }
            val codeScanSessionConfig = CodeScanSessionConfig.create(file, project, scope, initiatedByChat)
            val selectedFile = codeScanSessionConfig.getSelectedFile()
            language = codeScanSessionConfig.getProgrammingLanguage()
            if (scope == CodeWhispererConstants.CodeAnalysisScope.FILE &&
                isInValidFile(selectedFile, language, codeScanSessionConfig) && !initiatedByChat
            ) {
                skipTelemetry = true
                LOG.debug { "Language is unknown or plaintext, skipping code review." }
                codeScanStatus = Result.Cancelled
                return@launch
            } else {
                withTimeout(Duration.ofSeconds(codeScanSessionConfig.overallJobTimeoutInSeconds())) {
                    // 1. Generate truncation (zip files) based on the current editor.
                    val sessionContext = CodeScanSessionContext(project, codeScanSessionConfig, scope)
                    val session = CodeWhispererCodeScanSession(sessionContext)
                    val codeScanResponse = session.run()
                    language = codeScanSessionConfig.getProgrammingLanguage()
                    if (language == CodeWhispererPlainText.INSTANCE) { skipTelemetry = true }
                    codeScanResponseContext = codeScanResponse.responseContext
                    when (codeScanResponse) {
                        is CodeScanResponse.Success -> {
                            if (initiatedByChat) {
                                ondemandScanIssues = if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
                                    codeScanResponse.issues
                                } else {
                                    ondemandScanIssues + codeScanResponse.issues
                                }
                            } else {
                                autoScanIssues = codeScanResponse.issues
                            }
                            coroutineContext.ensureActive()
                            renderResponseOnUIThread(
                                getCombinedScanIssues(),
                                codeScanResponse.responseContext.payloadContext.scannedFiles,
                                scope
                            )
                            codeScanStatus = Result.Succeeded
                            if (initiatedByChat) {
                                notifyChat(getChatMessageResponse(codeScanResponse, scope), scope = scope)
                            }
                        }

                        is CodeScanResponse.Failure -> {
                            if (codeScanResponse.failureReason !is TimeoutCancellationException && codeScanResponse.failureReason is CancellationException) {
                                codeScanStatus = Result.Cancelled
                            }
                            if (initiatedByChat) {
                                notifyChat(codeScanResponse, scope = scope)
                            }
                            throw codeScanResponse.failureReason
                        }
                    }
                    LOG.info { "Code review completed for jobID: ${codeScanResponseContext.codeScanJobId}." }
                }
            }
        } catch (e: Error) {
            afterCodeScan(scope, initiatedByChat)
            val errorMessage = handleError(coroutineContext, e)
            codeScanResponseContext = codeScanResponseContext.copy(reason = errorMessage)
        } catch (e: Exception) {
            afterCodeScan(scope, initiatedByChat)
            val errorMessage = handleException(coroutineContext, e, scope)
            codeScanResponseContext = codeScanResponseContext.copy(reason = errorMessage)
        } finally {
            // After code scan
            afterCodeScan(scope, initiatedByChat)
            if (!skipTelemetry) {
                launch {
                    val duration = (Instant.now().toEpochMilli() - startTime).toDouble()
                    CodeWhispererTelemetryService.getInstance().sendSecurityScanEvent(
                        CodeScanTelemetryEvent(
                            codeScanResponseContext,
                            duration,
                            codeScanStatus,
                            codeScanResponseContext.payloadContext.srcPayloadSize.toDouble() ?: 0.0,
                            connection,
                            scope,
                            initiatedByChat
                        )
                    )
                    sendCodeScanTelemetryToServiceAPI(project, language, codeScanResponseContext, scope)
                }
            }
        }
    }

    private fun getChatMessageResponse(originalResponse: CodeScanResponse.Success, scope: CodeWhispererConstants.CodeAnalysisScope): CodeScanResponse {
        if (originalResponse.issues.isEmpty()) return originalResponse
        val chatIssues = if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            getCombinedScanIssues()
        } else {
            getCombinedScanIssues().filter { issue -> issue.file == originalResponse.issues.first().file }
        }
        val finalResponse = originalResponse.copy(issues = chatIssues)
        return finalResponse
    }

    private fun refreshUi() {
        val codeScanTreeModel = CodeWhispererCodeScanTreeModel(codeScanTreeNodeRoot)
        val totalIssuesCount = codeScanTreeModel.getTotalIssuesCount()
        val displayName = if (totalIssuesCount > 0) {
            message("codewhisperer.codescan.scan_display_with_issues", totalIssuesCount, INACTIVE_TEXT_COLOR)
        } else {
            message("codewhisperer.codescan.scan_display")
        }

        withToolWindow { window ->
            window.contentManager.contents.filter { it.isCodeScanView() }
                .forEach {
                    it.displayName = displayName
                }
        }

        codeScanResultsPanel.refreshUIWithUpdatedModel(codeScanTreeModel)
    }

    fun updateIssue(updatedIssue: CodeWhispererCodeScanIssue) {
        autoScanIssues.find { it.findingId == updatedIssue.findingId }?.let { oldIssue ->
            val updatedList = autoScanIssues.toMutableList()
            val index = autoScanIssues.indexOf(oldIssue)
            updatedList[index] = updatedIssue
            autoScanIssues = updatedList
            return
        }
        ondemandScanIssues.find { it.findingId == updatedIssue.findingId }?.let { oldIssue ->
            val updatedList = ondemandScanIssues.toMutableList()
            val index = ondemandScanIssues.indexOf(oldIssue)
            updatedList[index] = updatedIssue
            ondemandScanIssues = updatedList
        }
    }

    fun removeIssue(issue: CodeWhispererCodeScanIssue) {
        autoScanIssues = autoScanIssues.filter { it.findingId != issue.findingId }
        ondemandScanIssues = ondemandScanIssues.filter { it.findingId != issue.findingId }
    }

    fun addOnDemandIssues(issues: List<CodeWhispererCodeScanIssue>, scannedFiles: List<VirtualFile>, scope: CodeWhispererConstants.CodeAnalysisScope) =
        defaultScope.launch {
            ondemandScanIssues = ondemandScanIssues + issues
            renderResponseOnUIThread(
                getCombinedScanIssues(),
                scannedFiles,
                scope
            )
        }

    fun removeIssueByFindingId(issue: CodeWhispererCodeScanIssue, findingId: String) {
        scanNodesLookup[issue.file]?.forEach { node ->
            val issueNode = node.userObject as CodeWhispererCodeScanIssue
            if (issueNode.findingId == findingId) {
                issueNode.rangeHighlighter?.textAttributes = null
                issueNode.rangeHighlighter?.dispose()
                node.removeFromParent()
                removeIssue(issue)
            }
        }
        refreshUi()
    }

    fun handleError(coroutineContext: CoroutineContext, e: Error): String {
        val errorMessage = when (e) {
            is NoClassDefFoundError -> {
                if (e.cause?.message?.contains("com.intellij.openapi.compiler.CompilerPaths") == true) {
                    message("codewhisperer.codescan.java_module_not_found")
                } else {
                    null
                }
            }
            else -> null
        } ?: message("codewhisperer.codescan.run_scan_error")

        if (!coroutineContext.isActive) {
            codeScanResultsPanel.setDefaultUI()
        } else {
            codeScanResultsPanel.showError(errorMessage)
        }

        return errorMessage
    }

    private fun getCodeScanExceptionMessage(e: CodeWhispererCodeScanException): String? {
        val message = e.message
        return when {
            message.isNullOrBlank() -> null
            message == message("codewhisperer.codescan.invalid_source_zip_telemetry") ||
                message == "Illegal repetition near index" -> message("codewhisperer.codescan.run_scan_error")
            else -> message
        }
    }

    private fun getCodeScanServerExceptionMessage(e: CodeWhispererCodeScanServerException): String? =
        when {
            e.message?.startsWith("UploadArtifactToS3Exception:") == true ->
                message("codewhisperer.codescan.upload_to_s3_failed")
            e.message?.startsWith("You've reached") == true ->
                message("codewhisperer.codescan.quota_exceeded")
            else -> null
        }
    fun handleException(coroutineContext: CoroutineContext, e: Exception, scope: CodeWhispererConstants.CodeAnalysisScope): String {
        val errorMessage = when (e) {
            is CodeWhispererRuntimeException -> e.awsErrorDetails().errorMessage() ?: message("codewhisperer.codescan.run_scan_error")
            is CodeWhispererCodeScanException -> getCodeScanExceptionMessage(e)
            is CodeWhispererCodeScanServerException -> getCodeScanServerExceptionMessage(e)
            is WaiterTimeoutException, is TimeoutCancellationException -> message("codewhisperer.codescan.scan_timed_out")
            is CancellationException -> message("codewhisperer.codescan.cancelled_by_user_exception")
            is IllegalStateException -> message("codewhisperer.codescan.cannot_read_file")
            else -> null
        } ?: message("codewhisperer.codescan.run_scan_error")

        val errorCode = (e as? CodeWhispererRuntimeException)?.awsErrorDetails()?.errorCode()
        val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null

        if (!coroutineContext.isActive) {
            codeScanResultsPanel.setDefaultUI()
        } else {
            codeScanResultsPanel.showError(errorMessage)
        }

        if (
            e is ThrottlingException &&
            e.message == CodeWhispererConstants.THROTTLING_MESSAGE
        ) {
            CodeWhispererExplorerActionManager.getInstance().setSuspended(project)
        }

        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            LOG.error(e) {
                "Failed to run code review and display results. Caused by: $errorMessage, status code: $errorCode, " +
                    "exception: ${e::class.simpleName}, request ID: $requestId " +
                    "Jetbrains IDE: ${ApplicationInfo.getInstance().fullApplicationName}, " +
                    "IDE version: ${ApplicationInfo.getInstance().apiVersion}, "
            }
        }

        val telemetryErrorMessage = when (e) {
            is CodeWhispererRuntimeException -> e.awsErrorDetails().errorMessage() ?: message("codewhisperer.codescan.run_scan_error_telemetry")
            is CodeWhispererCodeScanException -> when (e.message) {
                message("codewhisperer.codescan.no_file_open") -> message("codewhisperer.codescan.no_file_open_telemetry")
                message("codewhisperer.codescan.unsupported_language_error") -> message("codewhisperer.codescan.unsupported_language_error_telemetry")
                message("codewhisperer.codescan.file_too_large") -> message("codewhisperer.codescan.file_too_large_telemetry")
                else -> e.message
            }
            is CodeWhispererCodeScanServerException -> when (e.message) {
                message("testgen.error.maximum_generations_reach") -> message("codewhisperer.codescan.quota_exceeded")
                else -> e.message
            }
            is WaiterTimeoutException, is TimeoutCancellationException -> message("codewhisperer.codescan.scan_timed_out")
            is CancellationException -> message("codewhisperer.codescan.cancelled_by_user_exception")
            else -> e.message
        } ?: message("codewhisperer.codescan.run_scan_error_telemetry")

        return telemetryErrorMessage
    }

    /**
     * The initial landing UI for the code scan results view panel.
     * This method adds code content to the problems view if not already added.
     */
    fun buildCodeScanUI() = runInEdt {
        withToolWindow { problemsWindow ->
            val contentManager = problemsWindow.contentManager
            if (!contentManager.contents.any { it.isCodeScanView() }) {
                contentManager.addContent(
                    contentManager.factory.createContent(
                        codeScanResultsPanel,
                        message("codewhisperer.codescan.scan_display"),
                        false
                    ).also {
                        it.tabName = message("codewhisperer.codescan.scan_display")
                        Disposer.register(contentManager, it)
                        contentManager.addContentManagerListener(object : ContentManagerListener {
                            override fun contentRemoved(event: ContentManagerEvent) {
                                if (event.content == it) reset()
                            }
                        })
                    }
                )
            }
        }
    }

    /**
     * This method shows the code content panel in problems view
     */
    fun showCodeScanUI() = runInEdt {
        withToolWindow { problemsWindow ->
            problemsWindow.contentManager.contents.firstOrNull { it.isCodeScanView() }
                ?.let { problemsWindow.contentManager.setSelectedContent(it) }
            problemsWindow.show()
        }
    }

    fun removeCodeScanUI() = runInEdt {
        withToolWindow { problemsWindow ->
            problemsWindow.contentManager.contents.filter { it.isCodeScanView() }
                .forEach {
                    problemsWindow.contentManager.removeContent(it, true)
                }
        }
    }

    fun getScanNodesInRange(file: VirtualFile, startOffset: Int): List<DefaultMutableTreeNode> =
        getOverlappingScanNodes(file, TextRange.create(startOffset, startOffset + 1))

    fun getOverlappingScanNodes(file: VirtualFile, range: TextRange): List<DefaultMutableTreeNode> = synchronized(scanNodesLookup) {
        scanNodesLookup[file]?.mapNotNull { node ->
            val issue = node.userObject as CodeWhispererCodeScanIssue
            if (issue.textRange?.overlaps(range) == true && !issue.isInvalid) node else null
        }.orEmpty()
    }

    fun getScanTree(): Tree = codeScanResultsPanel.getCodeScanTree()

    /**
     * Updates the scan nodes in a [file] with the new text range.
     */
    fun updateScanNodes(file: VirtualFile) {
        scanNodesLookup[file]?.forEach { node ->
            val issue = node.userObject as CodeWhispererCodeScanIssue
            val newRange = issue.rangeHighlighter?.range
            val oldRange = issue.textRange
            // Check if the location of the issue is changed and only update the valid nodes.
            if (newRange != null && oldRange != newRange && !issue.isInvalid) {
                val newIssue = issue.copyRange(newRange)
                synchronized(node) {
                    getScanTree().model.valueForPathChanged(TreePath(node.path), newIssue)
                    node.userObject = newIssue
                }
                updateIssue(newIssue)
            }
        }
    }

    fun updateScanNodesForOffSet(file: VirtualFile, lineOffset: Int, editedTextRange: TextRange) {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        scanNodesLookup[file]?.forEach { node ->
            val issue = node.userObject as CodeWhispererCodeScanIssue
            if (document.getLineNumber(editedTextRange.startOffset) <= issue.startLine) {
                val newIssue = issue.copy()
                newIssue.startLine = issue.startLine + lineOffset
                newIssue.endLine = issue.endLine + lineOffset
                newIssue.suggestedFixes = issue.suggestedFixes.map { fix -> offsetSuggestedFix(fix, lineOffset) }.toMutableList()
                synchronized(node) {
                    node.userObject = newIssue
                }
                updateIssue(issue)
            }
        }
    }

    private fun CodeWhispererCodeScanIssue.copyRange(newRange: TextRange): CodeWhispererCodeScanIssue {
        val newStartLine = document.getLineNumber(newRange.startOffset)
        val newStartCol = newRange.startOffset - document.getLineStartOffset(newStartLine)
        val newEndLine = document.getLineNumber(newRange.endOffset)
        val newEndCol = newRange.endOffset - document.getLineStartOffset(newEndLine)
        return copy(
            startLine = newStartLine + 1,
            startCol = newStartCol + 1,
            endLine = newEndLine + 1,
            endCol = newEndCol + 1
        )
    }

    private fun withToolWindow(runnable: (ToolWindow) -> Unit) {
        ProblemsViewMutator.EP.forEachExtensionSafe { mutator ->
            mutator.mutateProblemsView(project, runnable)
        }
    }

    private fun Content.isCodeScanView() = component == codeScanResultsPanel

    private fun reset() = runInEdt {
        // clear the codeScanTreeNodeRoot
        synchronized(codeScanTreeNodeRoot) {
            codeScanTreeNodeRoot.removeAllChildren()
        }
        severityNodeLookup.onEach { (_, node) ->
            node.removeAllChildren()
        }
        // Erase all range highlighter before cleaning up.
        scanNodesLookup.apply {
            forEach { (_, nodes) ->
                nodes.forEach { node ->
                    val issue = node.userObject as CodeWhispererCodeScanIssue
                    issue.rangeHighlighter?.dispose()
                }
            }
            clear()
        }
    }

    fun setEditorListeners() {
        runInEdt {
            val editorFactory = EditorFactory.getInstance()
            editorFactory.eventMulticaster.addDocumentListener(documentListener, project)
            editorFactory.addEditorFactoryListener(fileListener, project)
            editorFactory.eventMulticaster.addEditorMouseMotionListener(
                editorMouseListener,
                this
            )
        }
    }

    private suspend fun beforeCodeScan() {
        isOnDemandScanInProgress.set(true)
        // Show in progress indicator
        buildCodeScanUI()
        codeScanResultsPanel.showInProgressIndicator()
        withContext(EDT) {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }

    private fun getUniqueIssues(codeScanResponse: List<CodeWhispererCodeScanIssue>): List<CodeWhispererCodeScanIssue> {
        val uniqueIssues = codeScanResponse.distinctBy { issue ->
            Triple(issue.file.path, issue.title, issue.startLine)
        }
        val uniqueIssueList: MutableList<CodeWhispererCodeScanIssue> = mutableListOf()

        uniqueIssues.forEach { issue ->
            val isValid = runReadAction {
                FileDocumentManager.getInstance().getDocument(issue.file)?.let { document ->
                    val documentLines = document.getText().split("\n")
                    val (startLine, endLine) = issue.run { startLine to endLine }
                    checkIssueCodeSnippet(issue.codeSnippet, startLine, endLine, documentLines)
                } ?: false
            }
            if (isValid) {
                uniqueIssueList.add(issue)
            }
        }
        return uniqueIssueList
    }

    private fun afterCodeScan(scope: CodeWhispererConstants.CodeAnalysisScope, initiatedByChat: Boolean) {
        if (initiatedByChat || scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            isOnDemandScanInProgress.set(false)
            showCodeScanUI()
        }
    }

    private fun sendCodeScanTelemetryToServiceAPI(
        project: Project,
        programmingLanguage: CodeWhispererProgrammingLanguage,
        codeScanResponseContext: CodeScanResponseContext,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ) {
        runIfIdcConnectionOrTelemetryEnabled(project) {
            try {
                val client = CodeWhispererClientAdaptor.getInstance(project)
                val response = client.sendCodeScanTelemetry(programmingLanguage, codeScanResponseContext.codeScanJobId, scope)
                LOG.debug { "Successfully sent code review telemetry. RequestId: ${response.responseMetadata().requestId()} for ${scope.value} scan" }

                if (codeScanResponseContext.reason == "Succeeded") {
                    val codeScanSuccessResponse = client.sendCodeScanSucceededTelemetry(
                        programmingLanguage,
                        codeScanResponseContext.codeScanJobId,
                        scope,
                        codeScanResponseContext.codeScanTotalIssues
                    )
                    LOG.debug {
                        "Successfully sent code review succeeded telemetry. RequestId: ${
                            codeScanSuccessResponse.responseMetadata().requestId()
                        } for ${scope.value} review"
                    }
                } else {
                    val codeScanFailureResponse = client.sendCodeScanFailedTelemetry(
                        programmingLanguage,
                        codeScanResponseContext.codeScanJobId,
                        scope
                    )
                    LOG.debug {
                        "Successfully sent code review failed telemetry. RequestId: ${
                            codeScanFailureResponse.responseMetadata().requestId()
                        } for ${scope.value} review"
                    }
                }
            } catch (e: Exception) {
                val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                LOG.debug {
                    "Failed to send code scan telemetry. RequestId: $requestId, ErrorMessage: ${e.message}"
                }
            }
        }
    }

    private val codeScanTreeNodeRoot = DefaultMutableTreeNode("Amazon Q code review results")

    /**
     * Creates a CodeWhisperer code scan issues tree.
     * For each scan node:
     *   1. (Add file node if not already present and) add scan node to the file node.
     *   2. Update the lookups - [fileNodeLookup] for efficiently adding scan nodes and
     *   [scanNodesLookup] for receiving the editor events and updating the corresponding scan nodes.
     */
    private fun createCodeScanIssuesTree(codeScanIssues: List<CodeWhispererCodeScanIssue>): DefaultMutableTreeNode {
        LOG.debug { "Rendering response from the code review API" }

        synchronized(codeScanTreeNodeRoot) {
            codeScanTreeNodeRoot.removeAllChildren()
        }
        synchronized(scanNodesLookup) {
            scanNodesLookup.clear()
        }
        synchronized(severityNodeLookup) {
            severityNodeLookup.forEach { (_, node) ->
                node.removeAllChildren()
            }
        }
        synchronized(fileNodeLookup) {
            fileNodeLookup.clear()
        }

        return if (selectedGroupingStrategy == IssueGroupingStrategy.SEVERITY) {
            createCodeScanIssuesTreeBySeverity(codeScanIssues)
        } else {
            createCodeScanIssuesTreeByFileLocation(codeScanIssues)
        }
    }

    private fun createCodeScanIssuesTreeBySeverity(codeScanIssues: List<CodeWhispererCodeScanIssue>): DefaultMutableTreeNode {
        severityNodeLookup.forEach { (severity, node) ->
            if (selectedSeverityValues[severity] == true) {
                synchronized(codeScanTreeNodeRoot) {
                    codeScanTreeNodeRoot.add(node)
                }
            }
        }

        codeScanIssues.forEach { issue ->
            if (selectedSeverityValues[issue.severity] == true) {
                val scanNode = DefaultMutableTreeNode(issue)
                severityNodeLookup[issue.severity]?.add(scanNode)
                scanNodesLookup.getOrPut(issue.file) {
                    mutableListOf()
                }.add(scanNode)
            }
        }

        return codeScanTreeNodeRoot
    }

    private fun createCodeScanIssuesTreeByFileLocation(codeScanIssues: List<CodeWhispererCodeScanIssue>): DefaultMutableTreeNode {
        codeScanIssues.forEach { issue ->
            val fileNode = synchronized(fileNodeLookup) {
                fileNodeLookup.getOrPut(issue.file) {
                    val node = DefaultMutableTreeNode(issue.file)
                    synchronized(codeScanTreeNodeRoot) {
                        codeScanTreeNodeRoot.add(node)
                    }
                    node
                }
            }

            val scanNode = DefaultMutableTreeNode(issue)
            fileNode.add(scanNode)
            scanNodesLookup.getOrPut(issue.file) {
                mutableListOf()
            }.add(scanNode)
        }
        return codeScanTreeNodeRoot
    }

    private fun checkIssueCodeSnippet(codeSnippet: List<CodeLine>, startLine: Int, endLine: Int, documentLines: List<String>): Boolean = try {
        codeSnippet
            .asSequence()
            .filter { it.number in startLine..endLine }
            .all { codeBlock ->
                val lineNumber = codeBlock.number - 1
                val documentLine = documentLines.getOrNull(lineNumber) ?: return@all false

                when {
                    codeBlock.content.trim().replace(" ", "").all { it == '*' } ->
                        documentLine.length == codeBlock.content.length

                    else ->
                        documentLine == codeBlock.content
                }
            }
    } catch (e: Exception) {
        false
    }

    private fun updateCodeScanIssuesTree() {
        val codeScanTreeNodeRoot = createCodeScanIssuesTree(getCombinedScanIssues())
        val codeScanTreeModel = CodeWhispererCodeScanTreeModel(codeScanTreeNodeRoot)
        val totalIssuesCount = codeScanTreeModel.getTotalIssuesCount()
        if (totalIssuesCount > 0) {
            withToolWindow { problemsWindow ->
                problemsWindow.contentManager.contents.filter { it.isCodeScanView() }
                    .forEach {
                        it.displayName = message("codewhisperer.codescan.scan_display_with_issues", totalIssuesCount, INACTIVE_TEXT_COLOR)
                    }
            }
        }
        codeScanResultsPanel.refreshUIWithUpdatedModel(codeScanTreeModel)
    }

    suspend fun renderResponseOnUIThread(
        issues: List<CodeWhispererCodeScanIssue>,
        scannedFiles: List<VirtualFile>,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ) {
        withContext(getCoroutineUiContext()) {
            val root = createCodeScanIssuesTree(issues)
            val codeScanTreeModel = CodeWhispererCodeScanTreeModel(root)
            val totalIssuesCount = codeScanTreeModel.getTotalIssuesCount()
            if (totalIssuesCount > 0) {
                withToolWindow { window ->
                    window.contentManager.contents.filter { it.isCodeScanView() }
                        .forEach {
                            it.displayName =
                                message("codewhisperer.codescan.scan_display_with_issues", totalIssuesCount, INACTIVE_TEXT_COLOR)
                        }
                }
            }
            codeScanResultsPanel.updateAndDisplayScanResults(codeScanTreeModel, scannedFiles, scope)
        }
    }

    private fun notifyChat(codeScanResponse: CodeScanResponse?, scope: CodeWhispererConstants.CodeAnalysisScope) {
        // We can't use CodeScanMessageListener directly since it causes circular dependency between plugin-amazonq and plugin-core
        // Workaround: send an action performed event to notify Q of scan result
        val dataContext = SimpleDataContext.builder()
            .add(scanResultsKey, codeScanResponse)
            .add(CommonDataKeys.PROJECT, project)
            .add(scanScopeKey, scope)
            .build()
        val actionEvent = AnActionEvent.createFromDataContext("", null, dataContext)
        ActionManager.getInstance().getAction("aws.amazonq.codeScanComplete").actionPerformed(actionEvent)
    }

    @TestOnly
    suspend fun testRenderResponseOnUIThread(issues: List<CodeWhispererCodeScanIssue>, scannedFiles: List<VirtualFile>) {
        assert(ApplicationManager.getApplication().isUnitTestMode)
        renderResponseOnUIThread(issues, scannedFiles, CodeWhispererConstants.CodeAnalysisScope.PROJECT)
    }

    fun isInsideWorkTree(): Boolean {
        val projectDir = project.guessProjectDir() ?: run {
            LOG.error { "Failed to guess project directory" }
            return false
        }
        return isInsideWorkTree(projectDir)
    }

    override fun dispose() {
    }

    companion object {
        fun getInstance(project: Project): CodeWhispererCodeScanManager = project.service()
    }
}

/**
 * Wrapper Data class representing a CodeWhisperer code scan issue.
 * @param title is shown in the code scan tree in the `CodeWhisperer Security Issues` tab.
 * @param description is shown in the tooltip of the scan node and also shown when the mouse
 * is hovered over the highlighted text in the editor.
 */
data class CodeWhispererCodeScanIssue(
    val project: Project,
    val file: VirtualFile,
    var startLine: Int,
    val startCol: Int,
    var endLine: Int,
    val endCol: Int,
    val title: @InspectionMessage String,
    val description: Description,
    val detectorId: String,
    val detectorName: String,
    val findingId: String,
    val ruleId: String?,
    val relatedVulnerabilities: List<String>,
    val severity: String,
    val recommendation: Recommendation,
    var suggestedFixes: List<SuggestedFix>,
    val codeSnippet: List<CodeLine>,
    val issueSeverity: HighlightDisplayLevel = HighlightDisplayLevel.WARNING,
    val isInvalid: Boolean = false,
    var rangeHighlighter: RangeHighlighterEx? = null,
    var isVisible: Boolean = true,
    val autoDetected: Boolean = false,
    val scanJobId: String,
) {
    override fun toString(): String = title

    val document = runReadAction {
        FileDocumentManager.getInstance().getDocument(file)
            ?: cannotFindFile("Unable to find file", file.path)
    }

    /**
     * Immutable value of the textRange at the time the issue was constructed.
     */
    val textRange = toTextRange()

    val codeText = runReadAction {
        if (textRange == null) return@runReadAction ""
        document.getText(textRange)
    }

    fun displayTextRange() = "[$startLine:$startCol-$endLine:$endCol]"

    /**
     * Adds a range highlighter for the corresponding code scan issue with the given markup model.
     * Note that the default markup model which is fetched from [DocumentMarkupModel] can be null.
     * Must be run in [runInEdt].
     */
    fun addRangeHighlighter(
        markupModel: MarkupModel? =
            DocumentMarkupModel.forDocument(document, project, false),
    ): RangeHighlighterEx? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            LOG.warn(RuntimeException()) { "Attempted to call addRangeHighlighter on EDT" }
            return null
        }

        return markupModel?.let {
            textRange ?: return null
            it.addRangeHighlighter(
                textRange.startOffset,
                textRange.endOffset,
                HighlighterLayer.LAST + 1,
                ISSUE_HIGHLIGHT_TEXT_ATTRIBUTES,
                HighlighterTargetArea.EXACT_RANGE
            ) as RangeHighlighterEx
        }
    }

    private fun toTextRange(): TextRange? {
        if (startLine < 1 || endLine > document.lineCount) return null
        val startOffset = document.getLineStartOffset(startLine - 1) + startCol - 1
        val endOffset = document.getLineStartOffset(endLine - 1) + endCol - 1
        if (startOffset < 0 || endOffset > document.textLength || startOffset > endOffset) return null
        return TextRange.create(startOffset, endOffset)
    }
}

private fun offsetSuggestedFix(suggestedFix: SuggestedFix, lines: Int): SuggestedFix {
    val updatedCode = suggestedFix.code.replace(
        Regex("""(@@ -)(\d+)(,\d+ \+)(\d+)(,\d+ @@)""")
    ) { result ->
        val prefix = result.groupValues[1]
        val startLine = result.groupValues[2].toInt() + lines
        val middle = result.groupValues[3]
        val endLine = result.groupValues[4].toInt() + lines
        val suffix = result.groupValues[5]
        "$prefix$startLine$middle$endLine$suffix"
    }

    return suggestedFix.copy(code = updatedCode)
}
