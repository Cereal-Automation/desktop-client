package com.cereal.client.presentation.tasks

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.tasks.model.RecentCheckUiModel
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.SectionLabel
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.check_outcome_failed
import com.cereal_automation.cereal_client.generated.resources.check_outcome_ok
import com.cereal_automation.cereal_client.generated.resources.check_reported
import com.cereal_automation.cereal_client.generated.resources.recent_checks
import com.cereal_automation.cereal_client.generated.resources.report_wrong_result
import com.cereal_automation.cereal_client.generated.resources.report_wrong_result_note
import com.cereal_automation.cereal_client.generated.resources.report_wrong_result_note_count
import com.cereal_automation.cereal_client.generated.resources.submit
import org.jetbrains.compose.resources.stringResource

internal const val REPORT_NOTE_TEST_TAG = "reportWrongResultNote"

/**
 * The selected agent task's kept checks: time, outcome and label, and "Report wrong result" on an ok one the user may
 * still report. A trace is never viewable.
 */
@Composable
fun RecentChecksSection(
    checks: List<RecentCheckUiModel>,
    onReport: (traceId: String, note: String) -> Unit,
) {
    var reporting by remember { mutableStateOf<RecentCheckUiModel?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = stringResource(Res.string.recent_checks).uppercase(), contentPadding = PaddingValues())
        checks.forEach { check ->
            val shape = RoundedCornerShape(8.dp)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, CerealTheme.colorScheme.border, shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                CerealText(text = check.label, color = CerealTheme.colorScheme.contentSecondary, maxLines = 1, style = CerealTypography.logLine)
                val outcome = stringResource(if (check.failed) Res.string.check_outcome_failed else Res.string.check_outcome_ok)
                CerealText(
                    text = "${check.time} · $outcome",
                    color = if (check.failed) MaterialTheme.colorScheme.error else CerealTheme.colorScheme.contentTertiary,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                when {
                    check.reported -> {
                        CerealText(
                            text = stringResource(Res.string.check_reported),
                            color = CerealTheme.colorScheme.contentTertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    check.canReport -> {
                        CerealTextButton(onClick = { reporting = check }, contentPadding = PaddingValues(0.dp)) {
                            CerealText(stringResource(Res.string.report_wrong_result))
                        }
                    }
                }
            }
        }
    }
    reporting?.let { check ->
        ReportWrongResultDialog(
            noteMaxLength = check.noteMaxLength,
            onSubmit = { note ->
                onReport(check.traceId, note)
                reporting = null
            },
            onDismiss = { reporting = null },
        )
    }
}

/** Asks for the user's optional note on a wrong result: their own words, sent as they are. */
@Composable
private fun ReportWrongResultDialog(
    noteMaxLength: Int,
    onSubmit: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.widthIn(min = 400.dp),
        title = { CerealText(stringResource(Res.string.report_wrong_result)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CerealTextField(
                    value = note,
                    onValueChange = { note = it.take(noteMaxLength) },
                    modifier = Modifier.fillMaxWidth().testTag(REPORT_NOTE_TEST_TAG),
                    placeholder = stringResource(Res.string.report_wrong_result_note),
                    singleLine = false,
                )
                CerealText(
                    text = stringResource(Res.string.report_wrong_result_note_count, note.length, noteMaxLength),
                    color = CerealTheme.colorScheme.contentTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { CerealTextButton(onClick = { onSubmit(note) }) { CerealText(stringResource(Res.string.submit)) } },
        dismissButton = { CerealTextButton(onClick = onDismiss) { CerealText(stringResource(Res.string.cancel)) } },
        onDismissRequest = onDismiss,
    )
}
