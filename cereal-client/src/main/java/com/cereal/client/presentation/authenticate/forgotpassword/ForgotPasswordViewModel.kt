package com.cereal.client.presentation.authenticate.forgotpassword

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.auth.ForgotPasswordInteractor
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordViewModel(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val forgotPasswordInteractor: ForgotPasswordInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)

    val email = mutableStateOf("")
    val loadingState = mutableStateOf<LoadState>(LoadState.NotLoading())

    private val _successEvent = MutableSharedFlow<String>()
    val successEvent = _successEvent.asSharedFlow()

    fun submit() {
        if (email.value.isBlank() || loadingState.value is LoadState.Loading) return

        loadingState.value = LoadState.Loading(null)

        scope.launch(dispatcherProvider.io) {
            forgotPasswordInteractor(ForgotPasswordInteractor.Params(email.value)) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Failure -> {
                            loadingState.value =
                                LoadState.Error(result.error.localizedMessage)
                        }

                        is SuspendableResult.Success -> {
                            loadingState.value = LoadState.NotLoading()
                            _successEvent.emit(email.value)
                        }
                    }
                }
            }
        }
    }
}
