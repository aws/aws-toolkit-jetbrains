// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util

class CancellationToken {
    private var isCancelled = false

    fun isCancellationRequested(): Boolean = isCancelled

    internal fun cancel() {
        isCancelled = true
    }
}

class CancellationTokenSource {
    private val _token = CancellationToken()
    val token: CancellationToken
        get() = _token

    fun cancel() {
        _token.cancel()
    }
}
