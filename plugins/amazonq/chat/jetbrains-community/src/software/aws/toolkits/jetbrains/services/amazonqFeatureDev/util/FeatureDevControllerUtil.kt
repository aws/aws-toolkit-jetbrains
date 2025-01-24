// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

import software.aws.toolkits.jetbrains.services.amazonq.RepoSizeLimitError
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeGenerationException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.CodeIterationLimitException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ContentLengthException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ConversationIdNotFoundException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.EmptyPatchException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ExportParseException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.GuardrailsException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.NoChangeRequiredException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.PromptRefusalException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ThrottlingException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.UploadCodeException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.UploadURLExpired
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.ZipFileCorruptedException
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUp
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpIcons
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpStatusType
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.FollowUpTypes
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session.SessionStatePhase
import software.aws.toolkits.resources.message
import java.io.PrintWriter
import java.io.StringWriter

enum class InsertAction {
    ALL,
    REMAINING,
    CONTINUE,
    AUTO_CONTINUE,
}

fun getFollowUpOptions(phase: SessionStatePhase?, type: InsertAction): List<FollowUp> {
    when (phase) {
        SessionStatePhase.CODEGEN -> {
            return listOf(
                FollowUp(
                    pillText = when (type) {
                        InsertAction.ALL -> message("amazonqFeatureDev.follow_up.insert_all_code")
                        InsertAction.REMAINING -> message("amazonqFeatureDev.follow_up.insert_remaining_code")
                        InsertAction.CONTINUE -> message("amazonqFeatureDev.follow_up.continue")
                        InsertAction.AUTO_CONTINUE -> message("amazonqFeatureDev.follow_up.continue")
                    },
                    type = FollowUpTypes.INSERT_CODE,
                    icon = FollowUpIcons.Ok,
                    status = FollowUpStatusType.Success
                ),
                FollowUp(
                    pillText = message("amazonqFeatureDev.follow_up.provide_feedback_and_regenerate"),
                    type = FollowUpTypes.PROVIDE_FEEDBACK_AND_REGENERATE_CODE,
                    icon = FollowUpIcons.Refresh,
                    status = FollowUpStatusType.Info
                )
            )
        }
        else -> return emptyList()
    }
}

const val RECURSION_LIMIT = 3

// This function constructs a string similar to error.printStackTrace() for telemetry
// But include error messages only for safe exceptions
// i.e. exceptions with deterministic error messages and do not include sensitive data
fun getStackTraceForError(error: Throwable): String {
    val writer = StringWriter()
    val printer = PrintWriter(writer)
    val seenExceptions = mutableSetOf<Throwable>()

    fun printExceptionDetails(throwable: Throwable, depth: Int, prefix: String = "") {
        if (depth >= RECURSION_LIMIT || throwable in seenExceptions) {
            return
        }
        seenExceptions.add(throwable)

        when (throwable) {
            is NoChangeRequiredException,
            is EmptyPatchException,
            is ContentLengthException,
            is ZipFileCorruptedException,
            is UploadURLExpired,
            is CodeIterationLimitException,
            is GuardrailsException,
            is PromptRefusalException,
            is ThrottlingException,
            is ExportParseException,
            is CodeGenerationException,
            is UploadCodeException,
            is ConversationIdNotFoundException,
            is RepoSizeLimitError,
            -> {
                printer.println("$prefix${throwable.javaClass.name}: ${throwable.message}")
            }
            else -> {
                // No message included
                printer.println("$prefix${throwable.javaClass.name}")
            }
        }

        throwable.stackTrace.forEach { element ->
            printer.println("$prefix\tat $element")
        }

        throwable.cause?.let { cause ->
            printer.println("$prefix\tCaused by: ")
            printExceptionDetails(cause, depth + 1, "$prefix\t")
        }

        throwable.suppressed?.forEach { suppressed ->
            printer.println("$prefix\tSuppressed: ")
            printExceptionDetails(suppressed, depth + 1, "$prefix\t")
        }
    }

    printExceptionDetails(error, 0)
    return writer.toString()
}
