// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.utils

import java.net.ServerSocket

fun findDebugPort(): Int {
    try {
        ServerSocket(0).use {
            it.reuseAddress = true
            return it.localPort
        }
    } catch (e: Exception) {
        throw IllegalStateException("Failed to find free port", e)
    }
}

