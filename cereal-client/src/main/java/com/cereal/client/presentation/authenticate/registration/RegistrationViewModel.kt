package com.cereal.client.presentation.authenticate.registration

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.auth.RegisterInteractor
import com.cereal.client.domain.model.auth.PasswordStrength
import com.cereal.client.domain.model.user.User
import com.cereal.client.presentation.model.AuthType
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrationViewModel(
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val registerInteractor: RegisterInteractor,
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
    val isAuthenticated = mutableStateOf<Boolean?>(null)

    private val _registrationSuccess = MutableSharedFlow<Unit>()
    val registrationSuccess = _registrationSuccess.asSharedFlow()
    val name = mutableStateOf("")
    val username = mutableStateOf("")
    val password = mutableStateOf("")
    val passwordStrength = mutableStateOf(PasswordStrength.evaluate(""))

    val loadingState = mutableStateOf<LoadState>(LoadState.NotLoading())

    val user = mutableStateOf<User?>(null)
    private var authenticatedUser: User? = null

    init {
        scope.launch(dispatcherProvider.io) {
            getAuthenticatedUserInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        authenticatedUser = result.value
                        user.value = result.value
                    } else if (result is SuspendableResult.Failure) {
                        authenticatedUser = null
                        user.value = null
                    }

                    isAuthenticated.value =
                        authenticatedUser != null &&
                        loadingState.value is LoadState.NotLoading
                }
            }
        }
    }

    fun register() {
        if ((isAuthenticated.value == true && authenticatedUser?.isGuest != true) || loadingState.value is LoadState.Loading) {
            return
        }

        // Validate password strength before attempting registration
        if (!passwordStrength.value.isValid) {
            loadingState.value = LoadState.Error("Please ensure your password meets all requirements")
            return
        }

        loadingState.value = LoadState.Loading(AuthType.REGULAR)

        scope.launch(dispatcherProvider.io) {
            val params = RegisterInteractor.Params(name.value, username.value, password.value)
            registerInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Failure -> {
                            loadingState.value =
                                LoadState.Error(result.error.localizedMessage)
                        }

                        is SuspendableResult.Success -> {
                            loadingState.value = LoadState.NotLoading()
                            isAuthenticated.value = true
                            _registrationSuccess.emit(Unit)
                        }
                    }
                }
            }
        }
    }

    fun updatePassword(newPassword: String) {
        password.value = newPassword
        passwordStrength.value = PasswordStrength.evaluate(newPassword)
    }
}
