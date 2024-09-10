// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import com.intellij.idea.AppMode
import com.intellij.ui.jcef.JBCefApp

/**
 * @return true if running in any type of remote environment
 */
fun isRunningOnRemoteBackend() = AppMode.isRemoteDevHost()

/**
 * @return true if running in a codecatalyst remote environment
 */
fun isCodeCatalystDevEnv() = System.getenv("__DEV_ENVIRONMENT_ID") != null

fun isQWebviewsAvailable() = JBCefApp.isSupported() && !isRunningOnRemoteBackend()
