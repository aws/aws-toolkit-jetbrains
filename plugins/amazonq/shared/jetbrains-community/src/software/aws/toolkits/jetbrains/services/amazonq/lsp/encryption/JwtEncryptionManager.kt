// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.DirectDecrypter
import com.nimbusds.jose.crypto.DirectEncrypter
import software.aws.toolkits.jetbrains.services.amazonq.lsp.model.EncryptionInitializationRequest
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class JwtEncryptionManager(private val key: SecretKey) {
    constructor() : this(generateHmacKey())

    private val mapper = jacksonObjectMapper()

    fun writeInitializationPayload(os: OutputStream) {
        val payload = EncryptionInitializationRequest(
            EncryptionInitializationRequest.Version.V1_0,
            EncryptionInitializationRequest.Mode.JWT,
            Base64.getUrlEncoder().withoutPadding().encodeToString(key.encoded)
        )

        // write directly to stream because utils are closing the underlying stream
        os.write("${mapper.writeValueAsString(payload)}\n".toByteArray())
    }

    fun encrypt(data: Any): String {
        val header = JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
        val payload = if (data is String) {
            Payload(data)
        } else {
            Payload(mapper.writeValueAsBytes(data))
        }

        val jweObject = JWEObject(header, payload)
        jweObject.encrypt(DirectEncrypter(key))

        return jweObject.serialize()
    }

    fun decrypt(jwt: String): String {
        val jweObject = JWEObject.parse(jwt)
        jweObject.decrypt(DirectDecrypter(key))

        return jweObject.payload.toString()
    }

    private companion object {
        private fun generateHmacKey(): SecretKey {
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            return SecretKeySpec(keyBytes, "HmacSHA256")
        }
    }
}
