package com.cereal.client.infrastructure.data.datasource.network.security

import okhttp3.CertificatePinner
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.assertFailsWith

class CertificatePinnerFactoryTest {
    @Test
    fun `returns null when no pins are supplied`() {
        assertNull(CertificatePinnerFactory.create("https://example.com/", emptyList()))
    }

    @Test
    fun `returns null when the url has no parseable host`() {
        assertNull(CertificatePinnerFactory.create("not a url", listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
    }

    @Test
    fun `accepts a certificate whose pin matches`() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("example.com").build()
        val pinner = CertificatePinnerFactory.create("https://example.com/", listOf(CertificatePinner.pin(certificate.certificate)))!!

        // No exception means the pin matched the presented chain.
        pinner.check("example.com", listOf(certificate.certificate))
    }

    @Test
    fun `rejects a certificate that matches none of the pins`() {
        val presented = HeldCertificate.Builder().addSubjectAlternativeName("example.com").build()
        val other = HeldCertificate.Builder().addSubjectAlternativeName("example.com").build()
        val pinner = CertificatePinnerFactory.create("https://example.com/", listOf(CertificatePinner.pin(other.certificate)))!!

        assertFailsWith<SSLPeerUnverifiedException> {
            pinner.check("example.com", listOf(presented.certificate))
        }
    }

    @Test
    fun `accepts when any one of multiple pins matches (backup pin)`() {
        val presented = HeldCertificate.Builder().addSubjectAlternativeName("example.com").build()
        val backup = HeldCertificate.Builder().addSubjectAlternativeName("example.com").build()
        val pinner =
            CertificatePinnerFactory.create(
                "https://example.com/",
                listOf(CertificatePinner.pin(backup.certificate), CertificatePinner.pin(presented.certificate)),
            )!!

        pinner.check("example.com", listOf(presented.certificate))
    }
}
