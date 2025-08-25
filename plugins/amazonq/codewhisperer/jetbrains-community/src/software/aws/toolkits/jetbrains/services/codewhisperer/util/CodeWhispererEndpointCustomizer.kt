// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.util

import com.intellij.util.net.HttpConfigurable
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.token.credentials.SdkTokenProvider
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.http.SdkHttpRequest
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.ProxyConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClientBuilder
import software.amazon.awssdk.services.codewhispererstreaming.CodeWhispererStreamingAsyncClientBuilder
import software.aws.toolkits.core.ToolkitClientCustomizer
import software.aws.toolkits.core.utils.tryOrNull
import software.aws.toolkits.jetbrains.services.amazonq.profile.QDefaultServiceConfig
import software.aws.toolkits.jetbrains.services.amazonq.profile.QEndpoints
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.net.Proxy
import java.net.URI
import java.time.Duration
import javax.net.ssl.TrustManager

// TODO: move this file to package /client
class CodeWhispererEndpointCustomizer : ToolkitClientCustomizer {

    override fun customize(
        credentialProvider: AwsCredentialsProvider?,
        tokenProvider: SdkTokenProvider?,
        regionId: String,
        builder: AwsClientBuilder<*, *>,
        clientOverrideConfiguration: ClientOverrideConfiguration.Builder,
    ) {
        if (builder is CodeWhispererRuntimeClientBuilder || builder is CodeWhispererStreamingAsyncClientBuilder) {
            val endpoint = tryOrNull { QEndpoints.getQEndpointWithRegion(regionId) }
                ?.let { URI.create(it) }
                ?: URI.create(QDefaultServiceConfig.ENDPOINT)
            builder
                .endpointOverride(endpoint)
                .region(Region.of(regionId))
            clientOverrideConfiguration.retryPolicy(RetryPolicy.none())
            clientOverrideConfiguration.addExecutionInterceptor(
                object : ExecutionInterceptor {
                    override fun modifyHttpRequest(context: Context.ModifyHttpRequest, executionAttributes: ExecutionAttributes): SdkHttpRequest {
                        val requestBuilder = context.httpRequest().toBuilder()
                        executionAttributes.attributes.forEach { (k, v) ->
                            if (k.toString() != "OperationName") return@forEach
                            val isMetricOptIn = CodeWhispererSettings.getInstance().isMetricOptIn()
                            if (v == "GenerateCompletions") {
                                requestBuilder.putHeader(OPTOUT_KEY_NAME, (!isMetricOptIn).toString())
                            }
                            return requestBuilder.build()
                        }
                        return context.httpRequest()
                    }
                }
            )
            if (builder is CodeWhispererStreamingAsyncClientBuilder) {
                val proxy = CommonProxy.getInstance().select(endpoint).first()
                val address = proxy.address()
                val clientBuilder = NettyNioAsyncHttpClient.builder().readTimeout(Duration.ofMinutes(3))

                // proxy.type is one of {DIRECT, HTTP, SOCKS}, and is definitely a InetSocketAddress in the HTTP/SOCKS case
                // and is null in DIRECT case
                if (proxy.type() == Proxy.Type.SOCKS) {
                    error("Q Chat HTTP client does not support SOCKS proxies")
                } else if (address is java.net.InetSocketAddress) {
                    val proxyConfiguration = ProxyConfiguration.builder()
                        .host(address.getHostName())
                        .port(address.getPort())
                        .apply {
                            val configurable = HttpConfigurable.getInstance()
                            val proxyExceptions = configurable.PROXY_EXCEPTIONS
                            if (!proxyExceptions.isNullOrBlank()) {
                                // should be handled by CommonProxy, but also should be no harm in passing this along if something more complicated is happening
                                nonProxyHosts(proxyExceptions.split(',').toSet())
                            }

                            val login = HttpConfigurable.getInstance().proxyLogin
                            if (login != null) {
                                username(login)
                                password(HttpConfigurable.getInstance().plainProxyPassword)
                            }
                        }
                        .useSystemPropertyValues(false)

                    clientBuilder.proxyConfiguration(proxyConfiguration.build())
                }

                builder.httpClientBuilder(
                    clientBuilder
                        .tlsTrustManagersProvider { arrayOf<TrustManager>(CertificateManager.getInstance().trustManager) }
                )
            }
        }
    }

    companion object {
        internal const val TOKEN_KEY_NAME = "x-amzn-codewhisperer-token"
        internal const val OPTOUT_KEY_NAME = "x-amzn-codewhisperer-optout"
    }
}
