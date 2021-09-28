// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.lambda

import software.amazon.awssdk.services.lambda.model.Runtime

enum class LambdaRuntime(
    private val runtime: Runtime?,
    val minSamInit: String? = null,
    val minSamDebugging: String? = null,
    private val runtimeOverride: String? = null
) {
    GO1_X(
        Runtime.GO1_X,
        // Although go sam debugging was supported before 1.18.1, it does not work on 1.13.0-1.16.0
        // and 1.17.0 broke the arguments
        minSamDebugging = "1.18.1"
    ),
    NODEJS10_X(Runtime.NODEJS10_X),
    NODEJS12_X(Runtime.NODEJS12_X),
    NODEJS14_X(Runtime.NODEJS14_X, minSamDebugging = "1.17.0", minSamInit = "1.17.0"),
    JAVA8(Runtime.JAVA8),
    JAVA8_AL2(Runtime.JAVA8_AL2, minSamDebugging = "1.2.0"),
    JAVA11(Runtime.JAVA11),
    PYTHON3_6(Runtime.PYTHON3_6),
    PYTHON3_7(Runtime.PYTHON3_7),
    PYTHON3_8(Runtime.PYTHON3_8),
    PYTHON3_9(Runtime.PYTHON3_9, minSamDebugging = "1.28.0", minSamInit = "1.28.0"),
    DOTNETCORE2_1(Runtime.DOTNETCORE2_1),
    DOTNETCORE3_1(Runtime.DOTNETCORE3_1),
    DOTNET5_0(null, minSamInit = "1.16.0", runtimeOverride = "dotnet5.0");

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
