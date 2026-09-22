package com.cereal.client.presentation.authenticate.registration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.cereal.client.presentation.authenticate.components.AuthenticationLayout
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.already_have_account_label
import com.cereal_automation.cereal_client.generated.resources.login_action
import org.jetbrains.compose.resources.stringResource

@Composable
fun RegistrationScreen(
    registrationViewModel: RegistrationViewModel,
    onNavigateToLogin: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AuthenticationLayout(
        footerLabel = stringResource(Res.string.already_have_account_label) + " ",
        footerAction = stringResource(Res.string.login_action),
        onFooterActionClick = onNavigateToLogin,
    ) {
        RegistrationFormContent(
            registrationViewModel = registrationViewModel,
            focusRequester = focusRequester,
        )
    }
}
