// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.lsp

import com.intellij.util.io.DigestUtil
import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.conn.ssl.DefaultHostnameVerifier
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.SystemDefaultCredentialsProvider
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.jetbrains.annotations.TestOnly
import software.amazon.q.core.utils.getLogger
import software.amazon.q.core.utils.warn
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertPathBuilder
import java.security.cert.CertStore
import java.security.cert.Certificate
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathBuilderResult
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.collections.ifEmpty

object TrustChainUtil {
    private val LOG = getLogger<TrustChainUtil>()

    @TestOnly
    fun resolveTrustChain(certs: Collection<X509Certificate>, trustAnchors: Collection<X509Certificate>) = resolveTrustChain(
        certs,
        keystoreFromCertificates(trustAnchors)
    )

    /**
     * Build and validate the complete certificate chain
     * @param certs The end-entity certificate
     * @param trustAnchors The truststore containing trusted CA certificates
     * @return The complete certificate chain
     */
    fun resolveTrustChain(certs: Collection<X509Certificate>, trustAnchors: KeyStore): List<X509Certificate> {
        try {
            // Create the selector for the certificate
            val selector = X509CertSelector()
            selector.certificate = certs.first()

            // Create the parameters for path validation
            val pkixParams = PKIXBuilderParameters(trustAnchors, selector)

            // Disable CRL checking since we just want to build the path
            pkixParams.isRevocationEnabled = false

            // Create a CertStore containing the certificate we want to validate
            val ccsp = CollectionCertStoreParameters(certs)
            val certStore = CertStore.getInstance("Collection", ccsp)
            pkixParams.addCertStore(certStore)

            // Get the certification path
            val builder = CertPathBuilder.getInstance("PKIX")
            val result = builder.build(pkixParams) as PKIXCertPathBuilderResult
            val certPath = result.certPath
            val chain = (certPath.certificates as List<X509Certificate>).toMutableList()

            // Add the trust anchor (root CA) to complete the chain
            val trustAnchorCert = result.trustAnchor.trustedCert
            if (trustAnchorCert != null) {
                chain.add(trustAnchorCert)
            }

            return chain
        } catch (e: Exception) {
            // Java PKIX is happy with leaf cert in certification path, but Node.JS will not respect in NODE_CA_CERTS
            LOG.warn(e) { "Could not build trust anchor via CertPathBuilder? maybe user accepted leaf cert but not intermediate" }

            return emptyList()
        }
    }

    fun getTrustChain(uri: URI): List<X509Certificate> {
        val proxyProvider = JdkProxyProvider.getInstance()
        var peerCerts: Array<Certificate> = emptyArray()
        val verifierDelegate = DefaultHostnameVerifier()
        val client = HttpClientBuilder.create()
            .setRoutePlanner(SystemDefaultRoutePlanner(proxyProvider.proxySelector))
            .setDefaultCredentialsProvider(SystemDefaultCredentialsProvider())
            .setSSLHostnameVerifier { hostname, sslSession ->
                peerCerts = sslSession.peerCertificates

                verifierDelegate.verify(hostname, sslSession)
            }
            // prompt user via modal to accept certificate if needed; otherwise need to prompt separately prior to launching flare
            .setSSLContext(CertificateManager.getInstance().sslContext)

        // client request will fail if user did not accept cert
        client.build().use { it.execute(RequestBuilder.options(uri).build()) }

        val certificates = peerCerts as Array<X509Certificate>

        // java default + custom system
        // excluding leaf cert for case where user has both leaf and issuing CA as trusted roots
        val allAccepted = CertificateManager.getInstance().trustManager.acceptedIssuers.toSet() - certificates.first()
        val ks = keystoreFromCertificates(allAccepted)

        // if this throws then there is a bug because it passed PKIX validation in apache client
        val trustChain = try {
            resolveTrustChain(certificates.toList(), ks)
        } catch (e: Exception) {
            // Java PKIX is happy with leaf cert in certification path, but Node.JS will not respect in NODE_CA_CERTS
            LOG.warn(e) { "Passed Apache PKIX verification but could not build trust anchor via CertPathBuilder? maybe user accepted leaf cert but not root" }
            emptyList()
        }

        // if trust chain is empty, then somehow user only trusts the leaf cert???
        return trustChain.ifEmpty {
            // so return the served certificate chain from the server and hope that works
            certificates.toList()
        }
    }

    fun certsToPem(certs: List<X509Certificate>): String =
        buildList {
            certs.forEach {
                add("-----BEGIN CERTIFICATE-----")
                add(Base64.getMimeEncoder(64, System.lineSeparator().toByteArray()).encodeToString(it.encoded))
                add("-----END CERTIFICATE-----")
            }
        }.joinToString(separator = System.lineSeparator())

    private fun keystoreFromCertificates(certificates: Collection<X509Certificate>): KeyStore {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        certificates.forEachIndexed { index, cert ->
            ks.setCertificateEntry(
                cert.subjectX500Principal.toString() + "-" + DigestUtil.sha256Hex(cert.encoded),
                cert
            )
        }
        return ks
    }
}
