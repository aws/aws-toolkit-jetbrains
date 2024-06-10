// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.webview

import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider

interface BearerLoginHandler {
    fun onPendingToken(provider: InteractiveBearerTokenProvider) {}
    fun onSuccess(connection: ToolkitConnection) {}
    fun onError(e: Exception) {}
}
