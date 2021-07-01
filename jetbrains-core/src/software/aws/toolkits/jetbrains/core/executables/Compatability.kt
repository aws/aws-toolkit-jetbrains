// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.executables

sealed class Compatability {
    data class NotInstalled(val detailedMessage: String? = null) : Compatability()
    data class VersionTooOld(val minVersion: Version) : Compatability()
    data class VersionTooNew(val maxVersion: Version) : Compatability()
    object Valid : Compatability()
}
