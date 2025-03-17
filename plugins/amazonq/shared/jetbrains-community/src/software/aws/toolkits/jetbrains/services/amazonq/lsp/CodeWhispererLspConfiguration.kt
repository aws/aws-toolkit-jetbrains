// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.google.gson.annotations.SerializedName

data class CodeWhispererLspConfiguration(
    @SerializedName(AmazonQLspConstants.LSP_CW_OPT_OUT_KEY)
    val shouldShareData: Boolean? = null,

    @SerializedName(AmazonQLspConstants.LSP_CODE_REFERENCES_OPT_OUT_KEY)
    val shouldShareCodeReferences: Boolean? = null,
)
