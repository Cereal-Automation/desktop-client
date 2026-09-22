package com.cereal.client.presentation.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.authenticate.forgotpassword.ForgotPasswordScreen
import com.cereal.client.presentation.authenticate.forgotpassword.ForgotPasswordViewModel
import com.cereal.client.presentation.authenticate.login.LoginScreen
import com.cereal.client.presentation.authenticate.login.LoginViewModel
import com.cereal.client.presentation.authenticate.registration.RegistrationScreen
import com.cereal.client.presentation.authenticate.registration.RegistrationViewModel
import com.cereal.client.presentation.brand.BrandEntitlementGate
import com.cereal.client.presentation.brand.BrandPaywallViewModel
import com.cereal.client.presentation.navigation.Root
import com.cereal.client.presentation.navigation.backpress.BackPressHandler
import com.cereal.client.presentation.navigation.backpress.LocalBackPressHandler
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealSnackbarHost
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.forgot_password_success
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import java.awt.Desktop
import java.net.URI

private enum class AuthScreen {
    LOGIN,
    REGISTRATION,
    FORGOT_PASSWORD,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel =
        remember {
            KoinJavaComponent.get(
                MainViewModel::class.java,
            )
        },
    loginViewModel: LoginViewModel =
        run {
            val scope = rememberCoroutineScope()
            remember {
                KoinJavaComponent.get(
                    LoginViewModel::class.java,
                    parameters = { parametersOf(scope) },
                )
            }
        },
    registrationViewModel: RegistrationViewModel =
        remember {
            KoinJavaComponent.get(
                RegistrationViewModel::class.java,
            )
        },
    forgotPasswordViewModel: ForgotPasswordViewModel =
        remember {
            KoinJavaComponent.get(
                ForgotPasswordViewModel::class.java,
            )
        },
    brandPaywallViewModel: BrandPaywallViewModel =
        run {
            val scope = rememberCoroutineScope()
            remember {
                KoinJavaComponent.get(
                    BrandPaywallViewModel::class.java,
                    parameters = { parametersOf(scope) },
                )
            }
        },
) {
    val backPressHandler = remember { BackPressHandler() }
    val currentAuthScreen = remember { mutableStateOf(AuthScreen.LOGIN) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val successMessage = stringResource(Res.string.forgot_password_success)

    // Combine authentication state from both view models. derivedStateOf ensures both state
    // reads are always tracked by the snapshot system, so a change in either triggers recomposition
    // regardless of which value is non-null.
    val isAuthenticated by remember {
        derivedStateOf {
            loginViewModel.isAuthenticated.value ?: registrationViewModel.isAuthenticated.value
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            isAuthenticated?.let { authenticated ->
                if (authenticated) {
                    // A white-label build is gated behind its Brand-script subscription; stock
                    // Cereal passes straight through (see docs/adr/0004).
                    BrandEntitlementGate(brandPaywallViewModel) {
                        CompositionLocalProvider(LocalBackPressHandler provides backPressHandler) {
                            Root.content(Root.Routing.Tasks(), menuComposable = { backStack ->
                                MainMenu(
                                    menuItems = viewModel.menuItems.value,
                                    appVersion = viewModel.appVersion,
                                    activeScriptCount = viewModel.activeScriptCount.value,
                                    onClick = { menuItem ->
                                        if (menuItem.externalUrl != null) {
                                            Desktop.getDesktop().browse(URI(menuItem.externalUrl))
                                        } else if (menuItem.route != null) {
                                            if (menuItem.selected) {
                                                viewModel.onMenuItemReselected(menuItem.route)
                                            } else {
                                                backStack.newRoot(menuItem.route)
                                                viewModel.onMenuItemClicked(menuItem)
                                            }
                                        }
                                    },
                                )
                            }, onNavigateTo = { route ->
                                val menuItem = viewModel.menuItems.value.find { it.route != null && it.route::class == route::class }
                                menuItem?.let { viewModel.onMenuItemClicked(it) }
                            })
                        }
                    }
                } else {
                    when (currentAuthScreen.value) {
                        AuthScreen.LOGIN -> {
                            LoginScreen(
                                loginViewModel = loginViewModel,
                                onNavigateToRegister = { currentAuthScreen.value = AuthScreen.REGISTRATION },
                                onNavigateToForgotPassword = { currentAuthScreen.value = AuthScreen.FORGOT_PASSWORD },
                            )
                        }

                        AuthScreen.REGISTRATION -> {
                            RegistrationScreen(
                                registrationViewModel = registrationViewModel,
                                onNavigateToLogin = { currentAuthScreen.value = AuthScreen.LOGIN },
                            )
                        }

                        AuthScreen.FORGOT_PASSWORD -> {
                            ForgotPasswordScreen(
                                forgotPasswordViewModel = forgotPasswordViewModel,
                                onNavigateToLogin = { currentAuthScreen.value = AuthScreen.LOGIN },
                                onSuccess = { email ->
                                    loginViewModel.prefillUsername(email)
                                    currentAuthScreen.value = AuthScreen.LOGIN
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(successMessage)
                                    }
                                },
                            )
                        }
                    }
                }
            } ?: run {
                // As long as we don't know if the user is authenticated show a loading indicator.
                // Assumed is that there will be no error when determining if the user is authenticated or not. This is
                // possible because in the bootstrap we check if we can retrieve the user and the result is cached.
                Box(modifier = Modifier.fillMaxSize()) {
                    CerealCircularProgressIndicator(
                        modifier = Modifier.size(48.dp).align(alignment = Alignment.Center),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
            }

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
