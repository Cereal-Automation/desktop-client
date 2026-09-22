package com.cereal.client.presentation.customdataset

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.group.GroupNameDialog
import com.cereal.client.presentation.tasks.dialog.ImportFromFileDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
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
import com.cereal.client.presentation.view.table.PaneTableActions
import com.cereal.client.presentation.view.table.PaneTableCard
import com.cereal.client.presentation.view.table.PaneTableColumn
import com.cereal.client.presentation.view.table.PaneTableHeader
import com.cereal.client.presentation.view.table.PaneTableNameCell
import com.cereal.client.presentation.view.table.PaneTablePadding
import com.cereal.client.presentation.view.table.PaneTableRow
import com.cereal.client.presentation.view.table.PaneTableRowDivider
import com.cereal.client.presentation.view.table.SchemaColumn
import com.cereal.client.presentation.view.table.SchemaStrip
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.datasets_action_back
import com.cereal_automation.cereal_client.generated.resources.datasets_action_edit
import com.cereal_automation.cereal_client.generated.resources.datasets_action_import
import com.cereal_automation.cereal_client.generated.resources.datasets_column_created
import com.cereal_automation.cereal_client.generated.resources.datasets_column_name
import com.cereal_automation.cereal_client.generated.resources.datasets_column_rows
import com.cereal_automation.cereal_client.generated.resources.datasets_empty_placeholder
import com.cereal_automation.cereal_client.generated.resources.datasets_encrypted_subtitle
import com.cereal_automation.cereal_client.generated.resources.datasets_encrypted_title
import com.cereal_automation.cereal_client.generated.resources.datasets_meta_columns
import com.cereal_automation.cereal_client.generated.resources.datasets_meta_records
import com.cereal_automation.cereal_client.generated.resources.datasets_meta_updated
import com.cereal_automation.cereal_client.generated.resources.datasets_records_crumb
import com.cereal_automation.cereal_client.generated.resources.datasets_schema_label
import com.cereal_automation.cereal_client.generated.resources.datasets_script_origin_subtitle
import com.cereal_automation.cereal_client.generated.resources.delete
import com.cereal_automation.cereal_client.generated.resources.schema_type_bool
import com.cereal_automation.cereal_client.generated.resources.schema_type_enum
import com.cereal_automation.cereal_client.generated.resources.schema_type_float
import com.cereal_automation.cereal_client.generated.resources.schema_type_group
import com.cereal_automation.cereal_client.generated.resources.schema_type_int
import com.cereal_automation.cereal_client.generated.resources.schema_type_number
import com.cereal_automation.cereal_client.generated.resources.schema_type_proxy
import com.cereal_automation.cereal_client.generated.resources.schema_type_proxy_group
import com.cereal_automation.cereal_client.generated.resources.schema_type_record_list
import com.cereal_automation.cereal_client.generated.resources.schema_type_secret
import com.cereal_automation.cereal_client.generated.resources.schema_type_text
import com.cereal_automation.cereal_client.generated.resources.script_datasets
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.get
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

private val CreatedAtFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("MMM dd · HH:mm", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())

@Composable
@ExperimentalFoundationApi
fun CustomDatasetsScreen(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    viewModel: CustomDatasetViewModel =
        remember {
            get(
                CustomDatasetViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
) {
    val dialogState by remember { viewModel.dialogState }
    val groups by remember { viewModel.groups }
    val selectedGroup by remember { viewModel.selectedGroup }
    val detailsViewState by remember { viewModel.detailsViewState }

    val currentGroup = selectedGroup
    if (currentGroup != null) {
        PaneScreen {
            DatasetDetailsView(
                group = currentGroup,
                detailsViewState = detailsViewState,
                onBack = viewModel::onCloseDetails,
                onImport = { viewModel.onImportFromFile(currentGroup) },
                onDeleteItem = viewModel::onDeleteCustomDatasetItem,
            )
        }
    } else {
        PaneScreen(
            title = stringResource(Res.string.script_datasets),
        ) {
            if (groups.isEmpty()) {
                EmptyListView(stringResource(Res.string.datasets_empty_placeholder))
            } else {
                EncryptedBanner(
                    title = stringResource(Res.string.datasets_encrypted_title),
                    subtitle = stringResource(Res.string.datasets_encrypted_subtitle),
                )
                DatasetsTable(
                    groups = groups,
                    onOpenGroup = viewModel::onShowDetails,
                    onEditGroup = viewModel::onEditCustomDatasetGroup,
                    onImportToGroup = viewModel::onImportFromFile,
                )
                ScriptOriginHint()
            }
        }
    }

    errorView(viewModel.errorAction)

    when (val state = dialogState) {
        is CustomDatasetViewState.DialogState.Hidden -> {}

        is CustomDatasetViewState.DialogState.EditingCustomDatasetGroup -> {
            GroupNameDialog(
                initialValue = state.initialValue,
                onDismissRequest = { viewModel.closeDialog() },
                onDeleteClicked = { viewModel.deleteGroup() },
                onConfirmClicked = { value -> viewModel.updateGroup(value) },
            )
        }

        is CustomDatasetViewState.DialogState.ImportFromFile -> {
            ImportFromFileDialog(
                datasetType = DatasetType.Custom(state.items),
                closeDialog = { viewModel.onCloseImportFromFileDialog() },
                importFile = { file -> viewModel.onDatasetFileSelected(file) },
            )
        }
    }
}

@Composable
private fun ScriptOriginHint() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PaneTablePadding)
                .padding(top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = null,
            tint = CerealTheme.colorScheme.contentSubtle,
            modifier = Modifier.size(14.dp),
        )
        CerealText(
            text = stringResource(Res.string.datasets_script_origin_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = CerealTheme.colorScheme.contentSubtle,
        )
    }
}

@Composable
private fun DatasetsTable(
    groups: List<CustomDatasetGroup>,
    onOpenGroup: (CustomDatasetGroup) -> Unit,
    onEditGroup: (CustomDatasetGroup) -> Unit,
    onImportToGroup: (CustomDatasetGroup) -> Unit,
) {
    PaneTableCard {
        PaneTableHeader(
            columns =
                listOf(
                    PaneTableColumn(stringResource(Res.string.datasets_column_name), 2f),
                    PaneTableColumn(stringResource(Res.string.datasets_column_rows), 0.7f),
                    PaneTableColumn(stringResource(Res.string.datasets_column_created), 1f),
                ),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(groups, key = { it.id }) { group ->
                DatasetRow(
                    group = group,
                    onClick = { onOpenGroup(group) },
                    onEdit = { onEditGroup(group) },
                    onImport = { onImportToGroup(group) },
                )
                PaneTableRowDivider()
            }
        }
    }
}

@Composable
private fun DatasetRow(
    group: CustomDatasetGroup,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onImport: () -> Unit,
) {
    PaneTableRow(onClick = onClick) {
        PaneTableNameCell(
            icon = Icons.Outlined.GridView,
            text = group.name,
            weight = 2f,
        )
        CerealText(
            text = "%,d".format(group.numberOfItems),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = CerealTheme.colorScheme.contentSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(0.7f),
        )
        CerealText(
            text = formatCreatedAt(group),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = CerealTheme.colorScheme.contentTertiary,
            modifier = Modifier.weight(1f),
        )
        PaneTableActions {
            PaneIconActionButton(
                icon = Icons.Outlined.Edit,
                contentDescription = stringResource(Res.string.datasets_action_edit),
                onClick = onEdit,
            )
            PaneIconActionButton(
                icon = Icons.Outlined.UploadFile,
                contentDescription = stringResource(Res.string.datasets_action_import),
                onClick = onImport,
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun formatCreatedAt(group: CustomDatasetGroup): String = CreatedAtFormatter.format(group.createdAt.toJavaInstant())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatasetDetailsView(
    group: CustomDatasetGroup,
    detailsViewState: DetailViewState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onDeleteItem: (DetailListViewItem) -> Unit,
) {
    PaneDetailsHeader(
        title = group.name,
        crumb = stringResource(Res.string.datasets_records_crumb, group.numberOfItems),
        backContentDescription = stringResource(Res.string.datasets_action_back),
        onBack = onBack,
        actions = {
            PanePrimaryActionButton(
                icon = Icons.Outlined.UploadFile,
                text = stringResource(Res.string.datasets_action_import),
                onClick = onImport,
            )
        },
    )
    HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)

    DatasetGroupMetaStrip(group)
    DatasetSchemaStrip(group.itemDefinitions)

    when (detailsViewState) {
        is DetailViewState.Loading -> {
            LoadingView()
        }

        is DetailViewState.Empty -> {
            EmptyListView(stringResource(Res.string.datasets_empty_placeholder))
        }

        is DetailViewState.Filled -> {
            DatasetRecordsTable(
                itemDefinitions = group.itemDefinitions,
                items = detailsViewState.items,
                onDelete = onDeleteItem,
            )
        }

        is DetailViewState.NoSelection -> {}
    }
}

@Composable
private fun DatasetRecordsTable(
    itemDefinitions: List<ScriptConfigurationItemDefinition>,
    items: List<DetailListViewItem>,
    onDelete: (DetailListViewItem) -> Unit,
) {
    PaneTableCard {
        PaneTableHeader(
            columns = itemDefinitions.map { PaneTableColumn(it.name, 1f) },
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            // Key on the stable UUID, not hashCode(): hashCode is not collision-free and
            // collisions corrupt LazyColumn item identity across recompositions.
            items(items, key = { (it.id as? CustomDatasetItem)?.id ?: it }) { row ->
                DatasetRecordRow(
                    row = row,
                    itemDefinitions = itemDefinitions,
                    onDelete = { onDelete(row) },
                )
                PaneTableRowDivider()
            }
        }
    }
}

@Composable
private fun DatasetRecordRow(
    row: DetailListViewItem,
    itemDefinitions: List<ScriptConfigurationItemDefinition>,
    onDelete: () -> Unit,
) {
    PaneTableRow {
        itemDefinitions.forEachIndexed { index, definition ->
            val value = row.attributes.getOrNull(index)
            CerealText(
                text = value ?: "—",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color =
                    if (value == null) {
                        CerealTheme.colorScheme.contentSubtle
                    } else {
                        CerealTheme.colorScheme.contentSecondary
                    },
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        PaneTableActions {
            PaneIconActionButton(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(Res.string.delete),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun DatasetGroupMetaStrip(group: CustomDatasetGroup) {
    PaneMetaStrip {
        PaneMetaCell(
            label = stringResource(Res.string.datasets_meta_records),
            value = "%,d".format(group.numberOfItems),
        )
        PaneMetaDivider()
        PaneMetaCell(
            label = stringResource(Res.string.datasets_meta_columns),
            value = group.itemDefinitions.size.toString(),
            modifier = Modifier.weight(1f),
        )
        PaneMetaDivider()
        PaneMetaCell(
            label = stringResource(Res.string.datasets_meta_updated),
            value = formatCreatedAt(group),
            emphasis = false,
        )
    }
}

@Composable
private fun DatasetSchemaStrip(itemDefinitions: List<ScriptConfigurationItemDefinition>) {
    val columns = itemDefinitions.map { SchemaColumn(label = it.name, type = schemaLabel(it.type)) }
    SchemaStrip(
        label = stringResource(Res.string.datasets_schema_label),
        columns = columns,
    )
}

/**
 * Note: `ListConfigItem` is unreachable here — a list cannot be a dataset column, because
 * `valuePerTask` is rejected for it — but the branch keeps this mapping exhaustive at compile time.
 */
@Composable
private fun schemaLabel(type: ConfigItemType): String =
    stringResource(
        when (type) {
            ConfigItemType.BooleanConfigItem -> Res.string.schema_type_bool
            ConfigItemType.StringConfigItem -> Res.string.schema_type_text
            ConfigItemType.SecretConfigItem -> Res.string.schema_type_secret
            ConfigItemType.IntConfigItem -> Res.string.schema_type_int
            ConfigItemType.FloatConfigItem -> Res.string.schema_type_float
            ConfigItemType.DoubleConfigItem -> Res.string.schema_type_number
            is ConfigItemType.EnumConfigItem -> Res.string.schema_type_enum
            ConfigItemType.ProxyConfigItem -> Res.string.schema_type_proxy
            ConfigItemType.ProxyGroupConfigItem -> Res.string.schema_type_proxy_group
            is ConfigItemType.GroupedConfigItem -> Res.string.schema_type_group
            is ConfigItemType.ListConfigItem -> Res.string.schema_type_record_list
        },
    )
