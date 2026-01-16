// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.credentials

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import software.aws.toolkit.jetbrains.utils.computeOnEdt
import software.aws.toolkit.resources.AwsCoreBundle

fun promptForMfaToken(name: String, mfaSerial: String): String = computeOnEdt {
    Messages.showInputDialog(
        AwsCoreBundle.message("credentials.mfa.message", mfaSerial),
        AwsCoreBundle.message("credentials.mfa.title", name),
        null
    ) ?: throw ProcessCanceledException(IllegalStateException("MFA challenge is required"))
}
