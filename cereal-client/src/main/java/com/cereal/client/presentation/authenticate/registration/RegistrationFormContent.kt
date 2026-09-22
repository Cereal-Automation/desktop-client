package com.cereal.client.presentation.authenticate.registration

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.authenticate.components.AuthErrorMessage
import com.cereal.client.presentation.authenticate.components.AuthTextField
import com.cereal.client.presentation.authenticate.components.PasswordStrengthIndicator
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
import com.cereal_automation.cereal_client.generated.resources.name
import com.cereal_automation.cereal_client.generated.resources.name_placeholder
import com.cereal_automation.cereal_client.generated.resources.password
import com.cereal_automation.cereal_client.generated.resources.password_placeholder
import com.cereal_automation.cereal_client.generated.resources.register
import org.jetbrains.compose.resources.stringResource

/** Test tags exposed for UI tests targeting the registration form fields and submit button. */
object RegistrationTestTags {
    const val NAME_FIELD = "registration_name_field"
    const val EMAIL_FIELD = "registration_email_field"
    const val PASSWORD_FIELD = "registration_password_field"
    const val SUBMIT_BUTTON = "registration_submit_button"
}

/**
 * Reusable registration form content that can be used in both the full RegistrationScreen
 * and in dialog contexts.
 *
 * @param registrationViewModel The view model containing registration state and actions
 * @param focusRequester Optional focus requester for the name field
 */
@Composable
fun RegistrationFormContent(
    registrationViewModel: RegistrationViewModel,
    focusRequester: FocusRequester? = null,
) {
    // Name Field
    AuthTextField(
        label = stringResource(Res.string.name),
        value = registrationViewModel.name.value,
        onValueChange = { registrationViewModel.name.value = it },
        placeholder = stringResource(Res.string.name_placeholder),
        icon = Icons.Default.Person,
        modifier =
            (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .testTag(RegistrationTestTags.NAME_FIELD),
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Email Field
    AuthTextField(
        label = stringResource(Res.string.email_address),
        value = registrationViewModel.username.value,
        onValueChange = { registrationViewModel.username.value = it },
        placeholder = stringResource(Res.string.email_address_placeholder),
        icon = Icons.Default.AlternateEmail,
        modifier = Modifier.testTag(RegistrationTestTags.EMAIL_FIELD),
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Password Field
    Column {
        CerealText(
            text = stringResource(Res.string.password).uppercase(),
            modifier = Modifier.padding(horizontal = 4.dp),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    color = CerealTheme.colorScheme.contentTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        AuthTextField(
            label = "",
            value = registrationViewModel.password.value,
            onValueChange = { registrationViewModel.updatePassword(it) },
            placeholder = stringResource(Res.string.password_placeholder),
            icon = Icons.Default.LockOpen,
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { registrationViewModel.register() }),
            modifier = Modifier.testTag(RegistrationTestTags.PASSWORD_FIELD),
        )

        // Show password strength indicator when password is not empty
        if (registrationViewModel.password.value.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PasswordStrengthIndicator(
                passwordStrength = registrationViewModel.passwordStrength.value,
            )
        }
    }

    if (registrationViewModel.loadingState.value is LoadState.Error) {
        val errorMessage = (registrationViewModel.loadingState.value as LoadState.Error).message

        Spacer(modifier = Modifier.height(24.dp))
        AuthErrorMessage(errorMessage = errorMessage)
    }

    Spacer(modifier = Modifier.height(24.dp))

    val loadingState = registrationViewModel.loadingState.value

    // Register Button
    CerealButton(
        onClick = { registrationViewModel.register() },
        modifier = Modifier.fillMaxWidth().testTag(RegistrationTestTags.SUBMIT_BUTTON),
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
                    text = stringResource(Res.string.register),
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
}
