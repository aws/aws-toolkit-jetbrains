// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile
import software.amazon.awssdk.arns.Arn

data class QRegionProfile(
    var profileName: String = "",
    var arn: String = "",
) {
    val accountId: String by lazy {
        try {
            Arn.fromString(arn).accountId().get()
        } catch (e: Exception) {
            ""
        }
    }
    val region: String by lazy {
        try {
            Arn.fromString(arn).region().get()
        } catch (e: Exception) {
            ""
        }
    }
}
