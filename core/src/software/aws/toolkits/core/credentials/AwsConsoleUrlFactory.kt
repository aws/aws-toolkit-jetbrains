// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.core.credentials

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.aws.toolkits.core.region.AwsRegion
import java.net.URL
import java.net.URLEncoder

object AwsConsoleUrlFactory {
    fun federationUrl(region: AwsRegion? = null) = when (region?.partitionId) {
        "aws-cn" -> {
            "https://signin.amazonaws.cn/federation"
        }
        "aws-gov" -> {
            "https://signin.amazonaws-us-gov.com/federation"
        }
        else -> "https://signin.aws.amazon.com/federation"
    }

    fun consoleUrl(fragment: String = "/", region: AwsRegion? = null): String {
        val regionSubdomain = when (region?.id) {
            // cn-north-1 is not valid, but is the default...
            null, "cn-north-1" -> ""
            else -> "${region.id}."
        }

        val base = when (region?.partitionId) {
            "aws-cn" -> {
                "https://${regionSubdomain}console.amazonaws.cn"
            }
            "aws-gov" -> {
                "https://${regionSubdomain}console.amazonaws-us-gov.com"
            }
            else -> "https://${regionSubdomain}console.aws.amazon.com"
        }

        return "$base$fragment"
    }

    private fun getSigninTokenUrl(credentials: AwsCredentials, region: AwsRegion? = null): String {
        val sessionJson = """
            {"sessionId": "${credentials.accessKeyId()}", "sessionKey": "${credentials.secretAccessKey()}" ${if (credentials is AwsSessionCredentials) { """, "sessionToken": "${credentials.sessionToken()}"""" } else { "" }} }
        """.trimIndent()
        return """${federationUrl(region)}?Action=getSigninToken&DurationSeconds=43200&SessionType=json&Session=${URLEncoder.encode(sessionJson, "UTF-8")}"""
    }

    private fun getSigninUrl(token: String, destination: String? = null, region: AwsRegion? = null): String {
        val signinTokenParameter = "&SigninToken=" + URLEncoder.encode(token, "UTF-8")
        val destinationParameter = "&Destination=" + URLEncoder.encode(destination ?: consoleUrl(region = region), "UTF-8")
        return "${federationUrl(region)}?Action=login$signinTokenParameter$destinationParameter"
    }

    fun getSigninUrl(credentials: AwsCredentials, destination: String?, region: AwsRegion? = null): String {
        return getSigninUrl(jacksonObjectMapper().readTree(URL(getSigninTokenUrl(credentials, region))).get("SigninToken").asText(), destination, region)
    }
}
