// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.codetest

import software.aws.toolkits.resources.message

open class CodeTestException(
    override val message: String?,
    val statusCode: String? = "400",
    val code: String? = "DefaultError",
    val uiMessage: String? = message(
        "testgen.error.generic_error_message"
    ),
) : RuntimeException()

open class CodeTestServerException(
    override val message: String?,
    val statusCode: String? = "400",
    val code: String? = "DefaultError",
    val uiMessage: String? = message(
        "testgen.error.generic_technical_error_message"
    ),
) : RuntimeException()

internal fun noFileOpenError(): Nothing =
    throw CodeTestException(message("codewhisperer.codescan.no_file_open"), "400", "ProjectZipError")

internal fun fileTooLarge(): Nothing =
    throw CodeTestException(message("codewhisperer.codescan.file_too_large_telemetry"), "400", "ProjectZipError")

internal fun cannotFindFile(errorMessage: String, filepath: String): Nothing =
    error(message("codewhisperer.codescan.file_not_found", filepath, errorMessage))

internal fun cannotFindValidFile(errorMessage: String): Nothing =
    throw CodeTestException(errorMessage, "400", "ProjectZipError")

internal fun cannotFindBuildArtifacts(errorMessage: String): Nothing =
    throw CodeTestException(errorMessage, "400", "ProjectZipError")

internal fun invalidSourceZipError(): Nothing =
    throw CodeTestException(message("codewhisperer.codescan.invalid_source_zip_telemetry"), "400", "InvalidSourceZipError")

fun codeTestServerException(errorMessage: String, statusCode: String?, code: String?, uiMessage: String?): Nothing =
    throw CodeTestServerException(errorMessage, statusCode, code, uiMessage)

fun testGenStoppedError(): Nothing =
    throw CodeTestException(message("testgen.message.cancelled"), "400", "TestGenCancelled", message("testgen.message.cancelled"))
