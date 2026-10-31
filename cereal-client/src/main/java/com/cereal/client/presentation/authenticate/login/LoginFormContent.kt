package com.cereal.client.presentation.authenticate.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import com.cereal_automation.cereal_client.generated.resources.login_guest_hint
import com.cereal_automation.cereal_client.generated.resources.login_or
import com.cereal_automation.cereal_client.generated.resources.password
import com.cereal_automation.cereal_client.generated.resources.signing_in
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Reusable login form content that can be used in both the full LoginScreen
 * and in dialog contexts: the SSO buttons (each behind its feature flag), the "or" divider,
 * the email/password form and the submit button.
 *
 * @param loginViewModel The view model containing login state and actions
 * @param focusRequester Optional focus requester for the email field
 */
@Composable
fun LoginFormContent(
    loginViewModel: LoginViewModel,
    focusRequester: FocusRequester? = null,
    onForgotPasswordClick: () -> Unit = {},
) {
    val loadingState = loginViewModel.loadingState.value
    val flags = koinInject<FeatureFlagRepository>()
    val google = flags.isEnabled(FeatureFlag.GOOGLE_SIGN_IN)
    val discord = flags.isEnabled(FeatureFlag.DISCORD_SIGN_IN)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (google || discord) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Brand marks render as Image (not Icon) so they are never tinted, per Google's and
                // Discord's brand guidelines. Sign-in is brokered through the marketplace backend.
                if (google) {
                    OAuthButton(Res.drawable.ic_google, stringResource(Res.string.login_google), loadingState.isLoading(AuthType.GOOGLE)) {
                        loginViewModel.loginWithGoogle()
                    }
                }
                if (discord) {
                    OAuthButton(Res.drawable.ic_discord, stringResource(Res.string.login_discord), loadingState.isLoading(AuthType.DISCORD)) {
                        loginViewModel.loginWithDiscord()
                    }
                }
            }
            OrDivider()
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val isError = loadingState is LoadState.Error
            if (loadingState is LoadState.Error) {
                AuthErrorMessage(errorMessage = loadingState.message)
            }

            AuthTextField(
                label = stringResource(Res.string.email_address),
                value = loginViewModel.username.value,
                onValueChange = { loginViewModel.onUsernameChanged(it) },
                placeholder = stringResource(Res.string.email_address_placeholder),
                icon = Icons.Outlined.Email,
                isError = isError,
                modifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            )

            AuthTextField(
                label = stringResource(Res.string.password),
                labelAction = stringResource(Res.string.forgot_password),
                onLabelActionClick = onForgotPasswordClick,
                value = loginViewModel.password.value,
                onValueChange = { loginViewModel.onPasswordChanged(it) },
                placeholder = "",
                icon = Icons.Outlined.Lock,
                isPassword = true,
                isError = isError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { loginViewModel.login() }),
            )

            CerealButton(
                onClick = { loginViewModel.login() },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(40.dp),
                type = CerealButtonType.Primary,
                size = CerealButtonSize.Large,
            ) {
                if (loadingState.isLoading(AuthType.REGULAR)) {
                    CerealCircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    CerealText(text = stringResource(Res.string.signing_in), modifier = Modifier.padding(start = 8.dp))
                } else {
                    CerealText(text = stringResource(Res.string.login))
                }
            }
        }
    }
}

private fun LoadState.isLoading(authType: AuthType) = this is LoadState.Loading && this.authType == authType

@Composable
private fun RowScope.OAuthButton(
    mark: DrawableResource,
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    CerealButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(38.dp),
        type = CerealButtonType.Neutral,
    ) {
        if (loading) {
            CerealCircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
            )
        } else {
            Image(painter = painterResource(mark), contentDescription = null, modifier = Modifier.size(16.dp))
            CerealText(
                text = text,
                modifier = Modifier.padding(start = 9.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = CerealTheme.colorScheme.border)
        CerealText(
            text = stringResource(Res.string.login_or).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.14.em),
            color = CerealTheme.colorScheme.contentTertiary,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = CerealTheme.colorScheme.border)
    }
}

/** The dashed "Continue without an account" card shown below the login card. */
@Composable
fun GuestLoginButton(loginViewModel: LoginViewModel) {
    val borderColor = CerealTheme.colorScheme.borderDark
    val shape = MaterialTheme.shapes.large
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val radius = 12.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }.clip(shape)
                .clickable(role = Role.Button) { loginViewModel.loginAsGuest() }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = stringResource(Res.string.login_guest),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            CerealText(
                text = stringResource(Res.string.login_guest_hint),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        if (loginViewModel.loadingState.value.isLoading(AuthType.GUEST)) {
            CerealCircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
