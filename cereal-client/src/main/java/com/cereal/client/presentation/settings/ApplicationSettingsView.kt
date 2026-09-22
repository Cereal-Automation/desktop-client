package com.cereal.client.presentation.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.feedback.feedbackView
import com.cereal.client.presentation.settings.components.DiscordWebhookUrlField
import com.cereal.client.presentation.settings.components.EmailFields
import com.cereal.client.presentation.settings.components.TelegramFields
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.ConfigurationPanel
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.SettingsActionRow
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSectionCard
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.SettingsToggleRow
import com.cereal.client.presentation.view.TestButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.app_icon
import com.cereal_automation.cereal_client.generated.resources.application
import com.cereal_automation.cereal_client.generated.resources.check
import com.cereal_automation.cereal_client.generated.resources.check_for_updates
import com.cereal_automation.cereal_client.generated.resources.check_for_updates_available_desc
import com.cereal_automation.cereal_client.generated.resources.check_for_updates_desc
import com.cereal_automation.cereal_client.generated.resources.crash_reporting
import com.cereal_automation.cereal_client.generated.resources.crash_reporting_desc
import com.cereal_automation.cereal_client.generated.resources.desktop_notifications
import com.cereal_automation.cereal_client.generated.resources.desktop_notifications_desc
import com.cereal_automation.cereal_client.generated.resources.developers
import com.cereal_automation.cereal_client.generated.resources.discord_servers
import com.cereal_automation.cereal_client.generated.resources.discord_servers_desc
import com.cereal_automation.cereal_client.generated.resources.enable_discord_status
import com.cereal_automation.cereal_client.generated.resources.enable_discord_status_desc
import com.cereal_automation.cereal_client.generated.resources.enable_email
import com.cereal_automation.cereal_client.generated.resources.enable_email_desc
import com.cereal_automation.cereal_client.generated.resources.enable_telegram
import com.cereal_automation.cereal_client.generated.resources.enable_telegram_desc
import com.cereal_automation.cereal_client.generated.resources.enable_webhook
import com.cereal_automation.cereal_client.generated.resources.enable_webhook_desc
import com.cereal_automation.cereal_client.generated.resources.general
import com.cereal_automation.cereal_client.generated.resources.get_in_touch
import com.cereal_automation.cereal_client.generated.resources.github
import com.cereal_automation.cereal_client.generated.resources.github_desc
import com.cereal_automation.cereal_client.generated.resources.logs_directory
import com.cereal_automation.cereal_client.generated.resources.logs_directory_desc
import com.cereal_automation.cereal_client.generated.resources.my_scripts
import com.cereal_automation.cereal_client.generated.resources.my_scripts_desc
import com.cereal_automation.cereal_client.generated.resources.notifications
import com.cereal_automation.cereal_client.generated.resources.open
import com.cereal_automation.cereal_client.generated.resources.privacy
import com.cereal_automation.cereal_client.generated.resources.proxies
import com.cereal_automation.cereal_client.generated.resources.save
import com.cereal_automation.cereal_client.generated.resources.settings_footer_tagline
import com.cereal_automation.cereal_client.generated.resources.settings_proxy_health_check_24h
import com.cereal_automation.cereal_client.generated.resources.settings_proxy_health_check_6h
import com.cereal_automation.cereal_client.generated.resources.settings_proxy_health_check_desc
import com.cereal_automation.cereal_client.generated.resources.settings_proxy_health_check_off
import com.cereal_automation.cereal_client.generated.resources.settings_proxy_health_check_title
import com.cereal_automation.cereal_client.generated.resources.show_debug_logs
import com.cereal_automation.cereal_client.generated.resources.show_debug_logs_desc
import com.cereal_automation.cereal_client.generated.resources.show_development_scripts
import com.cereal_automation.cereal_client.generated.resources.show_development_scripts_desc
import com.cereal_automation.cereal_client.generated.resources.test_message
import com.cereal_automation.cereal_client.generated.resources.test_webhook
import com.cereal_automation.cereal_client.generated.resources.update
import com.cereal_automation.cereal_client.generated.resources.update_available_message
import com.cereal_automation.cereal_client.generated.resources.update_available_title
import com.cereal_automation.cereal_client.generated.resources.update_required_message
import com.cereal_automation.cereal_client.generated.resources.update_required_title
import com.cereal_automation.cereal_client.generated.resources.version
import com.cereal_automation.cereal_client.generated.resources.visit
import com.cereal_automation.cereal_client.generated.resources.website
import com.cereal_automation.cereal_client.generated.resources.website_desc
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
@ExperimentalFoundationApi
fun ApplicationSettingsScreen(
    onNavigateToMyScripts: () -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    applicationConfig: ApplicationConfig = koinInject(),
    vm: ApplicationSettingsViewModel =
        remember {
            KoinJavaComponent.get(
                ApplicationSettingsViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
) {
    Scaffold { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .widthIn(max = 900.dp) // 720px × 1.25
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp), // 32px × 1.25
                verticalArrangement = Arrangement.spacedBy(50.dp), // 40px × 1.25
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 50.dp), // 40px × 1.25
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(90.dp) // 72px × 1.25
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)) // 16 × 1.25
                                    .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(20.dp))
                                    .padding(12.dp), // 10 × 1.25 ≈ 12
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.application),
                                contentDescription = stringResource(Res.string.app_icon),
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp)), // 8 × 1.25
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp)) // 14 × 1.25
                        CerealText(
                            text = applicationConfig.name,
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp, // 22px × 1.25
                                    letterSpacing = (-0.25).sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        CerealText(
                            text = stringResource(Res.string.version, applicationConfig.versionName).uppercase(),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 13.sp, // 10.5px × 1.25
                                    letterSpacing = 2.4.sp,
                                    color = CerealTheme.colorScheme.contentSubtle,
                                    fontWeight = FontWeight.Bold,
                                ),
                            modifier = Modifier.padding(top = 3.dp), // 2 × 1.25
                        )
                    }
                }

                // General Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.general)) {
                        // "My Scripts" manages installed/marketplace scripts — hidden in white-label
                        // builds, which are locked to their Brand scripts (see docs/adr/0003).
                        if (!applicationConfig.isBranded) {
                            SettingsActionRow(
                                title = stringResource(Res.string.my_scripts),
                                description = stringResource(Res.string.my_scripts_desc),
                                actionText = stringResource(Res.string.open),
                                onClick = onNavigateToMyScripts,
                            )
                        }
                        SettingsToggleRow(
                            title = stringResource(Res.string.enable_discord_status),
                            description = stringResource(Res.string.enable_discord_status_desc),
                            checked = vm.discordActivityStatusEnabled.value,
                            onCheckedChange = { vm.setDiscordActivityStatusEnabled(it) },
                        )
                        val checkForUpdatesDesc =
                            vm.updateVersionString.value
                                ?.let { stringResource(Res.string.check_for_updates_available_desc, it) }
                                ?: stringResource(Res.string.check_for_updates_desc)
                        SettingsActionRow(
                            title = if (vm.updateAvailableIndicator.value) stringResource(Res.string.update_available_title) else stringResource(Res.string.check_for_updates),
                            description = checkForUpdatesDesc,
                            actionText = if (vm.updateAvailableIndicator.value) stringResource(Res.string.update).uppercase() else stringResource(Res.string.check),
                            isLoading = vm.isCheckingForUpdates.value,
                            enabled = !vm.isCheckingForUpdates.value,
                            showNotificationDot = vm.updateAvailableIndicator.value,
                            isPrimary = vm.updateAvailableIndicator.value,
                            onClick = { vm.checkForUpdates() },
                        )
                    }
                }

                // Notifications Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.notifications)) {
                        // Desktop
                        SettingsToggleRow(
                            title = stringResource(Res.string.desktop_notifications),
                            description = stringResource(Res.string.desktop_notifications_desc),
                            checked = vm.desktopNotificationsEnabled.value,
                            onCheckedChange = { vm.setDesktopNotificationsEnabled(it) },
                        )

                        // Telegram
                        SettingsToggleRow(
                            title = stringResource(Res.string.enable_telegram),
                            description = stringResource(Res.string.enable_telegram_desc),
                            checked = vm.telegramEnabled.value,
                            onCheckedChange = { vm.setTelegramEnabled(it) },
                        )
                        if (vm.telegramEnabled.value) {
                            ConfigurationPanel {
                                TelegramFields(
                                    botTokenState = vm.telegramBotTokenState,
                                    chatIdState = vm.telegramChatIdState,
                                )
                                TestButton(
                                    text = stringResource(Res.string.test_message),
                                    onClick = { vm.testTelegramMessage() },
                                )
                            }
                        }

                        // Discord
                        SettingsToggleRow(
                            title = stringResource(Res.string.enable_webhook),
                            description = stringResource(Res.string.enable_webhook_desc),
                            checked = vm.discordWebhookEnabledState.value,
                            onCheckedChange = { vm.setDiscordWebhookEnabled(it) },
                        )

                        if (vm.discordWebhookEnabledState.value) {
                            ConfigurationPanel {
                                DiscordWebhookUrlField(vm.discordWebhookUrlState)
                                TestButton(
                                    text = stringResource(Res.string.test_webhook),
                                    onClick = { vm.testDiscordWebhook() },
                                )
                            }
                        }

                        // Email
                        SettingsToggleRow(
                            title = stringResource(Res.string.enable_email),
                            description = stringResource(Res.string.enable_email_desc),
                            checked = vm.emailEnabled.value,
                            onCheckedChange = { vm.setEmailEnabled(it) },
                        )

                        if (vm.emailEnabled.value) {
                            ConfigurationPanel {
                                EmailFields(
                                    smtpHostState = vm.emailSmtpHostState,
                                    smtpPortState = vm.emailSmtpPortState,
                                    usernameState = vm.emailUsernameState,
                                    passwordState = vm.emailPasswordState,
                                    fromState = vm.emailFromState,
                                    toState = vm.emailToState,
                                    useTls = vm.emailUseTls,
                                    onUseTlsChange = { vm.setEmailUseTls(it) },
                                )
                                TestButton(
                                    text = stringResource(Res.string.test_message),
                                    onClick = { vm.testEmailMessage() },
                                )
                            }
                        }

                        // Save Button
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 18.dp), // 18/14 × 1.25
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            SettingsSmallButton(
                                text = stringResource(Res.string.save),
                                onClick = { vm.saveAllNotifications() },
                                variant = SettingsButtonVariant.Primary,
                            )
                        }
                    }
                }

                // Get In Touch Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.get_in_touch)) {
                        SettingsActionRow(
                            title = stringResource(Res.string.discord_servers),
                            description = stringResource(Res.string.discord_servers_desc),
                            actionText = stringResource(Res.string.visit),
                            icon = { GetInTouchIcon(Icons.Default.Forum) },
                            onClick = { vm.openUrl(applicationConfig.discordUrl) },
                        )

                        SettingsActionRow(
                            title = stringResource(Res.string.github),
                            description = stringResource(Res.string.github_desc),
                            actionText = stringResource(Res.string.visit),
                            icon = { GetInTouchIcon(Icons.Default.Code) },
                            onClick = { vm.openUrl(applicationConfig.githubUrl) },
                        )

                        SettingsActionRow(
                            title = stringResource(Res.string.website),
                            description = stringResource(Res.string.website_desc),
                            actionText = stringResource(Res.string.visit),
                            icon = { GetInTouchIcon(Icons.Default.Public) },
                            onClick = { vm.openUrl(applicationConfig.websiteUrl) },
                        )

                        SettingsActionRow(
                            title = stringResource(Res.string.logs_directory),
                            description = stringResource(Res.string.logs_directory_desc),
                            actionText = stringResource(Res.string.open),
                            icon = { GetInTouchIcon(Icons.Default.FolderOpen) },
                            onClick = { vm.openPath(applicationConfig.logsDirectory) },
                        )
                    }
                }

                // Proxies Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.proxies)) {
                        ProxyHealthCheckIntervalRow(
                            selected = vm.proxyHealthCheckInterval.value,
                            onSelect = { vm.setProxyHealthCheckInterval(it) },
                        )
                    }
                }

                // Privacy Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.privacy)) {
                        SettingsToggleRow(
                            title = stringResource(Res.string.crash_reporting),
                            description = stringResource(Res.string.crash_reporting_desc),
                            checked = vm.crashReportingEnabled.value,
                            onCheckedChange = { vm.setCrashReportingEnabled(it) },
                        )
                    }
                }

                // Developers Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.developers), noBorder = true) {
                        SettingsToggleRow(
                            title = stringResource(Res.string.show_development_scripts),
                            description = stringResource(Res.string.show_development_scripts_desc),
                            checked = vm.developmentScriptsEnabled.value,
                            enabled = !vm.isDevelopmentScriptsUpdating.value,
                            onCheckedChange = { vm.setDevelopmentScriptsEnabled(it) },
                        )
                        SettingsToggleRow(
                            title = stringResource(Res.string.show_debug_logs),
                            description = stringResource(Res.string.show_debug_logs_desc),
                            checked = vm.showDebugLogsEnabled.value,
                            onCheckedChange = { vm.setShowDebugLogs(it) },
                        )
                    }
                }

                // Footer
                item {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 40.dp), // 32 × 1.25
                        contentAlignment = Alignment.Center,
                    ) {
                        CerealText(
                            text = stringResource(Res.string.settings_footer_tagline),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 13.sp, // 10px × 1.25
                                    letterSpacing = 2.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CerealTheme.colorScheme.contentTertiary,
                                ),
                            modifier = Modifier.alpha(0.5f),
                        )
                    }
                }
            }
        }

        errorView(vm.errorAction)
        feedbackView(vm.feedbackAction.value)

        // Update available dialog
        vm.updateAvailableVersion.value?.let { version ->
            ConfirmationDialog(
                title = stringResource(Res.string.update_available_title),
                message = stringResource(Res.string.update_available_message),
                confirmButtonText = stringResource(Res.string.update),
                onConfirm = { vm.onConfirmUpdate(version) },
                onDismiss = { vm.dismissUpdateAvailableDialog() },
            )
        }

        // Update required dialog (no dismiss)
        vm.updateRequiredVersion.value?.let { version ->
            AlertDialog(
                modifier = Modifier.widthIn(min = 400.dp),
                onDismissRequest = { /* mandatory — no dismiss */ },
                title = { CerealText(stringResource(Res.string.update_required_title)) },
                text = { CerealText(stringResource(Res.string.update_required_message)) },
                confirmButton = {
                    CerealTextButton(onClick = { vm.onConfirmUpdate(version) }) {
                        CerealText(stringResource(Res.string.update).uppercase())
                    }
                },
            )
        }

        // Download progress dialog
        vm.updateDownloadProgress.value?.let { progress ->
            CheckForUpdatesDownloadDialog(
                progress = progress,
                statusText = vm.updateDownloadStatus.value,
                installerFile = vm.openInstaller.value,
                onOpenInstaller = { file ->
                    vm.openInstallerFile(file)
                    vm.dismissDownloadDialog()
                },
                onDismiss = { vm.dismissDownloadDialog() },
            )
        }
    }
}

@Composable
private fun GetInTouchIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = CerealTheme.colorScheme.contentSecondary,
        modifier = Modifier.size(16.dp), // 13px × 1.25
    )
}

@Composable
private fun ProxyHealthCheckIntervalRow(
    selected: com.cereal.client.domain.model.settings.ProxyHealthCheckInterval,
    onSelect: (com.cereal.client.domain.model.settings.ProxyHealthCheckInterval) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = stringResource(Res.string.settings_proxy_health_check_title),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White,
                    ),
            )
            Spacer(modifier = Modifier.size(4.dp))
            CerealText(
                text = stringResource(Res.string.settings_proxy_health_check_desc),
                color = CerealTheme.colorScheme.contentTertiary,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
            )
        }
        Spacer(modifier = Modifier.size(20.dp))
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.cereal.client.domain.model.settings.ProxyHealthCheckInterval.entries.forEach { option ->
                SettingsSmallButton(
                    text = stringResource(option.labelResource()),
                    onClick = { onSelect(option) },
                    variant =
                        if (option == selected) {
                            SettingsButtonVariant.Primary
                        } else {
                            SettingsButtonVariant.Flat
                        },
                )
            }
        }
    }
}

private fun com.cereal.client.domain.model.settings.ProxyHealthCheckInterval.labelResource() =
    when (this) {
        com.cereal.client.domain.model.settings.ProxyHealthCheckInterval.OFF -> {
            Res.string.settings_proxy_health_check_off
        }

        com.cereal.client.domain.model.settings.ProxyHealthCheckInterval.EVERY_6_HOURS -> {
            Res.string.settings_proxy_health_check_6h
        }

        com.cereal.client.domain.model.settings.ProxyHealthCheckInterval.EVERY_24_HOURS -> {
            Res.string.settings_proxy_health_check_24h
        }
    }
