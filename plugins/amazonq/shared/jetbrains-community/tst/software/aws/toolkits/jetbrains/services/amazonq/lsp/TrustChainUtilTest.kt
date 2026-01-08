// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.Slf4jNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.net.ssl.CertificateManager
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.q.core.utils.outputStream
import software.amazon.q.core.utils.writeText
import java.math.BigInteger
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@ExtendWith(ApplicationExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustChainUtilTest {
    companion object {
        private val certs = CertificateGenerator.generateCertificateChain()
    }

    @BeforeEach
    @AfterEach
    fun clearCerts() {
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
    fun `returns entire chain if CA is trust anchor`() {
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
            // leaf, intermediate
            assertThat(trustChain)
                .isEqualTo(certs.keys.toList())
        }
    }

    @Test
    fun `returns empty if CA is trusted but does not provide intermediate`() {
        val (leaf, _, root) = certs.keys.take(3)
        assertThat(
            TrustChainUtil.resolveTrustChain(listOf(leaf), listOf(root))
        ).isEmpty()
    }

    @Test
    fun `returns entire chain if CA is trusted and provides intermediate`() {
        val (leaf, intermediate, root) = certs.keys.take(3)
        assertThat(
            TrustChainUtil.resolveTrustChain(listOf(leaf, intermediate), listOf(root))
        ).isEqualTo(
            listOf(leaf, intermediate, root)
        )
    }

    @Test
    fun `returns empty if CA is not trusted`() {
        val (leaf, intermediate) = certs.keys.take(2)
        assertThat(
            TrustChainUtil.resolveTrustChain(
                listOf(leaf, intermediate),
                listOf(CertificateManager.getInstance().trustManager.acceptedIssuers.first())
            )
        ).isEmpty()
    }

    @Test
    fun `node accepts full chain`() {
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
            },
            {
                val pemFile = Files.createTempFile("test", ".pem").apply {
                    writeText(
                        TrustChainUtil.certsToPem(certs.keys.toList())
                    )
                }

                val output = ExecUtil.execAndGetOutput(
                    GeneralCommandLine(
                        "node",
                        "--use-bundled-ca",
                        Files.createTempFile("test", ".js").apply { writeText(nodeTest(it.httpsPort())) }.toAbsolutePath().toString(),
                    ).withEnvironment("NODE_EXTRA_CA_CERTS", pemFile.toAbsolutePath().toString())
                )

                assertThat(output.exitCode).withFailMessage { "node validation failed: ${output.stdout}\n${output.stderr}" }
                    .isEqualTo(0)
            }
        )
    }

    @Test
    fun `node does not accept intermediate only`() {
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
            },
            {
                val pemFile = Files.createTempFile("test", ".pem").apply {
                    writeText(
                        TrustChainUtil.certsToPem(certs.keys.take(2).toList())
                    )
                }

                // node does not support partial chains
                val output = ExecUtil.execAndGetOutput(
                    GeneralCommandLine(
                        "node",
                        "--use-bundled-ca",
                        Files.createTempFile("test", ".js").apply { writeText(nodeTest(it.httpsPort())) }.toAbsolutePath().toString(),
                    ).withEnvironment("NODE_EXTRA_CA_CERTS", pemFile.toAbsolutePath().toString())
                )

                assertThat(output.exitCode).withFailMessage { "node validation succeeded instead of failed: ${output.stdout}\n${output.stderr}" }
                    .isEqualTo(1)
            }
        )
    }

    // language=JavaScript
    private fun nodeTest(port: Int) = """
        const https = require("https");
        
        async function main() {  // Wrapped in async function for better error handling
            try {
                const options = {
                    host: "localhost",
                    port: $port,
                    path: "/",
                    requestCert: true,
                    rejectUnauthorized: true,
                };
        
                const req = https.get(options, (res) => {
                    console.log("Certificate authorized:", res.socket.authorized);
                    const cert = res.socket.getPeerCertificate();
                    console.log("Certificate details:", cert);
                    process.exit(0)
                });
        
                req.on("error", (err) => {  // Added error handling
                    console.error("Request error:", err);
                    process.exit(1)
                });
        
                req.end();
            } catch (error) {
                console.error("Error:", error);
                process.exit(1)
            }
        }
        
        main();
    """.trimIndent()

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

object CertificateGenerator {
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
        issuerPrivateKey: PrivateKey,
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
                // not allowed to issue sub-CA
                BasicConstraints(0)
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
        issuerPrivateKey: PrivateKey,
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
        trustChain: Array<X509Certificate>,
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
