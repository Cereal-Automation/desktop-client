package com.cereal.client.presentation.tasks.script.overview.configuration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigSection
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.tasks.dialog.ImportFromFileDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CaptionText
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ClickableUrlText
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.InfoBox
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.fields.DropdownTextField
import com.cereal.client.presentation.view.fields.FileField
import com.cereal.client.presentation.view.fields.MAX_OPTION_CARDS
import com.cereal.client.presentation.view.fields.OptionCardsField
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
import com.cereal_automation.cereal_client.generated.resources.hide_section_settings
import com.cereal_automation.cereal_client.generated.resources.import_from_csv
import com.cereal_automation.cereal_client.generated.resources.remove_entry
import com.cereal_automation.cereal_client.generated.resources.row_number
import com.cereal_automation.cereal_client.generated.resources.rows_replace_confirmation_message
import com.cereal_automation.cereal_client.generated.resources.rows_replace_confirmation_title
import com.cereal_automation.cereal_client.generated.resources.section_error_count
import com.cereal_automation.cereal_client.generated.resources.select_script
import com.cereal_automation.cereal_client.generated.resources.show_section_settings
import org.jetbrains.compose.resources.pluralStringResource
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(25.dp), // 20px × 1.25
        verticalArrangement = Arrangement.spacedBy(20.dp), // 16px × 1.25
    ) {
        // Display instructions if present
        viewModel.scriptInstructions?.let { instructions ->
            InfoBox(
                text = instructions,
            )
        }

        // Copy from existing script instance dropdown
        val existingInstances = viewModel.existingInstances.value
        if (existingInstances.isNotEmpty()) {
            val copyFromOptions =
                remember(existingInstances) {
                    existingInstances.map { instance ->
                        val formattedDate = dateFormatter.format(instance.createdAt.toJavaInstant())
                        val identifierValue =
                            instance.entryScript.configuration
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
            ConfigField(
                title = stringResource(Res.string.copy_from_existing_script),
                description = stringResource(Res.string.copy_from_existing_script_desc),
            ) {
                DropdownTextField(stringResource(Res.string.select_script), copyFromState)
            }
        }

        val scriptConfigurationForm = viewModel.scriptConfigurationForm.value
        viewModel.fieldSections().forEach { (section, fields) ->
            if (section != null && fields.values.any { it.isVisible.value }) {
                ConfigSectionHeader(
                    section = section,
                    isOpen = viewModel.isSectionOpen(section),
                    errorCount = viewModel.sectionErrorCounts[section.key] ?: 0,
                    onToggle = { viewModel.toggleSection(section) },
                )
            }
            // Collapsing only skips rendering: the field states, and so the values, are kept.
            if (section == null || viewModel.isSectionOpen(section)) {
                fields.forEach { (t, u) ->
                    if (u.isVisible.value && u is ListFieldState) {
                        ListField(t.definition.name, t.definition.description, u.isRequired.value, u)
                    } else if (u.isVisible.value) {
                        ConfigField(
                            title = t.definition.name,
                            description = t.definition.description,
                            isRequired = u.isRequired.value,
                        ) {
                            FormFieldContent(t, u)
                        }
                    }
                }
            }
        }
        // Notification Overrides Section
        NotificationOverridesSection(viewModel.notificationOverridesForm)

        if (viewModel.showIndicatesRequiredFieldsHint.value) {
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

private val SectionRuleColor = Color(0xFF232323)

/** A configuration field stacked as label, control, then hint — the label names the control, so it carries none itself. */
@Composable
internal fun ConfigField(
    title: String,
    description: String?,
    isRequired: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(title, isRequired)
        content()
        if (!description.isNullOrBlank()) {
            ClickableUrlText(description, color = CerealTheme.colorScheme.contentSubtle)
        }
    }
}

@Composable
internal fun FieldLabel(
    title: String,
    isRequired: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CerealText(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = CerealTheme.colorScheme.contentSecondary,
        )
        if (isRequired) {
            CerealText(
                text = "*",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ConfigSectionHeader(
    section: ConfigSection,
    isOpen: Boolean,
    errorCount: Int,
    onToggle: () -> Unit,
) {
    val rowModifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    Row(
        modifier = if (section.collapsed) rowModifier.clickable(onClick = onToggle) else rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp), // 10px × 1.25
    ) {
        val label =
            if (section.collapsed) {
                stringResource(if (isOpen) Res.string.hide_section_settings else Res.string.show_section_settings, section.name)
            } else {
                section.name
            }
        if (section.collapsed) {
            Icon(
                imageVector = if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(18.dp),
            )
        }
        SectionLabel(text = label.uppercase(), contentPadding = PaddingValues())
        if (errorCount > 0) {
            CaptionText(
                pluralStringResource(Res.plurals.section_error_count, errorCount, errorCount),
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = SectionRuleColor)
    }
}

@Composable
internal fun FormFieldContent(
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
        if (configurationItem.definition.type is ConfigItemType.EnumConfigItem && formFieldState.values.value.size <= MAX_OPTION_CARDS) {
            OptionCardsField(formFieldState)
        } else {
            Dropdown(configurationItem, formFieldState)
        }
    }

    is FileFieldState -> {
        File(formFieldState)
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
    TextField(configurationItem.definition.name, state, labelVisible = false)
}

@Composable
private fun SecretText(
    configurationItem: ConfigurationItem,
    state: SecretTextFieldState,
) {
    SecretTextField(configurationItem.definition.name, state, labelVisible = false)
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
            DropdownTextField(configurationItem.definition.name, state, modifier = Modifier.weight(1f), labelVisible = false)
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
        DropdownTextField(configurationItem.definition.name, state, labelVisible = false)
    }
}

@Composable
private fun File(state: FileFieldState) {
    FileField(state)
}
