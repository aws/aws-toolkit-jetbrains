// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.BrowserUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import software.amazon.awssdk.services.codewhispererruntime.model.DiagnosticSeverity
import software.amazon.awssdk.services.codewhispererruntime.model.IdeDiagnostic
import software.amazon.awssdk.services.codewhispererruntime.model.OptOutPreference
import software.amazon.awssdk.services.codewhispererruntime.model.Position
import software.amazon.awssdk.services.codewhispererruntime.model.Range
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import software.amazon.q.jetbrains.core.credentials.AwsBearerTokenConnection
import software.amazon.q.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.amazon.q.jetbrains.core.credentials.ReauthSource
import software.amazon.q.jetbrains.core.credentials.ToolkitConnection
import software.amazon.q.jetbrains.core.credentials.ToolkitConnectionManager
import software.amazon.q.jetbrains.core.credentials.maybeReauthProviderIfNeeded
import software.amazon.q.jetbrains.core.credentials.pinning.QConnection
import software.amazon.q.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.amazon.q.jetbrains.core.credentials.sono.isSono
import software.amazon.q.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.amazon.q.jetbrains.core.gettingstarted.editor.ActiveConnection
import software.amazon.q.jetbrains.core.gettingstarted.editor.ActiveConnectionType
import software.amazon.q.jetbrains.core.gettingstarted.editor.BearerTokenFeatureSet
import software.amazon.q.jetbrains.core.gettingstarted.editor.checkBearerConnectionValidity
import software.amazon.q.jetbrains.settings.AwsSettings
import software.amazon.q.jetbrains.utils.isQExpired
import software.amazon.q.jetbrains.utils.notifyError
import software.amazon.q.jetbrains.utils.notifyInfo
import software.amazon.q.jetbrains.utils.pluginAwareExecuteOnPooledThread
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.aws.textDocument.InlineCompletionItem
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.learn.LearnCodeWhispererManager.Companion.taskTypeToFilename
import software.aws.toolkits.jetbrains.services.codewhisperer.model.Chunk
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.isTelemetryEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CrossFile.NUMBER_OF_CHUNK_TO_FETCH
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CrossFile.NUMBER_OF_LINE_IN_CHUNK
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererGettingStartedTask
import software.aws.toolkits.telemetry.CredentialSourceId
import java.nio.file.Path
import java.nio.file.Paths

// Controls the condition to send telemetry event to CodeWhisperer service, currently:
// 1. It will be sent for Builder ID users, only if they have optin telemetry sharing.
// 2. It will be sent for IdC users, regardless of telemetry optout status.
fun runIfIdcConnectionOrTelemetryEnabled(project: Project, callback: (connection: ToolkitConnection) -> Unit) =
    ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())?.let {
        runIfIdcConnectionOrTelemetryEnabled(it, callback)
    }

fun runIfIdcConnectionOrTelemetryEnabled(connection: ToolkitConnection, callback: (connection: ToolkitConnection) -> Unit) {
    if (connection.isSono() && !isTelemetryEnabled()) return
    callback(connection)
}

fun VirtualFile.content(): String = VfsUtil.loadText(this)

// we call it a chunk every 10 lines of code
// [[L1, L2, ...L10], [L11, L12, ...L20]...]
// use VirtualFile.toCodeChunk instead
suspend fun String.toCodeChunk(path: String): List<Chunk> {
    val chunks = this.trimEnd()

    var chunksOfStringsPreprocessed = chunks
        .split("\n")
        .chunked(10)
        .map { chunkContent ->
            yield()
            chunkContent.joinToString(separator = "\n").trimEnd()
        }

    // special process for edge case: first since first chunk is never referenced by other chunk, we define first 3 lines of its content referencing the first
    chunksOfStringsPreprocessed = listOf(
        chunksOfStringsPreprocessed
            .first()
            .split("\n")
            .take(3)
            .joinToString(separator = "\n").trimEnd()
    ) + chunksOfStringsPreprocessed

    return chunksOfStringsPreprocessed.mapIndexed { index, chunkContent ->
        yield()
        val nextChunkContent = if (index == chunksOfStringsPreprocessed.size - 1) {
            chunkContent
        } else {
            chunksOfStringsPreprocessed[index + 1]
        }
        Chunk(
            content = chunkContent,
            path = path,
            nextChunk = nextChunkContent
        )
    }
}

fun truncateLineByLine(input: String, l: Int): String {
    val maxLength = if (l > 0) l else -1 * l
    if (input.isEmpty()) {
        return ""
    }
    val shouldAddNewLineBack = input.last() == '\n'
    var lines = input.trim().split("\n")
    var curLen = input.length
    while (curLen > maxLength) {
        val last = lines.last()
        lines = lines.dropLast(1)
        curLen -= last.length + 1
    }

    val r = lines.joinToString("\n")
    return if (shouldAddNewLineBack) {
        r + "\n"
    } else {
        r
    }
}

fun getAuthType(project: Project): CredentialSourceId? {
    val connection = checkBearerConnectionValidity(project, BearerTokenFeatureSet.Q)
    var authType: CredentialSourceId? = null
    if (connection.connectionType == ActiveConnectionType.IAM_IDC && connection is ActiveConnection.ValidBearer) {
        authType = CredentialSourceId.IamIdentityCenter
    } else if (connection.connectionType == ActiveConnectionType.BUILDER_ID && connection is ActiveConnection.ValidBearer) {
        authType = CredentialSourceId.AwsId
    }
    return authType
}

// we refer 10 lines of code as "Code Chunk"
// [[L1, L2, ...L10], [L11, L12, ...L20]...]
// use VirtualFile.toCodeChunk
// TODO: path as param is weird
fun VirtualFile.toCodeChunk(path: String): Sequence<Chunk> = sequence {
    var prevChunk: String? = null
    inputStream.bufferedReader(Charsets.UTF_8).useLines {
        val iter = it.chunked(NUMBER_OF_LINE_IN_CHUNK).iterator()
        while (iter.hasNext()) {
            val currentChunk = iter.next().joinToString("\n").trimEnd()

            // chunk[0]
            if (prevChunk == null) {
                val first3Lines = currentChunk.split("\n").take(NUMBER_OF_CHUNK_TO_FETCH).joinToString("\n").trimEnd()
                yield(Chunk(content = first3Lines, path = path, nextChunk = currentChunk))
            } else {
                // chunk[1]...chunk[n-1]
                prevChunk?.let { chunk ->
                    yield(Chunk(content = chunk, path = path, nextChunk = currentChunk))
                }
            }

            prevChunk = currentChunk
        }

        prevChunk?.let { lastChunk ->
            // chunk[n]
            yield(Chunk(content = lastChunk, path = path, nextChunk = lastChunk))
        }
    }
}

fun VirtualFile.isWithin(ancestor: VirtualFile): Boolean = VfsUtilCore.isAncestor(ancestor, this, false)

object CodeWhispererUtil {
    fun getCompletionType(completion: InlineCompletionItem): CodewhispererCompletionType {
        val content = completion.insertText
        val nonBlankLines = content.split("\n").count { it.isNotBlank() }

        return when {
            content.isEmpty() -> CodewhispererCompletionType.Line
            nonBlankLines > 1 -> CodewhispererCompletionType.Block
            else -> CodewhispererCompletionType.Line
        }
    }

    fun notifyErrorCodeWhispererUsageLimit(project: Project? = null, isCodeScan: Boolean = false) {
        notifyError(
            "",
            if (!isCodeScan) {
                message("codewhisperer.notification.usage_limit.codesuggestion.warn.content")
            } else {
                message("codewhisperer.notification.usage_limit.codescan.warn.content")
            },
            project,
        )
    }

    // This will be called only when there's a CW connection, but it has expired(either accessToken or refreshToken)
    // 1. If connection is expired, try to refresh
    // 2. If not able to refresh, requesting re-login by showing a notification
    // 3. The notification will be shown
    //   3.1 At most once per IDE restarts.
    //   3.2 At most once after IDE restarts,
    //   for example, when user performs security scan or fetch code completion for the first time
    // Return true if need to re-auth, false otherwise
    fun promptReAuth(project: Project, isPluginStarting: Boolean = false): Boolean {
        if (!isQExpired(project)) return false
        val tokenProvider = tokenProvider(project) ?: return false
        return try {
            maybeReauthProviderIfNeeded(project, ReauthSource.CODEWHISPERER, tokenProvider) {
                runInEdt {
                    if (!CodeWhispererService.hasReAuthPromptBeenShown()) {
                        notifyConnectionExpiredRequestReauth(project)
                    }
                    if (!isPluginStarting) {
                        CodeWhispererService.markReAuthPromptShown()
                    }
                    if (!tokenConnection(project).isSono()) {
                        notifySessionConfiguration(project)
                    }
                }
            }
        } catch (e: Exception) {
            getLogger<CodeWhispererService>().warn(e) { "prompt reauth failed with unexpected error" }
            true
        }
    }

    private fun notifyConnectionExpiredRequestReauth(project: Project) {
        if (CodeWhispererExplorerActionManager.getInstance().getConnectionExpiredDoNotShowAgain()) {
            return
        }
        notifyError(
            message("toolkit.sso_expire.dialog.title"),
            message("toolkit.sso_expire.dialog_message"),
            project,
            listOf(
                NotificationAction.createSimpleExpiring(message("toolkit.sso_expire.dialog.yes_button")) {
                    reconnectCodeWhisperer(project)
                },
                NotificationAction.createSimpleExpiring(message("toolkit.sso_expire.dialog.no_button")) {
                    CodeWhispererExplorerActionManager.getInstance().setConnectionExpiredDoNotShowAgain(true)
                }
            )
        )
    }

    private fun notifySessionConfiguration(project: Project) {
        if (CodeWhispererExplorerActionManager.getInstance().getSessionConfigurationMessageShown()) {
            return
        }
        val learnMoreLink = "https://docs.aws.amazon.com/singlesignon/latest/userguide/configure-user-session.html#90-day-extended-session-duration"
        notifyInfo(
            message("q.session_configuration"),
            message("q.session_configuration.description"),
            project,
            listOf(
                NotificationAction.createSimple(message("q.learn.more")) {
                    BrowserUtil.browse(learnMoreLink)
                }
            )
        )
        CodeWhispererExplorerActionManager.getInstance().setSessionConfigurationMessageShown(true)
    }

    fun getConnectionStartUrl(connection: ToolkitConnection?): String? {
        connection ?: return null
        if (connection !is ManagedBearerSsoConnection) return null
        return connection.startUrl
    }

    fun getCodeWhispererStartUrl(project: Project): String? {
        val connection = ToolkitConnectionManager.getInstance(
            project
        ).activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection?
        return connection?.startUrl
    }

    private fun tokenConnection(project: Project) = (
        ToolkitConnectionManager
            .getInstance(project)
            .activeConnectionForFeature(QConnection.getInstance()) as? AwsBearerTokenConnection
        )

    private fun tokenProvider(project: Project) =
        tokenConnection(project)
            ?.getConnectionSettings()
            ?.tokenProvider
            ?.delegate as? BearerTokenProvider

    fun reconnectCodeWhisperer(project: Project) {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())
        if (connection !is ManagedBearerSsoConnection) return
        pluginAwareExecuteOnPooledThread {
            reauthConnectionIfNeeded(project, connection, isReAuth = true, reauthSource = ReauthSource.CODEWHISPERER)
        }
    }

    // We want to know if a specific trigger happens in the Getting Started page examples files.
    // We use the current file name to know this info. If file name doesn't match any of the below, we will assume
    // that it's coming from a normal file and return null.
    fun getGettingStartedTaskType(editor: Editor): CodewhispererGettingStartedTask? {
        if (ApplicationManager.getApplication().isUnitTestMode) return null
        val filename = (editor as EditorImpl).virtualFile?.name ?: return null
        return taskTypeToFilename.filter { filename.startsWith(it.value) }.keys.firstOrNull()
    }

    fun getTelemetryOptOutPreference() =
        if (AwsSettings.getInstance().isTelemetryEnabled) {
            OptOutPreference.OPTIN
        } else {
            OptOutPreference.OPTOUT
        }

    fun <T> debounce(
        waitMs: Long = 300L,
        coroutineScope: CoroutineScope,
        destinationFunction: (T) -> Unit,
    ): (T) -> Unit {
        var debounceJob: Job? = null
        return { param: T ->
            debounceJob?.cancel()
            debounceJob = coroutineScope.launch {
                delay(waitMs)
                destinationFunction(param)
            }
        }
    }

    fun setIntelliSensePopupAlpha(editor: Editor, alpha: Float) {
        ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)?.let {
            WindowManager.getInstance().setAlphaModeRatio(it, alpha)
        }
    }

    fun getNormalizedRelativePath(projectName: String, relativePath: Path): String =
        Paths.get(projectName).resolve(relativePath).normalize().toString()
}

enum class CaretMovement {
    NO_CHANGE, MOVE_FORWARD, MOVE_BACKWARD
}

val diagnosticPatterns = mapOf(
    "TYPE_ERROR" to listOf("type", "cast"),
    "SYNTAX_ERROR" to listOf("expected", "indent", "syntax"),
    "REFERENCE_ERROR" to listOf("undefined", "not defined", "undeclared", "reference", "symbol"),
    "BEST_PRACTICE" to listOf("deprecated", "unused", "uninitialized", "not initialized"),
    "SECURITY" to listOf("security", "vulnerability")
)

fun getDiagnosticsType(message: String): String {
    val lowercaseMessage = message.lowercase()
    return diagnosticPatterns
        .entries
        .firstOrNull { (_, keywords) ->
            keywords.any { lowercaseMessage.contains(it) }
        }
        ?.key ?: "OTHER"
}

fun convertSeverity(severity: HighlightSeverity): DiagnosticSeverity = when {
    severity == HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
    severity == HighlightSeverity.WARNING ||
        severity == HighlightSeverity.WEAK_WARNING -> DiagnosticSeverity.WARNING
    severity == HighlightSeverity.INFORMATION -> DiagnosticSeverity.INFORMATION
    severity == HighlightSeverity.TEXT_ATTRIBUTES -> DiagnosticSeverity.HINT
    severity == HighlightSeverity.INFO -> DiagnosticSeverity.INFORMATION
    // For severities that might indicate performance issues
    severity.toString().contains("PERFORMANCE", ignoreCase = true) -> DiagnosticSeverity.WARNING
    // For deprecation warnings
    severity.toString().contains("DEPRECATED", ignoreCase = true) -> DiagnosticSeverity.WARNING
    // Default case
    else -> DiagnosticSeverity.INFORMATION
}

fun getDocumentDiagnostics(document: Document, project: Project): List<IdeDiagnostic> = runCatching {
    DocumentMarkupModel.forDocument(document, project, true)
        .allHighlighters
        .mapNotNull { it.errorStripeTooltip as? HighlightInfo }
        .filter { !it.description.isNullOrEmpty() }
        .map { info ->
            val startLine = document.getLineNumber(info.startOffset)
            val endLine = document.getLineNumber(info.endOffset)

            IdeDiagnostic.builder()
                .ideDiagnosticType(getDiagnosticsType(info.description))
                .severity(convertSeverity(info.severity))
                .source(info.inspectionToolId)
                .range(
                    Range.builder()
                        .start(
                            Position.builder()
                                .line(startLine)
                                .character(document.getLineStartOffset(startLine))
                                .build()
                        )
                        .end(
                            Position.builder()
                                .line(endLine)
                                .character(document.getLineStartOffset(endLine))
                                .build()
                        )
                        .build()
                )
                .build()
        }
}.getOrElse { e ->
    getLogger<CodeWhispererUtil>().warn { "Failed to get document diagnostics ${e.message}" }
    emptyList()
}

data class DiagnosticDifferences(
    val added: List<IdeDiagnostic>,
    val removed: List<IdeDiagnostic>,
)

fun serializeDiagnostics(diagnostic: IdeDiagnostic): String = "${diagnostic.source()}-${diagnostic.severity()}-${diagnostic.ideDiagnosticType()}"

fun getDiagnosticDifferences(oldDiagnostic: List<IdeDiagnostic>, newDiagnostic: List<IdeDiagnostic>): DiagnosticDifferences {
    val oldSet = oldDiagnostic.map { i -> serializeDiagnostics(i) }.toSet()
    val newSet = newDiagnostic.map { i -> serializeDiagnostics(i) }.toSet()
    val added = newDiagnostic.filter { i -> !oldSet.contains(serializeDiagnostics(i)) }.distinctBy { serializeDiagnostics(it) }
    val removed = oldDiagnostic.filter { i -> !newSet.contains(serializeDiagnostics(i)) }.distinctBy { serializeDiagnostics(it) }
    return DiagnosticDifferences(added, removed)
}
