// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import info.debatty.java.stringsimilarity.Levenshtein
import org.assertj.core.util.VisibleForTesting
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererLanguageManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.CodeWhispererCodeCoverageTracker.Companion
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getConnectionStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getUnmodifiedAcceptedCharsCount
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.InsertedCodeModificationEntry
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.AwsSettings
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean


@Service(Service.Level.PROJECT)
class UserWrittenCodeTracker(private val project: Project) : Disposable {
    private val userWrittenCodePerLanguage = mutableMapOf<String, Int>()
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val isShuttingDown = AtomicBoolean(false)

    init {
        scheduleTracker()
    }

    private fun scheduleTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, DEFAULT_CHECK_INTERVAL.toMillis())
        }
    }

    private fun isTelemetryEnabled(): Boolean = AwsSettings.getInstance().isTelemetryEnabled


    private fun flush() {
        try {
            if (!isTelemetryEnabled()) {
                return
            }
            for ((language, userWrittenCode) in userWrittenCodePerLanguage) {

            }

        } finally {
            scheduleTracker()
        }
    }

    internal fun documentChanged(event: DocumentEvent) {
        // When open a file for the first time, IDE will also emit DocumentEvent for loading with `isWholeTextReplaced = true`
        // Added this condition to filter out those events
        if (event.isWholeTextReplaced) {
            LOG.debug { "event with isWholeTextReplaced flag: $event" }
            if (event.oldTimeStamp == 0L) return
        }
        // only count total tokens when it is a user keystroke input
        // do not count doc changes from copy & paste of >=50 characters
        // do not count other changes from formatter, git command, etc
        // edge case: event can be from user hit enter with indentation where change is \n\t\t, count as 1 char increase in total chars
        // when event is auto closing [{(', there will be 2 separated events, both count as 1 char increase in total chars
        val text = event.newFragment.toString()
        if (event.newLength < DOCUMENT_COPY_THRESSHOLD && text.trim().isNotEmpty()) {
            // count doc changes from <50 multi character input as total user written code
            // ignore all white space changes, this usually comes from IntelliJ formatting
            val language = ""
            val newUserWrittenCode = userWrittenCodePerLanguage.getOrDefault(language, 0) + event.newLength
            userWrittenCodePerLanguage.set(language, newUserWrittenCode)
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
        private const val DEFAULT_MODIFICATION_INTERVAL_IN_SECONDS = 300 // 5 minutes

        private const val DOCUMENT_COPY_THRESSHOLD = 50
        private val LOG = getLogger<UserWrittenCodeTracker>()

        fun getInstance(project: Project) = project.service<UserWrittenCodeTracker>()
    }

    override fun dispose() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }

        flush()
    }
}
