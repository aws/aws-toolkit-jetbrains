// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

const val FEATURE_EVALUATION_PRODUCT_NAME = "DocGeneration"

const val FEATURE_NAME = "Amazon Q Documentation Generation"

// Max number of times a user can attempt to retry a code generation request if it fails
const val CODE_GENERATION_RETRY_LIMIT = 3

// The default retry limit used when the session could not be found
const val DEFAULT_RETRY_LIMIT = 0

// Max allowed size for a repository in bytes
const val MAX_PROJECT_SIZE_BYTES: Long = 200 * 1024 * 1024

const val INFRA_DIAGRAM_PREFIX = "infra."
const val DIAGRAM_SVG_EXT = "svg"
const val DIAGRAM_DOT_EXT = "dot"
val SUPPORTED_DIAGRAM_EXT_SET: Set<String> = setOf(DIAGRAM_SVG_EXT, DIAGRAM_DOT_EXT)
val SUPPORTED_DIAGRAM_FILE_NAME_SET: Set<String> = SUPPORTED_DIAGRAM_EXT_SET.map { INFRA_DIAGRAM_PREFIX + it }.toSet()

enum class MetricDataOperationName(private val operationName: String) {
    StartDocGeneration("StartDocGeneration"),
    EndDocGeneration("EndDocGeneration"),
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
