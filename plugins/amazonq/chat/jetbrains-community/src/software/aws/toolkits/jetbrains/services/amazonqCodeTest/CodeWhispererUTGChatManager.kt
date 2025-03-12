// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import software.amazon.awssdk.core.exception.SdkServiceException
import software.amazon.awssdk.services.codewhispererruntime.model.GetTestGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.Range
import software.amazon.awssdk.services.codewhispererruntime.model.StartTestGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.TargetCode
import software.amazon.awssdk.services.codewhispererruntime.model.TestGenerationJobStatus
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportContext
import software.amazon.awssdk.services.codewhispererstreaming.model.ExportIntent
import software.aws.toolkits.core.utils.Waiters.waitUntil
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.credentials.sono.isInternalUser
import software.aws.toolkits.jetbrains.services.amazonq.clients.AmazonQStreamingClient
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller.CodeTestChatHelper
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.Button
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestChatMessageContent
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.PreviousUTGIterationContext
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.ShortAnswer
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteProgressStatus
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.Session
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.utils.combineBuildAndExecuteLogFiles
import software.aws.toolkits.jetbrains.services.codemodernizer.utils.calculateTotalLatency
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.CodeTestException
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.fileTooLarge
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.sessionconfig.CodeTestSessionConfig
import software.aws.toolkits.jetbrains.services.codewhisperer.codetest.testGenStoppedError
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.promptReAuth
import software.aws.toolkits.jetbrains.services.codewhisperer.util.getTelemetryErrorMessage
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.cwc.messages.CodeReference
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.jetbrains.utils.isQConnected
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Status
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

@Service
class CodeWhispererUTGChatManager(val project: Project, private val cs: CoroutineScope) {
    // TODO: consider combining this with session.isGeneratingTests
    private val isUTGInProgress = AtomicBoolean(false)
    private val mapper = jacksonObjectMapper()
    private val generatedTestDiffs = mutableMapOf<String, String>()

    private fun throwIfCancelled(session: Session) {
        if (!session.isGeneratingTests) {
            testGenStoppedError()
        }
    }

    private suspend fun launchTestGenFlow(
        prompt: String,
        codeTestChatHelper: CodeTestChatHelper,
        previousIterationContext: PreviousUTGIterationContext?,
        selectionRange: Range?,
    ) {
        // 1st API call: Zip project and call CreateUploadUrl
        val session = codeTestChatHelper.getActiveSession()
        session.isGeneratingTests = true
        session.iteration++

        // Set the Progress bar to "Generating unit tests..."
        codeTestChatHelper.updateUI(
            promptInputDisabledState = true,
            promptInputProgress = testGenProgressField(0),
        )

        val codeTestResponseContext = createUploadUrl(codeTestChatHelper, previousIterationContext)
        session.srcPayloadSize = codeTestResponseContext.payloadContext.srcPayloadSize
        session.srcZipFileSize = codeTestResponseContext.payloadContext.srcZipFileSize
        session.artifactUploadDuration = codeTestResponseContext.serviceInvocationContext.artifactsUploadDuration
        val path = codeTestResponseContext.currentFileRelativePath
        if (codeTestResponseContext.payloadContext.payloadLimitCrossed == true) {
            fileTooLarge()
        }

        val createUploadUrlResponse = codeTestResponseContext.createUploadUrlResponse ?: return
        throwIfCancelled(session)

        LOG.debug {
            "Q TestGen StartTestGenerationRequest: TabId= ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                "uploadId: ${createUploadUrlResponse.uploadId()}, relativeTargetPath: ${codeTestResponseContext.currentFileRelativePath}, " +
                "selectionRange: $selectionRange, "
        }

        // 2nd API call: StartTestGeneration
        val startTestGenerationResponse = try {
            var response: StartTestGenerationResponse? = null

            waitUntil(
                succeedOn = { response?.sdkHttpResponse()?.statusCode() == 200 },
                maxDuration = Duration.ofSeconds(1), // 1 second timeout
            ) {
                try {
                    response = startTestGeneration(
                        uploadId = createUploadUrlResponse.uploadId(),
                        targetCode = listOf(
                            TargetCode.builder()
                                .relativeTargetPath(codeTestResponseContext.currentFileRelativePath.toString())
                                .targetLineRangeList(
                                    if (selectionRange != null) {
                                        listOf(selectionRange)
                                    } else {
                                        emptyList()
                                    }
                                )
                                .build()
                        ),
                        userInput = prompt
                    )
                    delay(200)
                    response?.testGenerationJob() != null
                } catch (e: Exception) {
                    throw e
                }
            }

            response ?: throw RuntimeException("Failed to start test generation")
        } catch (e: Exception) {
            LOG.error(e) { "Unexpected error while creating test generation job" }
            val errorMessage = getTelemetryErrorMessage(e, CodeWhispererConstants.FeatureName.TEST_GENERATION)

            // Sending requestId to telemetry if there is Validation Exception
            if (e is SdkServiceException) {
                session.startTestGenerationRequestId = e.requestId()
            }
            throw CodeTestException(
                "CreateTestJobError: $errorMessage",
                "CreateTestJobError",
                message("testgen.error.generic_technical_error_message")
            )
        }

        val job = startTestGenerationResponse.testGenerationJob()
        session.startTestGenerationRequestId = startTestGenerationResponse.responseMetadata().requestId()
        session.testGenerationJobGroupName = job.testGenerationJobGroupName()
        session.testGenerationJob = job.testGenerationJobId()
        throwIfCancelled(session)

        // 3rd API call: Step 3:  Polling mechanism on test job status with getTestGenStatus getTestGeneration
        var finished = false
        var testGenerationResponse: GetTestGenerationResponse? = null

        var shortAnswer = ShortAnswer()
        LOG.debug {
            "Q TestGen session: ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                "polling result for id: ${job.testGenerationJobId()}, group name: ${job.testGenerationJobGroupName()}, " +
                "request id: ${startTestGenerationResponse.responseMetadata().requestId()}"
        }

        while (!finished) {
            throwIfCancelled(session)
            testGenerationResponse = getTestGenerationStatus(job.testGenerationJobId(), job.testGenerationJobGroupName())

            val status = testGenerationResponse.testGenerationJob().status()
            if (status == TestGenerationJobStatus.COMPLETED) {
                LOG.debug {
                    "Q TestGen session: ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                        "Test generation completed, short answer string: ${testGenerationResponse.testGenerationJob().shortAnswer()}"
                }
                finished = true
                if (testGenerationResponse.testGenerationJob().shortAnswer() != null) {
                    shortAnswer = parseShortAnswerString(testGenerationResponse.testGenerationJob().shortAnswer())

                    val testFileName = shortAnswer.testFilePath?.let { File(it).name }.orEmpty()
                    session.testFileName = testFileName
                    // Setting default value to 0 if the value is null or invalid
                    session.numberOfUnitTestCasesGenerated = shortAnswer.numberOfTestMethods
                    session.testFileRelativePathToProjectRoot = getTestFilePathRelativeToRoot(shortAnswer)

                    // update test summary card in success case
                    if (previousIterationContext == null) {
                        codeTestChatHelper.updateAnswer(
                            CodeTestChatMessageContent(
                                message = generateSummaryMessage(path.fileName.toString()) + shortAnswer.planSummary,
                                type = ChatMessageType.Answer,
                                footer = listOf(testFileName)
                            ),
                            messageIdOverride = codeTestResponseContext.testSummaryMessageId
                        )
                    }
                    // update test summary card
                } else {
                    // If job status is Completed and has no ShortAnswer then there might be some issue in the backend.
                    throw CodeTestException(
                        "TestGenFailedError: " + message("testgen.message.failed"),
                        "TestGenFailedError",
                        message("testgen.error.generic_technical_error_message")
                    )
                }
            } else if (status == TestGenerationJobStatus.FAILED) {
                LOG.debug {
                    "Q TestGen session: ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                        "Test generation failed, short answer string: ${testGenerationResponse.testGenerationJob().shortAnswer()}"
                }
                if (testGenerationResponse.testGenerationJob().shortAnswer() != null) {
                    shortAnswer = parseShortAnswerString(testGenerationResponse.testGenerationJob().shortAnswer())
                    if (shortAnswer.stopIteration == "true") {
                        throw CodeTestException("TestGenFailedError: ${shortAnswer.planSummary}", "TestGenFailedError", shortAnswer.planSummary)
                    }
                }

                // If job status is Failed and has no ShortAnswer then there might be some issue in the backend.
                throw CodeTestException(
                    "TestGenFailedError: " + message("testgen.message.failed"),
                    "TestGenFailedError",
                    message("testgen.error.generic_technical_error_message")
                )
            } else {
                // In progress
                LOG.debug {
                    "Q TestGen session: ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                        "Test generation in progress, progress rate ${testGenerationResponse.testGenerationJob().progressRate()}}"
                }
                val progressRate = testGenerationResponse.testGenerationJob().progressRate() ?: 0

                if (previousIterationContext == null && testGenerationResponse.testGenerationJob().shortAnswer() != null) {
                    shortAnswer = parseShortAnswerString(testGenerationResponse.testGenerationJob().shortAnswer())
                    if (shortAnswer.stopIteration == "true") {
                        throw CodeTestException("TestGenFailedError: ${shortAnswer.planSummary}", "TestGenFailedError", shortAnswer.planSummary)
                    }
                    val fileName = shortAnswer.sourceFilePath?.let { Path.of(it).fileName.toString() } ?: path.fileName.toString()
                    codeTestChatHelper.updateAnswer(
                        CodeTestChatMessageContent(
                            message = generateSummaryMessage(fileName) + shortAnswer.planSummary,
                            type = ChatMessageType.Answer
                        ),
                        messageIdOverride = codeTestResponseContext.testSummaryMessageId
                    )
                }
                codeTestChatHelper.updateUI(
                    promptInputDisabledState = true,
                    promptInputProgress = testGenProgressField(progressRate),
                )
            }

            // polling every 2 seconds to reduce # of API calls
            delay(2000)
        }

        throwIfCancelled(session)

        // 4th API call: Step 4: ExportResultsArchive
        val byteArray = AmazonQStreamingClient.getInstance(project).exportResultArchive(
            createUploadUrlResponse.uploadId(),
            ExportIntent.UNIT_TESTS,
            ExportContext.fromUnitTestGenerationExportContext {
                it.testGenerationJobId(job.testGenerationJobId())
                it.testGenerationJobGroupName(job.testGenerationJobGroupName())
            },
            { e ->
                LOG.error(e) { "ExportResultArchive failed: ${e.message}" }
                throw CodeTestException(
                    "ExportResultsArchiveError: ${e.message}",
                    "ExportResultsArchiveError",
                    message("testgen.error.generic_technical_error_message")
                )
            },
            { startTime ->
                LOG.info { "ExportResultArchive latency: ${calculateTotalLatency(startTime, Instant.now())}" }
            }
        )
        val result = byteArray.reduce { acc, next -> acc + next } // To map the result it is needed to combine the  full byte array
        storeGeneratedTestDiffs(result, session)
        if (!session.isGeneratingTests) {
            // TODO: Modify text according to FnF
            codeTestChatHelper.addAnswer(
                CodeTestChatMessageContent(
                    message = message("testgen.error.generic_technical_error_message"),
                    type = ChatMessageType.Answer,
                    canBeVoted = true
                )
            )
            return
        }

        val codeReference = shortAnswer.codeReferences?.map { ref ->
            CodeReference(
                licenseName = ref.licenseName,
                url = ref.url,
                information = "${ref.licenseName} - <a href=\"${ref.url}\">${ref.repository}</a>"
            )
        }
        shortAnswer.codeReferences?.let { session.codeReferences = it }
        val isReferenceAllowed = CodeWhispererSettings.getInstance().isIncludeCodeWithReference()
        if (!isReferenceAllowed && codeReference?.isNotEmpty() == true) {
            codeTestChatHelper.addAnswer(
                CodeTestChatMessageContent(
                    message = """
                    Your settings do not allow code generation with references.
                    """.trimIndent(),
                    type = ChatMessageType.Answer,
                )
            )
        } else {
            if (previousIterationContext == null) {
                // show another card as the answer
                val viewDiffMessageId = codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = """
                    Please see the unit tests generated below. Click "View Diff" to review the changes in the code editor.
                        """.trimIndent(),
                        type = ChatMessageType.Answer,
                        buttons = listOf(Button("utg_view_diff", "View Diff", keepCardAfterClick = true, position = "outside", status = "info")),
                        fileList = listOf(getTestFilePathRelativeToRoot(shortAnswer)),
                        projectRootName = project.name,
                        canBeVoted = true,
                        codeReference = codeReference
                    )
                )
                session.viewDiffMessageId = viewDiffMessageId
                codeTestChatHelper.updateUI(
                    promptInputDisabledState = false,
                    promptInputPlaceholder = "Specify a function(s) in the current file(optional)",
                    promptInputProgress = testGenCompletedField,
                )
            } else {
                codeTestChatHelper.updateAnswer(
                    CodeTestChatMessageContent(
                        type = ChatMessageType.Answer,
                        buttons = listOf(Button("utg_view_diff", "View Diff", keepCardAfterClick = true, position = "outside", status = "info")),
                        fileList = listOf(getTestFilePathRelativeToRoot(shortAnswer)),
                        projectRootName = project.name,
                        codeReference = codeReference
                    ),
                    messageIdOverride = previousIterationContext.buildAndExecuteMessageId
                )
                session.viewDiffMessageId = previousIterationContext.buildAndExecuteMessageId
                codeTestChatHelper.updateUI(
                    loadingChat = false,
                )
            }
            codeTestChatHelper.updateUI(
                promptInputDisabledState = true,
                promptInputPlaceholder = message("testgen.placeholder.view_diff"),
                promptInputProgress = testGenCompletedField,
            )
            delay(1000)
        }

        codeTestChatHelper.sendUpdatePromptProgress(codeTestChatHelper.getActiveSession().tabId, null)
    }

    // Input: test file path relative to project root's parent .
    // Output: test file path relative to project root.
    // shortAnswer.testFilePath has a format of <projectName>/<test file path relative to project root>.
    // test file path in generatedTestDiffs map has a format of resultArtifacts/<test file path relative to project root>.
    // both needs to be handled the same way which is remove the first sub-directory
    private fun getTestFilePathRelativeToRoot(shortAnswer: ShortAnswer): String {
        val pathString = shortAnswer.testFilePath ?: generatedTestDiffs.keys.firstOrNull() ?: throw RuntimeException("No test file path found")
        val path = Paths.get(pathString)
        val updatedPath = path.subpath(1, path.nameCount).toString()
        return updatedPath
    }

    private fun parseShortAnswerString(shortAnswerString: String): ShortAnswer {
        // Step 1: Replace single quotes with double quotes
        var jsonString = shortAnswerString.replace("'", "\"").replace("```", "")

        // Step 2: Replace Python's None with JSON's null
        jsonString = jsonString.replace(": None", ": null")

        // Step 3: remove extra quotes in the head and tail
        if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
            jsonString = jsonString.substring(1, jsonString.length - 1) // Remove the first and last quote
        }

        // Step 4: unescape it
        jsonString = jsonString.replace("\\\"", "\"")
            .replace("\\\\", "\\")
        // Deserialize JSON to Kotlin data class
        try {
            val shortAnswer: ShortAnswer = mapper.readValue(jsonString, ShortAnswer::class.java)
            return shortAnswer
        } catch (e: JsonParseException) {
            LOG.debug(e) { "Test Generation JSON parsing error: ${e.message}" }
            throw e
        } catch (e: Exception) {
            LOG.debug(e) { "Error parsing JSON" }
            throw e
        }
    }

    private fun storeGeneratedTestDiffs(byteArray: ByteArray, session: Session) {
        try {
            val byteArrayInputStream = ByteArrayInputStream(byteArray)
            ZipInputStream(byteArrayInputStream).use { zipInputStream ->
                var zipEntry = zipInputStream.nextEntry

                while (zipEntry != null) {
                    if (zipEntry.isDirectory) {
                        zipInputStream.closeEntry()
                        zipEntry = zipInputStream.nextEntry
                        // We are only interested in test file diff in zip entries
                        continue
                    }

                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var len: Int

                    while (zipInputStream.read(buffer).also { len = it } > 0) {
                        baos.write(buffer, 0, len)
                    }

                    val fileContent = baos.toByteArray()
                    if (fileContent.toString(Charsets.UTF_8).isEmpty()) {
                        session.isGeneratingTests = false
                        return
                    }
                    val zipEntryPath = Paths.get(zipEntry.name)

                    // relative path to project root
                    val updatedZipEntryPath = zipEntryPath.subpath(1, zipEntryPath.nameCount).toString()
                    session.generatedTestDiffs[updatedZipEntryPath] = fileContent.toString(Charsets.UTF_8)

                    zipInputStream.closeEntry()
                    zipEntry = zipInputStream.nextEntry
                }
            }
        } catch (e: IOException) {
            LOG.debug(e) { "Error reading ZIP entries" }
            throw e
        }
    }

    private suspend fun createUploadUrl(
        codeTestChatHelper: CodeTestChatHelper,
        previousIterationContext: PreviousUTGIterationContext?,
    ): CodeTestResponseContext {
        throwIfCancelled(codeTestChatHelper.getActiveSession())
        val file =
            if (previousIterationContext == null) {
                FileEditorManager.getInstance(project).selectedEditor?.file.also {
                    codeTestChatHelper.getActiveSession().selectedFile = it
                }
            } else {
                previousIterationContext.selectedFile
            }

        val combinedBuildAndExecuteLogFile = combineBuildAndExecuteLogFiles(
            previousIterationContext?.buildLogFile,
            previousIterationContext?.testLogFile
        )
        val codeTestSessionConfig = CodeTestSessionConfig(file, project, combinedBuildAndExecuteLogFile)
        codeTestChatHelper.getActiveSession().projectRoot = codeTestSessionConfig.projectRoot.path

        val codeTestSessionContext = CodeTestSessionContext(project, codeTestSessionConfig)
        val codeWhispererCodeTestSession = CodeWhispererCodeTestSession(codeTestSessionContext)
        return codeWhispererCodeTestSession.run(codeTestChatHelper, previousIterationContext)
    }

    private fun startTestGeneration(uploadId: String, targetCode: List<TargetCode>, userInput: String): StartTestGenerationResponse =
        CodeWhispererClientAdaptor.getInstance(project).startTestGeneration(uploadId, targetCode, userInput)

    private fun getTestGenerationStatus(jobId: String, jobGroupName: String): GetTestGenerationResponse =
        CodeWhispererClientAdaptor.getInstance(project).getTestGeneration(jobId, jobGroupName)

    /**
     * Returns true if the UTG is in progress.
     * This function will return true for a cancelled UTG job which is in cancellation state.
     */
    fun isUTGInProgress(): Boolean = isUTGInProgress.get()

    private fun beforeTestGenFlow(session: Session) {
        resetTestGenFlowSession(session)
        session.isGeneratingTests = true
        isUTGInProgress.set(true)
        // Show in progress indicator

        ApplicationManager.getApplication().invokeLater {
            (FileDocumentManager.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
        }
    }

    private fun resetTestGenFlowSession(session: Session) {
        // session.selectedFile doesn't need to be reset since it will remain unchanged
        session.conversationState = ConversationState.IN_PROGRESS
        session.shortAnswer = ShortAnswer()
        session.openedDiffFile = null
        session.testFileRelativePathToProjectRoot = ""
        session.testFileName = ""
        session.openedDiffFile = null
        session.generatedTestDiffs.clear()
        session.buildAndExecuteTaskContext.apply {
            buildExitCode = -1
            testExitCode = -1
            progressStatus = BuildAndExecuteProgressStatus.START_STEP
        }
    }

    private fun afterTestGenFlow() {
        isUTGInProgress.set(false)
    }

    /**
     * Triggers a unit test generation flow based on current open file.
     */
    fun generateTests(
        prompt: String,
        codeTestChatHelper: CodeTestChatHelper,
        previousIterationContext: PreviousUTGIterationContext?,
        selectionRange: Range?,
    ): Job? {
        val shouldStart = performTestGenPreChecks()
        val session = codeTestChatHelper.getActiveSession()
        if (!shouldStart) {
            session.conversationState = ConversationState.IDLE
            return null
        }

        beforeTestGenFlow(session)

        return cs.launch {
            try {
                launchTestGenFlow(prompt, codeTestChatHelper, previousIterationContext, selectionRange)
            } catch (e: Exception) {
                // reset number of unitTestGenerated to null
                session.numberOfUnitTestCasesGenerated = null
                // Add an answer for displaying error message
                val errorMessage = when {
                    e is CodeTestException &&
                        e.message?.startsWith("CreateTestJobError: Maximum") == true ->
                        message("testgen.error.maximum_generations_reach")

                    e is CodeTestException -> e.uiMessage
                    e is JsonParseException -> message("testgen.error.generic_technical_error_message")
                    else -> message("testgen.error.generic_error_message")
                }
                val buttonList = mutableListOf<Button>()
                if (isInternalUser(getStartUrl(project))) {
                    buttonList.add(
                        Button(
                            "utg_feedback",
                            message("testgen.button.feedback"),
                            keepCardAfterClick = true,
                            position = "outside",
                            status = "info",
                            icon = "comment"
                        ),
                    )
                }
                codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = errorMessage,
                        type = ChatMessageType.Answer,
                        canBeVoted = false,
                        buttons = buttonList
                    )
                )

                AmazonqTelemetry.utgGenerateTests(
                    cwsprChatProgrammingLanguage = session.programmingLanguage.languageId,
                    hasUserPromptSupplied = session.hasUserPromptSupplied,
                    isFileInWorkspace = true,
                    isSupportedLanguage = true,
                    credentialStartUrl = getStartUrl(project),
                    jobGroup = session.testGenerationJobGroupName,
                    jobId = session.testGenerationJob,
                    result = if (e.message == message("testgen.message.cancelled")) MetricResult.Cancelled else MetricResult.Failed,
                    reason = (e as CodeTestException).code ?: "DefaultError",
                    reasonDesc = if (e.message == message("testgen.message.cancelled")) "${e.code}: ${e.message}" else e.message,
                    perfClientLatency = (Instant.now().toEpochMilli() - session.startTimeOfTestGeneration),
                    isCodeBlockSelected = session.isCodeBlockSelected,
                    artifactsUploadDuration = session.artifactUploadDuration,
                    buildPayloadBytes = session.srcPayloadSize,
                    buildZipFileBytes = session.srcZipFileSize,
                    requestId = session.startTestGenerationRequestId,
                    status = if (e.message == message("testgen.message.cancelled")) Status.CANCELLED else Status.FAILED,
                )
                session.isGeneratingTests = false
            } finally {
                // Reset the flow if there is any error
                if (!session.isGeneratingTests) {
                    codeTestChatHelper.updateUI(
                        promptInputProgress = cancellingProgressField
                    )
                    delay(1000)
                    codeTestChatHelper.sendUpdatePromptProgress(session.tabId, null)
                    codeTestChatHelper.deleteSession(session.tabId)
                    codeTestChatHelper.updateUI(
                        promptInputDisabledState = false,
                        promptInputPlaceholder = message("testgen.placeholder.enter_slash_quick_actions"),
                    )
                }
                session.isGeneratingTests = false
                session.conversationState = ConversationState.IDLE
                afterTestGenFlow()
                // send message displaying card
            }
        }
    }

    private fun performTestGenPreChecks(): Boolean {
        if (!isQConnected(project)) return false
        if (isUTGInProgress()) return false
        val connectionExpired = promptReAuth(project)
        if (connectionExpired) return false
        return true
    }

    companion object {
        fun getInstance(project: Project) = project.service<CodeWhispererUTGChatManager>()
        private val LOG = getLogger<CodeWhispererUTGChatManager>()
    }
}
