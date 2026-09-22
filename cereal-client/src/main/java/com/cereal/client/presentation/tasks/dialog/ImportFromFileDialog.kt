package com.cereal.client.presentation.tasks.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.configuration.BOOLEAN_FALSE_VALUES
import com.cereal.client.domain.model.script.configuration.BOOLEAN_TRUE_VALUES
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.constantNames
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealOutlinedButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.Dialog
import com.cereal.client.presentation.view.rememberFileDialogLauncher
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.import
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_description
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_download_template
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_field_description
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_field_optional
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_field_required
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_field_type_description
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_fields
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_import_file_type
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_open_file
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_records_detected_label
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_required_fields
import com.cereal_automation.cereal_client.generated.resources.import_from_file_dialog_selected_file_label
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import java.awt.FileDialog.LOAD
import java.awt.FileDialog.SAVE
import java.io.File

@Composable
fun ImportFromFileDialog(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    datasetType: DatasetType,
    viewModel: ImportFromFileViewModel =
        remember {
            KoinJavaComponent.get(
                ImportFromFileViewModel::class.java,
                parameters = { parametersOf(coroutineScope, datasetType) },
            )
        },
    closeDialog: () -> Unit,
    importFile: (file: File) -> Unit,
) {
    val selectedFile = viewModel.selectedFile.value
    val openFileDialog = rememberFileDialogLauncher()

    Dialog(
        title = stringResource(Res.string.import_from_file_dialog_import_file_type, viewModel.fileTypeName.value!!),
        modifier =
            Modifier
                .width(600.dp)
                .heightIn(max = 300.dp)
                .padding(horizontal = 6.dp),
        onDismissRequest = {
            closeDialog()
        },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Description text
                CerealText(
                    text = stringResource(Res.string.import_from_file_dialog_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Field hints: which columns the file needs, and what may go in them.
                FieldHintsSection(datasetType)
            }

            // Error message
            val errorMessage = viewModel.fileErrorMessage.value
            if (errorMessage != null) {
                CerealText(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // File information when selected
            if (selectedFile != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CerealText(
                        text =
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(stringResource(Res.string.import_from_file_dialog_selected_file_label))
                                }
                                append(" ")
                                append(selectedFile.file.name)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CerealText(
                        text =
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(stringResource(Res.string.import_from_file_dialog_records_detected_label))
                                }
                                append(" ")
                                append(selectedFile.fileInfo.numberOfRecords.toString())
                            },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CerealOutlinedButton(
                        onClick = {
                            val file = openFileDialog(LOAD)
                            if (file != null) {
                                viewModel.onOpenFile(file)
                            }
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                    ) {
                        CerealText(text = stringResource(Res.string.import_from_file_dialog_open_file))
                    }

                    CerealTextButton(
                        onClick = {
                            val file = openFileDialog(SAVE)
                            if (file != null) {
                                viewModel.onDownloadTemplate(file)
                            }
                        },
                    ) {
                        CerealText(
                            text = stringResource(Res.string.import_from_file_dialog_download_template),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                CerealButton(
                    onClick = {
                        selectedFile?.file?.let {
                            importFile(it)
                        }
                    },
                    enabled = selectedFile != null,
                    modifier =
                        Modifier
                            .testTag("import_button")
                            .defaultMinSize(minWidth = 160.dp, minHeight = 40.dp),
                ) {
                    CerealText(
                        text = stringResource(Res.string.import),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

/**
 * Lists the record's fields so the file can be built without leaving the app.
 *
 * A list shows more than a custom dataset does — the type of every column, whether it is
 * required, and for the closed types exactly which values are accepted — because its template is a
 * bare header row with no example to copy from.
 */
@Composable
private fun FieldHintsSection(datasetType: DatasetType) {
    val (title, fields) =
        when (datasetType) {
            is DatasetType.Custom -> stringResource(Res.string.import_from_file_dialog_required_fields) to datasetType.definitions
            is DatasetType.ConfigList -> stringResource(Res.string.import_from_file_dialog_fields) to datasetType.definitions
            DatasetType.Proxy -> return
        }
    val describeType = datasetType is DatasetType.ConfigList

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CerealText(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fields.forEach { field ->
                FieldHintItem(field, describeType)
            }
        }
    }
}

@Composable
private fun FieldHintItem(
    field: ScriptConfigurationItemDefinition,
    describeType: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = CerealTheme.colorScheme.success,
            modifier = Modifier.size(20.dp),
        )

        val requiredness =
            if (field.isNullable) {
                stringResource(Res.string.import_from_file_dialog_field_optional)
            } else {
                stringResource(Res.string.import_from_file_dialog_field_required)
            }
        val details =
            if (describeType) {
                stringResource(
                    Res.string.import_from_file_dialog_field_type_description,
                    field.type.csvTypeDescription(),
                    requiredness,
                    field.description,
                )
            } else {
                stringResource(Res.string.import_from_file_dialog_field_description, field.description)
            }

        CerealText(
            text =
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(field.key)
                    }
                    append(" ")
                    append(details)
                },
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 16.sp,
        )
    }
}

/**
 * How a record field should be filled in: the kind of value a cell must hold and, for the closed
 * types, exactly which values are accepted. The accepted spellings themselves come from the domain,
 * so the hint cannot drift from what the importer actually allows.
 */
private fun ConfigItemType.csvTypeDescription(): String =
    when (this) {
        ConfigItemType.IntConfigItem -> "whole number"
        ConfigItemType.FloatConfigItem, ConfigItemType.DoubleConfigItem -> "number"
        ConfigItemType.BooleanConfigItem -> "yes/no (${(BOOLEAN_TRUE_VALUES + BOOLEAN_FALSE_VALUES).joinToString(", ")})"
        is ConfigItemType.EnumConfigItem -> "one of: ${constantNames.joinToString(", ")}"
        else -> "text"
    }
