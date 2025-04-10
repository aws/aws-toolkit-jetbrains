// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.profile
import software.amazon.awssdk.arns.Arn
import software.aws.toolkits.core.utils.tryOrNull

data class QRegionProfile(
    var profileName: String = "",
    var arn: String = "",
) {
    private val parsedArn: Arn? by lazy {
        tryOrNull {
            Arn.fromString(arn)
        }
    }
    val accountId: String by lazy {
        parsedArn?.accountId()?.get().orEmpty()
    }

    val region: String by lazy {
        parsedArn?.region()?.get().orEmpty()
    }
}
