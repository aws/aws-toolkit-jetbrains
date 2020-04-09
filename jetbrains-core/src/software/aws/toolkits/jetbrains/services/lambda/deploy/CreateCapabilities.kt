// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.deploy

import software.aws.toolkits.resources.message

enum class CreateCapabilities(val capability: String, val text: String, val toolTipText: String, val defaultSet: Boolean) {
    IAM(
        "CAPABILITY_IAM",
        message("cloudformation.capabilities.iam"),
        message("cloudformation.capabilities.iam.toolTipText"),
        true
    ),
    NAMED_IAM(
        "CAPABILITY_NAMED_IAM",
        message("cloudformation.capabilities.named_iam"),
        message("cloudformation.capabilities.named_iam.toolTipText"),
        true
    ),
    AUTO_EXPAND(
        "CAPABILITY_AUTO_EXPAND",
        message("cloudformation.capabilities.auto_expand"),
        message("cloudformation.capabilities.auto_expand.toolTipText"),
        false
    );
}
