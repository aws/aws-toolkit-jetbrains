// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package base

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil

val dotNetSdk by lazy {
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine("dotnet", "--version"))
    if (output.exitCode == 0) {
        "C:\\Program Files\\dotnet\\sdk\\${output.stdout.trim()}".also {
            println("Using dotnet SDK at $it")
        }
    } else {
        throw IllegalStateException("Failed to locate dotnet version: ${output.stderr}")
    }
}

val msBuild by lazy {
    "${dotNetSdk}\\MSBuild.dll"
}
