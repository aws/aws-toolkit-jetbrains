// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile

/**
 * 'user' -> users change the profile through Q menu
 * 'auth' -> users change the profile through webview profile selector page
 * 'update' -> plugin auto select the profile on users' behalf as there is only 1 profile
 * 'reload' -> on plugin restart, plugin will try to reload previous selected profile
 * 'customization' -> users selected a customization tied to a different profile, triggering a profile switch
 */
enum class QProfileSwitchIntent(val value: String) {
    User("user"),
    Auth("auth"),
    Update("update"),
    Reload("reload"),
    Customization("customization"), ;

    override fun toString() = value
}
