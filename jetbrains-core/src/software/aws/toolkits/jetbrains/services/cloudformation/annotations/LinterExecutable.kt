// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package software.aws.toolkits.jetbrains.services.cloudformation.annotations

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfo

class LinterExecutable {

    companion object {
        private val EXECUTABLE_NAME = "cfn-lint"

        @Throws(Exception::class)
        fun getExecutablePath(): String? =
            if (SystemInfo.isWindows) {
                PathEnvironmentVariableUtil.findExecutableInWindowsPath(EXECUTABLE_NAME)
            } else {
                val executableFile = PathEnvironmentVariableUtil.findInPath(EXECUTABLE_NAME)
                executableFile?.absolutePath
            }
    }
}
