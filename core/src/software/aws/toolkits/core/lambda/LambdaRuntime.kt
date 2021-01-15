// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.lambda

enum class LambdaRuntime(val value: String) {
    NODEJS10_X("nodejs10.x"),
    NODEJS12_X("nodejs12.x"),
    JAVA8("java8"),
    JAVA8_AL2("java8.al2"),
    JAVA11("java11"),
    PYTHON2_7("python2.7"),
    PYTHON3_6("python3.6"),
    PYTHON3_7("python3.7"),
    PYTHON3_8("python3.8"),
    DOTNETCORE2_1("dotnetcore2.1"),
    DOTNETCORE3_1("dotnetcore3.1"),
    DOTNET5_0("dotnet5.0");

    override fun toString() = value

    companion object {
        fun fromValue(value: String?): LambdaRuntime? = if (value == null) {
            null
        } else {
            values().find { it.toString() == value }
        }
    }
}
