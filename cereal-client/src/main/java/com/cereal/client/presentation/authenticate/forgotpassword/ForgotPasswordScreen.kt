package com.cereal.client.presentation.authenticate.forgotpassword

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.cereal.client.presentation.authenticate.components.AuthenticationLayout
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.forgot_password_back_to_login
import org.jetbrains.compose.resources.stringResource

@Composable
fun ForgotPasswordScreen(
    forgotPasswordViewModel: ForgotPasswordViewModel,
    onNavigateToLogin: () -> Unit,
    onSuccess: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AuthenticationLayout(
        footerLabel = "",
        footerAction = stringResource(Res.string.forgot_password_back_to_login),
        onFooterActionClick = onNavigateToLogin,
    ) {
        ForgotPasswordFormContent(
            viewModel = forgotPasswordViewModel,
            focusRequester = focusRequester,
            onSuccess = onSuccess,
        )
    }
}
