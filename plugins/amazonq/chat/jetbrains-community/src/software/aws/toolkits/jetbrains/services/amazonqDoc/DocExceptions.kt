// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqDoc

import software.aws.toolkits.jetbrains.services.amazonq.project.RepoSizeError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ClientException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.LlmException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ServiceException
import software.aws.toolkits.resources.message

open class DocClientException(
    message: String,
    operation: String,
    desc: String?,
    cause: Throwable? = null,
    val remainingIterations: Int? = null,
) : ClientException(message, operation, desc, cause)

val denyListedErrors = arrayOf("Deserialization error", "Inaccessible host", "UnknownHost")
fun createUserFacingErrorMessage(message: String?): String? =
    if (message != null && denyListedErrors.any { message.contains(it) }) "$FEATURE_NAME API request failed" else message

class ReadmeTooLargeException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
    remainingIterations: Int? = null,
) : DocClientException(message("amazonqDoc.exception.readme_too_large"), operation, desc, cause, remainingIterations)

class ReadmeUpdateTooLargeException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
    remainingIterations: Int? = null,
) : DocClientException(message("amazonqDoc.exception.readme_update_too_large"), operation, desc, cause, remainingIterations)

class ContentLengthException(
    override val message: String = message("amazonqDoc.exception.content_length_error"),
    operation: String,
    desc: String?,
    cause: Throwable? = null,
) :
    RepoSizeError, ClientException(message, operation, desc, cause)

class WorkspaceEmptyException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
) : DocClientException(message("amazonqDoc.exception.workspace_empty"), operation, desc, cause)

class PromptUnrelatedException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
    remainingIterations: Int? = null,
) : DocClientException(message("amazonqDoc.exception.prompt_unrelated"), operation, desc, cause, remainingIterations)

class PromptTooVagueException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
    remainingIterations: Int? = null,
) : DocClientException(message("amazonqDoc.exception.prompt_too_vague"), operation, desc, cause, remainingIterations)

class PromptRefusalException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
    remainingIterations: Int? = null,
) : DocClientException(message("amazonqFeatureDev.exception.prompt_refusal"), operation, desc, cause, remainingIterations)

class GuardrailsException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
) : DocClientException(message("amazonqDoc.error_text"), operation, desc, cause)

class NoChangeRequiredException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
) : DocClientException(message("amazonqDoc.exception.no_change_required"), operation, desc, cause)

class EmptyPatchException(operation: String, desc: String?, cause: Throwable? = null) :
    LlmException(message("amazonqDoc.error_text"), operation, desc, cause)

class ThrottlingException(
    operation: String,
    desc: String?,
    cause: Throwable? = null,
) : DocClientException(message("amazonqFeatureDev.exception.throttling"), operation, desc, cause)

class DocGenerationException(operation: String, desc: String?, cause: Throwable? = null) :
    ServiceException(message("amazonqDoc.error_text"), operation, desc, cause)
