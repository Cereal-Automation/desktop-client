package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.tasks.dialog.ImportFromFileDialog
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsForm
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CaptionText
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.InfoBox
import com.cereal.client.presentation.view.SettingsItem
import com.cereal.client.presentation.view.fields.DropdownTextField
import com.cereal.client.presentation.view.fields.FileField
import com.cereal.client.presentation.view.fields.SecretTextField
import com.cereal.client.presentation.view.fields.SwitchField
import com.cereal.client.presentation.view.fields.TextField
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal.client.presentation.view.fields.state.FileFieldState
import com.cereal.client.presentation.view.fields.state.FormFieldState
import com.cereal.client.presentation.view.fields.state.ListFieldState
import com.cereal.client.presentation.view.fields.state.ListRowState
import com.cereal.client.presentation.view.fields.state.SecretTextFieldState
import com.cereal.client.presentation.view.fields.state.SwitchFieldState
import com.cereal.client.presentation.view.fields.state.TextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.add
import com.cereal_automation.cereal_client.generated.resources.copy_from_existing_script
import com.cereal_automation.cereal_client.generated.resources.copy_from_existing_script_desc
import com.cereal_automation.cereal_client.generated.resources.import_from_csv
import com.cereal_automation.cereal_client.generated.resources.remove_entry
import com.cereal_automation.cereal_client.generated.resources.row_number
import com.cereal_automation.cereal_client.generated.resources.rows_replace_confirmation_message
import com.cereal_automation.cereal_client.generated.resources.rows_replace_confirmation_title
import com.cereal_automation.cereal_client.generated.resources.select_script
import org.jetbrains.compose.resources.stringResource
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

private data class CopyFromInstanceOption(
    val instance: ScriptPackageInstance,
    val displayLabel: String,
) {
    override fun toString(): String = displayLabel
}

@OptIn(ExperimentalTime::class)
@Composable
fun ScriptConfigurationView(viewModel: ScriptConfigurationViewModel) {
    val scrollState = rememberScrollState()
    val dateFormatter =
        remember {
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        }

    // More padding at the bottom to prevent overlap with the floating action button.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
    ) {
        // Display instructions if present
        viewModel.scriptInstructions?.let { instructions ->
            InfoBox(
                text = instructions,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Copy from existing script instance dropdown
        val existingInstances = viewModel.existingInstances.value
        if (existingInstances.isNotEmpty()) {
            val copyFromOptions =
                remember(existingInstances) {
                    existingInstances.map { instance ->
                        val formattedDate = dateFormatter.format(instance.createdAt.toJavaInstant())
                        val identifierValue =
                            instance.definition.mainScript.configuration
                                .getScriptIdentifierValue(instance.mainConfiguration)
                        val displayLabel =
                            identifierValue?.let { "$it ($formattedDate)" } ?: formattedDate
                        CopyFromInstanceOption(
                            instance = instance,
                            displayLabel = displayLabel,
                        )
                    }
                }
            val initialSelectedOption =
                remember(copyFromOptions, viewModel.initialScriptPackageInstance) {
                    viewModel.initialScriptPackageInstance?.let { initial ->
                        copyFromOptions.find { it.instance.id == initial.id }
                    }
                }
            val copyFromState =
                remember(copyFromOptions, initialSelectedOption) {
                    DropDownFieldState(
                        initialValues = copyFromOptions,
                        onValueChange = { state ->
                            state.selectedValue?.let { option ->
                                viewModel.copyFromInstance(option.instance)
                            }
                        },
                        initialSelectedValue = initialSelectedOption,
                    )
                }
            SettingsItem(
                title = stringResource(Res.string.copy_from_existing_script),
                description = stringResource(Res.string.copy_from_existing_script_desc),
            ) {
                DropdownTextField(stringResource(Res.string.select_script), copyFromState)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        val scriptConfigurationForm = viewModel.scriptConfigurationForm.value
        scriptConfigurationForm?.getAllConfigurationItemWithFieldStates().orEmpty().forEach { (t, u) ->
            if (u.isVisible.value) {
                val nameSuffix = if (u.isRequired.value) "*" else ""
                SettingsItem(
                    title = t.definition.name + nameSuffix,
                    description = t.definition.description,
                ) {
                    FormFieldContent(t, u)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (scriptConfigurationForm != null && viewModel.showConcurrentTasks.value) {
            SettingsItem(
                title = "# of concurrent tasks*",
                description = "The number of tasks to run concurrently for this script (Max: ${ConfigurationItemsForm.MAX_CONCURRENT_TASKS}).",
            ) {
                TextField("# of concurrent tasks", scriptConfigurationForm.numberOfConcurrentTasks)
            }
        }

        // Notification Overrides Section
        NotificationOverridesSection(viewModel.notificationOverridesForm)

        if (viewModel.showIndicatesRequiredFieldsHint.value) {
            Spacer(modifier = Modifier.height(24.dp))
            CaptionText(
                "* indicates required field",
                color = com.cereal.client.presentation.theme.CerealTheme.colorScheme.contentTertiary,
            )
        }
    }
    errorView(viewModel.errorAction)

    val fileImportConfig = viewModel.fileImportConfig.value
    if (fileImportConfig != null) {
        ImportFromFileDialog(datasetType = fileImportConfig.datasetType, closeDialog = {
            viewModel.onCloseImportFromFileDialog()
        }, importFile = { file ->
            viewModel.onDatasetFileSelected(file)
        })
    }

    val replaceConfirmation = viewModel.listReplaceConfirmation.value
    if (replaceConfirmation != null) {
        ConfirmationDialog(
            title = stringResource(Res.string.rows_replace_confirmation_title),
            message =
                stringResource(
                    Res.string.rows_replace_confirmation_message,
                    replaceConfirmation.importedRowCount,
                    replaceConfirmation.existingRowCount,
                ),
            onConfirm = { viewModel.confirmListReplace() },
            onDismiss = { viewModel.cancelListReplace() },
        )
    }
}

@Composable
private fun FormFieldContent(
    configurationItem: ConfigurationItem,
    formFieldState: FormFieldState<*, *>,
) = when (formFieldState) {
    // Before the general TextFieldState branch: a SecretTextFieldState *is* one, and the first
    // matching branch wins, so this ordering is what keeps a credential from rendering in clear text.
    is SecretTextFieldState -> {
        SecretText(configurationItem, formFieldState)
    }

    is TextFieldState -> {
        Text(configurationItem, formFieldState)
    }

    is SwitchFieldState -> {
        Switch(formFieldState)
    }

    is DropDownFieldState<*> -> {
        Dropdown(configurationItem, formFieldState)
    }

    is FileFieldState -> {
        File(formFieldState)
    }

    is ListFieldState -> {
        ListField(formFieldState)
    }

    else -> {
        throw RuntimeException("Unknown configurationItem ${configurationItem::class.simpleName}")
    }
}

@Composable
private fun Text(
    configurationItem: ConfigurationItem,
    state: TextFieldState<*>,
) {
    TextField(configurationItem.definition.name, state)
}

@Composable
private fun SecretText(
    configurationItem: ConfigurationItem,
    state: SecretTextFieldState,
) {
    SecretTextField(configurationItem.definition.name, state)
}

@Composable
private fun Switch(state: SwitchFieldState) {
    SwitchField(state)
}

@Composable
private fun Dropdown(
    configurationItem: ConfigurationItem,
    state: DropDownFieldState<*>,
) {
    if (state.showImportButton()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DropdownTextField(configurationItem.definition.name, state, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                CerealButton(
                    text = "Import",
                    onClick = {
                        state.onImport()
                    },
                )
            }
        }
    } else {
        DropdownTextField(configurationItem.definition.name, state)
    }
}

@Composable
private fun File(state: FileFieldState) {
    FileField(state)
}

/**
 * The height the row list is capped at. The configuration screen is one long vertically-scrolling
 * column, which cannot host an unbounded lazy list — so the rows scroll inside a window of their own.
 * The trade-off is knowingly accepted: the wheel is captured while the cursor is over that window.
 */
private val LIST_MAX_HEIGHT = 400.dp

/**
 * Renders a list as one row per record, with the add and import controls below.
 *
 * A record of two or more fields gets a card per row, so its fields stay legible with their own
 * labels. A record of a single field gets a plain input per row instead — forty SKUs are forty text
 * boxes, not forty cards.
 *
 * Rows are rendered lazily so a list of a few thousand imported rows only builds what is on screen.
 * The actions sit outside the scrolling region so they stay reachable however far down the user is.
 */
@Composable
private fun ListField(state: ListFieldState) {
    val isSingleField = state.fieldDefinitions.size == 1
    Column {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = LIST_MAX_HEIGHT),
        ) {
            itemsIndexed(state.rows) { index, row ->
                if (isSingleField) {
                    ListSingleFieldRow(state, row, index)
                } else {
                    ListRowCard(state, row, index)
                }
            }
        }
        if (state.showErrors()) {
            state.error?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CerealButton(
                text = stringResource(Res.string.add),
                onClick = { state.addRow() },
            )
            if (state.showImportButton()) {
                Spacer(modifier = Modifier.width(8.dp))
                CerealButton(
                    text = stringResource(Res.string.import_from_csv),
                    onClick = { state.onImport() },
                )
            }
        }
    }
}

/**
 * One row of a single-field list: the field's own widget with a remove control beside it, and no
 * card, no row number and no per-field label. With one field there is nothing to group and nothing to
 * disambiguate — the item's own title already names what the column holds, and repeating it forty
 * times alongside a row number is noise.
 */
@Composable
private fun ListSingleFieldRow(
    state: ListFieldState,
    row: ListRowState,
    index: Int,
) {
    val fieldDefinition = row.fieldDefinitions.first()
    val fieldState = row.fieldStates[fieldDefinition.key] ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ListRowField(fieldDefinition, fieldState, showLabel = false)
        }
        IconButton(onClick = { state.removeRow(index) }) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.remove_entry),
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ListRowCard(
    state: ListFieldState,
    row: ListRowState,
    index: Int,
) {
    CerealCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptionText(
                    text = stringResource(Res.string.row_number, index + 1),
                    color = CerealTheme.colorScheme.contentTertiary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { state.removeRow(index) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.remove_entry),
                    )
                }
            }
            row.fieldDefinitions.forEach { fieldDefinition ->
                val fieldState = row.fieldStates[fieldDefinition.key] ?: return@forEach
                ListRowField(fieldDefinition, fieldState)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * @param showLabel false for a single-field row, where the configuration item's own title already
 *   names the field and repeating it on every row is noise.
 */
@Composable
private fun ListRowField(
    fieldDefinition: ScriptConfigurationItemDefinition,
    fieldState: FormFieldState<*, *>,
    showLabel: Boolean = true,
) {
    val label =
        if (showLabel) fieldDefinition.name + if (fieldDefinition.isNullable) "" else "*" else ""
    when (fieldState) {
        is TextFieldState -> {
            TextField(label, fieldState)
        }

        is SwitchFieldState -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwitchField(fieldState)
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is DropDownFieldState<*> -> {
            DropdownTextField(label, fieldState)
        }

        else -> {
            throw RuntimeException(
                "Unsupported list field state ${fieldState::class.simpleName} for '${fieldDefinition.key}'.",
            )
        }
    }
    // The per-field error is rendered by FormField inside each widget above, so it lands next to the
    // field that is wrong.
}
