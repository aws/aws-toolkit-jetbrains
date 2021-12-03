// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.lambda

import software.amazon.awssdk.services.lambda.model.Architecture

enum class LambdaArchitecture(
    private val architecture: Architecture,
    val minSamInit: String? = null,
) {
    X86_64(Architecture.X86_64),
    ARM64(Architecture.ARM64, minSamInit = "1.33.0");

    override fun toString() = architecture.toString()

    fun toSdkArchitecture() = architecture.validOrNull

    companion object {
        fun fromValue(value: String?): LambdaArchitecture? = if (value == null) {
            null
        } else {
            values().find { it.toString() == value }
        }

        fun fromValue(value: Architecture): LambdaArchitecture? = values().find { it.architecture == value }

        val default = X86_64
    }
}
