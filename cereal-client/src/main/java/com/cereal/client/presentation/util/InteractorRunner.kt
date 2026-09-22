package com.cereal.client.presentation.util

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.error.handleFailureOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs one-shot [Interactor]s on behalf of a ViewModel, collapsing the recurring
 *
 * ```
 * scope.launch(dispatcherProvider.io) {
 *     interactor(params) { result ->
 *         withContext(dispatcherProvider.main) {
 *             result.handleFailureOrElse(errorResolver) { /* success */ }
 *         }
 *     }
 * }
 * ```
 *
 * boilerplate into a single [launch] call. The interactor runs on the IO dispatcher, failures are
 * routed to the [ErrorResolver], and [onSuccess] is invoked on the Main dispatcher — matching the
 * original hand-written behaviour exactly.
 *
 * Construct it from the ViewModel's existing collaborators, e.g.
 * `private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)`.
 */
class InteractorRunner(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val errorResolver: ErrorResolver,
) {
    fun <T : Any, P> launch(
        interactor: Interactor<T, P>,
        params: P,
        onSuccess: (T) -> Unit = {},
    ): Job =
        scope.launch(dispatcherProvider.io) {
            interactor(params) { result ->
                withContext(dispatcherProvider.main) {
                    result.handleFailureOrElse(errorResolver, onSuccess)
                }
            }
        }
}
