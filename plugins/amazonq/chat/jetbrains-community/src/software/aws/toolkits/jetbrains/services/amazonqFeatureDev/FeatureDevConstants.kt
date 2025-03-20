// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev

const val FEATURE_EVALUATION_PRODUCT_NAME = "FeatureDev"

const val FEATURE_NAME = "Amazon Q Developer Agent for software development"

@Suppress("MaxLineLength")
const val GENERATE_DEV_FILE_PROMPT = "generate a devfile in my repository. Note that you should only use devfile version 2.0.0 and the only supported commands are install, build and test (are all optional). so you may have to bundle some commands together using '&&'. also you can use \"public.ecr.aws/aws-mde/universal-image:latest\" as universal image if you aren’t sure which image to use. here is an example for a node repository (but don't assume it's always a node project. look at the existing repository structure before generating the devfile): schemaVersion: 2.0.0 components: - name: dev container: image: public.ecr.aws/aws-mde/universal-image:latest commands: - id: install exec: component: dev commandLine: \"npm install\" - id: build exec: component: dev commandLine: \"npm run build\" - id: test exec: component: dev commandLine: \"npm run test\""

// Max number of times a user can attempt to retry a code generation request if it fails
const val CODE_GENERATION_RETRY_LIMIT = 3

// The default retry limit used when the session could not be found
const val DEFAULT_RETRY_LIMIT = 0

// Max allowed size for a repository in bytes
const val MAX_PROJECT_SIZE_BYTES: Long = 200 * 1024 * 1024

val CLIENT_ERROR_MESSAGES = setOf(
    "StartTaskAssistCodeGeneration reached for this month.",
    "The folder you chose did not contain any source files in a supported language. Choose another folder and try again.",
    "reached the quota for number of iterations on code generation."
)

enum class ModifySourceFolderErrorReason(
    private val reasonText: String,
) {
    ClosedBeforeSelection("ClosedBeforeSelection"),
    NotInWorkspaceFolder("NotInWorkspaceFolder"),
    ;

    override fun toString(): String = reasonText
}

enum class FeatureDevOperation(private val operationName: String) {
    StartTaskAssistCodeGeneration("StartTaskAssistCodeGenerator"),
    CreateConversation("CreateConversation"),
    CreateUploadUrl("CreateUploadUrl"),
    GenerateCode("GenerateCode"),
    GetTaskAssistCodeGeneration("GetTaskAssistCodeGenerator"),
    ExportTaskAssistArchiveResult("ExportTaskAssistArchiveResult"),
    UploadToS3("UploadToS3"),
    ;

    override fun toString(): String = operationName
}

enum class MetricDataOperationName(private val operationName: String) {
    StartCodeGeneration("StartCodeGeneration"),
    EndCodeGeneration("EndCodeGeneration"),
    ;

    override fun toString(): String = operationName
}

enum class MetricDataResult(private val resultName: String) {
    Success("Success"),
    Fault("Fault"),
    Error("Error"),
    LlmFailure("LLMFailure"),
    ;

    override fun toString(): String = resultName
}
