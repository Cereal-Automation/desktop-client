package com.cereal.client.presentation.error

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.presentation.util.exhaustive
import com.github.kittinunf.result.coroutines.SuspendableResult

const val DEFAULT_ERROR_MESSAGE =
    "An unexpected error occurred, we are notified about this and will fix it asap."

class ErrorResolver {
    private val _errorAction = mutableStateOf<ErrorAction>(ErrorAction.None)
    val errorAction: State<ErrorAction> = _errorAction

    fun setError(ex: Exception) {
        val message = ex.localizedMessage ?: DEFAULT_ERROR_MESSAGE
        setError(message)
    }

    /**
     * Convenience method to set an [ErrorAction.Message] with the provider message as error.
     */
    fun setError(message: String) {
        val error =
            ErrorAction.Message(message) {
                // Reset error on dismiss.
                _errorAction.value = ErrorAction.None
            }
        _errorAction.value = error
    }

    /**
     * Removes the current error.
     */
    fun reset() {
        _errorAction.value = ErrorAction.None
    }
}

fun <V : Any, E : Exception> SuspendableResult<V, E>.handleFailureOrElse(
    errorResolver: ErrorResolver,
    success: (result: V) -> Unit,
) {
    when (this) {
        is SuspendableResult.Failure -> errorResolver.setError(error)
        is SuspendableResult.Success -> success(value)
    }.exhaustive
}
