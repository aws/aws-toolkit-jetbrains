// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class LspInstallExceptionTest {

    @Test
    fun `exception contains message and error code`() {
        val exception = LspInstallException(
            "Test error message",
            LspInstallException.ErrorCode.DOWNLOAD_FAILED
        )

        assertThat(exception.message).isEqualTo("Test error message")
        assertThat(exception.errorCode).isEqualTo(LspInstallException.ErrorCode.DOWNLOAD_FAILED)
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `exception contains cause when provided`() {
        val cause = RuntimeException("Root cause")
        val exception = LspInstallException(
            "Wrapper message",
            LspInstallException.ErrorCode.MANIFEST_FETCH_FAILED,
            cause
        )

        assertThat(exception.message).isEqualTo("Wrapper message")
        assertThat(exception.errorCode).isEqualTo(LspInstallException.ErrorCode.MANIFEST_FETCH_FAILED)
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `error codes cover all failure scenarios including NODE_NOT_FOUND`() {
        val errorCodes = LspInstallException.ErrorCode.entries.toTypedArray()

        assertThat(errorCodes).containsExactlyInAnyOrder(
            LspInstallException.ErrorCode.MANIFEST_FETCH_FAILED,
            LspInstallException.ErrorCode.NO_COMPATIBLE_VERSION,
            LspInstallException.ErrorCode.DOWNLOAD_FAILED,
            LspInstallException.ErrorCode.EXTRACTION_FAILED,
            LspInstallException.ErrorCode.HASH_VERIFICATION_FAILED,
            LspInstallException.ErrorCode.NODE_NOT_FOUND,
        )
    }

    @Test
    fun `NODE_NOT_FOUND error code exists for runtime resolution failures`() {
        val exception = LspInstallException(
            "Node.js not found",
            LspInstallException.ErrorCode.NODE_NOT_FOUND
        )
        assertThat(exception.errorCode).isEqualTo(LspInstallException.ErrorCode.NODE_NOT_FOUND)
    }
}
