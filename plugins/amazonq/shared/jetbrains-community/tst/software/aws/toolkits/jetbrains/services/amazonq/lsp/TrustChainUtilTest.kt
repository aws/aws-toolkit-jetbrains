// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.common.ssl.KeyStoreSettings
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.intellij.testFramework.ApplicationExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URI
import java.security.cert.X509Certificate

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.*
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import software.aws.toolkits.core.utils.outputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.PrivateKey
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(ApplicationExtension::class)
class TrustChainUtilTest {
    companion object {
        @RegisterExtension
        @JvmStatic
        val wm1 = WireMockExtension.newInstance()
            .options(
                wireMockConfig()
                    .httpDisabled(true)
                    .http2TlsDisabled(true)
                    .keystorePath(Files.createTempFile("certs", "jks").toAbsolutePath().apply { CertificateGenerator.generateCertificateChain(this) }.toString())
                    .keystoreType("jks")
                    .keystorePassword("changeit")
                    .keyManagerPassword("changeit")
                    .dynamicHttpsPort()
                    .notifier(Slf4jNotifier(true))
            )
            .build()
    }

    @Test
    fun `TrustChainUtil should return a valid trust chain`() {
        val trustChain = TrustChainUtil.getTrustChain(URI("https://localhost:${wm1.httpsPort}"))
        println(trustChain)
        assert(trustChain.isNotEmpty())
    }
}

class CertificateGenerator {
    companion object {
        private const val KEY_ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val KEY_SIZE = 4096

        fun generateCertificateChain(keystorePath: Path) {
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

            // Store in KeyStore
            saveToKeyStore(
                keystorePath,
                rootKeyPair, rootCert,
                intermediateKeyPair, intermediateCert,
                leafKeyPair, leafCert
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

        private fun saveToKeyStore(
            keystorePath: Path,
            rootKeyPair: KeyPair,
            rootCert: X509Certificate,
            intermediateKeyPair: KeyPair,
            intermediateCert: X509Certificate,
            leafKeyPair: KeyPair,
            leafCert: X509Certificate
        ) {
            val password = "changeit".toCharArray()

            // Create KeyStore
            val keyStore = KeyStore.getInstance("JKS").apply {
                load(null, password)
            }

            // Store root CA
//            keyStore.setKeyEntry(
//                "root",
//                rootKeyPair.private,
//                password,
//                arrayOf(rootCert)
//            )

//            // Store intermediate CA
//            keyStore.setKeyEntry(
//                "intermediate",
//                intermediateKeyPair.private,
//                password,
//                arrayOf(intermediateCert, rootCert)
//            )

            // Store leaf certificate
            keyStore.setKeyEntry(
                "leaf",
                leafKeyPair.private,
                password,
                arrayOf(leafCert, intermediateCert)
            )

            // Save to file
            keystorePath.outputStream().use { fos ->
                keyStore.store(fos, password)
            }
        }
    }
}
