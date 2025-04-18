// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp.encryption

import com.nimbusds.jose.JOSEException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class JwtEncryptionManagerTest {
    @Test
    fun `uses a different encryption key for each instance`() {
        val blob = Random.Default.nextBytes(256)
        val sut1 = JwtEncryptionManager()
        val encrypted = sut1.encrypt(blob)

        assertThrows<JOSEException> {
            assertThat(sut1.decrypt(encrypted))
                .isNotEqualTo(JwtEncryptionManager().decrypt(encrypted))
        }
    }

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun `encryption is stable with static key`() {
        val blob = Random.Default.nextBytes(256)
        val bytes = "DEADBEEF".repeat(8).hexToByteArray() // 32 bytes
        val key = SecretKeySpec(bytes, "HmacSHA256")
        val sut1 = JwtEncryptionManager(key)
        val encrypted = sut1.encrypt(blob)

        // each encrypt() call will use a different IV so we can't just directly compare
        assertThat(sut1.decrypt(encrypted))
            .isEqualTo(JwtEncryptionManager(key).decrypt(encrypted))
    }

    @Test
    fun `encryption can be round-tripped`() {
        val sut = JwtEncryptionManager()
        val blob = "DEADBEEF".repeat(8)
        assertThat(sut.decrypt(sut.encrypt(blob))).isEqualTo(blob)
    }

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun writeInitializationPayload() {
        val bytes = "DEADBEEF".repeat(8).hexToByteArray() // 32 bytes
        val key = SecretKeySpec(bytes, "HmacSHA256")

        val closed = AtomicBoolean(false)
        val os = object : ByteArrayOutputStream() {
            override fun close() {
                closed.set(true)
            }
        }
        JwtEncryptionManager(key).writeInitializationPayload(os)
        assertThat(os.toString())
            // Flare requires encryption ends with new line
            // https://github.com/aws/language-server-runtimes/blob/4d7f81295dc12b59ed2e1c0ebaedb85ccb86cf76/runtimes/README.md#encryption
            .endsWith("\n")
            .isEqualTo(
                // language=JSON
                """
            |{"version":"1.0","mode":"JWT","key":"3q2-796tvu_erb7v3q2-796tvu_erb7v3q2-796tvu8"}
            |
                """.trimMargin()
            )

        // writeInitializationPayload should not close the stream
        assertThat(closed.get()).isFalse
    }
}
