package com.cereal.client.smoke

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException

class SmokeFailureClassifierTest {
    @Test
    fun `NoClassDefFoundError is an obfuscation failure that blocks the release`() {
        val outcome = SmokeFailureClassifier.classify(NoClassDefFoundError("okio/Buffer"))

        val failed = assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
        assertTrue(failed.reason.contains("obfuscation/packaging"))
        assertTrue(failed.isBlocking)
    }

    @Test
    fun `VerifyError is an obfuscation failure`() {
        val outcome = SmokeFailureClassifier.classify(VerifyError("Bad return type"))

        assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `NoSuchMethodError is an obfuscation failure`() {
        val outcome = SmokeFailureClassifier.classify(NoSuchMethodError("BundledSQLiteDriverKt.nativeThreadSafeMode()I"))

        assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `UnsatisfiedLinkError is an obfuscation failure`() {
        val outcome = SmokeFailureClassifier.classify(UnsatisfiedLinkError("sekret"))

        assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `a linkage failure wrapped several causes deep is still detected`() {
        val wrapped = RuntimeException("init failed", IllegalStateException("boot", NoClassDefFoundError("a/B")))

        val outcome = SmokeFailureClassifier.classify(wrapped)

        assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `certificate pinning rejection blocks the release`() {
        val outcome = SmokeFailureClassifier.classify(SSLPeerUnverifiedException("Certificate pinning failure"))

        val failed = assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
        assertTrue(failed.reason.contains("pinning"))
    }

    @Test
    fun `a plain connection failure passes because the fragile code already ran`() {
        val outcome = SmokeFailureClassifier.classify(ConnectException("Connection refused"))

        assertInstanceOf(ProbeOutcome.Pass::class.java, outcome)
        assertFalse(outcome.isBlocking)
    }

    @Test
    fun `a socket timeout passes as environmental`() {
        assertInstanceOf(ProbeOutcome.Pass::class.java, SmokeFailureClassifier.classify(SocketTimeoutException()))
    }

    @Test
    fun `an unknown host passes as environmental`() {
        assertInstanceOf(ProbeOutcome.Pass::class.java, SmokeFailureClassifier.classify(UnknownHostException("api")))
    }

    @Test
    fun `a linkage error wrapped in an IOException is fatal, not environmental`() {
        // The linkage failure must win over the surrounding IOException: obfuscation throws before
        // the wire, so a wrapping IOException does not make it benign.
        val outcome = SmokeFailureClassifier.classify(IOException("io", NoClassDefFoundError("x/Y")))

        assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `an unclassified error blocks the release conservatively`() {
        val outcome = SmokeFailureClassifier.classify(IllegalStateException("something odd"))

        assertInstanceOf(ProbeOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `containsLinkageFailure walks the cause chain`() {
        assertTrue(SmokeFailureClassifier.containsLinkageFailure(RuntimeException(NoClassDefFoundError("a"))))
        assertFalse(SmokeFailureClassifier.containsLinkageFailure(RuntimeException(ConnectException("refused"))))
    }

    @Test
    fun `a cyclic cause chain does not loop forever`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b")
        a.initCause(b)
        b.initCause(a)

        // Must terminate and classify (conservatively fatal), not hang.
        assertInstanceOf(ProbeOutcome.Failed::class.java, SmokeFailureClassifier.classify(a))
    }
}
