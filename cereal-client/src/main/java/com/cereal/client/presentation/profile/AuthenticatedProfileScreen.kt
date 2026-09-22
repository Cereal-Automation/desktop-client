package com.cereal.client.presentation.profile

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealOutlinedButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SettingsActionRow
import com.cereal.client.presentation.view.SettingsSectionCard
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ic_material_logout
import com.cereal_automation.cereal_client.generated.resources.logout
import com.cereal_automation.cereal_client.generated.resources.profile
import com.cereal_automation.cereal_client.generated.resources.subscriptions
import com.cereal_automation.cereal_client.generated.resources.subscriptions_desc
import com.cereal_automation.cereal_client.generated.resources.view_profile
import com.cereal_automation.cereal_client.generated.resources.view_profile_desc
import com.cereal_automation.cereal_client.generated.resources.visit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

@Composable
fun AuthenticatedProfileScreen(
    applicationConfig: ApplicationConfig,
    vm: ProfileViewModel,
) {
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
                        .widthIn(max = 800.dp)
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
                        val initials =
                            vm.user.value
                                ?.name
                                ?.split(' ')
                                ?.mapNotNull { it.firstOrNull()?.toString() }
                                ?.joinToString("")
                                .orEmpty()

                        val color = MaterialTheme.colorScheme.primary

                        CerealText(
                            modifier =
                                Modifier
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val currentMax = maxOf(placeable.width, placeable.height)
                                        layout(currentMax, currentMax) {
                                            placeable.placeRelative(
                                                (currentMax - placeable.width) / 2,
                                                (currentMax - placeable.height) / 2,
                                            )
                                        }
                                    }.drawBehind {
                                        drawCircle(
                                            color = color,
                                            radius = this.size.maxDimension / 2f,
                                        )
                                    }.padding(20.dp),
                            text =
                                if (initials.length >= 2) {
                                    ("${initials.first()}${initials.last()}").uppercase()
                                } else {
                                    initials.uppercase()
                                },
                            style = TextStyle(color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        CerealText(
                            text =
                                vm.user.value
                                    ?.name
                                    .orEmpty(),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )

                        CerealText(
                            text =
                                vm.user.value
                                    ?.email
                                    .orEmpty(),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 2.sp,
                                    color = CerealTheme.colorScheme.contentTertiary,
                                    fontWeight = FontWeight.Bold,
                                ),
                            modifier = Modifier.padding(top = 4.dp),
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
                                onClick = { Desktop.getDesktop().browse(URI(applicationConfig.marketplaceViewProfileUrl)) },
                            )
                        }

                        SettingsActionRow(
                            title = stringResource(Res.string.subscriptions),
                            description = stringResource(Res.string.subscriptions_desc),
                            actionText = stringResource(Res.string.visit),
                            onClick = { Desktop.getDesktop().browse(URI(applicationConfig.marketplaceViewSubscriptionsUrl)) },
                        )
                    }
                }

                // Logout Button
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        CerealOutlinedButton(
                            onClick = { vm.logout() },
                            type = CerealButtonType.Primary,
                        ) {
                            CerealText(stringResource(Res.string.logout))
                            Spacer(modifier = Modifier.width(8.dp))
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
