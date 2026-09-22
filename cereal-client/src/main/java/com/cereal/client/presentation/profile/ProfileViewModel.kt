package com.cereal.client.presentation.profile

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.auth.LogoutInteractor
import com.cereal.client.domain.model.user.User
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    getUserInteractor: GetAuthenticatedUserInteractor,
    private val logoutInteractor: LogoutInteractor,
) {
    val user = mutableStateOf<User?>(null)

    init {
        scope.launch(dispatcherProvider.io) {
            getUserInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        user.value = result.value
                    }
                }
            }
        }
    }

    fun logout() {
        scope.launch(dispatcherProvider.io) {
            logoutInteractor(Interactor.None()) { _ ->
                withContext(dispatcherProvider.main) {
                    // No-op
                }
            }
        }
    }
}
