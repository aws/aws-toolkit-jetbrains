// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.caws.envclient

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.text.nullize
import org.apache.http.NoHttpResponseException
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.services.caws.CawsConstants
import software.aws.toolkits.jetbrains.services.caws.envclient.models.CreateDevfileRequest
import software.aws.toolkits.jetbrains.services.caws.envclient.models.CreateDevfileResponse
import software.aws.toolkits.jetbrains.services.caws.envclient.models.GetActivityResponse
import software.aws.toolkits.jetbrains.services.caws.envclient.models.GetStatusResponse
import software.aws.toolkits.jetbrains.services.caws.envclient.models.StartDevfileRequest
import software.aws.toolkits.jetbrains.services.caws.envclient.models.UpdateActivityRequest
import software.aws.toolkits.jetbrains.utils.notifyError
import software.aws.toolkits.resources.message

@Service
class CawsEnvironmentClient(
    private val endpoint: String = System.getenv(CawsConstants.CAWS_ENV_API_ENDPOINT).nullize(true) ?: CawsConstants.DEFAULT_CAWS_ENV_API_ENDPOINT,
    private val httpClient: CloseableHttpClient = HttpClientBuilder.create().build()
) : Disposable {
    init {
        LOG.info { "Initialized with endpoint: $endpoint" }
    }

    private val objectMapper = jacksonObjectMapper().also {
        it.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        it.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private val authToken: String? by lazy {
        System.getenv(CawsConstants.CAWS_ENV_AUTH_TOKEN_VAR)
    }

    /**
     * Create a devfile for the project.
     */
    fun createDevfile(request: CreateDevfileRequest): CreateDevfileResponse {
        val body = objectMapper.writeValueAsString(request)
        val httpRequest = HttpPost("$endpoint/devfile/create").also {
            it.entity = StringEntity(body, ContentType.APPLICATION_JSON)
        }

        return execute(httpRequest).use { response ->
            objectMapper.readValue(response.entity.content)
        }
    }

    /**
     * Start an action with the payload.
     */
    fun startDevfile(request: StartDevfileRequest) {
        val body = objectMapper.writeValueAsString(request)
        val httpRequest = HttpPost("$endpoint/start").also {
            it.entity = StringEntity(body, ContentType.APPLICATION_JSON)
        }

        // currently response will never be read if the call succeeds since the env restarts. the api impl, however does try to return 200
        try {
            execute(httpRequest).use {
                if (it.statusLine.statusCode != 200) {
                    val message = try {
                        message("caws.rebuild.devfile.failed_server", objectMapper.readTree(it.entity.content).get("message").asText())
                    } catch (e: Exception) {
                        LOG.error(e) { "Couldn't parse response from /start API" }
                        message("caws.rebuild.devfile.failed", request.location ?: "null")
                    }

                    notifyError(message("caws.rebuild.failed.title"), message)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException(message("caws.rebuild.failed.title"), e)
        }
    }

    /**
     * Get status and action type
     */
    fun getStatus(): GetStatusResponse {
        val request = HttpGet("$endpoint/status")
        return execute(request).use { response ->
            objectMapper.readValue(response.entity.content)
        }
    }

    fun getActivity(): GetActivityResponse? = try {
        val request = HttpGet("$endpoint/activity")

        execute(request).use { response ->
            if (response.statusLine.statusCode == 400) {
                LOG.error { "Inactivity tracking may not enabled" }
                null
            } else {
                objectMapper.readValue<GetActivityResponse>(response.entity.content)
            }
        }
    } catch (e: Exception) {
        LOG.error(e) { "Couldn't parse response from /activity API" }
        null
    }

    fun putActivityTimestamp(request: UpdateActivityRequest) {
        try {
            val body = objectMapper.writeValueAsString(request)
            val httpRequest = HttpPut("$endpoint/activity").also {
                it.entity = StringEntity(body, ContentType.APPLICATION_JSON)
            }

            execute(httpRequest).use {
                // defensive in case they decide to actually start returning 200s
                if (it.statusLine.statusCode != 200) {
                    val response = objectMapper.readValue<Error>(it.entity.content)
                    LOG.debug { "Wrote '$request' to /activity with error response: $response" }
                } else {
                    LOG.debug { "Wrote '$request' to /activity with response: ${it.entity.content.readAllBytes().decodeToString()}" }
                }
            }
        } catch (e: NoHttpResponseException) {
            // on success, env API closes connection with no response: org.apache.http.NoHttpResponseException: localhost:1339 failed to respond
            LOG.debug { "Wrote '$request' to /activity with no response (likely success?)" }
        } catch (e: Exception) {
            LOG.error(e) { "Couldn't execute /activity API" }
        }
    }

    private fun execute(request: HttpUriRequest): CloseableHttpResponse {
        request.addHeader("Authorization", authToken)
        return httpClient.execute(request)
    }

    override fun dispose() {
        httpClient.close()
    }

    companion object {
        fun getInstance() = service<CawsEnvironmentClient>()

        private val LOG = getLogger<CawsEnvironmentClient>()
    }
}
