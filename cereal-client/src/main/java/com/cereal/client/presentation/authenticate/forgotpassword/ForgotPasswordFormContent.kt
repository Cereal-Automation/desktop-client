package com.cereal.client.presentation.authenticate.forgotpassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.authenticate.components.AuthErrorMessage
import com.cereal.client.presentation.authenticate.components.AuthTextField
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonSize
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.email_address
import com.cereal_automation.cereal_client.generated.resources.email_address_placeholder
import com.cereal_automation.cereal_client.generated.resources.forgot_password_description
import com.cereal_automation.cereal_client.generated.resources.forgot_password_submit
import org.jetbrains.compose.resources.stringResource

/** Test tags exposed for UI tests targeting the forgot-password form field and submit button. */
object ForgotPasswordTestTags {
    const val EMAIL_FIELD = "forgot_password_email_field"
    const val SUBMIT_BUTTON = "forgot_password_submit_button"
}

@Composable
fun ForgotPasswordFormContent(
    viewModel: ForgotPasswordViewModel,
    focusRequester: FocusRequester? = null,
    onSuccess: (submittedEmail: String) -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.successEvent.collect { email ->
            onSuccess(email)
        }
    }

    val loadingState = viewModel.loadingState.value

    Column {
        CerealText(
            text = stringResource(Res.string.forgot_password_description),
            modifier = Modifier.padding(bottom = 24.dp),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )

        AuthTextField(
            label = stringResource(Res.string.email_address),
            value = viewModel.email.value,
            onValueChange = { viewModel.email.value = it },
            placeholder = stringResource(Res.string.email_address_placeholder),
            icon = Icons.Default.AlternateEmail,
            modifier =
                (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .testTag(ForgotPasswordTestTags.EMAIL_FIELD),
        )

        if (loadingState is LoadState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            AuthErrorMessage(errorMessage = loadingState.message)
        }

        Spacer(modifier = Modifier.height(24.dp))

        CerealButton(
            onClick = { viewModel.submit() },
            modifier = Modifier.fillMaxWidth().testTag(ForgotPasswordTestTags.SUBMIT_BUTTON),
            type = CerealButtonType.Primary,
            size = CerealButtonSize.Large,
        ) {
            if (loadingState is LoadState.Loading) {
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
                        text = stringResource(Res.string.forgot_password_submit),
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
}
