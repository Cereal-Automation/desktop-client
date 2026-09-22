package com.cereal.client.presentation.proxy.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.domain.model.proxy.ProxyGeoCatalogue
import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealOutlinedTextField
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_back
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_cancel
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_city
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_city_placeholder
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_count_label
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_country
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_done_health_check
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_done_into
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_done_more
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_done_title
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_done_view
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_failed_retry
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_failed_title
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_geo_label
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_in_progress_body
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_in_progress_title
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_preview_label
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_preview_rotating
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_preview_sticky
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_preview_worldwide
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_run_in_background
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_session_label
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_session_rotating
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_session_rotating_sub
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_session_sticky
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_session_sticky_sub
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_start
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_start_one
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_state
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_state_any
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_step_configure
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_step_sync
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_subtitle_configure
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_subtitle_done
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_subtitle_syncing
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_target_existing
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_target_label
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_target_new
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_target_new_placeholder
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_target_select
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_title
import com.cereal_automation.cereal_client.generated.resources.proxies_sync_zero_traffic
import org.jetbrains.compose.resources.stringResource

private val DialogWidth = 600.dp

// The real MarsProxies residential gateway (the mock uses a placeholder host).
private const val GATEWAY_PREVIEW = "ultra.marsproxies.com:44443"
private const val COUNT_STEP = 50

/**
 * The "Sync new proxies" wizard modal: Configure → Syncing → Done, mirroring the design handoff.
 * Sticky is the primary session; the count control is shown for sticky only and a rotating pull
 * collapses to a single endpoint. A zero-traffic account shows a non-blocking warning; a sync failure
 * surfaces an inline message and writes nothing.
 */
@Composable
fun SyncProxiesDialog(
    state: SyncWizardState.Open,
    providerName: String,
    onClose: () -> Unit,
    onCountryChanged: (String) -> Unit,
    onStateChanged: (String?) -> Unit,
    onCityChanged: (String) -> Unit,
    onSessionChanged: (ProxySession) -> Unit,
    onCountChanged: (Int) -> Unit,
    onTargetModeChanged: (SyncTargetMode) -> Unit,
    onNewGroupNameChanged: (String) -> Unit,
    onExistingGroupSelected: (String) -> Unit,
    onStartSync: () -> Unit,
    onRunInBackground: () -> Unit,
    onSyncMore: () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
        onDismissRequest = onClose,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(DialogWidth)
                        .border(1.dp, CerealTheme.colorScheme.borderDark, RoundedCornerShape(14.dp))
                        .background(CerealTheme.colorScheme.cardDark, RoundedCornerShape(14.dp)),
            ) {
                SyncHeader(providerName, state.step, onClose)
                if (state.step != SyncWizardStep.DONE) {
                    SyncStepper(state.step)
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    when (state.step) {
                        SyncWizardStep.CONFIGURE -> {
                            ConfigureStep(
                                state = state,
                                providerName = providerName,
                                onCountryChanged = onCountryChanged,
                                onStateChanged = onStateChanged,
                                onCityChanged = onCityChanged,
                                onSessionChanged = onSessionChanged,
                                onCountChanged = onCountChanged,
                                onTargetModeChanged = onTargetModeChanged,
                                onNewGroupNameChanged = onNewGroupNameChanged,
                                onExistingGroupSelected = onExistingGroupSelected,
                            )
                        }

                        SyncWizardStep.SYNCING -> {
                            SyncingStep(state, providerName)
                        }

                        SyncWizardStep.DONE -> {
                            DoneStep(state)
                        }
                    }
                }
                SyncFooter(state, onClose, onStartSync, onRunInBackground, onSyncMore)
            }
        }
    }
}

@Composable
private fun SyncHeader(
    providerName: String,
    step: SyncWizardStep,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = stringResource(Res.string.proxies_sync_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            CerealText(
                text =
                    when (step) {
                        SyncWizardStep.CONFIGURE -> stringResource(Res.string.proxies_sync_subtitle_configure)
                        SyncWizardStep.SYNCING -> stringResource(Res.string.proxies_sync_subtitle_syncing)
                        SyncWizardStep.DONE -> stringResource(Res.string.proxies_sync_subtitle_done)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        CerealIconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.proxies_sync_cancel), tint = CerealTheme.colorScheme.contentTertiary)
        }
    }
}

@Composable
private fun SyncStepper(step: SyncWizardStep) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.backgroundDark)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SyncStepChip(1, stringResource(Res.string.proxies_sync_step_configure), active = step == SyncWizardStep.CONFIGURE, done = step != SyncWizardStep.CONFIGURE)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .height(1.dp)
                    .background(CerealTheme.colorScheme.border),
        )
        SyncStepChip(2, stringResource(Res.string.proxies_sync_step_sync), active = step != SyncWizardStep.CONFIGURE, done = false)
    }
}

@Composable
private fun SyncStepChip(
    index: Int,
    label: String,
    active: Boolean,
    done: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(if (active || done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            } else {
                CerealText(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active) Color.White else CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
        CerealText(
            text = label,
            style = CerealTypography.controlLabel,
            color = if (active || done) MaterialTheme.colorScheme.onBackground else CerealTheme.colorScheme.contentTertiary,
        )
    }
}

@Composable
private fun ConfigureStep(
    state: SyncWizardState.Open,
    providerName: String,
    onCountryChanged: (String) -> Unit,
    onStateChanged: (String?) -> Unit,
    onCityChanged: (String) -> Unit,
    onSessionChanged: (ProxySession) -> Unit,
    onCountChanged: (Int) -> Unit,
    onTargetModeChanged: (SyncTargetMode) -> Unit,
    onNewGroupNameChanged: (String) -> Unit,
    onExistingGroupSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.failed) {
            InlineBanner(
                icon = Icons.Outlined.ErrorOutline,
                text = stringResource(Res.string.proxies_sync_failed_title),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.zeroTraffic) {
            InlineBanner(
                icon = Icons.Outlined.ErrorOutline,
                text = stringResource(Res.string.proxies_sync_zero_traffic),
                color = CerealTheme.colorScheme.contentSecondary,
            )
        }

        // Geo targeting.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(Icons.Outlined.Place, stringResource(Res.string.proxies_sync_geo_label))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Dropdown(
                    modifier = Modifier.weight(1f),
                    value = ProxyGeoCatalogue.countryName(state.country),
                    placeholder = stringResource(Res.string.proxies_sync_country),
                    options = ProxyGeoCatalogue.countries.map { it.code to it.displayName },
                    onPick = onCountryChanged,
                )
                if (ProxyGeoCatalogue.supportsStates(state.country)) {
                    val anyState = stringResource(Res.string.proxies_sync_state_any)
                    Dropdown(
                        modifier = Modifier.weight(1f),
                        value = state.state ?: anyState,
                        placeholder = stringResource(Res.string.proxies_sync_state),
                        options = listOf("" to anyState) + ProxyGeoCatalogue.statesFor(state.country).map { it to it },
                        onPick = { onStateChanged(it.ifBlank { null }) },
                    )
                }
            }
            CerealOutlinedTextField(
                value = state.city ?: "",
                onValueChange = onCityChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { CerealText(stringResource(Res.string.proxies_sync_city_placeholder), color = CerealTheme.colorScheme.contentTertiary) },
            )
        }

        // Session segmented control (sticky is the primary/default).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(Icons.Outlined.Sync, stringResource(Res.string.proxies_sync_session_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SessionOption(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.proxies_sync_session_sticky),
                    subtitle = stringResource(Res.string.proxies_sync_session_sticky_sub),
                    selected = state.session == ProxySession.STICKY,
                    onClick = { onSessionChanged(ProxySession.STICKY) },
                )
                SessionOption(
                    modifier = Modifier.weight(1f),
                    title = stringResource(Res.string.proxies_sync_session_rotating),
                    subtitle = stringResource(Res.string.proxies_sync_session_rotating_sub),
                    selected = state.session == ProxySession.ROTATING,
                    onClick = { onSessionChanged(ProxySession.ROTATING) },
                )
            }
        }

        // Count: sticky only.
        if (state.session == ProxySession.STICKY) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(Icons.Outlined.Layers, stringResource(Res.string.proxies_sync_count_label))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepButton(Icons.Outlined.Remove) { onCountChanged(state.count - COUNT_STEP) }
                    Slider(
                        value = state.count.toFloat(),
                        onValueChange = { onCountChanged(it.toInt()) },
                        valueRange = ProxySyncConfig.MIN_COUNT.toFloat()..ProxySyncConfig.MAX_COUNT.toFloat(),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    StepButton(Icons.Outlined.Add) { onCountChanged(state.count + COUNT_STEP) }
                    Box(
                        modifier =
                            Modifier
                                .width(72.dp)
                                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CerealText(
                            text = "%,d".format(state.count),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }

        // Target group.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(Icons.Outlined.Storage, stringResource(Res.string.proxies_sync_target_label))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RadioOption(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.proxies_sync_target_new),
                    selected = state.targetMode == SyncTargetMode.NEW,
                    onClick = { onTargetModeChanged(SyncTargetMode.NEW) },
                )
                RadioOption(
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.proxies_sync_target_existing),
                    selected = state.targetMode == SyncTargetMode.EXISTING,
                    onClick = { onTargetModeChanged(SyncTargetMode.EXISTING) },
                )
            }
            when (state.targetMode) {
                SyncTargetMode.NEW -> {
                    CerealOutlinedTextField(
                        value = state.newGroupName,
                        onValueChange = onNewGroupNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { CerealText(stringResource(Res.string.proxies_sync_target_new_placeholder), color = CerealTheme.colorScheme.contentTertiary) },
                    )
                }

                SyncTargetMode.EXISTING -> {
                    val selectedName = state.existingGroups.firstOrNull { it.id == state.selectedGroupId }?.name
                    Dropdown(
                        modifier = Modifier.fillMaxWidth(),
                        value = selectedName ?: stringResource(Res.string.proxies_sync_target_select),
                        placeholder = stringResource(Res.string.proxies_sync_target_select),
                        options = state.existingGroups.map { it.id to it.name },
                        onPick = onExistingGroupSelected,
                    )
                }
            }
        }

        SyncPreview(state, providerName)
    }
}

@Composable
private fun SyncPreview(
    state: SyncWizardState.Open,
    providerName: String,
) {
    val geo = geoLabel(state)
    val preview =
        when (state.session) {
            ProxySession.STICKY -> stringResource(Res.string.proxies_sync_preview_sticky, "%,d".format(state.count), providerName, geo)
            ProxySession.ROTATING -> stringResource(Res.string.proxies_sync_preview_rotating, providerName, geo)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                .background(CerealTheme.colorScheme.backgroundDark, RoundedCornerShape(8.dp))
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CerealText(
            text = stringResource(Res.string.proxies_sync_preview_label).uppercase(),
            style = CerealTypography.sectionLabel,
            color = CerealTheme.colorScheme.contentSubtle,
        )
        CerealText(text = preview, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentSecondary)
        CerealText(
            text = GATEWAY_PREVIEW,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = CerealTheme.colorScheme.link,
        )
    }
}

@Composable
private fun geoLabel(state: SyncWizardState.Open): String {
    val parts = listOfNotNull(ProxyGeoCatalogue.countryName(state.country), state.state, state.city)
    return if (parts.isEmpty()) stringResource(Res.string.proxies_sync_preview_worldwide) else parts.joinToString(" · ")
}

@Composable
private fun SyncingStep(
    state: SyncWizardState.Open,
    providerName: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary)
        CerealText(
            text = stringResource(Res.string.proxies_sync_in_progress_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        CerealText(
            text = stringResource(Res.string.proxies_sync_in_progress_body, providerName),
            style = MaterialTheme.typography.bodySmall,
            color = CerealTheme.colorScheme.contentTertiary,
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun DoneStep(state: SyncWizardState.Open) {
    val done = state.done ?: return
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(CerealTheme.colorScheme.success.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = CerealTheme.colorScheme.success, modifier = Modifier.size(34.dp))
        }
        CerealText(
            text = stringResource(Res.string.proxies_sync_done_title, "%,d".format(done.syncedCount)),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                    .background(CerealTheme.colorScheme.backgroundDark, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                CerealText(
                    text = done.groupName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CerealText(
                    text = stringResource(Res.string.proxies_sync_done_into, done.groupName),
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
        if (done.healthCheckRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.NetworkCheck,
                    contentDescription = null,
                    tint = CerealTheme.colorScheme.contentTertiary,
                    modifier = Modifier.size(15.dp),
                )
                CerealText(
                    text = stringResource(Res.string.proxies_sync_done_health_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
    }
}

@Composable
private fun SyncFooter(
    state: SyncWizardState.Open,
    onClose: () -> Unit,
    onStartSync: () -> Unit,
    onRunInBackground: () -> Unit,
    onSyncMore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.backgroundDark)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.step) {
            SyncWizardStep.CONFIGURE -> {
                CerealTextButton(onClick = onClose, type = CerealButtonType.Surface) {
                    CerealText(stringResource(Res.string.proxies_sync_back), style = CerealTypography.controlLabel)
                }
                Spacer(modifier = Modifier.weight(1f))
                CerealButton(
                    onClick = onStartSync,
                    enabled = state.canSync,
                    type = CerealButtonType.Primary,
                    text =
                        if (state.failed) {
                            stringResource(Res.string.proxies_sync_failed_retry)
                        } else if (state.session == ProxySession.ROTATING) {
                            stringResource(Res.string.proxies_sync_start_one)
                        } else {
                            stringResource(Res.string.proxies_sync_start, "%,d".format(state.count))
                        },
                )
            }

            SyncWizardStep.SYNCING -> {
                Spacer(modifier = Modifier.weight(1f))
                CerealTextButton(onClick = onRunInBackground, type = CerealButtonType.Surface) {
                    CerealText(stringResource(Res.string.proxies_sync_run_in_background), style = CerealTypography.controlLabel)
                }
            }

            SyncWizardStep.DONE -> {
                CerealTextButton(onClick = onSyncMore, type = CerealButtonType.Surface) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    CerealText(stringResource(Res.string.proxies_sync_done_more), modifier = Modifier.padding(start = 6.dp), style = CerealTypography.controlLabel)
                }
                Spacer(modifier = Modifier.weight(1f))
                CerealButton(onClick = onClose, text = stringResource(Res.string.proxies_sync_done_view), type = CerealButtonType.Primary)
            }
        }
    }
}

// ── small building blocks ──────────────────────────────────────────────────────────────────

@Composable
private fun FieldLabel(
    icon: ImageVector,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary, modifier = Modifier.size(14.dp))
        CerealText(text = label, style = CerealTypography.controlLabel, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun InlineBanner(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        CerealText(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun SessionOption(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.border,
                    RoundedCornerShape(8.dp),
                ).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CerealText(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
        CerealText(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
    }
}

@Composable
private fun RadioOption(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.border,
                    RoundedCornerShape(8.dp),
                ).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.borderDark, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        CerealText(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onBackground else CerealTheme.colorScheme.contentSecondary,
        )
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
    }
}

/** A minimal click-to-open dropdown matching the design's PxSelect. */
@Composable
private fun Dropdown(
    modifier: Modifier = Modifier,
    value: String,
    placeholder: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CerealText(
                text = value.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) CerealTheme.colorScheme.contentTertiary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary, modifier = Modifier.size(16.dp))
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                properties = PopupProperties(focusable = true),
                onDismissRequest = { expanded = false },
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(DialogWidth - 80.dp)
                            .border(1.dp, CerealTheme.colorScheme.borderDark, RoundedCornerShape(8.dp))
                            .background(CerealTheme.colorScheme.cardDark, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(5.dp),
                ) {
                    options.forEach { (key, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(key)
                                        expanded = false
                                    }.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CerealText(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            if (label == value) {
                                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
