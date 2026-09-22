package com.cereal.client.infrastructure.crashreporting

/**
 * Records whether the crash-reporting client was started or stopped, without a Sentry client.
 *
 * The distinction the opt-out is judged on — "never started" versus "started and then filtered" —
 * is only observable here: [CrashReportingClient] delegates to Sentry, whose static API reports the
 * SDK as disabled in any build with an empty DSN, which includes every test build.
 */
class RecordingCrashReportingLifecycle : CrashReportingLifecycle {
    var startCount = 0
        private set
    var stopCount = 0
        private set
    var lastHomeDirectory: String? = null
        private set

    override fun start(homeDirectory: String?) {
        startCount++
        lastHomeDirectory = homeDirectory
    }

    override fun stop() {
        stopCount++
    }
}
