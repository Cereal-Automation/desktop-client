package com.cereal.client.application

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.CorruptDownloadException
import com.cereal.client.application.exception.InvalidSignatureException
import com.cereal.client.application.exception.findSentryDiagnostics
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.sentry.IScope
import io.sentry.Sentry
import io.sentry.SpanStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import org.slf4j.LoggerFactory
import java.io.IOException

abstract class Interactor<out Type, in Params> where Type : Any {
    abstract suspend fun run(params: Params): Type

    suspend operator fun invoke(
        params: Params,
        onResult: suspend (SuspendableResult<Type, Exception>) -> Unit = {},
    ) {
        val transaction =
            Sentry.startTransaction(
                this::class.simpleName ?: "UnknownInteractor",
                "interactor",
            )
        // Compute the result inside the guarded block, but invoke onResult exactly once *outside* it.
        // If onResult ran inside the try, an exception thrown by the caller's success handler would be
        // caught here and re-dispatched as a Failure — invoking onResult twice and misreporting the
        // caller's own bug as an interactor failure. The transaction is finished with an explicit
        // outcome so failed interactors are distinguishable from successful ones in Sentry.
        val result =
            try {
                val value = SuspendableResult.Success<Type, Exception>(run(params))
                transaction.finish(SpanStatus.OK)
                value
            } catch (ce: CancellationException) {
                // Cancellation is normal control flow — never report it or dispatch it as a Failure.
                transaction.finish(SpanStatus.CANCELLED)
                throw ce
            } catch (e: Exception) {
                transaction.throwable = e
                transaction.finish(SpanStatus.INTERNAL_ERROR)
                e.toFailure(params)
            }
        onResult(result)
    }

    class None
}

@FlowPreview
abstract class FlowInteractor<out Type, in Params> where Type : Any? {
    abstract suspend fun run(params: Params): Flow<Type>

    suspend operator fun invoke(params: Params): Flow<SuspendableResult<Type, Exception>> =
        try {
            run(params).toSuspendableResult(params)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // `run` may do suspending work (e.g. a network fetch) before it returns the flow.
            // `toSuspendableResult` only guards collection, so an exception thrown while building
            // the flow would otherwise escape uncaught and crash the app. Surface it as a Failure
            // so callers handle it like any other interactor error.
            flowOf(e.toFailure(params))
        }

    class None
}

@FlowPreview
fun <T : Any?, Params> Flow<T>.toSuspendableResult(params: Params): Flow<SuspendableResult<T, Exception>> =
    channelFlow {
        try {
            collect { value -> send(SuspendableResult.Success(value)) }
        } catch (ce: CancellationException) {
            // Ignore these kind of warnings because they are expected when a Flow is cancelled.
        } catch (e: Exception) {
            send(e.toFailure(params))
        }
    }

/**
 * Attaches the structured evidence carried by exceptions implementing SentryDiagnostics (an API
 * error's HTTP status and CDN headers, a signature failure's reason), searching the cause chain so a
 * wrapped exception is still enriched.
 */
private fun Throwable.attachDiagnostics(scope: IScope) {
    findSentryDiagnostics()?.let { scope.setContexts(it.sentryContextKey, it.sentryContext()) }
}

private fun <T : Any?, Params> Exception.toFailure(params: Params): SuspendableResult.Failure<T, Exception> {
    val logger = LoggerFactory.getLogger(Interactor::class.java)

    val exception =
        when (this) {
            is CerealException -> {
                this
            }

            is InvalidSignatureException -> {
                // An invalid signature signals response tampering or a key mismatch, not a transient
                // network error. Report it to Sentry even though it is an IOException subtype. This branch
                // must precede `is IOException` because `when` matches top-down.
                Sentry.captureException(this) {
                    it.setContexts(sentryContextKey, sentryContext())
                }
                CerealException("The server response could not be verified. Please try again later.", this)
            }

            is CorruptDownloadException -> {
                // A downloaded update whose SHA-256 doesn't match the signed metadata. This is not a
                // connectivity problem (so don't tell the user to check their connection) and usually
                // clears on retry — a truncated download, or a stale/incorrect file still served by the
                // CDN. Like a transient network error it stays out of Sentry. This branch must precede
                // `is IOException` because `when` matches top-down.
                CerealException(
                    "The update file could not be verified after downloading — it may be corrupted or out of date. Please try again in a few minutes.",
                    this,
                )
            }

            is IOException -> {
                // Network errors (timeouts, connectivity) are expected; don't report to Sentry.
                // Raw IOException messages (host/port, "Read timed out", "Connection reset") are too
                // technical to show a user, so always surface a friendly message and keep the original
                // as the cause for logging.
                CerealException("A network error occurred. Please check your connection and try again.", this)
            }

            is RuntimeException -> {
                Sentry.captureException(this) {
                    it.setContexts("interactor_params", paramsContext(params))
                    attachDiagnostics(it)
                }
                CerealException("An unexpected error occurred, we are notified about this and will fix it asap.", this)
            }

            else -> {
                // ApiException lands here (it is neither a CerealException nor an IOException), so its
                // status and CDN diagnostics are attached alongside the params.
                Sentry.captureException(this) {
                    it.setContexts("interactor_params", paramsContext(params))
                    attachDiagnostics(it)
                }
                // Unknown checked exception — its own message is likely technical, so show the friendly
                // default and keep the original as the cause (already captured to Sentry above).
                CerealException("An unexpected error occurred, we are notified about this and will fix it asap.", this)
            }
        }

    // Expected failures (domain CerealExceptions, transient network IOExceptions) are logged at WARN
    // so ERROR stays meaningful for the unexpected ones we actually report to Sentry.
    val expected = this is CerealException || (this is IOException && this !is InvalidSignatureException)
    if (expected) {
        logger.warn(exception.message, this)
    } else {
        logger.error(exception.message, this)
    }

    return SuspendableResult.Failure<T, Exception>(exception)
}

private const val REDACTED_PARAMS = "<redacted: sensitive params>"

/**
 * Renders interactor [params] for the Sentry `interactor_params` context, redacting any that carry
 * secrets. Without this, a `data class Params(val password: String)` would leak its plaintext value
 * to Sentry via the auto-generated `toString()`. See [SensitiveParams].
 */
private fun paramsContext(params: Any?): String = if (params is SensitiveParams) REDACTED_PARAMS else params.toString()
