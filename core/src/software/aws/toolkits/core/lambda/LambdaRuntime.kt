// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.lambda

import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.core.lambda.LambdaArchitecture.Companion.ARM_COMPATIBLE

enum class LambdaRuntime(
    private val runtime: Runtime?,
    val minSamInit: String? = null,
    val minSamDebugging: String? = null,
    val architectures: List<LambdaArchitecture>? = listOf(LambdaArchitecture.DEFAULT),
    private val runtimeOverride: String? = null
) {
    GO1_X(
        Runtime.GO1_X,
        // Although go sam debugging was supported before 1.18.1, it does not work on 1.13.0-1.16.0
        // and 1.17.0 broke the arguments
        minSamDebugging = "1.18.1"
    ),
    NODEJS14_X(Runtime.NODEJS14_X, minSamDebugging = "1.17.0", minSamInit = "1.17.0", architectures = ARM_COMPATIBLE),
    NODEJS16_X(Runtime.NODEJS16_X, minSamDebugging = "1.49.0", minSamInit = "1.49.0", architectures = ARM_COMPATIBLE),
    NODEJS18_X(Runtime.NODEJS18_X, minSamDebugging = "1.65.0", minSamInit = "1.65.0", architectures = ARM_COMPATIBLE),
    JAVA8(Runtime.JAVA8),
    JAVA8_AL2(Runtime.JAVA8_AL2, minSamDebugging = "1.2.0", architectures = ARM_COMPATIBLE),
    JAVA11(Runtime.JAVA11, architectures = ARM_COMPATIBLE),
    PYTHON3_7(Runtime.PYTHON3_7),
    PYTHON3_8(Runtime.PYTHON3_8, architectures = ARM_COMPATIBLE),
    PYTHON3_9(Runtime.PYTHON3_9, minSamDebugging = "1.28.0", minSamInit = "1.28.0", architectures = ARM_COMPATIBLE),
    PYTHON3_10(Runtime.PYTHON3_10, minSamDebugging = "1.78.0", minSamInit = "1.78.0", architectures = ARM_COMPATIBLE),
    DOTNET5_0(null, minSamInit = "1.16.0", runtimeOverride = "dotnet5.0"),
    DOTNET6_0(Runtime.DOTNET6, minSamDebugging = "1.40.1", minSamInit = "1.40.1", architectures = ARM_COMPATIBLE);

    override fun toString() = runtime?.toString() ?: runtimeOverride ?: throw IllegalStateException("LambdaRuntime has no runtime or override string")

    fun toSdkRuntime() = runtime.validOrNull

    companion object {
        fun fromValue(value: String?): LambdaRuntime? = if (value == null) {
            null
        } else {
            values().find { it.toString() == value }
        }

        fun fromValue(value: Runtime): LambdaRuntime? = values().find { it.runtime == value }
    }
}
