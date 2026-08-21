// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.lsp

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val httpClient by lazy {
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
}

private fun buildRequest(url: String, timeout: Duration) = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout).GET().build()

fun fetchText(url: String, client: HttpClient = httpClient, timeout: Duration = Duration.ofMinutes(1)): String {
    val response = client.send(buildRequest(url, timeout), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "HTTP fetch error: ${response.statusCode()} for $url" }
    return response.body()
}

fun fetchBytes(url: String, client: HttpClient = httpClient, timeout: Duration = Duration.ofMinutes(5)): ByteArray {
    val response = client.send(buildRequest(url, timeout), HttpResponse.BodyHandlers.ofByteArray())
    check(response.statusCode() == 200) { "HTTP fetch error: ${response.statusCode()} for $url" }
    return response.body()
}
