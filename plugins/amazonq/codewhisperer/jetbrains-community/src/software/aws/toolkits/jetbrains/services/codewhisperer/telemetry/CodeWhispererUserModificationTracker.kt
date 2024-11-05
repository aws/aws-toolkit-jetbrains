// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import info.debatty.java.stringsimilarity.Levenshtein
import org.assertj.core.util.VisibleForTesting
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererLanguageManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getUnmodifiedAcceptedCharsCount
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.InsertedCodeModificationEntry
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererRuntime
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

data class AcceptedSuggestionEntry(
    override val time: Instant,
    val vFile: VirtualFile?,
    val range: RangeMarker,
    val suggestion: String,
    val sessionId: String,
    val requestId: String,
    val index: Int,
    val triggerType: CodewhispererTriggerType,
    val completionType: CodewhispererCompletionType,
    val codewhispererLanguage: CodeWhispererProgrammingLanguage,
    val codewhispererRuntime: CodewhispererRuntime?,
    val codewhispererRuntimeSource: String?,
    val connection: ToolkitConnection?,
) : UserModificationTrackingEntry

data class CodeInsertionDiff(
    val original: String,
    val modified: String,
    val diff: Double,
)

fun CodeInsertionDiff?.percentage(): Double = when {
    this == null -> 1.0

    // TODO: should revisit this case
    original.isEmpty() || modified.isEmpty() -> 1.0

    else -> min(1.0, (diff / original.length))
}

@Service(Service.Level.PROJECT)
class CodeWhispererUserModificationTracker(private val project: Project) : Disposable {
    private val acceptedSuggestions = LinkedBlockingDeque<UserModificationTrackingEntry>(DEFAULT_MAX_QUEUE_SIZE)
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val isShuttingDown = AtomicBoolean(false)

    init {
        scheduleCodeWhispererTracker()
    }

    private fun scheduleCodeWhispererTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, DEFAULT_CHECK_INTERVAL.toMillis())
        }
    }

    private fun isTelemetryEnabled(): Boolean = AwsSettings.getInstance().isTelemetryEnabled

    fun enqueue(event: UserModificationTrackingEntry) {
        if (!isTelemetryEnabled()) {
            return
        }

        acceptedSuggestions.add(event)
        LOG.debug { "Enqueue Accepted Suggestion on line $event.lineNumber in $event.filePath" }
    }

    private fun flush() {
        try {
            if (!isTelemetryEnabled()) {
                acceptedSuggestions.clear()
                return
            }

            val copyList = LinkedBlockingDeque<UserModificationTrackingEntry>()

            val currentTime = Instant.now()
            for (acceptedSuggestion in acceptedSuggestions) {
                if (Duration.between(acceptedSuggestion.time, currentTime).seconds > DEFAULT_MODIFICATION_INTERVAL_IN_SECONDS) {
                    LOG.debug { "Passed $DEFAULT_MODIFICATION_INTERVAL_IN_SECONDS for $acceptedSuggestion" }
                    when (acceptedSuggestion) {
                        is AcceptedSuggestionEntry -> emitTelemetryOnSuggestion(acceptedSuggestion)
                        is InsertedCodeModificationEntry -> emitTelemetryOnChatCodeInsert(acceptedSuggestion)
                        else -> {}
                    }
                } else {
                    copyList.add(acceptedSuggestion)
                }
            }

            acceptedSuggestions.clear()
            acceptedSuggestions.addAll(copyList)
        } finally {
            scheduleCodeWhispererTracker()
        }
    }

    private fun emitTelemetryOnChatCodeInsert(insertedCode: InsertedCodeModificationEntry) {
        try {
            val file = insertedCode.vFile
            if (file == null || (!file.isValid)) throw Exception("Record OnChatCodeInsert - invalid file")

            val document = runReadAction {
                FileDocumentManager.getInstance().getDocument(file)
            }
            val currentString = document?.getText(
                TextRange(insertedCode.range.startOffset, insertedCode.range.endOffset)
            )
            val modificationPercentage = checkDiff(currentString?.trim(), insertedCode.originalString.trim())
            sendModificationWithChatTelemetry(insertedCode, modificationPercentage)
        } catch (e: Exception) {
            sendModificationWithChatTelemetry(insertedCode, null)
        }
    }

    private fun emitTelemetryOnSuggestion(acceptedSuggestion: AcceptedSuggestionEntry) {
        val file = acceptedSuggestion.vFile

        if (file == null || (!file.isValid)) {
            sendModificationTelemetry(acceptedSuggestion, null)
            sendUserModificationTelemetryToServiceAPI(acceptedSuggestion)
        } else {
            // Will remove this later when we truly don't need toolkit user modification telemetry anymore
            val document = runReadAction {
                FileDocumentManager.getInstance().getDocument(file)
            }
            val currentString = document?.getText(
                TextRange(acceptedSuggestion.range.startOffset, acceptedSuggestion.range.endOffset)
            )
            val modificationPercentage = checkDiff(currentString?.trim(), acceptedSuggestion.suggestion.trim())
            sendModificationTelemetry(acceptedSuggestion, modificationPercentage)
            sendUserModificationTelemetryToServiceAPI(acceptedSuggestion)
        }
    }

    /**
     * Use Levenshtein distance to check how
     * Levenshtein distance was preferred over Jaro–Winkler distance for simplicity
     */
    @VisibleForTesting
    internal fun checkDiff(currString: String?, acceptedString: String?): CodeInsertionDiff? {
        if (currString == null || acceptedString == null || acceptedString.isEmpty() || currString.isEmpty()) {
            return null
        }
        val diff = checker.distance(currString, acceptedString)
        return CodeInsertionDiff(
            original = acceptedString,
            modified = currString,
            diff = diff
        )
    }

    private fun sendModificationTelemetry(suggestion: AcceptedSuggestionEntry, diff: CodeInsertionDiff?) {
        LOG.debug { "Sending user modification telemetry. Request Id: ${suggestion.requestId}" }
        val startUrl = getConnectionStartUrl(suggestion.connection)
        CodewhispererTelemetry.userModification(
            project = project,
            codewhispererCompletionType = suggestion.completionType,
            codewhispererLanguage = suggestion.codewhispererLanguage.toTelemetryType(),
            codewhispererModificationPercentage = diff.percentage(),
            codewhispererRequestId = suggestion.requestId,
            codewhispererRuntime = suggestion.codewhispererRuntime,
            codewhispererRuntimeSource = suggestion.codewhispererRuntimeSource,
            codewhispererSessionId = suggestion.sessionId,
            codewhispererSuggestionIndex = suggestion.index.toLong(),
            codewhispererTriggerType = suggestion.triggerType,
            credentialStartUrl = startUrl,
            codewhispererCharactersModified = diff?.modified?.length?.toLong() ?: 0,
            codewhispererCharactersAccepted = diff?.original?.length?.toLong() ?: 0
        )
    }

    private fun sendModificationWithChatTelemetry(insertedCode: InsertedCodeModificationEntry, diff: CodeInsertionDiff?) {
        AmazonqTelemetry.modifyCode(
            cwsprChatConversationId = insertedCode.conversationId,
            cwsprChatMessageId = insertedCode.messageId,
            cwsprChatModificationPercentage = diff.percentage(),
            credentialStartUrl = getStartUrl(project)
        )
        val lang = insertedCode.vFile?.programmingLanguage() ?: CodeWhispererUnknownLanguage.INSTANCE

        CodeWhispererClientAdaptor.getInstance(
            project
        ).sendChatUserModificationTelemetry(
            insertedCode.conversationId,
            insertedCode.messageId,
            lang,
            diff.percentage(),
            CodeWhispererSettings.getInstance().isProjectContextEnabled(),
            CodeWhispererModelConfigurator.getInstance().activeCustomization(project)
        ).also {
            LOG.debug { "Successfully sendTelemetryEvent for ChatModificationWithChat with requestId: ${it.responseMetadata().requestId()}" }
        }
    }

    private fun sendUserModificationTelemetryToServiceAPI(
        suggestion: AcceptedSuggestionEntry,
    ) {
        runIfIdcConnectionOrTelemetryEnabled(project) {
            try {
                // should be impossible from the caller logic
                if (suggestion.vFile == null) return@runIfIdcConnectionOrTelemetryEnabled
                val document = runReadAction {
                    FileDocumentManager.getInstance().getDocument(suggestion.vFile)
                }
                val modifiedSuggestion = document?.getText(
                    TextRange(suggestion.range.startOffset, suggestion.range.endOffset)
                ).orEmpty()
                val response = CodeWhispererClientAdaptor.getInstance(project)
                    .sendUserModificationTelemetry(
                        suggestion.sessionId,
                        suggestion.requestId,
                        CodeWhispererLanguageManager.getInstance().getLanguage(suggestion.vFile),
                        CodeWhispererModelConfigurator.getInstance().activeCustomization(project)?.arn.orEmpty(),
                        suggestion.suggestion.length,
                        getUnmodifiedAcceptedCharsCount(suggestion.suggestion, modifiedSuggestion)
                    )
                LOG.debug { "Successfully sent user modification telemetry. RequestId: ${response.responseMetadata().requestId()}" }
            } catch (e: Exception) {
                val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                LOG.debug {
                    "Failed to send user modification telemetry. RequestId: $requestId, ErrorMessage: ${e.message}"
                }
            }
        }
    }

    companion object {
        private val DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(1)
        private const val DEFAULT_MAX_QUEUE_SIZE = 10000
        private const val DEFAULT_MODIFICATION_INTERVAL_IN_SECONDS = 300 // 5 minutes

        private val checker = Levenshtein()

        private val LOG = getLogger<CodeWhispererUserModificationTracker>()

        fun getInstance(project: Project) = project.service<CodeWhispererUserModificationTracker>()
    }

    override fun dispose() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }

        flush()
    }
}
