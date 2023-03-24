// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.ui.feedback

import software.aws.toolkits.jetbrains.services.telemetry.ClientMetadata
import java.net.URLEncoder

const val TOOLKIT_REPOSITORY_LINK = "https://github.com/aws/aws-toolkit-jetbrains"
const val GITHUB_LINK_BASE = "https://github.com/aws/aws-toolkit-jetbrains/issues/new?body="
val TOOLKIT_METADATA by lazy {
    ClientMetadata.DEFAULT_METADATA.let {
        """
            ---
            Toolkit: ${it.productName} ${it.productVersion}
            OS: ${it.os} ${it.osVersion}
            IDE: ${it.parentProduct} ${it.parentProductVersion}
        """.trimIndent()
    }
}

fun buildGithubIssueUrl(issueBody: String) = "$GITHUB_LINK_BASE${ URLEncoder.encode("$issueBody\n\n$TOOLKIT_METADATA", Charsets.UTF_8.name())}"
