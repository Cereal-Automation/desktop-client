package com.cereal.client.presentation.authenticate.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.cereal.client.presentation.authenticate.components.AuthenticationLayout
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.new_to_platform_action
import com.cereal_automation.cereal_client.generated.resources.new_to_platform_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AuthenticationLayout(
        footerLabel = stringResource(Res.string.new_to_platform_label) + " ",
        footerAction = stringResource(Res.string.new_to_platform_action),
        onFooterActionClick = onNavigateToRegister,
    ) {
        val setupProgress = loginViewModel.setupState.value
        if (setupProgress != null) {
            // Authentication is accepted and post-auth setup is taking a while: reframe the wait as
            // setup progress instead of leaving the now-irrelevant login form on screen.
            LoginSetupContent(setupProgress = setupProgress)
        } else {
            LoginFormContent(
                loginViewModel = loginViewModel,
                focusRequester = focusRequester,
                showGuestLogin = true,
                onForgotPasswordClick = onNavigateToForgotPassword,
            )
        }
    }
}
