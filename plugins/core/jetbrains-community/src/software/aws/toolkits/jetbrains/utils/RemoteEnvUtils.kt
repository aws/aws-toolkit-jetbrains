// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.BuildNumber

/**
 * @return true if running in any type of remote environment
 */
fun isRunningOnRemoteBackend() = AppMode.isRemoteDevHost()

/**
 * @return true if running in a codecatalyst remote environment
 */
fun isCodeCatalystDevEnv() = System.getenv("__DEV_ENVIRONMENT_ID") != null

fun disableExtensionIfRemoteBackend() {
    if (isRunningOnRemoteBackend()) {
        throw ExtensionNotApplicableException.create()
    }
}

// CW can be supported only after at least build 232.9921.47 on remote env
fun isRunningOnCWNotSupportedRemoteBackend() =
    ApplicationInfo.getInstance().build.compareTo(BuildNumber.fromStringOrNull("232.9921.47")) < 0 &&
        AppMode.isRemoteDevHost()
