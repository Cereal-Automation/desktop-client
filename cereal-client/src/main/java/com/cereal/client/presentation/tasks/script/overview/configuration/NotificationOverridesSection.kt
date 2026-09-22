package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.settings.components.ConfigurationContainer
import com.cereal.client.presentation.settings.components.DiscordWebhookUrlField
import com.cereal.client.presentation.settings.components.EmailFields
import com.cereal.client.presentation.settings.components.TelegramFields
import com.cereal.client.presentation.tasks.script.overview.configuration.model.NotificationOverridesForm
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.Body2Text
import com.cereal.client.presentation.view.CaptionText
import com.cereal.client.presentation.view.SettingsItem
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.collapse
import com.cereal_automation.cereal_client.generated.resources.expand
import com.cereal_automation.cereal_client.generated.resources.notification_overrides
import com.cereal_automation.cereal_client.generated.resources.notification_overrides_desc
import com.cereal_automation.cereal_client.generated.resources.override_discord_settings
import com.cereal_automation.cereal_client.generated.resources.override_discord_settings_desc
import com.cereal_automation.cereal_client.generated.resources.override_email_settings
import com.cereal_automation.cereal_client.generated.resources.override_email_settings_desc
import com.cereal_automation.cereal_client.generated.resources.override_telegram_settings
import com.cereal_automation.cereal_client.generated.resources.override_telegram_settings_desc
import org.jetbrains.compose.resources.stringResource

/**
 * Collapsible section for configuring per-script notification channel overrides.
 */
@Composable
fun NotificationOverridesSection(form: NotificationOverridesForm) {
    val expanded = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Collapsible header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded.value = !expanded.value }
                    .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Body2Text(stringResource(Res.string.notification_overrides))
                CaptionText(
                    text = stringResource(Res.string.notification_overrides_desc),
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
            Icon(
                imageVector = if (expanded.value) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded.value) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
                modifier = Modifier.size(24.dp),
            )
        }

        // Collapsible content
        AnimatedVisibility(
            visible = expanded.value,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                // Discord Override
                DiscordOverrideSection(form)

                Spacer(modifier = Modifier.height(16.dp))

                // Telegram Override
                TelegramOverrideSection(form)

                Spacer(modifier = Modifier.height(16.dp))

                // Email Override
                EmailOverrideSection(form)
            }
        }
    }
}

@Composable
private fun DiscordOverrideSection(form: NotificationOverridesForm) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            title = stringResource(Res.string.override_discord_settings),
            description = stringResource(Res.string.override_discord_settings_desc),
        ) {
            Switch(
                checked = form.discordOverrideEnabled.value,
                onCheckedChange = { form.discordOverrideEnabled.value = it },
            )
        }

        AnimatedVisibility(
            visible = form.discordOverrideEnabled.value,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ConfigurationContainer {
                    DiscordWebhookUrlField(form.discordWebhookUrl)
                }
            }
        }
    }
}

@Composable
private fun TelegramOverrideSection(form: NotificationOverridesForm) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            title = stringResource(Res.string.override_telegram_settings),
            description = stringResource(Res.string.override_telegram_settings_desc),
        ) {
            Switch(
                checked = form.telegramOverrideEnabled.value,
                onCheckedChange = { form.telegramOverrideEnabled.value = it },
            )
        }

        AnimatedVisibility(
            visible = form.telegramOverrideEnabled.value,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ConfigurationContainer {
                    TelegramFields(
                        botTokenState = form.telegramBotToken,
                        chatIdState = form.telegramChatId,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmailOverrideSection(form: NotificationOverridesForm) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            title = stringResource(Res.string.override_email_settings),
            description = stringResource(Res.string.override_email_settings_desc),
        ) {
            Switch(
                checked = form.emailOverrideEnabled.value,
                onCheckedChange = { form.emailOverrideEnabled.value = it },
            )
        }

        AnimatedVisibility(
            visible = form.emailOverrideEnabled.value,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ConfigurationContainer {
                    EmailFields(
                        smtpHostState = form.emailSmtpHost,
                        smtpPortState = form.emailSmtpPort,
                        usernameState = form.emailUsername,
                        passwordState = form.emailPassword,
                        fromState = form.emailFrom,
                        toState = form.emailTo,
                        useTls = form.emailUseTls,
                        onUseTlsChange = { form.emailUseTls.value = it },
                    )
                }
            }
        }
    }
}
