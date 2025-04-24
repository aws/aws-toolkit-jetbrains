// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.net.ssl.CertificateManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.security.cert.X509Certificate
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import software.aws.toolkits.core.utils.outputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.PrivateKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@ExtendWith(ApplicationExtension::class)
class TrustChainUtilTest : AfterAllCallback {
    companion object {
        private val certs = CertificateGenerator.generateCertificateChain()
    }

    override fun afterAll(context: ExtensionContext) {
        CertificateManager.getInstance().customTrustManager.apply {
            certificates.toList().forEach { removeCertificate(it) }
        }

        assertThat(CertificateManager.getInstance().customTrustManager.certificates).isEmpty()
    }

    @Test
    fun `returns chain from server if leaf is trust anchor`() {
        mockWithOptions(
            {
                it.keystorePath(
                    Files.createTempFile("certs", "jks")
                        .toAbsolutePath()
                        .apply {
                            CertificateGenerator.saveToKeyStore(
                                this,
                                certs.values.first(),
                                certs.keys.take(2).toTypedArray(),
                            )
                        }
                        .toString()
                )
            }
        ) {
            val trustChain = TrustChainUtil.getTrustChain(URI("https://localhost:${it.httpsPort()}"))
            // leaf, intermediate
            assertThat(trustChain)
                .isEqualTo(certs.keys.take(2).toList())
        }
    }

    @Test
    fun `returns entire chain if CA is trusted`() {
        CertificateManager.getInstance().customTrustManager.addCertificate(certs.keys.last())

        mockWithOptions(
            {
                it.keystorePath(
                    Files.createTempFile("certs", "jks")
                        .toAbsolutePath()
                        .apply {
                            CertificateGenerator.saveToKeyStore(
                                this,
                                certs.values.first(),
                                certs.keys.take(2).toTypedArray(),
                            )
                        }
                        .toString()
                )
            }
        ) {
            val trustChain = TrustChainUtil.getTrustChain(URI("https://localhost:${it.httpsPort()}"))
            // leaf, intermediate, root
            assertThat(trustChain)
                .isEqualTo(certs.keys.toList())
        }
    }

    @Test
    fun `returns entire chain if CA is trusted but only returns leaf`() {
        CertificateManager.getInstance().customTrustManager.addCertificate(certs.keys.last())

        mockWithOptions(
            {
                it.keystorePath(
                    Files.createTempFile("certs", "jks")
                        .toAbsolutePath()
                        .apply {
                            CertificateGenerator.saveToKeyStore(
                                this,
                                certs.values.first(),
                                certs.keys.take(1).toTypedArray(),
                            )
                        }
                        .toString()
                )
            }
        ) {
            val trustChain = TrustChainUtil.getTrustChain(URI("https://localhost:${it.httpsPort()}"))
            // leaf, intermediate, root
            assertThat(trustChain)
                .isEqualTo(certs.keys.toList())
        }
    }

    private fun mockWithOptions(options: (WireMockConfiguration) -> Unit, runnable: (WireMockServer) -> Unit) {
        val server = WireMockServer(
            wireMockConfig()
                .httpDisabled(true)
                .http2TlsDisabled(true)
                .keystoreType("jks")
                .keystorePassword("changeit")
                .keyManagerPassword("changeit")
                .dynamicHttpsPort()
                .notifier(Slf4jNotifier(true))
                .apply { options(this) }
        )

        try {
            server.start()

            runnable(server)
        } finally {
            server.stop()
        }
    }
}

class CertificateGenerator {
    companion object {
        private const val KEY_ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val KEY_SIZE = 4096

        fun generateCertificateChain(): Map<X509Certificate, KeyPair> {
            // Generate Root CA
            val rootKeyPair = generateKeyPair()
            val rootCert = generateRootCertificate(rootKeyPair)

            // Generate Intermediate CA
            val intermediateKeyPair = generateKeyPair()
            val intermediateCert = generateIntermediateCertificate(
                intermediateKeyPair,
                rootCert,
                rootKeyPair.private
            )

            // Generate Leaf Certificate
            val leafKeyPair = generateKeyPair()
            val leafCert = generateLeafCertificate(
                leafKeyPair,
                intermediateCert,
                intermediateKeyPair.private
            )

            return linkedMapOf(
                leafCert to leafKeyPair,
                intermediateCert to intermediateKeyPair,
                rootCert to rootKeyPair,
            )
        }

        private fun generateKeyPair(): KeyPair =
            KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
                initialize(KEY_SIZE)
            }.generateKeyPair()

        private fun generateRootCertificate(keyPair: KeyPair): X509Certificate {
            val name = X500Name("CN=Root CA,O=My Organization,C=US")

            val now = Instant.now()
            val startDate = Date.from(now)
            val endDate = Date.from(now.plus(3650, ChronoUnit.DAYS)) // 10 years validity

            val certBuilder = JcaX509v3CertificateBuilder(
                name, // issuer
                BigInteger.valueOf(System.currentTimeMillis()),
                startDate,
                endDate,
                name, // subject (same as issuer for root CA)
                keyPair.public
            ).apply {
                // Add Extensions
                addExtension(
                    Extension.basicConstraints,
                    true,
                    BasicConstraints(true)
                )
                addExtension(
                    Extension.keyUsage,
                    true,
                    KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
                )
            }

            // Sign the certificate
            val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(keyPair.private)

            return JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer))
        }

        private fun generateIntermediateCertificate(
            intermediateKeyPair: KeyPair,
            issuerCert: X509Certificate,
            issuerPrivateKey: PrivateKey
        ): X509Certificate {
            val subjectName = X500Name("CN=Intermediate CA,O=My Organization,C=US")

            val now = Instant.now()
            val startDate = Date.from(now)
            val endDate = Date.from(now.plus(1825, ChronoUnit.DAYS)) // 5 years validity

            val certBuilder = JcaX509v3CertificateBuilder(
                issuerCert,
                BigInteger.valueOf(System.currentTimeMillis()),
                startDate,
                endDate,
                subjectName,
                intermediateKeyPair.public
            ).apply {
                // Add Extensions
                addExtension(
                    Extension.basicConstraints,
                    true,
                    BasicConstraints(true)
                )
                addExtension(
                    Extension.keyUsage,
                    true,
                    KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
                )
            }

            val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(issuerPrivateKey)

            return JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer))
        }

        private fun generateLeafCertificate(
            leafKeyPair: KeyPair,
            issuerCert: X509Certificate,
            issuerPrivateKey: PrivateKey
        ): X509Certificate {
            val subjectName = X500Name("CN=localhost,O=My Organization,C=US")

            val now = Instant.now()
            val startDate = Date.from(now)
            val endDate = Date.from(now.plus(365, ChronoUnit.DAYS)) // 1 year validity

            val certBuilder = JcaX509v3CertificateBuilder(
                issuerCert,
                BigInteger.valueOf(System.currentTimeMillis()),
                startDate,
                endDate,
                subjectName,
                leafKeyPair.public
            ).apply {
                // Add Extensions
                addExtension(
                    Extension.basicConstraints,
                    true,
                    BasicConstraints(false)
                )
                addExtension(
                    Extension.keyUsage,
                    true,
                    KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
                )

                // Add Subject Alternative Names (SAN)
                val subjectAltNames = GeneralNames(
                    arrayOf(
                        GeneralName(GeneralName.dNSName, "localhost"),
                        GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                        GeneralName(GeneralName.iPAddress, "::1")
                    )
                )

                addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    subjectAltNames
                )
            }

            val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .build(issuerPrivateKey)

            return JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer))
        }

        fun saveToKeyStore(
            keystorePath: Path,
            leafKeyPair: KeyPair,
            trustChain: Array<X509Certificate>
        ) {
            val password = "changeit".toCharArray()

            // Create KeyStore
            val keyStore = KeyStore.getInstance("JKS").apply {
                load(null, password)
            }

            // Store leaf certificate
            keyStore.setKeyEntry(
                "leaf",
                leafKeyPair.private,
                password,
                trustChain
            )

            // Save to file
            keystorePath.outputStream().use { fos ->
                keyStore.store(fos, password)
            }
        }
    }
}
