// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.federation

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.StsClientBuilder
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.AwsClientManager.Companion.userAgent
import software.aws.toolkits.jetbrains.core.AwsSdkClient
import java.time.Duration

class AwsConsoleUrlFactory(
    private val httpClientBuilder: HttpClientBuilder = HttpClientBuilder.create(),
    private val stsClientBuilder: StsClientBuilder = StsClient.builder()
) {
    fun consoleTld(region: AwsRegion) = when (region.partitionId) {
        "aws-cn" -> {
            "amazonaws.cn"
        }
        "aws-gov" -> {
            "amazonaws-us-gov.com"
        }
        "aws" -> {
            "aws.amazon.com"
        }
        else -> throw IllegalStateException("Partition '${region.partitionId}' is not supported")
    }

    fun federationUrl(region: AwsRegion) = "https://signin.${consoleTld(region)}/federation"

    fun consoleUrl(fragment: String? = null, region: AwsRegion): String {
        val regionSubdomain = when (region.id) {
            // cn-north-1 is not valid, but is the default...
            "cn-north-1" -> ""
            else -> "${region.id}."
        }

        val consoleHome = "https://${regionSubdomain}console.${consoleTld(region)}"

        return "$consoleHome${fragment ?: "/"}"
    }

    fun getSigninToken(credentials: AwsCredentials, region: AwsRegion): String {
        val creds = if (credentials !is AwsSessionCredentials) {
            val stsClient = stsClientBuilder
                .httpClient(AwsSdkClient.getInstance().sharedSdkClient())
                .region(Region.of(region.id))
                .credentialsProvider { credentials }
                .overrideConfiguration { configuration ->
                    userAgent.let { configuration.putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_PREFIX, it) }
                }
                .build()

            val tokenResponse = stsClient.use {
                it.getFederationToken {
                    it.durationSeconds(Duration.ofMinutes(15).toSeconds().toInt())
                    it.name("FederatedLoginFromAWSToolkit")
                }
            }
            tokenResponse.credentials().let { AwsSessionCredentials.create(it.accessKeyId(), it.secretAccessKey(), it.sessionToken()) }
        } else {
            credentials
        }

        val sessionJson = """
            {"sessionId":"${creds.accessKeyId()}","sessionKey":"${creds.secretAccessKey()}","sessionToken": "${creds.sessionToken()}"}
        """.trimIndent()

        val params = mapOf(
            "Action" to "getSigninToken",
            "SessionType" to "json",
            "Session" to sessionJson
        ).map { BasicNameValuePair(it.key, it.value) }

        // TODO: should really be POST instead of GET, but that is not possible yet
//        val request = HttpPost(federationUrl(region))
//            .apply {
//                entity = UrlEncodedFormEntity(params)
//            }
        val request = HttpGet(federationUrl(region) + "?${UrlEncodedFormEntity(params).toUrlEncodedString()}")

        val result = httpClientBuilder
            .setUserAgent(AwsClientManager.userAgent)
            .build().use { c ->
                c.execute(
                    request
                ).use { resp ->
                    resp.entity.content.readAllBytes().decodeToString()
                }
            }

        return mapper.readValue<GetSigninTokenResponse>(result).signinToken
    }

    private fun getSigninUrl(token: String, destination: String? = null, region: AwsRegion): String {
        val params = mapOf(
            "Action" to "login",
            "SigninToken" to token,
            "Destination" to consoleUrl(fragment = destination, region = region)
        ).map { BasicNameValuePair(it.key, it.value) }

        return "${federationUrl(region)}?${UrlEncodedFormEntity(params).toUrlEncodedString()}"
    }

    fun getSigninUrl(credentials: AwsCredentials, destination: String?, region: AwsRegion): String {
        return getSigninUrl(getSigninToken(credentials, region), destination, region)
    }

    private val mapper = jacksonObjectMapper()
}

private data class GetSigninTokenResponse(
    @JsonProperty("SigninToken")
    val signinToken: String
)

private fun UrlEncodedFormEntity.toUrlEncodedString() = this.content.bufferedReader().readText()
