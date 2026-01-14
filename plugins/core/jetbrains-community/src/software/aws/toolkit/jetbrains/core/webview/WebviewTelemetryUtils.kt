// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkit.jetbrains.core.webview

import com.intellij.openapi.util.registry.Registry
import software.aws.toolkit.jetbrains.core.credentials.sono.CODECATALYST_SCOPES
import software.aws.toolkit.jetbrains.core.credentials.sono.Q_SCOPES
import software.aws.toolkits.telemetry.AuthType
import software.aws.toolkits.telemetry.FeatureId

fun getAuthType(region: String): AuthType {
    val isCommercialRegion = !region.startsWith("us-gov") && !region.startsWith("us-iso") && !region.startsWith("cn")
    if (!Registry.`is`("aws.dev.useDAG") && isCommercialRegion) {
        return AuthType.PKCE
    } else {
        return AuthType.DeviceCode
    }
}

fun getFeatureId(scopes: List<String>): FeatureId =
    if (scopes.intersect(Q_SCOPES.toSet()).isNotEmpty()) {
        FeatureId.AmazonQ
    } else if (scopes.intersect(CODECATALYST_SCOPES.toSet()).isNotEmpty()) {
        FeatureId.Codecatalyst
    } else {
        FeatureId.AwsExplorer
    }
