// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

sealed class Validity {
    data class NotInstalled(val detailedMessage: String? = null) : Validity()
    data class VersionTooOld(val minVersion: Version) : Validity()
    data class VersionTooNew(val maxVersion: Version) : Validity()
    object Valid : Validity()
}
