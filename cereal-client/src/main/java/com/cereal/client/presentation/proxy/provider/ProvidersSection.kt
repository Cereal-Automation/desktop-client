package com.cereal.client.presentation.proxy.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.table.PaneTablePadding
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_provider
import com.cereal_automation.cereal_client.generated.resources.proxies_hero_subtitle
import com.cereal_automation.cereal_client.generated.resources.proxies_hero_title
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_balance_gb
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_connected_pill
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_disconnect
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_manage
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_stat_balance
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_stat_connected
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_stat_last_sync
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_stat_subusers
import com.cereal_automation.cereal_client.generated.resources.proxies_provider_sync
import com.cereal_automation.cereal_client.generated.resources.proxies_providers_none_connected
import com.cereal_automation.cereal_client.generated.resources.proxies_providers_one_connected
import com.cereal_automation.cereal_client.generated.resources.proxies_providers_section_label
import org.jetbrains.compose.resources.stringResource

private val CardCornerRadius = 10.dp
private val ConnectedBrandTileSize = 44.dp
private val HeroBrandTileSize = 56.dp

/**
 * The Proxies screen "Providers" section: a connected-provider card when connected, or a connect hero
 * when not. Rendered above the proxy-groups table.
 */
@Composable
fun ProvidersSection(
    connectorState: ConnectorState,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaneTablePadding, vertical = 16.dp)) {
        SectionHeader(
            label = stringResource(Res.string.proxies_providers_section_label),
            sub =
                if (connectorState is ConnectorState.Connected) {
                    stringResource(Res.string.proxies_providers_one_connected)
                } else {
                    stringResource(Res.string.proxies_providers_none_connected)
                },
        )

        when (connectorState) {
            is ConnectorState.Connected -> ConnectedProviderCard(connectorState.card, onManage, onDisconnect, onSync)

            // Treat the brief Loading window like "not connected" rather than flashing a spinner; the
            // connector flow resolves almost immediately from the local store.
            ConnectorState.Loading, ConnectorState.NotConnected -> ConnectHero(onConnect)
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    sub: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CerealText(text = label.uppercase(), style = CerealTypography.sectionLabel, color = CerealTheme.colorScheme.contentSubtle)
        CerealText(text = sub, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
    }
}

@Composable
private fun ConnectedProviderCard(
    card: ConnectedProviderUiModel,
    onManage: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(CardCornerRadius))
                .background(CerealTheme.colorScheme.cardDark, RoundedCornerShape(CardCornerRadius))
                .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandTile(card.brandMark, card.brandColorArgb, ConnectedBrandTileSize)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CerealText(
                        text = card.providerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    ConnectedPill()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    StatCell(Icons.Outlined.AccountBalanceWallet, stringResource(Res.string.proxies_provider_stat_balance), stringResource(Res.string.proxies_provider_balance_gb, card.availableTrafficGb))
                    StatCell(Icons.Outlined.Group, stringResource(Res.string.proxies_provider_stat_subusers), card.subUserCount.toString())
                    StatCell(Icons.Outlined.Sync, stringResource(Res.string.proxies_provider_stat_last_sync), card.lastSync)
                    StatCell(Icons.Outlined.Schedule, stringResource(Res.string.proxies_provider_stat_connected), card.connectedAt)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            CerealButton(
                onClick = onDisconnect,
                text = stringResource(Res.string.proxies_provider_disconnect),
                type = CerealButtonType.Surface,
            )
            CerealButton(
                onClick = onManage,
                type = CerealButtonType.Surface,
                content = {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    CerealText(
                        text = stringResource(Res.string.proxies_provider_manage),
                        modifier = Modifier.padding(start = 8.dp),
                        style = CerealTypography.controlLabel,
                    )
                },
            )
            CerealButton(
                onClick = onSync,
                type = CerealButtonType.Primary,
                content = {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    CerealText(
                        text = stringResource(Res.string.proxies_provider_sync),
                        modifier = Modifier.padding(start = 8.dp),
                        style = CerealTypography.controlLabel,
                    )
                },
            )
        }
    }
}

@Composable
private fun ConnectHero(onConnect: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(CardCornerRadius))
                .background(CerealTheme.colorScheme.cardDark, RoundedCornerShape(CardCornerRadius))
                .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(HeroBrandTileSize)
                        .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                CerealText(
                    text = stringResource(Res.string.proxies_hero_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CerealText(
                    text = stringResource(Res.string.proxies_hero_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            CerealButton(
                onClick = onConnect,
                type = CerealButtonType.Primary,
                content = {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    CerealText(
                        text = stringResource(Res.string.proxies_connect_provider),
                        modifier = Modifier.padding(start = 8.dp),
                        style = CerealTypography.controlLabel,
                    )
                },
            )
        }
    }
}

@Composable
private fun StatCell(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = CerealTheme.colorScheme.contentSubtle, modifier = Modifier.size(12.dp))
            CerealText(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = CerealTheme.colorScheme.contentSubtle,
            )
        }
        CerealText(
            text = value,
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ConnectedPill() {
    Row(
        modifier =
            Modifier
                .border(1.dp, CerealTheme.colorScheme.success.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .background(CerealTheme.colorScheme.success.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).background(CerealTheme.colorScheme.success, CircleShape))
        CerealText(
            text = stringResource(Res.string.proxies_provider_connected_pill),
            style = CerealTypography.statusChipLabel,
            color = CerealTheme.colorScheme.success,
        )
    }
}

@Composable
internal fun BrandTile(
    mark: String,
    colorArgb: Long,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier.size(size).background(Color(colorArgb), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = mark,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default),
            color = Color.White,
        )
    }
}
