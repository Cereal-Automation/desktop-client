package com.cereal.client.infrastructure.crashreporting

/**
 * Starting and stopping the crash-reporting client, as a seam.
 *
 * [CrashReportingClient] is the only production implementation. It exists as an interface so the
 * opt-out can be tested for what it actually promises — that a disabled setting means the client is
 * never *started*, rather than started and then filtered — which is not observable through Sentry's
 * own static API in a build whose DSN is empty.
 */
interface CrashReportingLifecycle {
    /** [homeDirectory] is attached as report context when it is already known; see [CrashReportingClient.start]. */
    fun start(homeDirectory: String? = null)

    fun stop()
}
