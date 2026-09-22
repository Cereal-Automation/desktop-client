package com.cereal.client.presentation.authenticate.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.featureflag.FeatureFlag
import com.cereal.client.domain.repository.FeatureFlagRepository
import com.cereal.client.presentation.authenticate.components.AuthErrorMessage
import com.cereal.client.presentation.authenticate.components.AuthTextField
import com.cereal.client.presentation.model.AuthType
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonSize
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealOutlinedButton
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.email_address
import com.cereal_automation.cereal_client.generated.resources.email_address_placeholder
import com.cereal_automation.cereal_client.generated.resources.forgot_password
import com.cereal_automation.cereal_client.generated.resources.ic_discord
import com.cereal_automation.cereal_client.generated.resources.ic_google
import com.cereal_automation.cereal_client.generated.resources.login
import com.cereal_automation.cereal_client.generated.resources.login_discord
import com.cereal_automation.cereal_client.generated.resources.login_google
import com.cereal_automation.cereal_client.generated.resources.login_guest
import com.cereal_automation.cereal_client.generated.resources.password
import com.cereal_automation.cereal_client.generated.resources.password_placeholder
import com.cereal_automation.cereal_client.generated.resources.quick_access_seperator
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.get

/**
 * Reusable login form content that can be used in both the full LoginScreen
 * and in dialog contexts.
 *
 * @param loginViewModel The view model containing login state and actions
 * @param focusRequester Optional focus requester for the email field
 * @param showGuestLogin Whether to show the guest login option (default true for full screen, false for dialog)
 */
@Composable
fun LoginFormContent(
    loginViewModel: LoginViewModel,
    focusRequester: FocusRequester? = null,
    showGuestLogin: Boolean = true,
    onForgotPasswordClick: () -> Unit = {},
) {
    // Email Field
    AuthTextField(
        label = stringResource(Res.string.email_address),
        value = loginViewModel.username.value,
        onValueChange = { loginViewModel.onUsernameChanged(it) },
        placeholder = stringResource(Res.string.email_address_placeholder),
        icon = Icons.Default.AlternateEmail,
        modifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Password Field
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CerealText(
                text = stringResource(Res.string.password).uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = CerealTheme.colorScheme.contentTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    ),
            )
            CerealText(
                text = stringResource(Res.string.forgot_password),
                modifier = Modifier.clickable { onForgotPasswordClick() },
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AuthTextField(
            label = "",
            value = loginViewModel.password.value,
            onValueChange = { loginViewModel.onPasswordChanged(it) },
            placeholder = stringResource(Res.string.password_placeholder),
            icon = Icons.Default.LockOpen,
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { loginViewModel.login() }),
        )
    }

    if (loginViewModel.loadingState.value is LoadState.Error) {
        val errorMessage = (loginViewModel.loadingState.value as LoadState.Error).message

        Spacer(modifier = Modifier.height(24.dp))
        AuthErrorMessage(errorMessage = errorMessage)
    }

    Spacer(modifier = Modifier.height(24.dp))

    val loadingState = loginViewModel.loadingState.value
    val applicationConfig: ApplicationConfig =
        remember {
            get(ApplicationConfig::class.java)
        }

    // Buttons
    Column {
        CerealButton(
            onClick = { loginViewModel.login() },
            modifier = Modifier.fillMaxWidth(),
            type = CerealButtonType.Primary,
            size = CerealButtonSize.Large,
        ) {
            if (loadingState is LoadState.Loading && loadingState.authType == AuthType.REGULAR) {
                CerealCircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CerealText(
                        text = stringResource(Res.string.login),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Sign in with Google — brokered through the marketplace backend via the system browser.
        // Behind the GOOGLE_SIGN_IN feature flag (off in shipped builds) so it can ship dark.
        if (koinInject<FeatureFlagRepository>().isEnabled(FeatureFlag.GOOGLE_SIGN_IN)) {
            Spacer(modifier = Modifier.height(16.dp))

            CerealOutlinedButton(
                onClick = { loginViewModel.loginWithGoogle() },
                modifier = Modifier.fillMaxWidth(),
                type = CerealButtonType.Surface,
                size = CerealButtonSize.Large,
            ) {
                if (loadingState is LoadState.Loading && loadingState.authType == AuthType.GOOGLE) {
                    CerealCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Google's multi-colour "G" — rendered as an Image (not Icon) so it is
                        // never tinted, per Google's sign-in branding guidelines.
                        Image(
                            painter = painterResource(Res.drawable.ic_google),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        CerealText(
                            text = stringResource(Res.string.login_google),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }

        // Sign in with Discord — same backend broker as Google, distinct only by provider.
        // Behind the DISCORD_SIGN_IN feature flag (off in shipped builds) so it can ship dark.
        if (koinInject<FeatureFlagRepository>().isEnabled(FeatureFlag.DISCORD_SIGN_IN)) {
            Spacer(modifier = Modifier.height(16.dp))

            CerealOutlinedButton(
                onClick = { loginViewModel.loginWithDiscord() },
                modifier = Modifier.fillMaxWidth(),
                type = CerealButtonType.Surface,
                size = CerealButtonSize.Large,
            ) {
                if (loadingState is LoadState.Loading && loadingState.authType == AuthType.DISCORD) {
                    CerealCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Discord's blurple mark — rendered as an Image (not Icon) so it keeps its
                        // brand colour and is never tinted, per Discord's brand guidelines.
                        Image(
                            painter = painterResource(Res.drawable.ic_discord),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        CerealText(
                            text = stringResource(Res.string.login_discord),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }

        // Guest login section - only show if enabled and requested
        if (showGuestLogin && applicationConfig.isGuestLoginEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            // Separator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = CerealTheme.colorScheme.border,
                )
                CerealText(
                    text = stringResource(Res.string.quick_access_seperator),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            color = CerealTheme.colorScheme.contentTertiary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        ),
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = CerealTheme.colorScheme.border,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guest Button
            CerealOutlinedButton(
                onClick = { loginViewModel.loginAsGuest() },
                modifier = Modifier.fillMaxWidth(),
                type = CerealButtonType.Surface,
                size = CerealButtonSize.Large,
            ) {
                if (loadingState is LoadState.Loading && loadingState.authType == AuthType.GUEST) {
                    CerealCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        CerealText(
                            text = stringResource(Res.string.login_guest),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}
