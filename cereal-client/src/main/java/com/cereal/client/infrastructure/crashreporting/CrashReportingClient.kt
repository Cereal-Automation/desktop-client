package com.cereal.client.infrastructure.crashreporting

import com.cereal.client.application.exception.ExceptionFilter
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferenceKey
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences
import com.cereal_automation.cereal_client.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryOptions

/**
 * Starts and stops the Sentry client.
 *
 * This lives apart from [com.cereal.client.App] because the crash-reporting opt-out has to be able
 * to *restart* reporting, not just skip it at boot: the same configuration has to be reachable from
 * bootstrap (before dependency injection) and from the settings screen (long after it). Reporting
 * that only a relaunch can switch back on is a worse setting than one that says so.
 *
 * Capturing lives in [com.cereal.client.application.exception.CrashReporter]; this object only
 * decides whether there is a client to capture into. Sentry's static API no-ops while the SDK is
 * closed, so every `Sentry.*` call elsewhere is safe when the user has opted out.
 */
object CrashReportingClient : CrashReportingLifecycle {
    private const val TRACES_SAMPLE_RATE = 0.01

    /**
     * Initialises Sentry and re-applies the scope that does not depend on dependency injection.
     *
     * [homeDirectory] is passed on the restart path, where the application config is available; at
     * bootstrap it is not known yet and [com.cereal.client.App] attaches it once Koin is up.
     *
     * ponytail: the script-file inventory is *not* re-attached on a restart — it is a background
     * hash of the whole scripts directory, and re-running it on a settings toggle would be a
     * surprising amount of work for a diagnostic tag. Reports sent after re-enabling in the same
     * session carry one less context; the next launch attaches it again.
     */
    override fun start(homeDirectory: String?) {
        Sentry.init { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = BuildConfig.APP_VERSION
            options.tracesSampleRate = TRACES_SAMPLE_RATE
            options.environment = if (BuildConfig.IS_DEBUG) "development" else "production"
            options.isDebug = BuildConfig.IS_DEBUG
            options.beforeSend =
                SentryOptions.BeforeSendCallback { event, _ ->
                    // Ignore EOFException as it often occurs when the application is running and the user shuts down their PC.
                    // This causes network connections to close abruptly, which is not an error we need to track.
                    if (ExceptionFilter.shouldIgnoreSentryEvent(event)) {
                        null
                    } else {
                        event
                    }
                }
        }

        Sentry.configureScope { scope ->
            scope.setTag("os_name", System.getProperty("os.name").lowercase())
            scope.setTag("os_version", System.getProperty("os.version").lowercase())
            scope.setTag("os_arch", System.getProperty("os.arch").lowercase())
            // Pass the path as a String: Sentry wraps String contexts as `{"value": …}`, whereas a File
            // hits the generic Object overload and is serialized as a bare string, which the ingest
            // pipeline rejects with "expected an object" — silently dropping the context.
            homeDirectory?.let { scope.setContexts("home_dir", it) }
        }
    }

    /** Shuts the client down, so nothing further leaves the machine until [start] is called again. */
    override fun stop() {
        Sentry.close()
    }

    /**
     * The bootstrap entry point: starts reporting only if the user has not opted out.
     *
     * The check happens here, before anything is initialised, because that is the whole content of
     * the promise — an opt-out that starts the client and discards events afterwards has already
     * opened the connection. [com.cereal.client.App] calls this before Koin exists, which is why the
     * flag lives in [BootstrapPreferences] rather than the Room-backed preferences.
     */
    fun startIfEnabled(
        preferences: BootstrapPreferences = BootstrapPreferences.default,
        lifecycle: CrashReportingLifecycle = this,
    ) {
        if (preferences.get(BootstrapPreferenceKey.CrashReportingEnabled)) {
            lifecycle.start()
        }
    }
}
