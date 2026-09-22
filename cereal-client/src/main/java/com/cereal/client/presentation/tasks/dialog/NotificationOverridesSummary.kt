package com.cereal.client.presentation.tasks.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.Body2Text
import com.cereal.client.presentation.view.SettingsItem
import com.cereal.client.presentation.view.Subtitle2Text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.chat_id
import com.cereal_automation.cereal_client.generated.resources.disabled
import com.cereal_automation.cereal_client.generated.resources.email_from
import com.cereal_automation.cereal_client.generated.resources.email_password
import com.cereal_automation.cereal_client.generated.resources.email_smtp_host
import com.cereal_automation.cereal_client.generated.resources.email_smtp_port
import com.cereal_automation.cereal_client.generated.resources.email_to
import com.cereal_automation.cereal_client.generated.resources.email_use_tls
import com.cereal_automation.cereal_client.generated.resources.email_username
import com.cereal_automation.cereal_client.generated.resources.enabled
import com.cereal_automation.cereal_client.generated.resources.notification_overrides
import com.cereal_automation.cereal_client.generated.resources.override_discord_settings
import com.cereal_automation.cereal_client.generated.resources.override_email_settings
import com.cereal_automation.cereal_client.generated.resources.override_telegram_settings
import com.cereal_automation.cereal_client.generated.resources.password_placeholder
import com.cereal_automation.cereal_client.generated.resources.telegram_bot_token
import com.cereal_automation.cereal_client.generated.resources.webhook_url
import org.jetbrains.compose.resources.stringResource

/**
 * Read-only summary of the per-script notification channel overrides configured on a script
 * instance. Only the channels that are actually overridden are shown. Secret values (bot tokens,
 * passwords) are masked, mirroring how they are entered as password fields in the configuration
 * form.
 */
@Composable
fun NotificationOverridesSummary(overrides: ScriptNotificationOverrides) {
    if (!overrides.hasAnyOverrides()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Subtitle2Text(stringResource(Res.string.notification_overrides))

        overrides.discordOverrides?.let { discord ->
            OverrideChannel(title = stringResource(Res.string.override_discord_settings)) {
                OverrideValue(stringResource(Res.string.webhook_url), discord.webhookUrl)
            }
        }

        overrides.telegramOverrides?.let { telegram ->
            OverrideChannel(title = stringResource(Res.string.override_telegram_settings)) {
                OverrideValue(stringResource(Res.string.telegram_bot_token), stringResource(Res.string.password_placeholder))
                OverrideValue(stringResource(Res.string.chat_id), telegram.chatId)
            }
        }

        overrides.emailOverrides?.let { email ->
            OverrideChannel(title = stringResource(Res.string.override_email_settings)) {
                EmailOverrideValues(email)
            }
        }
    }
}

@Composable
private fun EmailOverrideValues(email: EmailOverrides) {
    OverrideValue(stringResource(Res.string.email_smtp_host), email.smtpHost)
    OverrideValue(stringResource(Res.string.email_smtp_port), email.smtpPort.toString())
    OverrideValue(stringResource(Res.string.email_username), email.username)
    OverrideValue(stringResource(Res.string.email_password), stringResource(Res.string.password_placeholder))
    OverrideValue(stringResource(Res.string.email_from), email.from)
    OverrideValue(stringResource(Res.string.email_to), email.to)
    OverrideValue(
        stringResource(Res.string.email_use_tls),
        stringResource(if (email.useTls) Res.string.enabled else Res.string.disabled),
    )
}

@Composable
private fun OverrideChannel(
    title: String,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Subtitle2Text(title, color = CerealTheme.colorScheme.contentSecondary)
    content()
}

@Composable
private fun OverrideValue(
    title: String,
    value: String,
) {
    Spacer(Modifier.height(8.dp))
    SettingsItem(title = title) {
        Body2Text(value)
    }
}
