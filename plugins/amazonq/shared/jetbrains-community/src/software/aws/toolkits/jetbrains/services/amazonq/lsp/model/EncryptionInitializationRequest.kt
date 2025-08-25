// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.model

import com.fasterxml.jackson.annotation.JsonValue

data class EncryptionInitializationRequest(
    val version: Version,
    val mode: Mode,
    val key: String,
) {
    enum class Version(@JsonValue val value: String) {
        V1_0("1.0"),
    }

    enum class Mode(@JsonValue val value: String) {
        JWT("JWT"),
    }
}
