// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codescan

import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.refactoring.suggested.range
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
import software.amazon.awssdk.services.codewhisperer.model.CodeWhispererException
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.ThrottlingException
import software.aws.toolkits.core.utils.WaiterTimeoutException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.coroutines.getCoroutineUiContext
import software.aws.toolkits.jetbrains.core.coroutines.projectCoroutineScope
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners.CodeWhispererCodeScanDocumentListener
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners.CodeWhispererCodeScanEditorMouseMotionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.listeners.CodeWhispererCodeScanFileListener
import software.aws.toolkits.jetbrains.services.codewhisperer.codescan.sessionconfig.CodeScanSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.editor.CodeWhispererEditorUtil.overlaps
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.isUserBuilderId
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.CodeScanTelemetryEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererTelemetryService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererColorUtil.INACTIVE_TEXT_COLOR
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.ISSUE_HIGHLIGHT_TEXT_ATTRIBUTES
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.jetbrains.utils.isRunningOnRemoteBackend
import software.aws.toolkits.jetbrains.utils.offsetSuggestedFix
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.Result
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.coroutines.CoroutineContext

class CodeWhispererCodeScanManager(val project: Project) {
    private val codeScanResultsPanel by lazy {
        CodeWhispererCodeScanResultsView(project)
    }
    private val codeScanIssuesContent by lazy {
        val contentManager = getProblemsWindow().contentManager
        contentManager.factory.createContent(
            codeScanResultsPanel,
            message("codewhisperer.codescan.scan_display"),
            false
        ).also {
            Disposer.register(contentManager, it)
            contentManager.addContentManagerListener(object : ContentManagerListener {
                override fun contentRemoved(event: ContentManagerEvent) {
                    if (event.content == it) reset()
                }
            })
        }
    }

    private val fileNodeLookup = mutableMapOf<VirtualFile, DefaultMutableTreeNode>()
    private val scanNodesLookup = mutableMapOf<VirtualFile, MutableList<DefaultMutableTreeNode>>()

    private val documentListener = CodeWhispererCodeScanDocumentListener(project)
    private val editorMouseListener = CodeWhispererCodeScanEditorMouseMotionListener(project)
    private val fileListener = CodeWhispererCodeScanFileListener(project)

    private val isProjectScanInProgress = AtomicBoolean(false)

    private val defaultScope = projectCoroutineScope(project)

    private lateinit var codeScanJob: Job
    private lateinit var debouncedCodeScanJob: Job

    /**
     * Returns true if the code scan is in progress.
     * This function will return true for a cancelled code scan job which is in cancellation state.
     */
    fun isProjectScanInProgress(): Boolean = isProjectScanInProgress.get()

    /**
     * Code scan job is active when the [Job] is started and is in active state.
     */
    fun isCodeScanJobActive(): Boolean = this::codeScanJob.isInitialized && codeScanJob.isActive && isProjectScanInProgress()

    fun getRunActionButtonIcon(): Icon = if (isProjectScanInProgress()) AllIcons.Process.Step_1 else AllIcons.Actions.Execute

    fun getActionButtonIconForExplorerNode(): Icon = if (isProjectScanInProgress()) AllIcons.Actions.Suspend else AllIcons.Actions.Execute

    fun getActionButtonText(): String = if (!isProjectScanInProgress()) {
        message(
            "codewhisperer.codescan.run_scan"
        )
    } else {
        message("codewhisperer.codescan.stop_scan")
    }

    /**
     * Triggers a code scan and displays results in the new tab in problems view panel.
     */
    fun runCodeScan(scope: CodeWhispererConstants.CodeAnalysisScope, isPluginStarting: Boolean = false) {
        if (!isQConnected(project)) return

        // Return if a scan is already in progress.
        if (isProjectScanInProgress() && scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) return

        val connectionExpired = if (isPluginStarting) {
            isQExpired(project)
        } else {
            promptReAuth(project)
        }
        if (connectionExpired) {
            isProjectScanInProgress.set(false)
            return
        }

        //  If scope is project
        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            // Prepare for a project code scan
            beforeCodeScan()
            // launch code scan coroutine
            codeScanJob = launchCodeScanCoroutine(CodeWhispererConstants.CodeAnalysisScope.PROJECT)
        } else {
            if (CodeWhispererExplorerActionManager.getInstance().isAutoEnabledForCodeScan() and !isUserBuilderId(project)) {
                // cancel if a file scan is running.
                if (!isProjectScanInProgress() && this::codeScanJob.isInitialized && codeScanJob.isActive) {
                    codeScanJob.cancel()
                }
                //  Add File Scan
                codeScanJob = launchCodeScanCoroutine(CodeWhispererConstants.CodeAnalysisScope.FILE)
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
        coroutineScope: CoroutineScope = defaultScope
    ) {
        if (this::debouncedCodeScanJob.isInitialized && debouncedCodeScanJob.isActive) {
            debouncedCodeScanJob.cancel()
        }
        debouncedCodeScanJob = coroutineScope.launch {
            delay(waitMs)
            runCodeScan(scope, isPluginStarting)
        }
    }

    fun stopCodeScan() {
        // Return if code scan job is not active.
        if (!codeScanJob.isActive) return
        if (isProjectScanInProgress() && confirmCancelCodeScan()) {
            LOG.info { "Security scan stopped by user..." }
            // Checking `codeScanJob.isActive` to ensure that the job is not already completed by the time user confirms.
            if (codeScanJob.isActive) {
                codeScanResultsPanel.setStoppingCodeScan()
                codeScanJob.cancel(CancellationException("User requested cancellation"))
            }
        }
    }

    private fun confirmCancelCodeScan(): Boolean = MessageDialogBuilder
        .okCancel(message("codewhisperer.codescan.stop_scan"), message("codewhisperer.codescan.stop_scan_confirm_message"))
        .yesText(message("codewhisperer.codescan.stop_scan_confirm_button"))
        .ask(project)

    private fun launchCodeScanCoroutine(scope: CodeWhispererConstants.CodeAnalysisScope) = projectCoroutineScope(project).launch {
        var codeScanStatus: Result = Result.Failed
        val startTime = Instant.now().toEpochMilli()
        var codeScanResponseContext = defaultCodeScanResponseContext()
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
        var codeScanJobId: String? = null
        var language: CodeWhispererProgrammingLanguage = CodeWhispererUnknownLanguage.INSTANCE
        try {
            val file =
                if (isRunningOnRemoteBackend()) {
                    FileEditorManager.getInstance(project).selectedEditorWithRemotes.firstOrNull()?.file
                } else {
                    FileEditorManager.getInstance(project).selectedEditor?.file
                }
            val codeScanSessionConfig = CodeScanSessionConfig.create(file, project, scope)
            val selectedFile = codeScanSessionConfig.getSelectedFile()
            language = selectedFile?.programmingLanguage() ?: CodeWhispererUnknownLanguage.INSTANCE
            if (scope == CodeWhispererConstants.CodeAnalysisScope.FILE &&
                (
                    selectedFile == null || !language.isAutoFileScanSupported() || !selectedFile.isFile ||
                        runReadAction { (codeScanSessionConfig.fileIndex.isInLibrarySource(selectedFile)) } ||
                        selectedFile.fileSystem.protocol == "remoteDeploymentFS"
                    )
            ) {
                LOG.debug { "Language is unknown or plaintext, skipping code scan." }
                codeScanStatus = Result.Cancelled
                return@launch
            } else {
                withTimeout(Duration.ofSeconds(codeScanSessionConfig.overallJobTimeoutInSeconds())) {
                    // 1. Generate truncation (zip files) based on the current editor.
                    val sessionContext = CodeScanSessionContext(project, codeScanSessionConfig, scope)
                    val session = CodeWhispererCodeScanSession(sessionContext)
                    val codeScanResponse = session.run()
                    language = codeScanSessionConfig.getProgrammingLanguage()
                    codeScanResponseContext = codeScanResponse.responseContext
                    codeScanJobId = codeScanResponseContext.codeScanJobId
                    when (codeScanResponse) {
                        is CodeScanResponse.Success -> {
                            val issues = codeScanResponse.issues
                            coroutineContext.ensureActive()
                            renderResponseOnUIThread(
                                issues,
                                codeScanResponse.responseContext.payloadContext.scannedFiles,
                                scope
                            )
                            codeScanStatus = Result.Succeeded
                        }

                        is CodeScanResponse.Failure -> {
                            if (codeScanResponse.failureReason !is TimeoutCancellationException && codeScanResponse.failureReason is CancellationException) {
                                codeScanStatus = Result.Cancelled
                            }
                            throw codeScanResponse.failureReason
                        }
                    }
                    LOG.info { "Security scan completed for jobID: $codeScanJobId." }
                }
            }
        } catch (e: Error) {
            if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
                isProjectScanInProgress.set(false)
            }
            val errorMessage = handleError(coroutineContext, e, scope)
            codeScanResponseContext = codeScanResponseContext.copy(reason = errorMessage)
        } catch (e: Exception) {
            if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
                isProjectScanInProgress.set(false)
            }
            val errorMessage = handleException(coroutineContext, e, scope)
            codeScanResponseContext = codeScanResponseContext.copy(reason = errorMessage)
        } finally {
            // After code scan
            afterCodeScan(scope)
            launch {
                val duration = (Instant.now().toEpochMilli() - startTime).toDouble()
                CodeWhispererTelemetryService.getInstance().sendSecurityScanEvent(
                    CodeScanTelemetryEvent(
                        codeScanResponseContext,
                        duration,
                        codeScanStatus,
                        codeScanResponseContext.payloadContext.srcPayloadSize.toDouble() ?: 0.0,
                        connection,
                        scope
                    )
                )
                sendCodeScanTelemetryToServiceAPI(project, language, codeScanJobId, scope)
            }
        }
    }

    fun handleError(coroutineContext: CoroutineContext, e: Error, scope: CodeWhispererConstants.CodeAnalysisScope): String {
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
            if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
                codeScanResultsPanel.showError(errorMessage)
            }
        }

        return errorMessage
    }

    private fun getCodeScanExceptionMessage(e: CodeWhispererCodeScanException): String? {
        val message = e.message
        return when {
            message.isNullOrBlank() -> null
            message == message("codewhisperer.codescan.invalid_source_zip_telemetry") -> {
                message("codewhisperer.codescan.run_scan_error")
            }
            else -> message
        }
    }

    private fun getCodeScanServerExceptionMessage(e: CodeWhispererCodeScanServerException): String? =
        e.message?.takeIf { it.startsWith("UploadArtifactToS3Exception:") }
            ?.let { message("codewhisperer.codescan.upload_to_s3_failed") }

    fun handleException(coroutineContext: CoroutineContext, e: Exception, scope: CodeWhispererConstants.CodeAnalysisScope): String {
        val errorMessage = when (e) {
            is CodeWhispererException -> e.awsErrorDetails().errorMessage() ?: message("codewhisperer.codescan.run_scan_error")
            is CodeWhispererCodeScanException -> getCodeScanExceptionMessage(e)
            is CodeWhispererCodeScanServerException -> getCodeScanServerExceptionMessage(e)
            is WaiterTimeoutException, is TimeoutCancellationException -> message("codewhisperer.codescan.scan_timed_out")
            is CancellationException -> message("codewhisperer.codescan.cancelled_by_user_exception")
            is IllegalStateException -> message("codewhisperer.codescan.cannot_read_file")
            else -> null
        } ?: message("codewhisperer.codescan.run_scan_error")

        val errorCode = (e as? CodeWhispererException)?.awsErrorDetails()?.errorCode()
        val requestId = if (e is CodeWhispererException) e.requestId() else null

        if (!coroutineContext.isActive) {
            codeScanResultsPanel.setDefaultUI()
        } else {
            if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
                codeScanResultsPanel.showError(errorMessage)
            }
        }

        if (
            e is ThrottlingException &&
            e.message == CodeWhispererConstants.THROTTLING_MESSAGE
        ) {
            CodeWhispererExplorerActionManager.getInstance().setSuspended(project)
        }

        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            LOG.error {
                "Failed to run security scan and display results. Caused by: $errorMessage, status code: $errorCode, " +
                    "exception: ${e::class.simpleName}, request ID: $requestId " +
                    "Jetbrains IDE: ${ApplicationInfo.getInstance().fullApplicationName}, " +
                    "IDE version: ${ApplicationInfo.getInstance().apiVersion}, " +
                    "stacktrace: ${e.stackTrace.contentDeepToString()}"
            }
        }

        val telemetryErrorMessage = when (e) {
            is CodeWhispererException -> e.awsErrorDetails().errorMessage() ?: message("codewhisperer.codescan.run_scan_error_telemetry")
            is CodeWhispererCodeScanException -> when (e.message) {
                message("codewhisperer.codescan.no_file_open") -> message("codewhisperer.codescan.no_file_open_telemetry")
                message("codewhisperer.codescan.unsupported_language_error") -> message("codewhisperer.codescan.unsupported_language_error_telemetry")
                message("codewhisperer.codescan.file_too_large") -> message("codewhisperer.codescan.file_too_large_telemetry")
                else -> e.message
            }
            is CodeWhispererCodeScanServerException -> e.message
            is WaiterTimeoutException, is TimeoutCancellationException -> message("codewhisperer.codescan.scan_timed_out")
            is CancellationException -> message("codewhisperer.codescan.cancelled_by_user_exception")
            else -> when {
                /**
                 * Error message has text with user details(like requestId) which is specific so sending a custom error message to calculate the occurence of this event.
                 */
                e.message?.startsWith("Too many requests, please wait before trying again.") == true ->
                    "Too many requests, please wait before trying again."
                else -> e.message
            }
        } ?: message("codewhisperer.codescan.run_scan_error_telemetry")

        return telemetryErrorMessage
    }

    /**
     * The initial landing UI for the code scan results view panel.
     * This method adds code content to the problems view if not already added.
     * When [setSelected] is true, code scan panel is set to be in focus.
     */
    fun addCodeScanUI(setSelected: Boolean = false) = runInEdt {
        reset()
        val problemsWindow = getProblemsWindow()
        if (!problemsWindow.contentManager.contents.contains(codeScanIssuesContent)) {
            problemsWindow.contentManager.addContent(codeScanIssuesContent)
        }
        codeScanIssuesContent.displayName = message("codewhisperer.codescan.scan_display")
        if (setSelected) {
            problemsWindow.contentManager.setSelectedContent(codeScanIssuesContent)
            problemsWindow.show()
        }
    }

    fun removeCodeScanUI() = runInEdt {
        val problemsWindow = getProblemsWindow()
        if (problemsWindow.contentManager.contents.contains(codeScanIssuesContent)) {
            problemsWindow.contentManager.removeContent(codeScanIssuesContent, false)
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
            }
        }
    }

    fun updateScanNodesForOffSet(file: VirtualFile, lineOffset: Int, editedTextRange: TextRange) {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        scanNodesLookup[file]?.forEach { node ->
            val issue = node.userObject as CodeWhispererCodeScanIssue
            if (document.getLineNumber(editedTextRange.startOffset) <= issue.startLine) {
                issue.startLine = issue.startLine + lineOffset
                issue.endLine = issue.endLine + lineOffset
                issue.suggestedFixes = issue.suggestedFixes.map { fix -> offsetSuggestedFix(fix, lineOffset) }
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

    private fun getProblemsWindow() = ProblemsView.getToolWindow(project)
        ?: error(message("codewhisperer.codescan.problems_window_not_found"))

    private fun reset() = runInEdt {
        // clear the codeScanTreeNodeRoot
        synchronized(codeScanTreeNodeRoot) {
            codeScanTreeNodeRoot.removeAllChildren()
        }
        // Remove previous document listeners before starting a new scan.
        fileNodeLookup.clear()
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
                codeScanIssuesContent
            )
        }
    }

    private fun beforeCodeScan() {
        isProjectScanInProgress.set(true)
        addCodeScanUI(setSelected = true)
        // Show in progress indicator
        codeScanResultsPanel.showInProgressIndicator()
        (FileDocumentManagerImpl.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
    }

    private fun afterCodeScan(scope: CodeWhispererConstants.CodeAnalysisScope) {
        if (scope == CodeWhispererConstants.CodeAnalysisScope.PROJECT) {
            isProjectScanInProgress.set(false)
        }
    }

    private fun sendCodeScanTelemetryToServiceAPI(
        project: Project,
        programmingLanguage: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope
    ) {
        runIfIdcConnectionOrTelemetryEnabled(project) {
            try {
                val response = CodeWhispererClientAdaptor.getInstance(project)
                    .sendCodeScanTelemetry(programmingLanguage, codeScanJobId, scope)
                LOG.debug { "Successfully sent code scan telemetry. RequestId: ${response.responseMetadata().requestId()} for ${scope.value} scan" }
            } catch (e: Exception) {
                val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                LOG.debug {
                    "Failed to send code scan telemetry. RequestId: $requestId, ErrorMessage: ${e.message}"
                }
            }
        }
    }

    private val codeScanTreeNodeRoot = DefaultMutableTreeNode("CodeWhisperer Code scan results")

    /**
     * Creates a CodeWhisperer code scan issues tree.
     * For each scan node:
     *   1. (Add file node if not already present and) add scan node to the file node.
     *   2. Update the lookups - [fileNodeLookup] for efficiently adding scan nodes and
     *   [scanNodesLookup] for receiving the editor events and updating the corresponding scan nodes.
     */
    private fun createCodeScanIssuesTree(codeScanIssues: List<CodeWhispererCodeScanIssue>): DefaultMutableTreeNode {
        LOG.debug { "Rendering response from the scan API" }

        synchronized(codeScanTreeNodeRoot) {
            codeScanTreeNodeRoot.removeAllChildren()
        }
        // clear file node lookup and scan node lookup
        synchronized(fileNodeLookup) {
            fileNodeLookup.clear()
        }
        synchronized(scanNodesLookup) {
            scanNodesLookup.clear()
        }

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

    /**
     * Updates a CodeWhisperer code scan issues tree for a file
     * For a given file, looks up its file node:
     *   1. Remove all its existing children scan nodes
     *   2. add the new issues as new scan nodes
     *   [scanNodesLookup] for receiving the editor events and updating the corresponding scan nodes.
     */
    fun updateFileIssues(file: VirtualFile, newIssues: List<CodeWhispererCodeScanIssue>): DefaultMutableTreeNode {
        val fileNode = synchronized(fileNodeLookup) {
            fileNodeLookup.getOrPut(file) {
                val node = DefaultMutableTreeNode(file)
                synchronized(codeScanTreeNodeRoot) {
                    codeScanTreeNodeRoot.add(node)
                }
                node
            }
        }
        // Remove the underline for old issues
        scanNodesLookup[file]?.forEach { node ->
            val issue = node.userObject as CodeWhispererCodeScanIssue
            issue.rangeHighlighter?.textAttributes = null
            issue.rangeHighlighter?.dispose()
        }
        // Remove the old scan nodes from the file node.
        synchronized(fileNode) {
            fileNode.removeAllChildren()
        }
        // Remove the entry for the file from the scan nodes lookup.
        synchronized(scanNodesLookup) {
            if (scanNodesLookup.containsKey(file)) {
                scanNodesLookup.remove(file)
            }
        }

        // Add new issues to the file node.
        newIssues.forEach { issue ->
            val scanNode = DefaultMutableTreeNode(issue)
            fileNode.add(scanNode)
            scanNodesLookup.getOrPut(issue.file) {
                mutableListOf()
            }.add(scanNode)
        }

        if (fileNode.childCount == 0) {
            fileNode.removeFromParent()
            fileNodeLookup.remove(file)
        }

        return codeScanTreeNodeRoot
    }

    fun removeIssueByFindingId(file: VirtualFile, findingId: String) {
        scanNodesLookup[file]?.forEach { node ->
            val issue = node.userObject as CodeWhispererCodeScanIssue
            if (issue.findingId == findingId) {
                issue.rangeHighlighter?.textAttributes = null
                issue.rangeHighlighter?.dispose()
                node.removeFromParent()
            }
        }
        fileNodeLookup[file]?.let {
            if (it.childCount == 0) {
                it.removeFromParent()
                fileNodeLookup.remove(file)
            }
        }
        val codeScanTreeModel = CodeWhispererCodeScanTreeModel(codeScanTreeNodeRoot)
        val totalIssuesCount = codeScanTreeModel.getTotalIssuesCount()
        if (totalIssuesCount > 0) {
            codeScanIssuesContent.displayName =
                message("codewhisperer.codescan.scan_display_with_issues", totalIssuesCount, INACTIVE_TEXT_COLOR)
        } else {
            codeScanIssuesContent.displayName = message("codewhisperer.codescan.scan_display")
        }
        codeScanResultsPanel.refreshUIWithUpdatedModel(codeScanTreeModel)
    }

    suspend fun renderResponseOnUIThread(
        issues: List<CodeWhispererCodeScanIssue>,
        scannedFiles: List<VirtualFile>,
        scope: CodeWhispererConstants.CodeAnalysisScope
    ) {
        withContext(getCoroutineUiContext()) {
            var root: DefaultMutableTreeNode? = null
            when (scope) {
                CodeWhispererConstants.CodeAnalysisScope.FILE -> {
                    val file = scannedFiles.first()
                    root = updateFileIssues(file, issues)
                } else -> {
                    root = createCodeScanIssuesTree(issues)
                }
            }
            val codeScanTreeModel = CodeWhispererCodeScanTreeModel(root)
            val totalIssuesCount = codeScanTreeModel.getTotalIssuesCount()
            if (totalIssuesCount > 0) {
                codeScanIssuesContent.displayName =
                    message("codewhisperer.codescan.scan_display_with_issues", totalIssuesCount, INACTIVE_TEXT_COLOR)
            }
            codeScanResultsPanel.updateAndDisplayScanResults(codeScanTreeModel, scannedFiles, scope)
        }
    }

    @TestOnly
    suspend fun testRenderResponseOnUIThread(issues: List<CodeWhispererCodeScanIssue>, scannedFiles: List<VirtualFile>) {
        assert(ApplicationManager.getApplication().isUnitTestMode)
        renderResponseOnUIThread(issues, scannedFiles, CodeWhispererConstants.CodeAnalysisScope.PROJECT)
    }

    companion object {
        private val LOG = getLogger<CodeWhispererCodeScanManager>()
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
    val issueSeverity: HighlightDisplayLevel = HighlightDisplayLevel.WARNING,
    val isInvalid: Boolean = false,
    var rangeHighlighter: RangeHighlighterEx? = null
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
            DocumentMarkupModel.forDocument(document, project, false)
    ): RangeHighlighterEx? {
        if (!ApplicationManager.getApplication().isDispatchThread) return null
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
