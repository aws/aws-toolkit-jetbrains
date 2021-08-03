// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

import com.intellij.openapi.ui.ValidationInfo
import software.aws.toolkits.jetbrains.utils.ui.validationInfo
import software.aws.toolkits.resources.message
import javax.swing.JComponent

sealed class Validity() {
    data class NotInstalled(val detailedMessage: String? = null) : Validity()
    data class VersionTooOld(val minVersion: Version) : Validity()
    data class VersionTooNew(val maxVersion: Version) : Validity()
    object Valid : Validity()
}

fun Validity.toErrorMessage(executableType: ExecutableType2<*>): String? = when (this) {
    is Validity.Valid -> null
    is Validity.NotInstalled -> {
        var message = message("executableCommon.missing_executable", executableType.displayName)
        if (this.detailedMessage != null) {
            message += "\n" + this.detailedMessage
        }
        message
    }
    is Validity.VersionTooNew -> message("executableCommon.version_too_high")
    is Validity.VersionTooOld -> message("executableCommon.version_too_low2", executableType.displayName, this.minVersion)
}

fun Validity.toValidationInfo(executableType: ExecutableType2<*>, component: JComponent): ValidationInfo? = this.toErrorMessage(executableType)?.let {
    component.validationInfo(it)
}
