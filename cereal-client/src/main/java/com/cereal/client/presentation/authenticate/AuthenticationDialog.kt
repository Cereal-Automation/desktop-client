package com.cereal.client.presentation.authenticate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.authenticate.forgotpassword.ForgotPasswordFormContent
import com.cereal.client.presentation.authenticate.forgotpassword.ForgotPasswordViewModel
import com.cereal.client.presentation.authenticate.login.LoginFormContent
import com.cereal.client.presentation.authenticate.login.LoginViewModel
import com.cereal.client.presentation.authenticate.registration.RegistrationFormContent
import com.cereal.client.presentation.authenticate.registration.RegistrationViewModel
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealSnackbarHost
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.Dialog
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.already_have_account_label
import com.cereal_automation.cereal_client.generated.resources.create_account
import com.cereal_automation.cereal_client.generated.resources.forgot_password_back_to_login
import com.cereal_automation.cereal_client.generated.resources.forgot_password_success
import com.cereal_automation.cereal_client.generated.resources.forgot_password_title
import com.cereal_automation.cereal_client.generated.resources.login
import com.cereal_automation.cereal_client.generated.resources.login_action
import com.cereal_automation.cereal_client.generated.resources.new_to_platform_action
import com.cereal_automation.cereal_client.generated.resources.new_to_platform_label
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

enum class AuthenticationDialogMode {
    LOGIN,
    REGISTRATION,
    FORGOT_PASSWORD,
}

@Composable
fun AuthenticationDialog(
    initialMode: AuthenticationDialogMode = AuthenticationDialogMode.LOGIN,
    loginViewModel: LoginViewModel =
        run {
            val scope = rememberCoroutineScope()
            remember {
                KoinJavaComponent.get(LoginViewModel::class.java, parameters = { parametersOf(scope) })
            }
        },
    registrationViewModel: RegistrationViewModel =
        remember {
            KoinJavaComponent.get(RegistrationViewModel::class.java)
        },
    forgotPasswordViewModel: ForgotPasswordViewModel =
        remember {
            KoinJavaComponent.get(ForgotPasswordViewModel::class.java)
        },
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
) {
    val currentMode = remember { mutableStateOf(initialMode) }
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val successMessage = stringResource(Res.string.forgot_password_success)

    // Close the dialog upon successful authentication
    LaunchedEffect(loginViewModel) {
        loginViewModel.loginSuccess.collect {
            onDismissRequest()
        }
    }

    LaunchedEffect(registrationViewModel) {
        registrationViewModel.registrationSuccess.collect {
            onDismissRequest()
        }
    }

    LaunchedEffect(currentMode.value) { focusRequester.requestFocus() }

    val title =
        when (currentMode.value) {
            AuthenticationDialogMode.LOGIN -> stringResource(Res.string.login)
            AuthenticationDialogMode.REGISTRATION -> stringResource(Res.string.create_account)
            AuthenticationDialogMode.FORGOT_PASSWORD -> stringResource(Res.string.forgot_password_title)
        }

    // Check if authentication is in progress
    val isAuthenticating =
        loginViewModel.loadingState.value is LoadState.Loading ||
            registrationViewModel.loadingState.value is LoadState.Loading

    Dialog(
        title = title,
        modifier =
            modifier
                .width(800.dp)
                .height(600.dp)
                .padding(horizontal = 6.dp),
        onDismissRequest = {
            if (!isAuthenticating) onDismissRequest()
        },
        dismissButtonEnabled = !isAuthenticating,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(450.dp)
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    when (currentMode.value) {
                        AuthenticationDialogMode.LOGIN -> {
                            LoginFormContent(
                                loginViewModel = loginViewModel,
                                focusRequester = focusRequester,
                                showGuestLogin = false,
                                onForgotPasswordClick = {
                                    currentMode.value = AuthenticationDialogMode.FORGOT_PASSWORD
                                },
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            AuthDialogFooter(
                                label = stringResource(Res.string.new_to_platform_label) + " ",
                                action = stringResource(Res.string.new_to_platform_action),
                                onClick = { currentMode.value = AuthenticationDialogMode.REGISTRATION },
                            )
                        }

                        AuthenticationDialogMode.REGISTRATION -> {
                            RegistrationFormContent(
                                registrationViewModel = registrationViewModel,
                                focusRequester = focusRequester,
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            AuthDialogFooter(
                                label = stringResource(Res.string.already_have_account_label) + " ",
                                action = stringResource(Res.string.login_action),
                                onClick = { currentMode.value = AuthenticationDialogMode.LOGIN },
                            )
                        }

                        AuthenticationDialogMode.FORGOT_PASSWORD -> {
                            ForgotPasswordFormContent(
                                viewModel = forgotPasswordViewModel,
                                focusRequester = focusRequester,
                                onSuccess = { submittedEmail ->
                                    // Pre-fill the login form email and navigate back
                                    loginViewModel.prefillUsername(submittedEmail)
                                    currentMode.value = AuthenticationDialogMode.LOGIN
                                    // Show toast notification
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(successMessage)
                                    }
                                },
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            AuthDialogFooter(
                                label = "",
                                action = stringResource(Res.string.forgot_password_back_to_login),
                                onClick = { currentMode.value = AuthenticationDialogMode.LOGIN },
                            )
                        }
                    }
                }
            }

            // Snackbar overlay
            CerealSnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
            )
        }
    }
}

@Composable
private fun AuthDialogFooter(
    label: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CerealText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CerealTheme.colorScheme.contentTertiary,
        )
        CerealTextButton(onClick = onClick) {
            CerealText(
                text = action,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
