// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials

import software.aws.toolkits.core.TokenConnectionSettings
import software.aws.toolkits.core.credentials.ToolkitBearerTokenProvider
import software.aws.toolkits.jetbrains.core.credentials.sso.bearer.InteractiveBearerTokenProvider
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider

class ManagedBearerSsoConnection(
    val startUrl: String,
    val region: String,
    private val prompt: SsoPrompt = SsoPrompt,
    override val scopes: List<String>
) : BearerSsoConnection {
    override val id: String = "sso;$startUrl"
    override val label: String = "SSO ($startUrl)"

    override fun getConnectionSettings(): TokenConnectionSettings =
        TokenConnectionSettings(
            ToolkitBearerTokenProvider(
                InteractiveBearerTokenProvider(
                    startUrl,
                    region,
                    prompt,
                    scopes
                )
            ),
            AwsRegionProvider.getInstance().get(region) ?: error("Partition data is missing for $region")
        )
}
