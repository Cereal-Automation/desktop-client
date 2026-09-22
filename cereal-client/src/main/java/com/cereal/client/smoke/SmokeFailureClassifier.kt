package com.cereal.client.smoke

import java.io.IOException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Maps a [Throwable] thrown while exercising the obfuscated build onto a [ProbeOutcome],
 * distinguishing genuine obfuscation / packaging / security breakage (which must block a release)
 * from environmental noise like an unreachable backend (which must not).
 *
 * The classification exploits *how* these failures surface. ProGuard / packaging breakage throws a
 * [LinkageError] — `NoClassDefFoundError`, `VerifyError`, `NoSuchMethodError`, `UnsatisfiedLinkError`,
 * `ExceptionInInitializerError` — or a [ClassNotFoundException] at class-load / link / first-use,
 * i.e. *before* any byte reaches the network. So once a probe gets far enough to see a plain
 * [IOException], the fragile obfuscated code has already proven it loaded and ran; the connection
 * simply could not complete, which is environmental and does not block the release. The one network
 * exception that is a genuine red flag is a certificate-pinning rejection
 * ([SSLPeerUnverifiedException]) against our own host — that means the shipped pins no longer match,
 * which a bad build could cause by stripping them.
 */
object SmokeFailureClassifier {
    private const val MAX_CAUSE_DEPTH = 25

    /**
     * Classifies [error] as [ProbeOutcome.Failed] (obfuscation/packaging or pinning), or
     * [ProbeOutcome.Pass] (an environmental `IOException` after the fragile code executed). An error
     * that matches none of the known families is treated conservatively as [ProbeOutcome.Failed] so
     * an unexpected failure never silently passes the gate.
     */
    fun classify(error: Throwable): ProbeOutcome {
        val chain = causeChain(error)

        chain.firstOrNull(::isLinkageFailure)?.let {
            return ProbeOutcome.Failed("obfuscation/packaging failure: ${describe(it)}", error)
        }
        chain.firstOrNull { it is SSLPeerUnverifiedException }?.let {
            return ProbeOutcome.Failed("certificate pinning rejected the connection: ${describe(it)}", error)
        }
        chain.firstOrNull { it is IOException }?.let {
            return ProbeOutcome.Pass("environmental (fragile code executed, then ${describe(it)})")
        }
        return ProbeOutcome.Failed("unclassified failure: ${describe(error)}", error)
    }

    /**
     * True when [error] or any of its causes is a class-load / link / verify failure — the ProGuard
     * signature. Used by the DI-enumeration probe, which treats only these as blocking (any other
     * construction failure under the throwaway smoke environment is benign and merely skipped).
     */
    fun containsLinkageFailure(error: Throwable): Boolean = causeChain(error).any(::isLinkageFailure)

    private fun isLinkageFailure(error: Throwable): Boolean = error is LinkageError || error is ClassNotFoundException

    private fun causeChain(error: Throwable): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = error
        while (current != null && chain.size < MAX_CAUSE_DEPTH && chain.none { it === current }) {
            chain.add(current)
            current = current.cause
        }
        return chain
    }

    private fun describe(error: Throwable): String {
        val type = error::class.qualifiedName ?: error::class.java.name
        val message = error.message?.takeIf { it.isNotBlank() }
        return if (message != null) "$type: $message" else type
    }
}
