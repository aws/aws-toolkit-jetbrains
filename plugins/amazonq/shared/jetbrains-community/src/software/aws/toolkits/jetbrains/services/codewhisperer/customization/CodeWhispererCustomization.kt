// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.customization

data class CodeWhispererCustomization(
    @JvmField
    val arn: String = "",

    @JvmField
    val name: String = "",

    @JvmField
    val description: String? = null,
)
