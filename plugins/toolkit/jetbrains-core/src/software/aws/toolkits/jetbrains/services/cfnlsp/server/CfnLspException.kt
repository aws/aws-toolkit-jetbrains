// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

class CfnLspException(
    message: String,
    val errorCode: ErrorCode,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class ErrorCode {
        MANIFEST_FETCH_FAILED,
        NO_COMPATIBLE_VERSION,
        DOWNLOAD_FAILED,
        EXTRACTION_FAILED,
        NODE_NOT_FOUND,
        HASH_VERIFICATION_FAILED
    }
}
