package com.cereal.client.smoke

/**
 * The result of running a single smoke probe against the obfuscated build.
 *
 * Only [Failed] blocks a release. [Pass] and [Skipped] are both non-blocking: a probe that reached
 * an environmental limit (e.g. an unreachable backend) after the fragile obfuscated code already
 * executed is a pass, and a binding that could not be auto-exercised for a benign reason is skipped.
 * See [SmokeFailureClassifier] for how a caught [Throwable] is mapped onto these.
 */
sealed interface ProbeOutcome {
    /** Whether this outcome must block the release. */
    val isBlocking: Boolean

    /** The probe ran and the exercised code behaved correctly. */
    data class Pass(
        val detail: String? = null,
    ) : ProbeOutcome {
        override val isBlocking: Boolean get() = false
    }

    /** The probe could not complete for a benign, non-obfuscation reason (does not block a release). */
    data class Skipped(
        val reason: String,
    ) : ProbeOutcome {
        override val isBlocking: Boolean get() = false
    }

    /** The probe hit an obfuscation, packaging, or security failure — the release must be blocked. */
    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : ProbeOutcome {
        override val isBlocking: Boolean get() = true
    }
}
