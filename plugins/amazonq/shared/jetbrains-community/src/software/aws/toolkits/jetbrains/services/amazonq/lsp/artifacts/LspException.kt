// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.artifacts

class LspException(message: String, private val errorCode: ErrorCode, cause: Throwable? = null) : Exception(message, cause) {

    enum class ErrorCode {
        MANIFEST_FETCH_FAILED,
        DOWNLOAD_FAILED,
        HASH_MISMATCH,
        TARGET_NOT_FOUND,
        NO_COMPATIBLE_LSP_VERSION,
        UNZIP_FAILED,
    }

    override fun toString(): String = buildString {
        append("LSP Error [$errorCode]: $message")
        cause?.let { append(", Cause: ${it.message}") }
    }
}
