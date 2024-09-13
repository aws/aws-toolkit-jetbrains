// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import software.aws.toolkits.resources.message

// A same accept action but different key shortcut and different promoter logic
class CodeWhispererForceAcceptAction(title: String = message("codewhisperer.inline.force.accept")) : CodeWhispererAcceptAction(title)
