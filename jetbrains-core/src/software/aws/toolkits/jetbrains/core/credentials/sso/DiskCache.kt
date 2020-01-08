// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sso

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import com.intellij.util.io.inputStreamIfExists
import com.intellij.util.io.outputStream
import software.aws.toolkits.core.utils.tryOrNull
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

class DiskCache(
    private val cacheDir: Path = Paths.get(SystemProperties.getUserHome(), ".aws", "sso", "cache"),
    private val clock: Clock = Clock.systemUTC()
) {
    private val objectMapper = jacksonObjectMapper().also {
        it.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        it.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        it.registerModule(JavaTimeModule())
        it.dateFormat = StdDateFormat().withTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC))
    }

    fun loadClientRegistration(ssoRegion: String): ClientRegistration? {
        val cacheFile = cacheDir.resolve(clientRegistrationCacheKey(ssoRegion))
        val inputStream = cacheFile.inputStreamIfExists() ?: return null
        return tryOrNull {
            val clientRegistration = objectMapper.readValue<ClientRegistration>(inputStream)
            if (clientRegistration.expiresAt.isAfter(Instant.now(clock))) {
                clientRegistration
            } else {
                null
            }
        }
    }

    fun saveClientRegistration(ssoRegion: String, registration: ClientRegistration) {
        cacheDir.resolve(clientRegistrationCacheKey(ssoRegion)).outputStream().use {
            objectMapper.writeValue(it, registration)
        }
    }

    private fun clientRegistrationCacheKey(ssoRegion: String): String = "aws-toolkit-jetbrains-$ssoRegion.json"

    fun loadAccessToken(ssoUrl: String): AccessToken? {
        val cacheFile = cacheDir.resolve(accessKeyCacheKey(ssoUrl))
        val inputStream = cacheFile.inputStreamIfExists() ?: return null
        return tryOrNull {
            val clientRegistration = objectMapper.readValue<AccessToken>(inputStream)
            if (clientRegistration.expiresAt.isAfter(Instant.now(clock))) {
                clientRegistration
            } else {
                null
            }
        }
    }

    fun saveAccessToken(ssoUrl: String, accessToken: AccessToken) {
        cacheDir.resolve(accessKeyCacheKey(ssoUrl)).outputStream().use {
            objectMapper.writeValue(it, accessToken)
        }
    }

    private fun accessKeyCacheKey(ssoUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val sha = StringUtil.toHexString(digest.digest(ssoUrl.toByteArray(Charsets.UTF_8)))
        return "$sha.json"
    }
}
