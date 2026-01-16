// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.server

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CfnLspExceptionTest {

    @Test
    fun `exception contains message and error code`() {
        val exception = CfnLspException(
            "Test error message",
            CfnLspException.ErrorCode.DOWNLOAD_FAILED
        )

        assertThat(exception.message).isEqualTo("Test error message")
        assertThat(exception.errorCode).isEqualTo(CfnLspException.ErrorCode.DOWNLOAD_FAILED)
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `exception contains cause when provided`() {
        val cause = RuntimeException("Root cause")
        val exception = CfnLspException(
            "Wrapper message",
            CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED,
            cause
        )

        assertThat(exception.message).isEqualTo("Wrapper message")
        assertThat(exception.errorCode).isEqualTo(CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED)
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `error codes cover all failure scenarios`() {
        val errorCodes = CfnLspException.ErrorCode.values()

        assertThat(errorCodes).containsExactlyInAnyOrder(
            CfnLspException.ErrorCode.MANIFEST_FETCH_FAILED,
            CfnLspException.ErrorCode.NO_COMPATIBLE_VERSION,
            CfnLspException.ErrorCode.DOWNLOAD_FAILED,
            CfnLspException.ErrorCode.EXTRACTION_FAILED,
            CfnLspException.ErrorCode.NODE_NOT_FOUND
        )
    }
}
