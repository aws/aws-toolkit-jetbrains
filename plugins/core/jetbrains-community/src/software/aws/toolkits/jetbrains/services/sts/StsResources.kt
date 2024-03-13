// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.sts

import software.amazon.awssdk.services.sts.StsClient
import software.aws.toolkits.jetbrains.core.ClientBackedCachedResource
import java.time.Duration

object StsResources {
    val ACCOUNT = ClientBackedCachedResource(StsClient::class, "sts.account", expiry = Duration.ofDays(1)) {
        callerIdentity.account()
    }
    val USER = ClientBackedCachedResource(StsClient::class, "sts.user", expiry = Duration.ofDays(1)) {
        callerIdentity.userId()
    }
}
