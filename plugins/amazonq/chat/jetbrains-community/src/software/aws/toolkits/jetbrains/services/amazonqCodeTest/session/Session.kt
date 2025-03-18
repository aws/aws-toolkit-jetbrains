// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqCodeTest.session

import com.intellij.openapi.vfs.VirtualFile
import software.amazon.awssdk.services.codewhispererruntime.model.PackageInfo
import software.amazon.awssdk.services.codewhispererruntime.model.Reference
import software.aws.toolkits.jetbrains.services.amazonqCodeTest.ConversationState
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.languages.CodeWhispererUnknownLanguage

data class Session(val tabId: String) {
    var isAuthenticating: Boolean = false
    var authNeededNotified: Boolean = false
    var conversationState: ConversationState = ConversationState.IDLE

    // Generating unit tests
    var isGeneratingTests: Boolean = false
    var programmingLanguage: CodeWhispererProgrammingLanguage = CodeWhispererUnknownLanguage.INSTANCE
    var testGenerationJob: String = ""
    var testGenerationJobGroupName: String = ""
    var startTestGenerationRequestId: String = ""

    // Telemetry
    var hasUserPromptSupplied: Boolean = false
    var numberOfUnitTestCasesGenerated: Int? = null
    var linesOfCodeGenerated: Int? = null
    var charsOfCodeGenerated: Int? = null
    var startTimeOfTestGeneration: Double = 0.0
    var latencyOfTestGeneration: Double = 0.0
    var isCodeBlockSelected: Boolean = false
    var srcPayloadSize: Long = 0
    var srcZipFileSize: Long = 0
    var artifactUploadDuration: Long = 0

    // First iteration will have a value of 1
    var iteration: Int = 0
    var projectRoot: String = "/"

    var packageInfoList: List<PackageInfo> = emptyList()
    var selectedFile: VirtualFile? = null
    var testFileRelativePathToProjectRoot: String = ""
    var testFileName: String = ""
    var viewDiffMessageId: String? = null
    var openedDiffFile: VirtualFile? = null
    val generatedTestDiffs = mutableMapOf<String, String>()
    var codeReferences: List<Reference>? = null

    // Build loop execution
    val buildAndExecuteTaskContext = BuildAndExecuteTaskContext()
}

data class BuildAndExecuteTaskContext(
    var buildCommand: String = "",
    var executionCommand: String = "",
    var buildExitCode: Int = -1,
    var testExitCode: Int = -1,
    var progressStatus: BuildAndExecuteProgressStatus = BuildAndExecuteProgressStatus.START_STEP,
)

enum class BuildAndExecuteProgressStatus {
    START_STEP,
    INSTALL_DEPENDENCIES,
    RUN_BUILD,
    RUN_EXECUTION_TESTS,
    TESTS_EXECUTED,
    FIXING_TEST_CASES,
    PROCESS_TEST_RESULTS,
}

const val UTG_CHAT_MAX_ITERATION = 4
