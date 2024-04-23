// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

import software.aws.toolkits.core.utils.tryOrNull
import java.net.URI

private const val ISSUER_URL_BASE = "identitycenter.amazonaws.com/"
private const val CLASSIC_START_URL = ".awsapps.com/start"
private const val GOVCLOUD_START_URL = "us-gov-home.awsapps.com/directory/"
private const val CHINA_START_URL = "awsapps.cn/directory/"

fun ssoIdentifierFromUrl(url: String): String {
    val base = url.removePrefix("https://")

    return when {
        base.startsWith(ISSUER_URL_BASE) -> base.removePrefix(ISSUER_URL_BASE)
        base.contains(CLASSIC_START_URL) -> base.substringBefore(CLASSIC_START_URL)
        base.contains(GOVCLOUD_START_URL) -> base.substringAfter(GOVCLOUD_START_URL)
        base.contains(CHINA_START_URL) -> base.substringAfter(CHINA_START_URL)
        else -> base
    }
}

fun validatedSsoIdentifierFromUrl(url: String): String {
    tryOrNull {
        URI(url)
    } ?: error("Invalid SSO URL")

    return ssoIdentifierFromUrl(url)
}
