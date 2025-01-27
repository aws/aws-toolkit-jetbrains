// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev

import software.aws.toolkits.jetbrains.services.amazonq.RepoSizeError
import software.aws.toolkits.jetbrains.services.telemetry.SafeMessageError
import software.aws.toolkits.resources.message

/**
 * FeatureDevException models failures from feature dev operations.
 *
 * - Each failure is annotated based on className, operation, and a short desc. Use the `reason()` and `reasonDesc()` members for instrumentation.
 * - To throw an exception without modeling, throw FeatureDevException directly.
 */
open class FeatureDevException(override val message: String?, val operation: String, val desc: String?, override val cause: Throwable? = null) :
    RuntimeException() {
    fun reason(): String = this.javaClass.simpleName

    fun reasonDesc(): String =
        when (desc) {
            desc -> "$operation | Description: $desc"
            else -> operation
        }
}

class NoChangeRequiredException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.no_change_required_exception"), operation, desc, cause), SafeMessageError

class EmptyPatchException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.guardrails"), operation, desc, cause), SafeMessageError

class ContentLengthException(
    override val message: String = message("amazonqFeatureDev.content_length.error_text"),
    operation: String,
    desc: String?,
    cause: Throwable? = null,
) :
    RepoSizeError, FeatureDevException(message, operation, desc, cause), SafeMessageError

class ZipFileCorruptedException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException("The zip file is corrupted", operation, desc, cause), SafeMessageError

class UploadURLExpired(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.upload_url_expiry"), operation, desc, cause), SafeMessageError

class CodeIterationLimitException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.code_generation.iteration_limit.error_text"), operation, desc, cause), SafeMessageError

class MonthlyConversationLimitError(message: String, operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message, operation, desc, cause)

class GuardrailsException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.guardrails"), operation, desc, cause), SafeMessageError

class PromptRefusalException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.prompt_refusal"), operation, desc, cause), SafeMessageError

class ThrottlingException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.throttling"), operation, desc, cause), SafeMessageError

class ExportParseException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.export_parsing_error"), operation, desc, cause), SafeMessageError

class CodeGenerationException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.code_generation.failed_generation"), operation, desc, cause), SafeMessageError

class UploadCodeException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.upload_code"), operation, desc, cause), SafeMessageError

class ConversationIdNotFoundException(operation: String, desc: String?, cause: Throwable? = null) :
    FeatureDevException(message("amazonqFeatureDev.exception.conversation_not_found"), operation, desc, cause), SafeMessageError

val denyListedErrors = arrayOf("Deserialization error", "Inaccessible host", "UnknownHost")
fun createUserFacingErrorMessage(message: String?): String? =
    if (message != null && denyListedErrors.any { message.contains(it) }) "$FEATURE_NAME API request failed" else message
