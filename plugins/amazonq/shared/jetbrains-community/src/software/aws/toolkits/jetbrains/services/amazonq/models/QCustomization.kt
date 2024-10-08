// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.models

data class QCustomization(
    @JvmField
    var arn: String = "",

    @JvmField
    var name: String = "",

    @JvmField
    var description: String? = null,
)
