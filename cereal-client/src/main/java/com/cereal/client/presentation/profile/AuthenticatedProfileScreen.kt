package com.cereal.client.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.util.initialsOf
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealOutlinedButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.SettingsActionRow
import com.cereal.client.presentation.view.SettingsSectionCard
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ic_material_logout
import com.cereal_automation.cereal_client.generated.resources.logout
import com.cereal_automation.cereal_client.generated.resources.logout_confirm_message
import com.cereal_automation.cereal_client.generated.resources.logout_confirm_title
import com.cereal_automation.cereal_client.generated.resources.profile
import com.cereal_automation.cereal_client.generated.resources.subscriptions
import com.cereal_automation.cereal_client.generated.resources.subscriptions_desc
import com.cereal_automation.cereal_client.generated.resources.view_profile
import com.cereal_automation.cereal_client.generated.resources.view_profile_desc
import com.cereal_automation.cereal_client.generated.resources.visit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AuthenticatedProfileScreen(
    applicationConfig: ApplicationConfig,
    vm: ProfileViewModel,
) {
    val showLogoutConfirm = remember { mutableStateOf(false) }

    if (showLogoutConfirm.value) {
        ConfirmationDialog(
            title = stringResource(Res.string.logout_confirm_title),
            message = stringResource(Res.string.logout_confirm_message),
            confirmButtonText = stringResource(Res.string.logout),
            onConfirm = {
                showLogoutConfirm.value = false
                vm.logout()
            },
            onDismiss = { showLogoutConfirm.value = false },
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
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
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(vertical = 48.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val initials = initialsOf(vm.user.value?.name)

                        Box(
                            modifier =
                                Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            CerealText(
                                text = initials,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }

                        Spacer(modifier = Modifier.height(CerealTheme.spacing.lg))

                        CerealText(
                            text =
                                vm.user.value
                                    ?.name
                                    .orEmpty(),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )

                        CerealText(
                            text =
                                vm.user.value
                                    ?.email
                                    .orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CerealTheme.colorScheme.contentSecondary,
                            modifier = Modifier.padding(top = CerealTheme.spacing.xxs),
                        )
                    }
                }

                // Profile Section
                item {
                    SettingsSectionCard(title = stringResource(Res.string.profile)) {
                        // The marketplace profile page is hidden in white-label builds (the platform
                        // stays invisible); the Subscriptions/billing link is kept (see docs/adr/0003).
                        if (!applicationConfig.isBranded) {
                            SettingsActionRow(
                                title = stringResource(Res.string.view_profile),
                                description = stringResource(Res.string.view_profile_desc),
                                actionText = stringResource(Res.string.visit),
                                icon = { ExternalLinkIcon() },
                                onClick = { vm.openUrl(applicationConfig.marketplaceViewProfileUrl) },
                            )
                        }

                        SettingsActionRow(
                            title = stringResource(Res.string.subscriptions),
                            description = stringResource(Res.string.subscriptions_desc),
                            actionText = stringResource(Res.string.visit),
                            icon = { ExternalLinkIcon() },
                            onClick = { vm.openUrl(applicationConfig.marketplaceViewSubscriptionsUrl) },
                        )
                    }
                }

                // Logout Button
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CerealOutlinedButton(
                            onClick = { showLogoutConfirm.value = true },
                            type = CerealButtonType.Neutral,
                        ) {
                            CerealText(text = stringResource(Res.string.logout))
                            Spacer(modifier = Modifier.width(CerealTheme.spacing.sm))
                            Icon(
                                painter = painterResource(Res.drawable.ic_material_logout),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalLinkIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
}
