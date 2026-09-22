package com.cereal.client.presentation.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.settings.state.DiscordWebhookUrlState
import com.cereal.client.presentation.settings.state.EmailFromState
import com.cereal.client.presentation.settings.state.EmailPasswordState
import com.cereal.client.presentation.settings.state.EmailSmtpHostState
import com.cereal.client.presentation.settings.state.EmailSmtpPortState
import com.cereal.client.presentation.settings.state.EmailToState
import com.cereal.client.presentation.settings.state.EmailUsernameState
import com.cereal.client.presentation.settings.state.TelegramBotTokenState
import com.cereal.client.presentation.settings.state.TelegramChatIdState
import com.cereal.client.presentation.view.InfoTooltip
import com.cereal.client.presentation.view.SettingsFieldRow
import com.cereal.client.presentation.view.SettingsTextInput
import com.cereal.client.presentation.view.SettingsToggle
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.bot_token
import com.cereal_automation.cereal_client.generated.resources.chat_id
import com.cereal_automation.cereal_client.generated.resources.discord_webhook
import com.cereal_automation.cereal_client.generated.resources.discord_webhook_desc
import com.cereal_automation.cereal_client.generated.resources.discord_webhook_help
import com.cereal_automation.cereal_client.generated.resources.discord_webhook_hint
import com.cereal_automation.cereal_client.generated.resources.email_from
import com.cereal_automation.cereal_client.generated.resources.email_from_desc
import com.cereal_automation.cereal_client.generated.resources.email_help
import com.cereal_automation.cereal_client.generated.resources.email_help_hint
import com.cereal_automation.cereal_client.generated.resources.email_password
import com.cereal_automation.cereal_client.generated.resources.email_password_desc
import com.cereal_automation.cereal_client.generated.resources.email_smtp_host
import com.cereal_automation.cereal_client.generated.resources.email_smtp_host_desc
import com.cereal_automation.cereal_client.generated.resources.email_smtp_port
import com.cereal_automation.cereal_client.generated.resources.email_smtp_port_desc
import com.cereal_automation.cereal_client.generated.resources.email_to
import com.cereal_automation.cereal_client.generated.resources.email_to_desc
import com.cereal_automation.cereal_client.generated.resources.email_use_tls
import com.cereal_automation.cereal_client.generated.resources.email_use_tls_desc
import com.cereal_automation.cereal_client.generated.resources.email_username
import com.cereal_automation.cereal_client.generated.resources.email_username_desc
import com.cereal_automation.cereal_client.generated.resources.from_address
import com.cereal_automation.cereal_client.generated.resources.password
import com.cereal_automation.cereal_client.generated.resources.smtp_host
import com.cereal_automation.cereal_client.generated.resources.smtp_port
import com.cereal_automation.cereal_client.generated.resources.telegram_bot_token
import com.cereal_automation.cereal_client.generated.resources.telegram_bot_token_desc
import com.cereal_automation.cereal_client.generated.resources.telegram_chat_id
import com.cereal_automation.cereal_client.generated.resources.telegram_chat_id_desc
import com.cereal_automation.cereal_client.generated.resources.telegram_chat_id_help
import com.cereal_automation.cereal_client.generated.resources.telegram_chat_id_help_hint
import com.cereal_automation.cereal_client.generated.resources.telegram_help
import com.cereal_automation.cereal_client.generated.resources.telegram_help_hint
import com.cereal_automation.cereal_client.generated.resources.to_address
import com.cereal_automation.cereal_client.generated.resources.url
import com.cereal_automation.cereal_client.generated.resources.username
import org.jetbrains.compose.resources.stringResource

/**
 * Legacy: kept for non-settings callers that might wrap fields in a surface.
 * The settings screen now uses [com.cereal.client.presentation.view.ConfigurationPanel].
 */
@Composable
fun ConfigurationContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscordWebhookUrlField(state: DiscordWebhookUrlState) {
    SettingsFieldRow(
        label = stringResource(Res.string.discord_webhook),
        description = stringResource(Res.string.discord_webhook_desc),
        help = {
            InfoTooltip(
                tooltipText = stringResource(Res.string.discord_webhook_hint),
                contentDescription = stringResource(Res.string.discord_webhook_help),
            )
        },
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.url))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramBotTokenField(state: TelegramBotTokenState) {
    SettingsFieldRow(
        label = stringResource(Res.string.telegram_bot_token),
        description = stringResource(Res.string.telegram_bot_token_desc),
        help = {
            InfoTooltip(
                tooltipText = stringResource(Res.string.telegram_help_hint),
                contentDescription = stringResource(Res.string.telegram_help),
            )
        },
    ) {
        SettingsTextInput(
            state = state,
            placeholder = stringResource(Res.string.bot_token),
            isPassword = true,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramChatIdField(state: TelegramChatIdState) {
    SettingsFieldRow(
        label = stringResource(Res.string.telegram_chat_id),
        description = stringResource(Res.string.telegram_chat_id_desc),
        help = {
            InfoTooltip(
                tooltipText = stringResource(Res.string.telegram_chat_id_help_hint),
                contentDescription = stringResource(Res.string.telegram_chat_id_help),
            )
        },
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.chat_id))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmailSmtpHostField(state: EmailSmtpHostState) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_smtp_host),
        description = stringResource(Res.string.email_smtp_host_desc),
        help = {
            InfoTooltip(
                tooltipText = stringResource(Res.string.email_help_hint),
                contentDescription = stringResource(Res.string.email_help),
            )
        },
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.smtp_host))
    }
}

@Composable
fun EmailSmtpPortField(state: EmailSmtpPortState) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_smtp_port),
        description = stringResource(Res.string.email_smtp_port_desc),
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.smtp_port))
    }
}

@Composable
fun EmailUsernameField(state: EmailUsernameState) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_username),
        description = stringResource(Res.string.email_username_desc),
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.username))
    }
}

@Composable
fun EmailPasswordField(state: EmailPasswordState) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_password),
        description = stringResource(Res.string.email_password_desc),
    ) {
        SettingsTextInput(
            state = state,
            placeholder = stringResource(Res.string.password),
            isPassword = true,
        )
    }
}

@Composable
fun EmailFromField(state: EmailFromState) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_from),
        description = stringResource(Res.string.email_from_desc),
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.from_address))
    }
}

@Composable
fun EmailToField(state: EmailToState) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_to),
        description = stringResource(Res.string.email_to_desc),
    ) {
        SettingsTextInput(state = state, placeholder = stringResource(Res.string.to_address))
    }
}

@Composable
fun EmailUseTlsField(
    useTls: MutableState<Boolean>,
    onUseTlsChange: (Boolean) -> Unit,
) {
    SettingsFieldRow(
        label = stringResource(Res.string.email_use_tls),
        description = stringResource(Res.string.email_use_tls_desc),
    ) {
        SettingsToggle(
            checked = useTls.value,
            onCheckedChange = onUseTlsChange,
        )
    }
}

@Composable
fun TelegramFields(
    botTokenState: TelegramBotTokenState,
    chatIdState: TelegramChatIdState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TelegramBotTokenField(botTokenState)
        TelegramChatIdField(chatIdState)
    }
}

@Composable
fun EmailFields(
    smtpHostState: EmailSmtpHostState,
    smtpPortState: EmailSmtpPortState,
    usernameState: EmailUsernameState,
    passwordState: EmailPasswordState,
    fromState: EmailFromState,
    toState: EmailToState,
    useTls: MutableState<Boolean>,
    onUseTlsChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EmailSmtpHostField(smtpHostState)
        EmailSmtpPortField(smtpPortState)
        EmailUsernameField(usernameState)
        EmailPasswordField(passwordState)
        EmailFromField(fromState)
        EmailToField(toState)
        EmailUseTlsField(useTls, onUseTlsChange)
    }
}
