// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.aws.toolkit.jetbrains.core.credentials.sso

import software.aws.toolkit.jetbrains.core.credentials.sso.SsoLoginCallback

interface SsoLoginCallbackProvider {
    fun getProvider(isAlwaysShowDeviceCode: Boolean, ssoUrl: String): SsoLoginCallback
}
