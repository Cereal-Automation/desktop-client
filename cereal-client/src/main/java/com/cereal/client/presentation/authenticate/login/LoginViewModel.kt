package com.cereal.client.presentation.authenticate.login

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Environment
import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthenticatingState
import com.cereal.client.application.interactor.auth.AuthenticateGuestInteractor
import com.cereal.client.application.interactor.auth.AuthenticateInteractor
import com.cereal.client.application.interactor.auth.AuthenticateWithOAuthInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.user.User
import com.cereal.client.presentation.model.AuthType
import com.cereal.client.presentation.model.LoadState
import com.cereal_automation.cereal_client.BuildConfig
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class LoginViewModel(
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val authenticateInteractor: AuthenticateInteractor,
    private val authenticateGuestInteractor: AuthenticateGuestInteractor,
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val authenticateWithOAuthInteractor: AuthenticateWithOAuthInteractor,
) {
    private val _isAuthenticated = mutableStateOf<Boolean?>(null)
    val isAuthenticated: State<Boolean?> = _isAuthenticated

    private val _loginSuccess = MutableSharedFlow<Unit>()
    val loginSuccess = _loginSuccess.asSharedFlow()

    private val _username = mutableStateOf("")
    val username: State<String> = _username

    private val _password = mutableStateOf("")
    val password: State<String> = _password

    private val _loadingState = mutableStateOf<LoadState>(LoadState.NotLoading())
    val loadingState: State<LoadState> = _loadingState

    private val _user = mutableStateOf<User?>(null)
    val user: State<User?> = _user

    private val _setupState = mutableStateOf<SetupProgress?>(null)

    /**
     * Non-null once the post-authentication setup phase has been running long enough to be worth
     * showing (see [SETUP_VIEW_GRACE_MILLIS]); drives the full "Signing you in..." setup view. Stays
     * null for fast/cached logins, so they go straight into the app without a flash.
     */
    val setupState: State<SetupProgress?> = _setupState

    private var authenticatedUser: User? = null

    init {
        if (BuildConfig.ENVIRONMENT == Environment.LOCAL) {
            _username.value = "sample@domain.com"
            _password.value = "somepassword"
        }

        coroutineScope.launch(dispatcherProvider.io) {
            getAuthenticatedUserInteractor(Interactor.None()).collectLatest { result ->
                withContext(dispatcherProvider.main) {
                    if (result is SuspendableResult.Success) {
                        authenticatedUser = result.value
                        _user.value = result.value
                    } else if (result is SuspendableResult.Failure) {
                        authenticatedUser = null
                        _user.value = null
                    }

                    _isAuthenticated.value =
                        authenticatedUser != null &&
                        _loadingState.value is LoadState.NotLoading
                }
            }
        }
    }

    fun onUsernameChanged(value: String) {
        _username.value = value
    }

    fun onPasswordChanged(value: String) {
        _password.value = value
    }

    /** Pre-fills the username field, e.g. after a successful forgot-password flow. */
    fun prefillUsername(email: String) {
        _username.value = email
    }

    fun login() {
        if (isSignedInOrBusy()) {
            return
        }

        authenticate(AuthType.REGULAR) {
            authenticateInteractor(
                AuthenticateInteractor.Params(
                    username = _username.value,
                    password = _password.value,
                ),
            )
        }
    }

    fun loginWithGoogle() {
        if (isSignedInOrBusy()) {
            return
        }

        authenticate(AuthType.GOOGLE) {
            authenticateWithOAuthInteractor(AuthenticateWithOAuthInteractor.Params(OAuthProvider.GOOGLE))
        }
    }

    fun loginWithDiscord() {
        if (isSignedInOrBusy()) {
            return
        }

        authenticate(AuthType.DISCORD) {
            authenticateWithOAuthInteractor(AuthenticateWithOAuthInteractor.Params(OAuthProvider.DISCORD))
        }
    }

    /**
     * A sign-in attempt is a no-op when already signed in as a non-guest (guests may still upgrade
     * via a real login) or while another authentication is in flight.
     */
    private fun isSignedInOrBusy(): Boolean =
        (_isAuthenticated.value == true && authenticatedUser?.isGuest != true) ||
            _loadingState.value is LoadState.Loading

    /**
     * Shared driver for the flow-based sign-in paths (password + SSO: Google, Discord): shows the
     * loading state, reveals the "Signing you in..." setup view only if setup outlives a short grace
     * period, and emits [loginSuccess] on completion. [obtainResults] supplies the interactor's result flow.
     */
    private fun authenticate(
        authType: AuthType,
        obtainResults: suspend () -> Flow<SuspendableResult<UserAuthenticatingState, Exception>>,
    ) {
        _loadingState.value = LoadState.Loading(authType)
        _setupState.value = null

        coroutineScope.launch(dispatcherProvider.io) {
            // Bookkeeping is confined to the main dispatcher (below), so plain vars are race-free.
            var failed = false
            var graceElapsed = false
            var latestProgress: SetupProgress? = null
            var graceJob: Job? = null

            obtainResults().collect { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Failure -> {
                            failed = true
                            graceJob?.cancel()
                            _setupState.value = null
                            _loadingState.value = LoadState.Error(result.error.localizedMessage)
                        }

                        is SuspendableResult.Success -> {
                            val progress = result.value.toSetupProgress()
                            latestProgress = progress
                            if (graceJob == null) {
                                // Only reveal the setup view if setup is still running after a short grace
                                // period, so fast (cached) logins go straight into the app without a flash.
                                graceJob =
                                    coroutineScope.launch(dispatcherProvider.main) {
                                        delay(SETUP_VIEW_GRACE_MILLIS)
                                        graceElapsed = true
                                        if (!failed) {
                                            _setupState.value = latestProgress
                                        }
                                    }
                            } else if (graceElapsed) {
                                _setupState.value = progress
                            }
                        }
                    }
                }
            }

            withContext(dispatcherProvider.main) {
                if (!failed) {
                    graceJob?.cancel()
                    _setupState.value = null
                    _loadingState.value = LoadState.NotLoading()
                    _isAuthenticated.value = true
                    _loginSuccess.emit(Unit)
                }
            }
        }
    }

    fun loginAsGuest() {
        if (_isAuthenticated.value == true || _loadingState.value is LoadState.Loading) {
            return
        }

        _loadingState.value = LoadState.Loading(AuthType.GUEST)

        coroutineScope.launch(dispatcherProvider.io) {
            authenticateGuestInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) {
                    when (result) {
                        is SuspendableResult.Failure -> {
                            _loadingState.value =
                                LoadState.Error(result.error.localizedMessage)
                        }

                        is SuspendableResult.Success -> {
                            _loadingState.value = LoadState.NotLoading()
                            _isAuthenticated.value = true
                            _loginSuccess.emit(Unit)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** How long setup may run before the "Signing you in..." view is shown, to avoid a fast-login flash. */
        private const val SETUP_VIEW_GRACE_MILLIS = 300L
    }
}
