// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import software.aws.toolkit.jetbrains.AwsPlugin
import software.aws.toolkit.jetbrains.AwsToolkit

object CfnLspExtensionConfig {
    const val EXTENSION_NAME: String = "aws.toolkit.jetbrains"
    val EXTENSION_VERSION: String = AwsToolkit.PLUGINS_INFO[AwsPlugin.TOOLKIT]?.version ?: "unknown"
    const val ENCRYPTION_MODE = "JWT"
    const val TELEMETRY_NOTIFICATION_GROUP_ID = "aws.cfn.telemetry"
    const val INTRO_NOTIFICATION_GROUP_ID = "CloudFormation LSP Introduction"
}
