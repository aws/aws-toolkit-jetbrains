// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import software.amazon.awssdk.services.codewhispererruntime.model.Completion
import software.amazon.awssdk.services.codewhispererruntime.model.OptOutPreference
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ManagedBearerSsoConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.maybeReauthProviderIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.credentials.reauthConnectionIfNeeded
import software.aws.toolkits.jetbrains.core.credentials.sono.isSono
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.BearerTokenProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.learn.LearnCodeWhispererManager.Companion.taskTypeToFilename
import software.aws.toolkits.jetbrains.services.codewhisperer.model.Chunk
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererCodeCoverageTracker.Companion.levenshteinChecker
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.isTelemetryEnabled
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CrossFile.NUMBER_OF_CHUNK_TO_FETCH
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.CrossFile.NUMBER_OF_LINE_IN_CHUNK
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.utils.isQExpired
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.utils.pluginAwareExecuteOnPooledThread
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererGettingStartedTask

// Controls the condition to send telemetry event to CodeWhisperer service, currently:
// 1. It will be sent for Builder ID users, only if they have optin telemetry sharing.
// 2. It will be sent for IdC users, regardless of telemetry optout status.
fun runIfIdcConnectionOrTelemetryEnabled(project: Project, callback: (connection: ToolkitConnection) -> Unit) =
    ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())?.let {
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

object CodeWhispererUtil {
    fun getCompletionType(completion: Completion): CodewhispererCompletionType {
        val content = completion.content()
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
            maybeReauthProviderIfNeeded(project, tokenProvider) {
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
        ).activeConnectionForFeature(CodeWhispererConnection.getInstance()) as? AwsBearerTokenConnection?
        return connection?.startUrl
    }

    private fun tokenConnection(project: Project) = (
        ToolkitConnectionManager
            .getInstance(project)
            .activeConnectionForFeature(CodeWhispererConnection.getInstance()) as? AwsBearerTokenConnection
        )

    private fun tokenProvider(project: Project) =
        tokenConnection(project)
            ?.getConnectionSettings()
            ?.tokenProvider
            ?.delegate as? BearerTokenProvider

    fun reconnectCodeWhisperer(project: Project) {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
        if (connection !is ManagedBearerSsoConnection) return
        pluginAwareExecuteOnPooledThread {
            reauthConnectionIfNeeded(project, connection, isReAuth = true)
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

    // With edit distance, complicate usermodification can be considered as simple edit(add, delete, replace),
    // and thus the unmodified part of recommendation length can be deducted/approximated
    // ex. (modified > original): originalRecom: foo -> modifiedRecom: fobarbarbaro, distance = 9, delta = 12 - 9 = 3
    // ex. (modified == original): originalRecom: helloworld -> modifiedRecom: HelloWorld, distance = 2, delta = 10 - 2 = 8
    // ex. (modified < original): originalRecom: CodeWhisperer -> modifiedRecom: CODE, distance = 12, delta = 13 - 12 = 1
    fun getUnmodifiedAcceptedCharsCount(originalRecommendation: String, modifiedRecommendation: String): Int {
        val editDistance = getEditDistance(modifiedRecommendation, originalRecommendation).toInt()
        return maxOf(originalRecommendation.length, modifiedRecommendation.length) - editDistance
    }

    private fun getEditDistance(modifiedString: String, originalString: String): Double =
        levenshteinChecker.distance(modifiedString, originalString)

    fun setIntelliSensePopupAlpha(editor: Editor, alpha: Float) {
        ComponentUtil.getWindow(LookupManager.getActiveLookup(editor)?.component)?.let {
            WindowManager.getInstance().setAlphaModeRatio(it, alpha)
        }
    }
}

enum class CaretMovement {
    NO_CHANGE, MOVE_FORWARD, MOVE_BACKWARD
}
