// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

interface NotifyTokenPending {
    fun tokenPending(authorization: Authorization)
    fun tokenRetrieved()
    fun tokenRetrievalFailure(e: Exception)
}
