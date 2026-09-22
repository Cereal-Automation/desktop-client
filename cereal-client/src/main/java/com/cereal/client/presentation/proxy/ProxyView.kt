package com.cereal.client.presentation.proxy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.featureflag.FeatureFlag
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyHealthStatus
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.repository.FeatureFlagRepository
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.group.GroupNameDialog
import com.cereal.client.presentation.proxy.provider.ConnectProviderDialog
import com.cereal.client.presentation.proxy.provider.ConnectWizardState
import com.cereal.client.presentation.proxy.provider.ConnectorState
import com.cereal.client.presentation.proxy.provider.ProvidersSection
import com.cereal.client.presentation.proxy.provider.ProxyProviderViewModel
import com.cereal.client.presentation.proxy.provider.SyncProxiesDialog
import com.cereal.client.presentation.proxy.provider.SyncProxiesViewModel
import com.cereal.client.presentation.proxy.provider.SyncWizardState
import com.cereal.client.presentation.proxy.provider.display
import com.cereal.client.presentation.tasks.dialog.ImportFromFileDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.EmptyListView
import com.cereal.client.presentation.view.LoadingView
import com.cereal.client.presentation.view.group.DetailListViewItem
import com.cereal.client.presentation.view.group.DetailViewState
import com.cereal.client.presentation.view.table.EncryptedBanner
import com.cereal.client.presentation.view.table.PaneDetailsHeader
import com.cereal.client.presentation.view.table.PaneIconActionButton
import com.cereal.client.presentation.view.table.PaneMetaCell
import com.cereal.client.presentation.view.table.PaneMetaDivider
import com.cereal.client.presentation.view.table.PaneMetaStrip
import com.cereal.client.presentation.view.table.PanePrimaryActionButton
import com.cereal.client.presentation.view.table.PaneScreen
import com.cereal.client.presentation.view.table.PaneTableActionAreaWidth
import com.cereal.client.presentation.view.table.PaneTableActions
import com.cereal.client.presentation.view.table.PaneTableCard
import com.cereal.client.presentation.view.table.PaneTableColumn
import com.cereal.client.presentation.view.table.PaneTableHeader
import com.cereal.client.presentation.view.table.PaneTableIconButtonGlyphSize
import com.cereal.client.presentation.view.table.PaneTableRow
import com.cereal.client.presentation.view.table.PaneTableRowDivider
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.import_proxies_hint
import com.cereal_automation.cereal_client.generated.resources.proxies
import com.cereal_automation.cereal_client.generated.resources.proxies_action_add
import com.cereal_automation.cereal_client.generated.resources.proxies_action_back
import com.cereal_automation.cereal_client.generated.resources.proxies_action_delete
import com.cereal_automation.cereal_client.generated.resources.proxies_action_delete_failing
import com.cereal_automation.cereal_client.generated.resources.proxies_action_edit
import com.cereal_automation.cereal_client.generated.resources.proxies_action_import
import com.cereal_automation.cereal_client.generated.resources.proxies_action_resync
import com.cereal_automation.cereal_client.generated.resources.proxies_action_test_all
import com.cereal_automation.cereal_client.generated.resources.proxies_auth_none
import com.cereal_automation.cereal_client.generated.resources.proxies_column_auth
import com.cereal_automation.cereal_client.generated.resources.proxies_column_endpoint
import com.cereal_automation.cereal_client.generated.resources.proxies_column_endpoints
import com.cereal_automation.cereal_client.generated.resources.proxies_column_name
import com.cereal_automation.cereal_client.generated.resources.proxies_delete_failing_confirm
import com.cereal_automation.cereal_client.generated.resources.proxies_delete_failing_message
import com.cereal_automation.cereal_client.generated.resources.proxies_delete_failing_title
import com.cereal_automation.cereal_client.generated.resources.proxies_disconnect_confirm
import com.cereal_automation.cereal_client.generated.resources.proxies_disconnect_message
import com.cereal_automation.cereal_client.generated.resources.proxies_disconnect_title
import com.cereal_automation.cereal_client.generated.resources.proxies_empty_placeholder
import com.cereal_automation.cereal_client.generated.resources.proxies_encrypted_subtitle
import com.cereal_automation.cereal_client.generated.resources.proxies_encrypted_title
import com.cereal_automation.cereal_client.generated.resources.proxies_meta_endpoints
import com.cereal_automation.cereal_client.generated.resources.proxies_meta_failed
import com.cereal_automation.cereal_client.generated.resources.proxies_meta_healthy
import com.cereal_automation.cereal_client.generated.resources.proxies_meta_no_auth
import com.cereal_automation.cereal_client.generated.resources.proxies_meta_with_auth
import com.cereal_automation.cereal_client.generated.resources.proxies_records_crumb
import com.cereal_automation.cereal_client.generated.resources.proxies_source_file
import com.cereal_automation.cereal_client.generated.resources.status
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import java.util.UUID

@Composable
@ExperimentalFoundationApi
fun ProxiesScreen(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    viewModel: ProxyViewModel =
        remember {
            KoinJavaComponent.get(
                ProxyViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
    providerViewModel: ProxyProviderViewModel =
        remember {
            KoinJavaComponent.get(
                ProxyProviderViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
    syncViewModel: SyncProxiesViewModel =
        remember {
            KoinJavaComponent.get(
                SyncProxiesViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
    // The whole MarsProxies proxy-provider feature is gated behind a compile-time flag (on in dev,
    // off in shipped builds). When off, the screen behaves exactly as it did before the connector:
    // manual proxy groups + file import only. Overridable in tests.
    proxyProviderEnabled: Boolean =
        koinInject<FeatureFlagRepository>().isEnabled(FeatureFlag.PROXY_PROVIDER_CONNECTOR),
) {
    val dialogState by remember { viewModel.dialogState }
    val groups by remember { viewModel.groups }
    val selectedGroup by remember { viewModel.selectedGroup }
    val detailsViewState by remember { viewModel.detailsViewState }
    val inFlightProxyIds by remember { viewModel.inFlightProxyIds }
    val connectorState by remember { providerViewModel.connectorState }
    val wizardState by remember { providerViewModel.wizardState }
    val syncWizardState by remember { syncViewModel.state }
    val showDisconnectConfirm by remember { providerViewModel.showDisconnectConfirm }

    val currentGroup = selectedGroup
    if (currentGroup != null) {
        val failedCount =
            (detailsViewState as? DetailViewState.Filled)
                ?.items
                ?.mapNotNull { it.id as? Proxy }
                ?.count { it.health.status == ProxyHealthStatus.FAILED }
                ?: 0
        PaneScreen {
            ProxyDetailsView(
                group = currentGroup,
                detailsViewState = detailsViewState,
                inFlightProxyIds = inFlightProxyIds,
                failedCount = failedCount,
                onBack = viewModel::onCloseDetails,
                onEdit = { viewModel.onEditProxyGroup(currentGroup) },
                onImport = { viewModel.onImportFromFile(currentGroup) },
                onDeleteItem = viewModel::onDeleteProxy,
                onTestAll = viewModel::onTestAllInGroup,
                onDeleteFailing = viewModel::onConfirmDeleteFailingProxies,
            )
        }
    } else {
        PaneScreen(
            title = stringResource(Res.string.proxies),
            toolbarActions = {
                PanePrimaryActionButton(
                    icon = Icons.Outlined.Add,
                    text = stringResource(Res.string.proxies_action_add),
                    onClick = viewModel::onCreateProxyGroup,
                )
            },
        ) {
            if (proxyProviderEnabled) {
                ProvidersSection(
                    connectorState = connectorState,
                    onConnect = providerViewModel::onConnectClicked,
                    onManage = providerViewModel::onManageClicked,
                    onDisconnect = providerViewModel::onDisconnectClicked,
                    onSync = { syncViewModel.onSyncClicked() },
                )
            }
            if (groups.isEmpty()) {
                EmptyListView(stringResource(Res.string.proxies_empty_placeholder))
            } else {
                EncryptedBanner(
                    title = stringResource(Res.string.proxies_encrypted_title),
                    subtitle = stringResource(Res.string.proxies_encrypted_subtitle),
                )
                ProxiesTable(
                    groups = groups,
                    showProviderAttribution = proxyProviderEnabled,
                    onOpenGroup = viewModel::onShowDetails,
                    onEditGroup = viewModel::onEditProxyGroup,
                    onImportToGroup = viewModel::onImportFromFile,
                    onResyncGroup = { group ->
                        syncViewModel.onResyncClicked(group.id, group.provider ?: ProxyVendor.MARSPROXIES)
                    },
                )
            }
        }
    }

    errorView(viewModel.errorAction)
    errorView(providerViewModel.errorAction)
    errorView(syncViewModel.errorAction)

    // Provider dialogs are only reachable from the (gated) Providers section, but guard them too so the
    // whole feature is contained behind the flag.
    if (proxyProviderEnabled) {
        (wizardState as? ConnectWizardState.Open)?.let { open ->
            ConnectProviderDialog(
                state = open,
                options = providerViewModel.providerOptions,
                onClose = providerViewModel::onCloseWizard,
                onProviderSelected = providerViewModel::onProviderSelected,
                onContinue = providerViewModel::onContinueToConnect,
                onBack = providerViewModel::onBackToProvider,
                onTokenChanged = providerViewModel::onTokenChanged,
                onToggleTokenVisibility = providerViewModel::onToggleTokenVisibility,
                onConnect = providerViewModel::onValidateAndConnect,
            )
        }

        (syncWizardState as? SyncWizardState.Open)?.let { open ->
            val providerName =
                (connectorState as? ConnectorState.Connected)?.card?.providerName
                    ?: ProxyVendor.MARSPROXIES.display.displayName
            SyncProxiesDialog(
                state = open,
                providerName = providerName,
                onClose = syncViewModel::onClose,
                onCountryChanged = syncViewModel::onCountryChanged,
                onStateChanged = syncViewModel::onStateChanged,
                onCityChanged = syncViewModel::onCityChanged,
                onSessionChanged = syncViewModel::onSessionChanged,
                onCountChanged = syncViewModel::onCountChanged,
                onTargetModeChanged = syncViewModel::onTargetModeChanged,
                onNewGroupNameChanged = syncViewModel::onNewGroupNameChanged,
                onExistingGroupSelected = syncViewModel::onExistingGroupSelected,
                onStartSync = { syncViewModel.onStartSync() },
                onRunInBackground = syncViewModel::onRunInBackground,
                onSyncMore = syncViewModel::onSyncMore,
            )
        }

        if (showDisconnectConfirm) {
            val providerName =
                (connectorState as? ConnectorState.Connected)?.card?.providerName
                    ?: ProxyVendor.MARSPROXIES.display.displayName
            ConfirmationDialog(
                title = stringResource(Res.string.proxies_disconnect_title),
                message = stringResource(Res.string.proxies_disconnect_message, providerName),
                confirmButtonText = stringResource(Res.string.proxies_disconnect_confirm),
                onConfirm = providerViewModel::onConfirmDisconnect,
                onDismiss = providerViewModel::onDismissDisconnect,
            )
        }
    }

    when (val state = dialogState) {
        is ProxyViewState.DialogState.Hidden -> {}

        is ProxyViewState.DialogState.AddingProxyGroup -> {
            GroupNameDialog(
                onDismissRequest = { viewModel.closeDialog() },
                onConfirmClicked = { value -> viewModel.createGroup(value) },
            )
        }

        is ProxyViewState.DialogState.EditingProxyGroup -> {
            GroupNameDialog(
                initialValue = state.initialValue,
                onDismissRequest = { viewModel.closeDialog() },
                onDeleteClicked = { viewModel.deleteGroup() },
                onConfirmClicked = { value -> viewModel.editGroup(value) },
            )
        }

        is ProxyViewState.DialogState.ImportFromFile -> {
            ImportFromFileDialog(
                datasetType = DatasetType.Proxy,
                closeDialog = { viewModel.onCloseImportFromFileDialog() },
                importFile = { file -> viewModel.onDatasetFileSelected(file) },
            )
        }

        is ProxyViewState.DialogState.ConfirmDeleteFailing -> {
            ConfirmationDialog(
                title = stringResource(Res.string.proxies_delete_failing_title),
                message = stringResource(Res.string.proxies_delete_failing_message, state.failedCount),
                confirmButtonText = stringResource(Res.string.proxies_delete_failing_confirm),
                onConfirm = { viewModel.onDeleteFailingProxiesConfirmed() },
                onDismiss = { viewModel.closeDialog() },
            )
        }
    }
}

// Wider than the default 100.dp slot so a provider-synced row's three actions (re-sync, edit, import)
// fit without crowding; the header reserves the same width so columns stay aligned.
private val ProxyActionAreaWidth = 140.dp

@Composable
private fun ProxiesTable(
    groups: List<ProxyGroup>,
    showProviderAttribution: Boolean,
    onOpenGroup: (ProxyGroup) -> Unit,
    onEditGroup: (ProxyGroup) -> Unit,
    onImportToGroup: (ProxyGroup) -> Unit,
    onResyncGroup: (ProxyGroup) -> Unit,
) {
    PaneTableCard {
        PaneTableHeader(
            columns =
                listOf(
                    PaneTableColumn(stringResource(Res.string.proxies_column_name), 2f),
                    PaneTableColumn(stringResource(Res.string.proxies_column_endpoints), 1f),
                ),
            // Reserve room for the extra re-sync action only when provider attribution is on.
            actionAreaWidth = if (showProviderAttribution) ProxyActionAreaWidth else PaneTableActionAreaWidth,
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(groups, key = { it.id }) { group ->
                ProxyRow(
                    group = group,
                    showProviderAttribution = showProviderAttribution,
                    onClick = { onOpenGroup(group) },
                    onEdit = { onEditGroup(group) },
                    onImport = { onImportToGroup(group) },
                    onResync = { onResyncGroup(group) },
                )
                PaneTableRowDivider()
            }
        }
    }
}

@Composable
private fun ProxyRow(
    group: ProxyGroup,
    showProviderAttribution: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onResync: () -> Unit,
) {
    PaneTableRow(onClick = onClick) {
        ProxyGroupNameCell(group = group, weight = 2f, showAttribution = showProviderAttribution)
        CerealText(
            text = "%,d".format(group.numberOfItems),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = CerealTheme.colorScheme.contentSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        if (!showProviderAttribution) {
            // Feature off: original manual-only row — edit + import in the default action slot.
            PaneTableActions {
                PaneIconActionButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = stringResource(Res.string.proxies_action_edit),
                    onClick = onEdit,
                )
                PaneIconActionButton(
                    icon = Icons.Outlined.UploadFile,
                    contentDescription = stringResource(Res.string.proxies_action_import),
                    onClick = onImport,
                )
            }
            return@PaneTableRow
        }
        Row(
            modifier = Modifier.width(ProxyActionAreaWidth),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Re-sync is only meaningful for provider-synced groups; manual/file groups don't get it.
            if (group.provider != null) {
                PaneIconActionButton(
                    icon = Icons.Outlined.Sync,
                    contentDescription = stringResource(Res.string.proxies_action_resync),
                    onClick = onResync,
                )
            }
            PaneIconActionButton(
                icon = Icons.Outlined.Edit,
                contentDescription = stringResource(Res.string.proxies_action_edit),
                onClick = onEdit,
            )
            PaneIconActionButton(
                icon = Icons.Outlined.UploadFile,
                contentDescription = stringResource(Res.string.proxies_action_import),
                onClick = onImport,
            )
        }
    }
}

/**
 * Name cell for a proxy group: icon + name. When [showAttribution] is on (the proxy-provider feature),
 * a meta row carries the source tag and geo label; when off it's just the icon + name as before.
 */
@Composable
private fun RowScope.ProxyGroupNameCell(
    group: ProxyGroup,
    weight: Float,
    showAttribution: Boolean,
) {
    Row(
        modifier = Modifier.weight(weight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = CerealTheme.colorScheme.contentSubtle,
            modifier = Modifier.size(PaneTableIconButtonGlyphSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAttribution) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceTag(provider = group.provider)
                    group.geoLabel?.takeIf { it.isNotBlank() }?.let { geo ->
                        CerealText(
                            text = geo,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = CerealTheme.colorScheme.contentSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small chip distinguishing a provider-synced group ([provider] non-null, e.g. "MarsProxies") from a
 * file-imported/manual group ([provider] null → "File import").
 */
@Composable
private fun SourceTag(provider: ProxyVendor?) {
    val isProvider = provider != null
    val contentColor = if (isProvider) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.contentTertiary
    val background =
        if (isProvider) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.05f)
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (provider != null) {
            CerealText(
                text = provider.display.brandMark,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                color = contentColor,
            )
            CerealText(
                text = provider.display.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = contentColor,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp),
            )
            CerealText(
                text = stringResource(Res.string.proxies_source_file),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProxyDetailsView(
    group: ProxyGroup,
    detailsViewState: DetailViewState,
    inFlightProxyIds: Set<UUID>,
    failedCount: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onDeleteItem: (DetailListViewItem) -> Unit,
    onTestAll: () -> Unit,
    onDeleteFailing: () -> Unit,
) {
    PaneDetailsHeader(
        title = group.name,
        crumb = stringResource(Res.string.proxies_records_crumb, group.numberOfItems),
        backContentDescription = stringResource(Res.string.proxies_action_back),
        onBack = onBack,
        actions = {
            PaneIconActionButton(
                icon = Icons.Outlined.Edit,
                contentDescription = stringResource(Res.string.proxies_action_edit),
                onClick = onEdit,
            )
            PaneIconActionButton(
                icon = Icons.Outlined.NetworkCheck,
                contentDescription = stringResource(Res.string.proxies_action_test_all),
                onClick = onTestAll,
            )
            if (failedCount > 0) {
                PaneIconActionButton(
                    icon = Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(Res.string.proxies_action_delete_failing),
                    onClick = onDeleteFailing,
                )
            }
            PanePrimaryActionButton(
                icon = Icons.Outlined.UploadFile,
                text = stringResource(Res.string.proxies_action_import),
                onClick = onImport,
            )
        },
    )
    HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)

    when (detailsViewState) {
        is DetailViewState.Loading -> {
            LoadingView()
        }

        is DetailViewState.Empty -> {
            EmptyListView(stringResource(Res.string.import_proxies_hint))
        }

        is DetailViewState.Filled -> {
            val proxies = detailsViewState.items.mapNotNull { it.id as? Proxy }
            ProxyDetailsMetaStrip(proxies = proxies)
            EndpointsTable(
                items = detailsViewState.items,
                inFlightProxyIds = inFlightProxyIds,
                onDelete = onDeleteItem,
            )
        }

        is DetailViewState.NoSelection -> {}
    }
}

@Composable
private fun ProxyDetailsMetaStrip(proxies: List<Proxy>) {
    val withAuth = proxies.count { it.username != null }
    val noAuth = proxies.size - withAuth
    val healthy = proxies.count { it.health.status == ProxyHealthStatus.HEALTHY }
    val failed = proxies.count { it.health.status == ProxyHealthStatus.FAILED }
    PaneMetaStrip {
        PaneMetaCell(
            label = stringResource(Res.string.proxies_meta_endpoints),
            value = "%,d".format(proxies.size),
        )
        PaneMetaDivider()
        PaneMetaCell(
            label = stringResource(Res.string.proxies_meta_with_auth),
            value = "%,d".format(withAuth),
        )
        PaneMetaDivider()
        PaneMetaCell(
            label = stringResource(Res.string.proxies_meta_no_auth),
            value = "%,d".format(noAuth),
            valueColor = CerealTheme.colorScheme.contentTertiary,
        )
        PaneMetaDivider()
        PaneMetaCell(
            label = stringResource(Res.string.proxies_meta_healthy),
            value = "%,d".format(healthy),
            valueColor = CerealTheme.colorScheme.success,
        )
        PaneMetaDivider()
        PaneMetaCell(
            label = stringResource(Res.string.proxies_meta_failed),
            value = "%,d".format(failed),
            valueColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EndpointsTable(
    items: List<DetailListViewItem>,
    inFlightProxyIds: Set<UUID>,
    onDelete: (DetailListViewItem) -> Unit,
) {
    val listState = rememberLazyListState()
    PaneTableCard {
        PaneTableHeader(
            columns =
                listOf(
                    PaneTableColumn(stringResource(Res.string.proxies_column_endpoint), 2f),
                    PaneTableColumn(stringResource(Res.string.proxies_column_auth), 1f),
                    PaneTableColumn(stringResource(Res.string.status), 1f),
                ),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
            ) {
                items(items, key = { (it.id as? Proxy)?.id ?: it }) { item ->
                    val proxy = item.id as? Proxy
                    if (proxy != null) {
                        EndpointRow(
                            proxy = proxy,
                            inFlight = proxy.id in inFlightProxyIds,
                            onDelete = { onDelete(item) },
                        )
                        PaneTableRowDivider()
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
            )
        }
    }
}

@Composable
private fun EndpointRow(
    proxy: Proxy,
    inFlight: Boolean,
    onDelete: () -> Unit,
) {
    PaneTableRow {
        EndpointCell(
            address = proxy.address,
            port = proxy.port,
            modifier = Modifier.weight(2f),
        )
        AuthCell(
            username = proxy.username,
            password = proxy.password,
            modifier = Modifier.weight(1f),
        )
        ProxyHealthChip(
            health = proxy.health,
            inFlight = inFlight,
            modifier = Modifier.weight(1f),
        )
        PaneTableActions {
            PaneIconActionButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(Res.string.proxies_action_delete),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun EndpointCell(
    address: String,
    port: Int,
    modifier: Modifier = Modifier,
) {
    val text: AnnotatedString =
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(address)
            }
            withStyle(SpanStyle(color = CerealTheme.colorScheme.contentSubtle)) {
                append(":$port")
            }
        }
    CerealText(
        text = text,
        style =
            MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}

private const val PASSWORD_PREVIEW_CHARS = 2
private const val PASSWORD_MASK = "••••"

@Composable
private fun AuthCell(
    username: String?,
    password: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (username == null) {
            CerealText(
                text = stringResource(Res.string.proxies_auth_none),
                style = MaterialTheme.typography.bodyMedium,
                color = CerealTheme.colorScheme.contentSubtle,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(PaneTableIconButtonGlyphSize),
            )
            CerealText(
                text = buildAuthText(username = username, password = password),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                color = CerealTheme.colorScheme.contentSecondary,
            )
        }
    }
}

@Composable
private fun buildAuthText(
    username: String,
    password: String?,
): AnnotatedString =
    buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append(username)
        }
        if (password != null) {
            withStyle(SpanStyle(color = CerealTheme.colorScheme.contentSubtle)) {
                append(":${maskPassword(password)}")
            }
        }
    }

private fun maskPassword(password: String): String =
    if (password.length <= PASSWORD_PREVIEW_CHARS) {
        PASSWORD_MASK
    } else {
        password.take(PASSWORD_PREVIEW_CHARS) + PASSWORD_MASK
    }
