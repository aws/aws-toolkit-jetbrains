// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.refactoring.suggested.range
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import info.debatty.java.stringsimilarity.Levenshtein
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.FileContextInfo
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.model.PreviewContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererCodeCompletionServiceListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants.TOTAL_SECONDS_IN_MINUTE
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getCodeWhispererStartUrl
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getUnmodifiedAcceptedCharsCount
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.telemetry.CodewhispererTelemetry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToLong

// TODO: reset code coverage calculator on logging out connection?
// TODO: rename "Tokens" to "Characters", and many more renames in this file
abstract class CodeWhispererCodeCoverageTracker(
    private val project: Project,
    private val timeWindowInSec: Long,
    private val language: CodeWhispererProgrammingLanguage,
    private val rangeMarkers: MutableList<RangeMarker>,
    private val fileToTokens: MutableMap<Document, CodeCoverageTokens>,
    private val myServiceInvocationCount: AtomicInteger,
) : Disposable {
    val percentage: Long?
        get() = if (totalCharsCount != 0L) calculatePercentage(acceptedCharsCount, totalCharsCount) else null
    val unmodifiedAcceptedCharsCount: Long
        get() = fileToTokens.map {
            it.value.unmodifiedAcceptedChars.get()
        }.fold(0) { acc, next ->
            acc + next
        }
    val totalCharsCount: Long
        get() = fileToTokens.map {
            it.value.totalChars.get()
        }.fold(0) { acc, next ->
            acc + next
        }
    private val acceptedCharsCount: Long
        get() = fileToTokens.map {
            it.value.acceptedChars.get()
        }.fold(0) { acc, next ->
            acc + next
        }
    val acceptedRecommendationsCount: Int
        get() = rangeMarkers.size
    val serviceInvocationCount: Int
        get() = myServiceInvocationCount.get()
    private val isActive: AtomicBoolean = AtomicBoolean(false)
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val isShuttingDown = AtomicBoolean(false)
    private var startTime: Instant = Instant.now()

    @Synchronized
    fun activateTrackerIfNotActive() {
        // tracker will only be activated if and only if IsTelemetryEnabled = true && isActive = false
        if (!isTelemetryEnabled() || isActive.getAndSet(true)) return

        val conn = ApplicationManager.getApplication().messageBus.connect()
        if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
            conn.subscribe(
                CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED,
                object : CodeWhispererUserActionListener {
                    override fun afterAccept(
                        states: InvocationContextNew,
                        previews: List<PreviewContext>,
                        sessionContext: SessionContextNew,
                        rangeMarker: RangeMarker,
                    ) {
                        if (states.requestContext.fileContextInfo.programmingLanguage != language) return
                        rangeMarkers.add(rangeMarker)
                        val originalRecommendation = extractRangeMarkerString(rangeMarker) ?: return
                        rangeMarker.putUserData(KEY_REMAINING_RECOMMENDATION, originalRecommendation)
                        runReadAction {
                            // also increment total tokens because accepted tokens are part of it
                            incrementTotalCharsCount(rangeMarker.document, originalRecommendation.length)
                            // avoid counting CodeWhisperer inserted suggestion twice in total tokens
                            if (rangeMarker.textRange.length in 2..49 && originalRecommendation.trim().isNotEmpty()) {
                                incrementTotalCharsCount(rangeMarker.document, -rangeMarker.textRange.length)
                            }
                        }
                    }
                }
            )
        } else {
            conn.subscribe(
                CodeWhispererPopupManager.CODEWHISPERER_USER_ACTION_PERFORMED,
                object : CodeWhispererUserActionListener {
                    override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, rangeMarker: RangeMarker) {
                        if (states.requestContext.fileContextInfo.programmingLanguage != language) return
                        rangeMarkers.add(rangeMarker)
                        val originalRecommendation = extractRangeMarkerString(rangeMarker) ?: return
                        rangeMarker.putUserData(KEY_REMAINING_RECOMMENDATION, originalRecommendation)
                        runReadAction {
                            // also increment total tokens because accepted tokens are part of it
                            incrementTotalCharsCount(rangeMarker.document, originalRecommendation.length)
                            // avoid counting CodeWhisperer inserted suggestion twice in total tokens
                            if (rangeMarker.textRange.length in 2..49 && originalRecommendation.trim().isNotEmpty()) {
                                incrementTotalCharsCount(rangeMarker.document, -rangeMarker.textRange.length)
                            }
                        }
                    }
                }
            )
        }

        conn.subscribe(
            CodeWhispererService.CODEWHISPERER_CODE_COMPLETION_PERFORMED,
            object : CodeWhispererCodeCompletionServiceListener {
                override fun onSuccess(fileContextInfo: FileContextInfo) {
                    if (language == fileContextInfo.programmingLanguage) {
                        myServiceInvocationCount.getAndIncrement()
                    }
                }
            }
        )
        startTime = Instant.now()
        scheduleCodeWhispererCodeCoverageTracker()
    }

    fun isTrackerActive() = isActive.get()

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
        if ((event.newLength == 1 && event.oldLength == 0) || (text.startsWith('\n') && text.trim().isEmpty())) {
            incrementTotalCharsCount(event.document, 1)
            return
        } else if (event.newLength < 50 && text.trim().isNotEmpty()) {
            // count doc changes from <50 multi character input as total user written code
            // ignore all white space changes, this usually comes from IntelliJ formatting
            incrementTotalCharsCount(event.document, event.newLength)
        }
    }

    internal fun extractRangeMarkerString(rangeMarker: RangeMarker): String? = runReadAction {
        rangeMarker.range?.let { myRange -> rangeMarker.document.getText(myRange) }
    }

    private fun flush() {
        try {
            if (isTelemetryEnabled()) emitCodeWhispererCodeContribution()
        } finally {
            reset()
            scheduleCodeWhispererCodeCoverageTracker()
        }
    }

    private fun scheduleCodeWhispererCodeCoverageTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, Duration.ofSeconds(timeWindowInSec).toMillis())
        }
    }

    private fun incrementUnmodifiedAcceptedCharsCount(document: Document, delta: Int) {
        val tokens = fileToTokens.getOrPut(document) { CodeCoverageTokens() }
        tokens.unmodifiedAcceptedChars.addAndGet(delta)
    }

    private fun incrementAcceptedCharsCount(document: Document, delta: Int) {
        val tokens = fileToTokens.getOrPut(document) { CodeCoverageTokens() }
        tokens.acceptedChars.addAndGet(delta)
    }

    private fun incrementTotalCharsCount(document: Document, delta: Int) {
        val tokens = fileToTokens.getOrPut(document) { CodeCoverageTokens() }
        tokens.apply {
            totalChars.addAndGet(delta)
            if (totalChars.get() < 0) totalChars.set(0)
        }
    }

    private fun reset() {
        startTime = Instant.now()
        rangeMarkers.clear()
        fileToTokens.clear()
        myServiceInvocationCount.set(0)
    }

    internal fun emitCodeWhispererCodeContribution() {
        // If the user is inactive or did not invoke, don't emit the telemetry
        if (percentage == null) return
        if (myServiceInvocationCount.get() <= 0) return

        rangeMarkers.forEach { rangeMarker ->
            if (!rangeMarker.isValid) return@forEach
            // if users add more code upon the recommendation generated from CodeWhisperer, we consider those added part as userToken but not CwsprTokens
            val originalRecommendation = rangeMarker.getUserData(KEY_REMAINING_RECOMMENDATION)
            val modifiedRecommendation = extractRangeMarkerString(rangeMarker)
            if (originalRecommendation == null || modifiedRecommendation == null) {
                LOG.debug {
                    "failed to get accepted recommendation. " +
                        "OriginalRecommendation is null: ${originalRecommendation == null}; " +
                        "ModifiedRecommendation is null: ${modifiedRecommendation == null}"
                }
                return@forEach
            }
            val unmodifiedRecommendationLength = getUnmodifiedAcceptedCharsCount(originalRecommendation, modifiedRecommendation)
            runReadAction {
                incrementAcceptedCharsCount(rangeMarker.document, originalRecommendation.length)
                incrementUnmodifiedAcceptedCharsCount(rangeMarker.document, unmodifiedRecommendationLength)
            }
        }
        val customizationArn: String? = CodeWhispererModelConfigurator.getInstance().activeCustomization(project)?.arn

        runIfIdcConnectionOrTelemetryEnabled(project) {
            // here acceptedTokensSize is the count of accepted chars post user modification
            try {
                val response = CodeWhispererClientAdaptor.getInstance(project).sendCodePercentageTelemetry(
                    language,
                    customizationArn,
                    acceptedCharsCount,
                    totalCharsCount,
                    unmodifiedAcceptedCharsCount
                )
                LOG.debug { "Successfully sent code percentage telemetry. RequestId: ${response.responseMetadata().requestId()}" }
            } catch (e: Exception) {
                val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                LOG.debug {
                    "Failed to send code percentage telemetry. RequestId: $requestId, ErrorMessage: ${e.message}"
                }
            }
        }

        // percentage == null means totalTokens == 0 and users are not editing the document, thus we shouldn't emit telemetry for this
        percentage?.let { percentage ->
            CodewhispererTelemetry.codePercentage(
                project = null,
                codewhispererAcceptedTokens = unmodifiedAcceptedCharsCount,
                codewhispererSuggestedTokens = acceptedCharsCount,
                codewhispererLanguage = language.toTelemetryType(),
                codewhispererPercentage = percentage,
                codewhispererTotalTokens = totalCharsCount,
                successCount = myServiceInvocationCount.get().toLong(),
                codewhispererCustomizationArn = customizationArn,
                credentialStartUrl = getCodeWhispererStartUrl(project)
            )
        }
    }

    @TestOnly
    fun forceTrackerFlush() {
        alarm.drainRequestsInTest()
    }

    @TestOnly
    fun activeRequestCount() = alarm.activeRequestCount

    override fun dispose() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }
        flush()
    }

    companion object {
        @JvmStatic
        val levenshteinChecker = Levenshtein()
        private const val REMAINING_RECOMMENDATION = "remainingRecommendation"
        private val KEY_REMAINING_RECOMMENDATION = Key<String>(REMAINING_RECOMMENDATION)
        private val LOG = getLogger<CodeWhispererCodeCoverageTracker>()
        private val instances: MutableMap<CodeWhispererProgrammingLanguage, CodeWhispererCodeCoverageTracker> = mutableMapOf()

        fun calculatePercentage(rawAcceptedTokenSize: Long, totalTokens: Long): Long = ((rawAcceptedTokenSize.toDouble() * 100) / totalTokens).roundToLong()
        fun getInstance(project: Project, language: CodeWhispererProgrammingLanguage): CodeWhispererCodeCoverageTracker =
            when (val instance = instances[language]) {
                null -> {
                    val newTracker = DefaultCodeWhispererCodeCoverageTracker(project, language)
                    instances[language] = newTracker
                    newTracker
                }
                else -> instance
            }

        @TestOnly
        fun getInstancesMap(): MutableMap<CodeWhispererProgrammingLanguage, CodeWhispererCodeCoverageTracker> {
            assert(ApplicationManager.getApplication().isUnitTestMode)
            return instances
        }
    }
}

class DefaultCodeWhispererCodeCoverageTracker(project: Project, language: CodeWhispererProgrammingLanguage) : CodeWhispererCodeCoverageTracker(
    project,
    5 * TOTAL_SECONDS_IN_MINUTE,
    language,
    mutableListOf(),
    mutableMapOf(),
    AtomicInteger(0)
)

class CodeCoverageTokens(totalChars: Int = 0, unmodifiedAcceptedChars: Int = 0, acceptedChars: Int = 0) {
    val totalChars = AtomicInteger(totalChars)
    val unmodifiedAcceptedChars = AtomicInteger(unmodifiedAcceptedChars)
    val acceptedChars = AtomicInteger(acceptedChars)
}
