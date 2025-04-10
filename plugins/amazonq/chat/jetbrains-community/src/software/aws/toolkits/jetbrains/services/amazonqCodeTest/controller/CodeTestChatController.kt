// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.controller
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffManagerEx
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.amazon.awssdk.services.codewhispererruntime.model.IdeCategory
import software.amazon.awssdk.services.codewhispererruntime.model.Position
import software.amazon.awssdk.services.codewhispererruntime.model.Range
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.amazon.awssdk.services.codewhispererruntime.model.Span
import software.amazon.awssdk.services.codewhispererstreaming.CodeWhispererStreamingAsyncClient
import software.amazon.awssdk.services.codewhispererstreaming.model.AssistantResponseEvent
import software.amazon.awssdk.services.codewhispererstreaming.model.ChatMessage
import software.amazon.awssdk.services.codewhispererstreaming.model.ChatResponseStream
import software.amazon.awssdk.services.codewhispererstreaming.model.ChatTriggerType
import software.amazon.awssdk.services.codewhispererstreaming.model.CursorState
import software.amazon.awssdk.services.codewhispererstreaming.model.DocumentSymbol
import software.amazon.awssdk.services.codewhispererstreaming.model.EditorState
import software.amazon.awssdk.services.codewhispererstreaming.model.GenerateAssistantResponseRequest
import software.amazon.awssdk.services.codewhispererstreaming.model.GenerateAssistantResponseResponseHandler
import software.amazon.awssdk.services.codewhispererstreaming.model.ProgrammingLanguage
import software.amazon.awssdk.services.codewhispererstreaming.model.RelevantTextDocument
import software.amazon.awssdk.services.codewhispererstreaming.model.SymbolType
import software.amazon.awssdk.services.codewhispererstreaming.model.TextDocument
import software.amazon.awssdk.services.codewhispererstreaming.model.UserInputMessage
import software.amazon.awssdk.services.codewhispererstreaming.model.UserInputMessageContext
import software.amazon.awssdk.services.codewhispererstreaming.model.UserIntent
import software.amazon.awssdk.services.toolkittelemetry.model.Sentiment
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.coroutines.EDT
import software.aws.toolkits.jetbrains.core.credentials.sono.isInternalUser
import software.aws.toolkits.jetbrains.services.amazonq.apps.AmazonQAppInitContext
import software.aws.toolkits.jetbrains.services.amazonq.auth.AuthController
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.project.RelevantDocument
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.CodeWhispererUTGChatManager
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.ConversationState
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.InboundAppMessagesHandler
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.Button
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestChatMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.CodeTestChatMessageContent
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.messages.IncomingCodeTestMessage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.model.PreviousUTGIterationContext
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.BuildAndExecuteProgressStatus
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.session.Session
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.storage.ChatSessionStorage
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.utils.constructBuildAndExecutionSummaryText
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.utils.runBuildOrTestCommand
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAuthNeededException
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.QFeatureEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.telemetry.broadcastQEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.toolwindow.CodeWhispererCodeReferenceManager
import software.aws.toolkits.jetbrains.services.codewhisperer.util.isWithin
import software.aws.toolkits.jetbrains.services.cwc.ChatConstants
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.ChatRequestData
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.model.TriggerType
import software.aws.toolkits.jetbrains.services.cwc.clients.chat.v1.ChatSessionV1.Companion.validLanguages
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticPrompt
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.StaticTextResponse
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.FeedbackComment
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContext
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ActiveFileContextExtractor
import software.aws.toolkits.jetbrains.services.cwc.editor.context.ExtractionTriggerType
import software.aws.toolkits.jetbrains.services.cwc.editor.context.file.FileContext
import software.aws.toolkits.jetbrains.services.cwc.messages.ChatMessageType
import software.aws.toolkits.jetbrains.services.telemetry.TelemetryService
import software.aws.toolkits.jetbrains.ui.feedback.TestGenFeedbackDialog
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.FeatureId
import software.aws.toolkits.telemetry.InteractionType
import software.aws.toolkits.telemetry.MetricResult
import software.aws.toolkits.telemetry.Status
import software.aws.toolkits.telemetry.UiTelemetry
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import software.amazon.awssdk.services.codewhispererstreaming.model.Position as StreamingPosition
import software.amazon.awssdk.services.codewhispererstreaming.model.Range as StreamingRange

class CodeTestChatController(
    private val context: AmazonQAppInitContext,
    private val chatSessionStorage: ChatSessionStorage,
    private val authController: AuthController = AuthController(),
    private val cs: CoroutineScope,
) : InboundAppMessagesHandler {
    val messenger = context.messagesFromAppToUi
    private val codeTestChatHelper = CodeTestChatHelper(context.messagesFromAppToUi, chatSessionStorage)
    private val supportedLanguage = setOf("python", "java")
    val client = CodeWhispererClientAdaptor.getInstance(context.project)
    override suspend fun processPromptChatMessage(message: IncomingCodeTestMessage.ChatPrompt) {
        handleChat(tabId = message.tabId, message = message.chatMessage)
    }

    private fun isLanguageSupported(languageId: String): Boolean =
        supportedLanguage.contains(languageId.lowercase())

    private fun getEditorSelectionRange(project: Project): Range? {
        var selectionRange: Range? = null

        ApplicationManager.getApplication().invokeAndWait {
            selectionRange = ApplicationManager.getApplication().runReadAction<Range?> {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                editor?.let {
                    val selectionModel = it.selectionModel
                    val startOffset = selectionModel.selectionStart
                    val endOffset = selectionModel.selectionEnd

                    val startLogicalPosition = editor.offsetToLogicalPosition(startOffset)
                    val endLogicalPosition = editor.offsetToLogicalPosition(endOffset)

                    if (startOffset != endOffset) {
                        val start = Position.builder()
                            .line(startLogicalPosition.line)
                            .character(startLogicalPosition.column)
                            .build()

                        val end = Position.builder()
                            .line(endLogicalPosition.line)
                            .character(endLogicalPosition.column)
                            .build()

                        Range.builder()
                            .start(start)
                            .end(end)
                            .build()
                    } else {
                        null
                    }
                }
            }
        }

        return selectionRange
    }

    override suspend fun processStartTestGen(message: IncomingCodeTestMessage.StartTestGen) {
        codeTestChatHelper.setActiveCodeTestTabId(message.tabId)
        val session = codeTestChatHelper.getActiveSession()
        if (session.isGeneratingTests) {
            return
        }
        sessionCleanUp(session.tabId)
        // check if IDE has active file open, yes return (fileName and filePath) else return null
        val project = context.project
        val fileInfo = checkActiveFileInIDE(project, message) ?: return
        session.programmingLanguage = fileInfo.fileLanguage
        session.startTimeOfTestGeneration = Instant.now().toEpochMilli().toDouble()
        session.isGeneratingTests = true

        var requestId: String = ""
        var statusCode: Int = 0
        var conversationId: String? = null
        var testResponseMessageId: String? = null
        var testResponseText: String = ""

        val userMessage = when {
            message.prompt != "" -> {
                "/test ${message.prompt}"
            }
            else -> "/test Generate unit tests for `${fileInfo.fileName}`"
        }
        session.hasUserPromptSupplied = message.prompt.isNotEmpty()

        // Send user prompt to chat
        codeTestChatHelper.addNewMessage(
            CodeTestChatMessageContent(message = userMessage, type = ChatMessageType.Prompt, canBeVoted = false),
            message.tabId,
            false
        )
        if (fileInfo.fileInWorkspace && isLanguageSupported(fileInfo.fileLanguage.languageId)) {
            // Send Capability card to chat
            codeTestChatHelper.addNewMessage(
                CodeTestChatMessageContent(informationCard = true, message = null, type = ChatMessageType.Answer, canBeVoted = false),
                message.tabId,
                false
            )

            var selectionRange = getEditorSelectionRange(project)

            session.isCodeBlockSelected = selectionRange !== null

            // This removes /test if user accidentally added while doing Regenerate unit tests and truncates the user response to 4096 characters.
            val userPrompt = message.prompt
                .let { if (it.startsWith("/test")) it.substringAfter("/test ").trim() else it }
                .take(4096)

            CodeWhispererUTGChatManager.getInstance(project).generateTests(userPrompt, codeTestChatHelper, null, selectionRange)
        } else {
            // Not adding a progress bar to unsupported language cases
            val responseHandler = GenerateAssistantResponseResponseHandler.builder()
                .onResponse {
                    requestId = it.responseMetadata().requestId()
                    statusCode = it.sdkHttpResponse().statusCode()
                    conversationId = it.conversationId()
                }
                .subscriber { stream: ChatResponseStream ->
                    stream.accept(object : GenerateAssistantResponseResponseHandler.Visitor {

                        override fun visitAssistantResponseEvent(event: AssistantResponseEvent) {
                            testResponseText += event.content()
                            cs.launch {
                                codeTestChatHelper.updateAnswer(
                                    CodeTestChatMessageContent(
                                        message = testResponseText,
                                        type = ChatMessageType.AnswerPart
                                    ),
                                    messageIdOverride = testResponseMessageId
                                )
                            }
                        }
                    })
                }
                .build()

            val messageContent = if (fileInfo.fileInWorkspace) {
                "<span style=\"color: #EE9D28;\">&#9888;<b> ${fileInfo.fileLanguage.languageId} is not a " +
                    "language I support specialized unit test generation for at the moment.</b><br></span>The languages " +
                    "I support now are Python and Java. I can still provide examples, instructions and code suggestions."
            } else {
                "<span style=\"color: #EE9D28;\">&#9888;<b> I can't generate tests for ${fileInfo.fileName}" +
                    " because it's outside the project directory.</b><br></span> " +
                    "I can still provide examples, instructions and code suggestions."
            }

            codeTestChatHelper.addNewMessage(
                CodeTestChatMessageContent(
                    message = messageContent,
                    type = ChatMessageType.Answer,
                    canBeVoted = false
                ),
                message.tabId,
                false
            )
            testResponseMessageId = codeTestChatHelper.addAnswer(
                CodeTestChatMessageContent(
                    message = "",
                    type = ChatMessageType.AnswerStream
                )
            )
            codeTestChatHelper.updateUI(
                loadingChat = true,
                promptInputDisabledState = true,
            )
            // Send Request to Sync UTG API
            val contextExtractor = ActiveFileContextExtractor.create(fqnWebviewAdapter = null, project = project)
            val activeFileContext = ActiveFileContext(
                fileContext = FileContext(
                    fileLanguage = fileInfo.fileLanguage.languageId,
                    filePath = fileInfo.filePath,
                    matchPolicy = null
                ),
                focusAreaContext = contextExtractor.extractContextForTrigger(ExtractionTriggerType.ChatMessage).focusAreaContext,
            )

            val requestData = ChatRequestData(
                tabId = session.tabId,
                message = "Generate unit tests for the following part of my code: ${message.prompt.ifBlank { fileInfo.fileName }}",
                activeFileContext = activeFileContext,
                userIntent = UserIntent.GENERATE_UNIT_TESTS,
                triggerType = TriggerType.ContextMenu,
                customization = CodeWhispererModelConfigurator.getInstance().activeCustomization(context.project),
                relevantTextDocuments = emptyList(),
                useRelevantDocuments = false,
            )

            val client = QRegionProfileManager.getInstance().getQClient<CodeWhispererStreamingAsyncClient>(project)
            val request = requestData.toChatRequest()
            client.generateAssistantResponse(request, responseHandler).await()
            // TODO: Need to send isCodeBlockSelected field
            requestId.let { id ->
                LOG.debug { "$FEATURE_NAME: Unit test generation requestId: $id" }
                AmazonqTelemetry.utgGenerateTests(
                    cwsprChatProgrammingLanguage = session.programmingLanguage.languageId,
                    hasUserPromptSupplied = session.hasUserPromptSupplied,
                    isFileInWorkspace = fileInfo.fileInWorkspace,
                    isSupportedLanguage = isLanguageSupported(fileInfo.fileLanguage.languageId),
                    credentialStartUrl = getStartUrl(project),
                    result = MetricResult.Succeeded,
                    perfClientLatency = (Instant.now().toEpochMilli() - session.startTimeOfTestGeneration),
                    requestId = id,
                    status = Status.ACCEPTED,
                )
            }
            session.isGeneratingTests = false
            codeTestChatHelper.updateUI(
                loadingChat = false,
                promptInputDisabledState = false
            )
        }
    }
    private fun ActiveFileContext.toEditorState(relevantDocuments: List<RelevantDocument>, useRelevantDocuments: Boolean): EditorState {
        val editorStateBuilder = EditorState.builder()
        if (fileContext != null) {
            val cursorStateBuilder = CursorState.builder()
            // Cursor State
            val start = focusAreaContext?.codeSelectionRange?.start
            val end = focusAreaContext?.codeSelectionRange?.end

            if (start != null && end != null) {
                cursorStateBuilder.range(
                    StreamingRange.builder()
                        .start(
                            StreamingPosition.builder()
                                .line(start.row)
                                .character(start.column)
                                .build(),
                        )
                        .end(
                            StreamingPosition.builder()
                                .line(end.row)
                                .character(end.column)
                                .build(),
                        ).build(),
                )
            }
            editorStateBuilder.cursorState(cursorStateBuilder.build())

            // Code Names -> DocumentSymbols
            val documentBuilder = TextDocument.builder()
            val codeNames = focusAreaContext?.codeNames

            val documentSymbolList = codeNames?.fullyQualifiedNames?.used?.map {
                DocumentSymbol.builder()
                    .name(it.symbol?.joinToString(separator = "."))
                    .type(SymbolType.USAGE)
                    .source(it.source?.joinToString(separator = "."))
                    .build()
            }?.filter { it.name().length in ChatConstants.FQN_SIZE_MIN until ChatConstants.FQN_SIZE_LIMIT }.orEmpty()
            documentBuilder.documentSymbols(documentSymbolList)
            // TODO: Do conditional check for focusAreaContext?.codeSelectionRange if undefined then get entire file
            // File Text
            val fileContent = Files.readString(Paths.get(fileContext.filePath))
            documentBuilder.text(fileContent)

            // Programming Language
            val programmingLanguage = fileContext.fileLanguage
            if (programmingLanguage != null && validLanguages.contains(programmingLanguage)) {
                documentBuilder.programmingLanguage(
                    ProgrammingLanguage.builder()
                        .languageName(programmingLanguage).build(),
                )
            }

            // Relative File Path
            val filePath = fileContext.filePath
            if (filePath != null) {
                documentBuilder.relativeFilePath(filePath.take(ChatConstants.FILE_PATH_SIZE_LIMIT))
            }
            editorStateBuilder.document(documentBuilder.build())
        }

        // Relevant Documents
        val documents: List<RelevantTextDocument> = relevantDocuments.map { doc ->
            RelevantTextDocument.builder().text(doc.text).relativeFilePath(doc.relativeFilePath.take(ChatConstants.FILE_PATH_SIZE_LIMIT)).build()
        }

        editorStateBuilder.relevantDocuments(documents)
        editorStateBuilder.useRelevantDocuments(useRelevantDocuments)
        return editorStateBuilder.build()
    }

    private fun ChatRequestData.toChatRequest(): GenerateAssistantResponseRequest {
        val userInputMessageContextBuilder = UserInputMessageContext.builder()
        userInputMessageContextBuilder.editorState(activeFileContext.toEditorState(relevantTextDocuments, useRelevantDocuments))
        val userInputMessageContext = userInputMessageContextBuilder.build() //
        val userInput = UserInputMessage.builder()
            .content(message.take(ChatConstants.CUSTOMER_MESSAGE_SIZE_LIMIT))
            .userInputMessageContext(userInputMessageContext)
            .userIntent(userIntent)
            .build()
        println("UserInput Message: $userInput")
        val conversationState = software.amazon.awssdk.services.codewhispererstreaming.model.ConversationState.builder()
            .currentMessage(ChatMessage.fromUserInputMessage(userInput))
            .chatTriggerType(if (triggerType == TriggerType.Inline) ChatTriggerType.INLINE_CHAT else ChatTriggerType.MANUAL)
            .customizationArn(customization?.arn)
            .build()
        return GenerateAssistantResponseRequest.builder()
            .conversationState(conversationState)
            .build()
    }

    override suspend fun processChatItemFeedBack(message: IncomingCodeTestMessage.ChatItemFeedback) {
        LOG.debug { "$FEATURE_NAME: Processing ChatItemFeedBackMessage: ${message.comment}" }

        val session = codeTestChatHelper.getActiveSession()

        val comment = FeedbackComment(
            conversationId = session.startTestGenerationRequestId,
            userComment = message.comment.orEmpty(),
            reason = message.selectedOption,
            type = "testgen-chat-answer-feedback",
            messageId = "",
        )

        try {
            TelemetryService.getInstance().sendFeedback(
                sentiment = Sentiment.NEGATIVE,
                comment = objectMapper.writeValueAsString(comment),
            )
            LOG.info { "$FEATURE_NAME answer feedback sent: \"Negative\"" }
        } catch (e: Throwable) {
            e.notifyError(message("feedback.submit_failed", e))
            LOG.warn(e) { "Failed to submit feedback" }
            return
        }
    }

    override suspend fun processChatItemVoted(message: IncomingCodeTestMessage.ChatItemVoted) {
        LOG.debug { "$FEATURE_NAME: Processing ChatItemVotedMessage: $message" }

        val session = codeTestChatHelper.getActiveSession()
        when (message.vote) {
            "upvote" -> {
                AmazonqTelemetry.feedback(
                    featureId = FeatureId.AmazonQTest,
                    interactionType = InteractionType.Upvote,
                    credentialStartUrl = getStartUrl(project = context.project),
                    amazonqConversationId = session.startTestGenerationRequestId

                )
            }
            "downvote" -> {
                AmazonqTelemetry.feedback(
                    featureId = FeatureId.AmazonQTest,
                    interactionType = InteractionType.Downvote,
                    credentialStartUrl = getStartUrl(project = context.project),
                    amazonqConversationId = session.startTestGenerationRequestId
                )
            }
        }
    }

    override suspend fun processNewTabCreatedMessage(message: IncomingCodeTestMessage.NewTabCreated) {
        newTabOpened(message.tabId)
        LOG.debug { "$FEATURE_NAME: New tab created: $message" }
        codeTestChatHelper.setActiveCodeTestTabId(message.tabId)
    }

    override suspend fun processTabRemovedMessage(message: IncomingCodeTestMessage.TabRemoved) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processClearQuickAction(message: IncomingCodeTestMessage.ClearChat) {
        chatSessionStorage.deleteSession(message.tabId)
    }

    override suspend fun processHelpQuickAction(message: IncomingCodeTestMessage.Help) {
        // TODO: Replace StaticPrompt and StaticTextResponse message according to Fnf
        codeTestChatHelper.addNewMessage(
            CodeTestChatMessageContent(
                message = StaticPrompt.Help.message,
                type = ChatMessageType.Prompt,
                canBeVoted = false
            ),
            message.tabId,
            false
        )
        codeTestChatHelper.addNewMessage(
            CodeTestChatMessageContent(
                message = StaticTextResponse.Help.message,
                type = ChatMessageType.Answer,
                canBeVoted = false
            ),
            message.tabId,
            false
        )
    }

    override suspend fun processLinkClick(message: IncomingCodeTestMessage.ClickedLink) {
        BrowserUtil.browse(message.link)
    }

    override suspend fun processButtonClickedMessage(message: IncomingCodeTestMessage.ButtonClicked) {
        val session = codeTestChatHelper.getActiveSession()
        var numberOfLinesGenerated = 0
        var numberOfLinesSelected = 0
        var lineDifference = 0
        var numberOfCharsGenerated = 0
        var numberOfCharsSelected = 0
        var charDifference = 0
        var generatedFileContent = ""
        var selectedFileContent = ""

        when (message.actionID) {
            "utg_view_diff" -> {
                withContext(EDT) {
                    // virtual file only needed for syntax highlighting when viewing diff
                    val tempPath = Files.createTempFile(null, ".${session.testFileName.substringAfterLast('.')}")
                    val virtualFile = tempPath.toFile().toVirtualFile()

                    (DiffManager.getInstance() as DiffManagerEx).showDiffBuiltin(
                        context.project,
                        SimpleDiffRequest(
                            session.testFileName,
                            DiffContentFactory.getInstance().create(
                                getFileContentAtTestFilePath(
                                    session.projectRoot,
                                    session.testFileRelativePathToProjectRoot
                                ),
                                virtualFile
                            ),
                            DiffContentFactory.getInstance().create(
                                session.generatedTestDiffs.values.first(),
                                virtualFile
                            ),
                            "Before",
                            "After"
                        )
                    )
                    Files.deleteIfExists(tempPath)
                    session.openedDiffFile = FileEditorManager.getInstance(context.project).selectedEditor?.file
                    ApplicationManager.getApplication().runReadAction {
                        generatedFileContent = getGeneratedFileContent(session)
                    }

                    selectedFileContent = getFileContentAtTestFilePath(
                        session.projectRoot,
                        session.testFileRelativePathToProjectRoot,
                    )

                    // Line difference calculation: linesOfCodeGenerated = number of lines in generated test file - number of lines in original test file
                    numberOfLinesGenerated = generatedFileContent.lines().size
                    numberOfLinesSelected = selectedFileContent.lines().size
                    lineDifference = numberOfLinesGenerated - numberOfLinesSelected

                    // Character difference calculation: charsOfCodeGenerated = number of characters in generated test file - number of characters in original test file
                    numberOfCharsGenerated = generatedFileContent.length
                    numberOfCharsSelected = selectedFileContent.length
                    charDifference = numberOfCharsGenerated - numberOfCharsSelected

                    session.linesOfCodeGenerated = lineDifference.coerceAtLeast(0)
                    session.charsOfCodeGenerated = charDifference.coerceAtLeast(0)
                    session.latencyOfTestGeneration = (Instant.now().toEpochMilli() - session.startTimeOfTestGeneration)
                    UiTelemetry.click(context.project, "unitTestGeneration_viewDiff")

                    val buttonList = mutableListOf<Button>()
                    buttonList.add(
                        Button(
                            "utg_reject",
                            "Reject",
                            keepCardAfterClick = true,
                            position = "outside",
                            status = "error",
                        ),
                    )
                    /*
                    // TODO: for unit test regeneration loop
                    if (session.iteration < 2) {
                        buttonList.add(
                            Button(
                                "utg_regenerate",
                                "Regenerate",
                                keepCardAfterClick = true,
                                position = "outside",
                                status = "info",
                            ),
                        )
                    }
                     */

                    buttonList.add(
                        Button(
                            "utg_accept",
                            "Accept",
                            keepCardAfterClick = true,
                            position = "outside",
                            status = "success",
                        ),
                    )

                    codeTestChatHelper.updateUI(
                        promptInputDisabledState = true,
                        promptInputPlaceholder = message("testgen.placeholder.select_an_option"),
                    )

                    codeTestChatHelper.updateAnswer(
                        CodeTestChatMessageContent(
                            type = ChatMessageType.AnswerPart,
                            buttons = buttonList,
                        ),
                        messageIdOverride = session.viewDiffMessageId
                    )
                }
            }
            "utg_accept" -> {
                // open the file at test path relative to the project root
                val testFileAbsolutePath = Paths.get(session.projectRoot, session.testFileRelativePathToProjectRoot)
                openOrCreateTestFileAndApplyDiff(context.project, testFileAbsolutePath, session.generatedTestDiffs.values.first(), session.openedDiffFile)
                session.codeReferences?.let { references ->
                    LOG.debug { "Accepted unit tests with references: $references" }
                    val manager = CodeWhispererCodeReferenceManager.getInstance(context.project)
                    references.forEach { ref ->
                        var referenceContentSpan: Span? = null
                        ref.recommendationContentSpan()?.let {
                            referenceContentSpan = Span.builder().start(ref.recommendationContentSpan().start())
                                .end(ref.recommendationContentSpan().end()).build()
                        }
                        val reference = Reference.builder().url(
                            ref.url()
                        ).licenseName(ref.licenseName()).repository(ref.repository()).recommendationContentSpan(referenceContentSpan).build()
                        var originalContent: String? = null
                        ref.recommendationContentSpan()?.let {
                            originalContent = session.generatedTestDiffs.values.first().substring(
                                ref.recommendationContentSpan().start(),
                                ref.recommendationContentSpan().end()
                            )
                        }
                        LOG.debug { "Original code content from reference span: $originalContent" }
                        withContext(EDT) {
                            manager.addReferenceLogPanelEntry(reference = reference, null, null, originalContent?.split("\n"))
                            manager.toolWindow?.show()
                        }
                    }
                }
                val testGenerationEventResponse = client.sendTestGenerationEvent(
                    session.testGenerationJob,
                    session.testGenerationJobGroupName,
                    session.programmingLanguage,
                    IdeCategory.JETBRAINS,
                    session.numberOfUnitTestCasesGenerated,
                    session.numberOfUnitTestCasesGenerated,
                    session.linesOfCodeGenerated,
                    session.linesOfCodeGenerated,
                    session.charsOfCodeGenerated,
                    session.charsOfCodeGenerated
                )
                LOG.debug {
                    "Successfully sent test generation telemetry. RequestId: ${
                        testGenerationEventResponse.responseMetadata().requestId()}"
                }

                UiTelemetry.click(context.project, "unitTestGeneration_acceptDiff")

                AmazonqTelemetry.utgGenerateTests(
                    cwsprChatProgrammingLanguage = session.programmingLanguage.languageId,
                    hasUserPromptSupplied = session.hasUserPromptSupplied,
                    isFileInWorkspace = true,
                    isSupportedLanguage = true,
                    credentialStartUrl = getStartUrl(project = context.project),
                    jobGroup = session.testGenerationJobGroupName,
                    jobId = session.testGenerationJob,
                    acceptedCount = session.numberOfUnitTestCasesGenerated?.toLong(),
                    generatedCount = session.numberOfUnitTestCasesGenerated?.toLong(),
                    acceptedLinesCount = session.linesOfCodeGenerated?.toLong(),
                    generatedLinesCount = session.linesOfCodeGenerated?.toLong(),
                    acceptedCharactersCount = session.charsOfCodeGenerated?.toLong(),
                    generatedCharactersCount = session.charsOfCodeGenerated?.toLong(),
                    result = MetricResult.Succeeded,
                    perfClientLatency = session.latencyOfTestGeneration,
                    isCodeBlockSelected = session.isCodeBlockSelected,
                    artifactsUploadDuration = session.artifactUploadDuration,
                    buildPayloadBytes = session.srcPayloadSize,
                    buildZipFileBytes = session.srcZipFileSize,
                    requestId = session.startTestGenerationRequestId,
                    status = Status.ACCEPTED,
                )
                codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = message("testgen.message.success"),
                        type = ChatMessageType.Answer,
                        canBeVoted = false,
                        buttons = this.showFeedbackButton()
                    )
                )
                codeTestChatHelper.updateUI(
                    promptInputDisabledState = false,
                    promptInputPlaceholder = message("testgen.placeholder.enter_slash_quick_actions"),
                )
                /*
                val taskContext = session.buildAndExecuteTaskContext
                if (session.iteration < 2) {
                    taskContext.buildCommand = getBuildCommand(message.tabId)
                    taskContext.executionCommand = getExecutionCommand(message.tabId)
                    codeTestChatHelper.addAnswer(
                        CodeTestChatMessageContent(
                            message = """
                           Would you like me to help build and execute the test? I'll run following commands

                           ```sh
                           ${taskContext.buildCommand}
                           ${taskContext.executionCommand}
                           ```
                            """.trimIndent(),
                            type = ChatMessageType.Answer,
                            canBeVoted = true,
                            buttons = listOf(
                                Button(
                                    "utg_skip_and_finish",
                                    "Skip and finish",
                                    keepCardAfterClick = true,
                                    position = "outside",
                                    status = "info",
                                ),
                                Button(
                                    "utg_modify_command",
                                    "Modify commands",
                                    keepCardAfterClick = true,
                                    position = "outside",
                                    status = "info",
                                ),
                                Button(
                                    "utg_build_and_execute",
                                    "Build and execute",
                                    keepCardAfterClick = true,
                                    position = "outside",
                                    status = "info",
                                ),
                            )
                        )
                    )
                    codeTestChatHelper.updateUI(
                        promptInputDisabledState = true,
                    )
                } else if (session.iteration < 4) {
                    // Already built and executed once, display # of iterations left message
                    val remainingIterationsCount = UTG_CHAT_MAX_ITERATION - session.iteration
                    val iterationCountString = "$remainingIterationsCount ${if (remainingIterationsCount > 1) "iterations" else "iteration"}"
                    codeTestChatHelper.addAnswer(
                        CodeTestChatMessageContent(
                            message = """
                                    Would you like Amazon Q to build and execute again, and fix errors?

                                    You have $iterationCountString left.

                            """.trimIndent(),
                            type = ChatMessageType.AIPrompt,
                            buttons = listOf(
                                Button(
                                    "utg_skip_and_finish",
                                    "Skip and finish",
                                    keepCardAfterClick = true,
                                    position = "outside",
                                    status = "info",
                                ),
                                Button(
                                    "utg_proceed",
                                    "Proceed",
                                    keepCardAfterClick = true,
                                    position = "outside",
                                    status = "info",
                                ),
                            ),
                        )
                    )
                    codeTestChatHelper.updateUI(
                        promptInputDisabledState = true,
                    )
                } else {
                    // TODO: change this hardcoded string
                    val monthlyLimitString = "25 out of 30"
                    codeTestChatHelper.addAnswer(
                        CodeTestChatMessageContent(
                            message = """
                                 You have gone through all three iterations and this unit test generation workflow is complete. You have $monthlyLimitString Amazon Q Developer Agent invocations left this month.
                            """.trimIndent(),
                            type = ChatMessageType.Answer,
                        )
                    )
                    codeTestChatHelper.updateUI(
                        promptInputPlaceholder = message("testgen.placeholder.newtab")
                    )
                }
                 */
            }
            /*
            //TODO: this is for unit test regeneration build iteration loop
            "utg_regenerate" -> {
                // close the existing open diff in the editor.
                ApplicationManager.getApplication().invokeLater {
                    session.openedDiffFile?.let { FileEditorManager.getInstance(context.project).closeFile(it) }
                }
                codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = message("testgen.message.regenerate_input"),
                        type = ChatMessageType.Answer,
                        canBeVoted = false
                    )
                )
                val testGenerationEventResponse = client.sendTestGenerationEvent(
                    session.testGenerationJob,
                    session.testGenerationJobGroupName,
                    session.programmingLanguage,
                    session.numberOfUnitTestCasesGenerated,
                    0,
                    session.linesOfCodeGenerated,
                    0,
                    session.charsOfCodeGenerated,
                    0
                )
                LOG.debug {
                    "Successfully sent test generation telemetry. RequestId: ${
                        testGenerationEventResponse.responseMetadata().requestId()}"
                }
                sessionCleanUp(session.tabId)
                codeTestChatHelper.updateUI(
                    promptInputDisabledState = false,
                    promptInputPlaceholder = message("testgen.placeholder.waiting_on_your_inputs"),
                )
            }
             */

            "utg_reject" -> {
                ApplicationManager.getApplication().invokeLater {
                    session.openedDiffFile?.let { FileEditorManager.getInstance(context.project).closeFile(it) }
                }
                codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = message("testgen.message.success"),
                        type = ChatMessageType.Answer,
                        canBeVoted = false,
                        buttons = this.showFeedbackButton()
                    )
                )
                val testGenerationEventResponse = client.sendTestGenerationEvent(
                    session.testGenerationJob,
                    session.testGenerationJobGroupName,
                    session.programmingLanguage,
                    IdeCategory.JETBRAINS,
                    session.numberOfUnitTestCasesGenerated,
                    0,
                    session.linesOfCodeGenerated,
                    0,
                    session.charsOfCodeGenerated,
                    0
                )
                LOG.debug {
                    "Successfully sent test generation telemetry. RequestId: ${
                        testGenerationEventResponse.responseMetadata().requestId()}"
                }

                UiTelemetry.click(null as Project?, "unitTestGeneration_rejectDiff")
                AmazonqTelemetry.utgGenerateTests(
                    cwsprChatProgrammingLanguage = session.programmingLanguage.languageId,
                    hasUserPromptSupplied = session.hasUserPromptSupplied,
                    isFileInWorkspace = true,
                    isSupportedLanguage = true,
                    credentialStartUrl = getStartUrl(project = context.project),
                    jobGroup = session.testGenerationJobGroupName,
                    jobId = session.testGenerationJob,
                    acceptedCount = 0,
                    generatedCount = session.numberOfUnitTestCasesGenerated?.toLong(),
                    acceptedLinesCount = 0,
                    generatedLinesCount = session.linesOfCodeGenerated?.toLong(),
                    acceptedCharactersCount = 0,
                    generatedCharactersCount = session.charsOfCodeGenerated?.toLong(),
                    result = MetricResult.Succeeded,
                    perfClientLatency = session.latencyOfTestGeneration,
                    isCodeBlockSelected = session.isCodeBlockSelected,
                    artifactsUploadDuration = session.artifactUploadDuration,
                    buildPayloadBytes = session.srcPayloadSize,
                    buildZipFileBytes = session.srcZipFileSize,
                    requestId = session.startTestGenerationRequestId,
                    status = Status.REJECTED,
                )
            }
            "utg_feedback" -> {
                sendFeedback()
                UiTelemetry.click(context.project, "unitTestGeneration_provideFeedback")
            }
            "utg_skip_and_finish" -> {
                codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = message("testgen.message.success"),
                        type = ChatMessageType.Answer,
                        canBeVoted = false
                    )
                )
                sessionCleanUp(message.tabId)
            }
            "utg_proceed", "utg_build_and_execute" -> {
                // handle both "Proceed" and "Build and execute" button clicks since their actions are similar
                // TODO: show install dependencies card if needed
                session.conversationState = ConversationState.IN_PROGRESS

                // display build in progress card
                val taskContext = session.buildAndExecuteTaskContext

                taskContext.progressStatus = BuildAndExecuteProgressStatus.RUN_BUILD
                val messageId = updateBuildAndExecuteProgressCard(taskContext.progressStatus, null, session.iteration)
                // TODO: build and execute case
                val buildLogsFile = VirtualFileManager.getInstance().findFileByNioPath(
                    withContext(currentCoroutineContext()) {
                        Files.createTempFile(null, null)
                    }
                )
                if (buildLogsFile == null) {
                    // TODO: handle no log file case
                    return
                }
                LOG.debug {
                    "Q TestGen session: ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                        "tmpFile for build logs:\n ${buildLogsFile.path}"
                }

                runBuildOrTestCommand(taskContext.buildCommand, buildLogsFile, context.project, isBuildCommand = true, taskContext)
                while (taskContext.buildExitCode < 0) {
                    // wait until build command finished
                    delay(1000)
                }

                // TODO: only go to future iterations when buildExitCode or testExitCode > 0, right now iterate regardless
                if (taskContext.buildExitCode > 0) {
                    // TODO: handle build failure case
                    // ...
//                    return
                }
                taskContext.progressStatus = BuildAndExecuteProgressStatus.RUN_EXECUTION_TESTS
                updateBuildAndExecuteProgressCard(taskContext.progressStatus, messageId, session.iteration)

                val testLogsFile = VirtualFileManager.getInstance().findFileByNioPath(
                    withContext(currentCoroutineContext()) {
                        Files.createTempFile(null, null)
                    }
                )
                if (testLogsFile == null) {
                    // TODO: handle no log file case
                    return
                }
                LOG.debug {
                    "Q TestGen session: ${codeTestChatHelper.getActiveCodeTestTabId()}: " +
                        "tmpFile for test logs:\n ${buildLogsFile.path}"
                }
                delay(1000)
                runBuildOrTestCommand(taskContext.executionCommand, testLogsFile, context.project, isBuildCommand = false, taskContext)
                while (taskContext.testExitCode < 0) {
                    // wait until test command finished
                    delay(1000)
                }

                if (taskContext.testExitCode == 0) {
                    taskContext.progressStatus = BuildAndExecuteProgressStatus.TESTS_EXECUTED
                    updateBuildAndExecuteProgressCard(taskContext.progressStatus, messageId, session.iteration)
                    codeTestChatHelper.addAnswer(
                        CodeTestChatMessageContent(
                            message = message("testgen.message.success"),
                            type = ChatMessageType.Answer,
                            canBeVoted = false
                        )
                    )
                    sessionCleanUp(message.tabId)
                    return
                }

                // has test failure, we will zip the latest project and invoke backend again
                taskContext.progressStatus = BuildAndExecuteProgressStatus.FIXING_TEST_CASES
                val buildAndExecuteMessageId = updateBuildAndExecuteProgressCard(taskContext.progressStatus, messageId, session.iteration)

                val previousUTGIterationContext = PreviousUTGIterationContext(
                    buildLogFile = buildLogsFile,
                    testLogFile = testLogsFile,
                    selectedFile = session.selectedFile,
                    buildAndExecuteMessageId = buildAndExecuteMessageId
                )

                val job = CodeWhispererUTGChatManager.getInstance(context.project).generateTests("", codeTestChatHelper, previousUTGIterationContext, null)
                job?.join()

                taskContext.progressStatus = BuildAndExecuteProgressStatus.PROCESS_TEST_RESULTS
                // session.iteration already updated in generateTests
                updateBuildAndExecuteProgressCard(taskContext.progressStatus, messageId, session.iteration - 1)
            }
            "utg_modify_command" -> {
                // TODO allow user input to modify the command
                codeTestChatHelper.addAnswer(
                    CodeTestChatMessageContent(
                        message = """
                            Sure. Let me know which command you'd like to modify or you could also provide all command lines you'd like me to run.
                            
                        """.trimIndent(),
                        type = ChatMessageType.Answer,
                        canBeVoted = false
                    )
                )
                session.conversationState = ConversationState.WAITING_FOR_BUILD_COMMAND_INPUT
            }
            "utg_install_and_continue" -> {
                // TODO: install dependencies and build
            }
            "stop_test_generation" -> {
                UiTelemetry.click(null as Project?, "unitTestGeneration_cancelTestGenerationProgress")
                session.isGeneratingTests = false
                sessionCleanUp(message.tabId)
                return
            }
            else -> {
                // Handle other cases or do nothing
            }
        }
    }

    override suspend fun processAuthFollowUpClick(message: IncomingCodeTestMessage.AuthFollowUpWasClicked) {
        codeTestChatHelper.sendUpdatePromptProgress(message.tabId, null)
        authController.handleAuth(context.project, message.authType)
        codeTestChatHelper.sendAuthenticationInProgressMessage(message.tabId) // show user that authentication is in progress
        codeTestChatHelper.sendChatInputEnabledMessage(false) // disable the input field while authentication is in progress
        sessionCleanUp(codeTestChatHelper.getActiveSession().tabId)
    }

    private suspend fun updateBuildAndExecuteProgressCard(
        currentStatus: BuildAndExecuteProgressStatus,
        messageId: String?,
        iterationNum: Int,
    ): String? {
        val updatedText = constructBuildAndExecutionSummaryText(currentStatus, iterationNum)

        if (currentStatus == BuildAndExecuteProgressStatus.RUN_BUILD) {
            val buildAndExecuteMessageId = codeTestChatHelper.addAnswer(
                CodeTestChatMessageContent(
                    message = updatedText,
                    type = ChatMessageType.AnswerStream,
                    canBeVoted = true,
                )
            )
            // For streaming effect
            codeTestChatHelper.updateAnswer(
                CodeTestChatMessageContent(type = ChatMessageType.AnswerPart),
                messageIdOverride = buildAndExecuteMessageId
            )
            codeTestChatHelper.updateUI(
                loadingChat = true,
                promptInputDisabledState = true,
            )
            return buildAndExecuteMessageId
        } else {
            val isLastStage = currentStatus == BuildAndExecuteProgressStatus.PROCESS_TEST_RESULTS
            codeTestChatHelper.updateAnswer(
                CodeTestChatMessageContent(
                    message = updatedText,
                    type = if (isLastStage) ChatMessageType.Answer else ChatMessageType.AnswerPart,
                    canBeVoted = true
                ),
                messageId
            )
            codeTestChatHelper.updateUI(
                loadingChat = !isLastStage,
                promptInputDisabledState = true,
            )
            return messageId
        }
    }

    /**
     * Perform Session CleanUp in below cases
     * 1. UTG success workflow or UTG build success.
     * 2. If user click Reject or SkipAndFinish button
     * 3. Error while generating unit tests
     * 4. After finishing 3 build loop iterations
     * 5. Closing a Q-Test tab
     * 6. Progress bar cancel
     */
    private suspend fun sessionCleanUp(tabId: String) {
        // TODO: May be need to clear all the session data like jobId, jobGroupName and etc along with temp build log files
        chatSessionStorage.deleteSession(tabId)
        codeTestChatHelper.updateUI(
            promptInputDisabledState = false
        )
        codeTestChatHelper.sendUpdatePlaceholder(tabId, message("testgen.placeholder.enter_slash_quick_actions"))
    }

    private fun openOrCreateTestFileAndApplyDiff(
        project: Project,
        testFileAbsolutePath: Path,
        afterContent: String,
        openedDiffFile: VirtualFile?,
    ) {
        val virtualFile: VirtualFile?

        // Check if the file exists
        if (Files.exists(testFileAbsolutePath)) {
            // File exists, get the VirtualFile
            virtualFile = LocalFileSystem.getInstance().findFileByPath(testFileAbsolutePath.toString())
            if (virtualFile == null) return
            val beforeContent = String(virtualFile.contentsToByteArray()) // Read the existing content

            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    applyDiffAndWriteContent(virtualFile, beforeContent, afterContent)
                }
            }
        } else {
            // File does not exist, create it
            virtualFile = createFile(testFileAbsolutePath, afterContent)
        }
        if (virtualFile == null) return
        ApplicationManager.getApplication().invokeLater {
            openedDiffFile?.let { FileEditorManager.getInstance(project).closeFile(it) }
            FileEditorManager.getInstance(project).openFile(virtualFile, true) // Open the file in editor
        }
    }

    // Function to create the file and write content
    private fun createFile(path: Path, content: String): VirtualFile? {
        val parentPath = path.parent
        if (!Files.exists(parentPath)) {
            Files.createDirectories(parentPath) // Ensure parent directories exist
        }

        val file = Files.createFile(path) // Create the file
        Files.writeString(file, content) // Write the afterContent to the file
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
    }

    // Function to apply the diff and write the new content
    private fun applyDiffAndWriteContent(
        virtualFile: VirtualFile,
        beforeContent: String,
        afterContent: String,
    ) {
        if (beforeContent == afterContent) return
        virtualFile.setBinaryContent(afterContent.toByteArray()) // Update the file content
    }

    // Return test file content if it exists, return an empty string otherwise.
    private fun getFileContentAtTestFilePath(projectRoot: String, testFileRelativePathToProjectRoot: String): String {
        val testFileAbsolutePath = Paths.get(projectRoot, testFileRelativePathToProjectRoot)
        return if (Files.exists(testFileAbsolutePath)) {
            Files.readString(testFileAbsolutePath) // Read and return the file content
        } else {
            "" // Return an empty string if the file does not exist
        }
    }

    // Return generated test file content
    private fun getGeneratedFileContent(session: Session): String {
        val generateFileContent = session.generatedTestDiffs[session.testFileRelativePathToProjectRoot].toString()
        return generateFileContent
    }

    /*
     If shortAnswer has buildCommand, use it, if it doesn't hardcode it according to the user type(internal or not)
    private fun getBuildCommand(tabId: String): String {
        val buildCommand = codeTestChatHelper.getSession(tabId).shortAnswer.buildCommand
        if (buildCommand != null) return buildCommand

        // TODO: remove hardcode
        return "pip install -e ."
    }

    private fun getExecutionCommand(tabId: String): String {
        val executionCommand = codeTestChatHelper.getSession(tabId).shortAnswer.executionCommand
        if (executionCommand != null) return executionCommand

        // TODO: remove hardcode
        return "pytest"
    }
     */

    private suspend fun newTabOpened(tabId: String) {
        // TODO: the logic of checking auth is needed (for calling APIs) but need refactor with FeatureDev
        val session: Session?
        try {
            session = codeTestChatHelper.getSession(tabId)
            LOG.debug {
                "$FEATURE_NAME:" +
                    " Session created with id: ${session.tabId}"
            }
            val credentialState = authController.getAuthNeededStates(context.project).amazonQ
            if (credentialState != null) {
                messenger.sendAuthNeededException(
                    tabId = tabId,
                    triggerId = UUID.randomUUID().toString(),
                    credentialState = credentialState,
                )
                session.isAuthenticating = true
                return
            }
        } catch (err: Exception) {
            messenger.publish(
                CodeTestChatMessage(
                    tabId = tabId,
                    messageType = ChatMessageType.Answer,
                    message = message("codescan.chat.message.error_request")
                )
            )
            return
        }
    }

    data class ActiveFileInfo(
        val filePath: String,
        val fileName: String,
        val fileLanguage: CodeWhispererProgrammingLanguage,
        val fileInWorkspace: Boolean = true,
    )

    private suspend fun updateUIState() {
        codeTestChatHelper.updateUI(
            promptInputDisabledState = false,
            promptInputPlaceholder = message("testgen.placeholder.newtab")
        )
    }

    private suspend fun handleInvalidFileState(tabId: String) {
        codeTestChatHelper.addNewMessage(
            CodeTestChatMessageContent(
                message = message("testgen.no_file_found"),
                type = ChatMessageType.Answer,
                canBeVoted = false
            ),
            tabId,
            false
        )
        sessionCleanUp(codeTestChatHelper.getActiveSession().tabId)
        updateUIState()
    }

    private suspend fun checkActiveFileInIDE(
        project: Project,
        message: IncomingCodeTestMessage.StartTestGen,
    ): ActiveFileInfo? {
        try {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val activeEditor = fileEditorManager.selectedEditor
            val activeFile = fileEditorManager.selectedFiles.firstOrNull()
            val projectRoot = project.basePath?.let { Path.of(it) }?.toFile()?.toVirtualFile() ?: run {
                project.guessProjectDir() ?: error("Cannot guess base directory for project ${project.name}")
            }

            if (activeEditor == null || activeFile == null) {
                handleInvalidFileState(message.tabId)
                return null
            }
            val programmingLanguage: CodeWhispererProgrammingLanguage = activeFile.programmingLanguage()
            if (programmingLanguage.languageId.equals("unknown", ignoreCase = true)) {
                handleInvalidFileState(message.tabId)
                return null
            }
            return ActiveFileInfo(
                filePath = activeFile.path,
                fileName = activeFile.name,
                fileLanguage = programmingLanguage,
                fileInWorkspace = activeFile.isWithin(projectRoot)
            )
        } catch (e: Exception) {
            LOG.debug { "Error checking active file: $e" }
            updateUIState()
            codeTestChatHelper.addNewMessage(
                CodeTestChatMessageContent(message = e.message, type = ChatMessageType.Answer, canBeVoted = false),
                message.tabId,
                false
            )
            return null
        }
    }

    private fun File.toVirtualFile() = LocalFileSystem.getInstance().findFileByIoFile(this)

    /* UTG Tab Chat input use cases:
     * 1. If User exits the flow and want to start a new generate unit test cycle.
     * 2. If User clicks on Modify build command option and can enter the build command from chat input
     * 3. If User trys to regenerate the unit tests case using Regenerate button
     * */
    private suspend fun handleChat(tabId: String, message: String) {
        val session = codeTestChatHelper.getActiveSession()
        session.projectRoot
        val credentialState = authController.getAuthNeededStates(context.project).amazonQ
        if (credentialState != null) {
            messenger.sendAuthNeededException(
                tabId = tabId,
                triggerId = UUID.randomUUID().toString(),
                credentialState = credentialState,
            )
            session.isAuthenticating = true
            return
        }
        LOG.debug {
            "$FEATURE_NAME: " +
                "Processing message: $message " +
                "tabId: $tabId"
        }
        broadcastQEvent(QFeatureEvent.INVOCATION)
        when (session.conversationState) {
            ConversationState.WAITING_FOR_BUILD_COMMAND_INPUT -> handleBuildCommandInput(session, message)
            ConversationState.WAITING_FOR_REGENERATE_INPUT -> handleRegenerateInput(session, message)
            else -> this.processStartTestGen(
                message = IncomingCodeTestMessage.StartTestGen(
                    tabId = session.tabId,
                    prompt = message,
                )
            )
        }
    }

    private suspend fun handleRegenerateInput(session: Session, message: String) {
        codeTestChatHelper.addAnswer(
            CodeTestChatMessageContent(
                message,
                type = ChatMessageType.Prompt,
                canBeVoted = false
            )
        )
        session.conversationState = ConversationState.IDLE
        // Start the UTG workflow with new user prompt
        CodeWhispererUTGChatManager.getInstance(
            context.project
        ).generateTests(message, codeTestChatHelper, null, getEditorSelectionRange(context.project))
    }

    private suspend fun handleBuildCommandInput(session: Session, message: String) {
        // TODO: Logic to store modified build command
        session.conversationState = ConversationState.IDLE
        // for now treat user's input as a single build command.
        session.buildAndExecuteTaskContext.buildCommand = message
        session.buildAndExecuteTaskContext.executionCommand = ""
        codeTestChatHelper.addAnswer(
            CodeTestChatMessageContent(
                message = """
                           Would you like me to help build and execute the test? I'll run following commands
                           
                           ```sh
                           $message
                           ```
                """.trimIndent(),
                type = ChatMessageType.Answer,
                canBeVoted = true,
                buttons = listOf(
                    Button(
                        "utg_skip_and_finish",
                        "Skip and finish task",
                        keepCardAfterClick = true,
                        position = "outside",
                        status = "info",
                    ),
                    Button(
                        "utg_modify_command",
                        "Modify commands",
                        keepCardAfterClick = true,
                        position = "outside",
                        status = "info",
                    ),
                    Button(
                        "utg_build_and_execute",
                        "Build and execute",
                        keepCardAfterClick = true,
                        position = "outside",
                        status = "info",
                    ),
                )
            )
        )
        codeTestChatHelper.updateUI(
            promptInputDisabledState = true,
        )
        println(message)
    }

    private fun sendFeedback() {
        runInEdt {
            TestGenFeedbackDialog(
                context.project,
                codeTestChatHelper.getActiveSession().startTestGenerationRequestId,
                codeTestChatHelper.getActiveSession().testGenerationJob
            ).show()
        }
    }

    private fun showFeedbackButton(): MutableList<Button> {
        val buttonList = mutableListOf<Button>()
        if (isInternalUser(getStartUrl(context.project))) {
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
        return buttonList
    }

    companion object {
        private val LOG = getLogger<CodeTestChatController>()

        private val objectMapper = jacksonObjectMapper()
    }
}
