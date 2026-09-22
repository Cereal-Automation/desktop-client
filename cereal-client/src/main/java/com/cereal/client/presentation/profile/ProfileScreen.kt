package com.cereal.client.presentation.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.presentation.authenticate.AuthenticationDialog
import com.cereal.client.presentation.authenticate.AuthenticationDialogMode
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
@ExperimentalFoundationApi
fun ProfileScreen(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    applicationConfig: ApplicationConfig = koinInject(),
    vm: ProfileViewModel =
        remember {
            KoinJavaComponent.get(
                ProfileViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
) {
    val user = vm.user.value
    val showAuthDialog = remember { mutableStateOf(false) }
    val authDialogMode = remember { mutableStateOf(AuthenticationDialogMode.LOGIN) }

    // Show authentication dialog
    if (showAuthDialog.value) {
        AuthenticationDialog(
            initialMode = authDialogMode.value,
            onDismissRequest = { showAuthDialog.value = false },
        )
    }

    if (user?.isGuest == true) {
        GuestProfileScreen(
            onLoginClick = {
                authDialogMode.value = AuthenticationDialogMode.LOGIN
                showAuthDialog.value = true
            },
            onCreateAccountClick = {
                authDialogMode.value = AuthenticationDialogMode.REGISTRATION
                showAuthDialog.value = true
            },
        )
    } else {
        AuthenticatedProfileScreen(
            applicationConfig = applicationConfig,
            vm = vm,
        )
    }
}
