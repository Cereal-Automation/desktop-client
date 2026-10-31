package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.presentation.logs.TaskArtifactsSection
import com.cereal.client.presentation.logs.TaskLogSection
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.HorizontalDivider
import com.cereal.client.presentation.view.SectionLabel
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.close
import com.cereal_automation.cereal_client.generated.resources.ic_play
import com.cereal_automation.cereal_client.generated.resources.ic_stop
import com.cereal_automation.cereal_client.generated.resources.latest_message
import com.cereal_automation.cereal_client.generated.resources.report_issue
import com.cereal_automation.cereal_client.generated.resources.restart
import com.cereal_automation.cereal_client.generated.resources.start
import com.cereal_automation.cereal_client.generated.resources.stop
import com.cereal_automation.cereal_client.generated.resources.task_number
import com.cereal_automation.cereal_client.generated.resources.task_ready
import com.cereal_automation.cereal_client.generated.resources.task_values
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Narrowest the inspector gets: room for the log's level filter and auto-scroll toggle on one line. */
internal val InspectorMinWidth = 400.dp

/** Narrowest the inspector leaves the task list beside it. */
private val ListMinWidth = 400.dp

/** Keeps a requested inspector width within bounds; when [available] can't fit both minimums, the inspector's wins. */
internal fun clampInspectorWidth(
    requested: Dp,
    available: Dp,
): Dp = requested.coerceAtMost(available - ListMinWidth).coerceAtLeast(InspectorMinWidth)

internal const val TASK_INSPECTOR_TEST_TAG = "task_inspector"

/** The selected task beside the list: its status, actions, latest message, values, log and artifacts. */
@Composable
internal fun TaskInspector(
    state: TaskInspectorState,
    events: List<LoggingEvent>,
    artifacts: List<Artifact>,
    onClose: () -> Unit,
    onAction: (TaskInspectorAction) -> Unit,
    onClearLog: () -> Unit,
    onDownloadArtifact: (Artifact) -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = state.task
    Column(modifier.testTag(TASK_INSPECTOR_TEST_TAG).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = PanePadding, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CerealText(
                text = stringResource(Res.string.task_number, task.taskNumber),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusChip(status = task.status, isRunning = task.isRunning, isError = task.isError, isSuccess = task.isSuccess)
            Spacer(Modifier.weight(1f))
            CerealIconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.close),
                    modifier = Modifier.size(18.dp),
                    tint = CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(PanePadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            InspectorActions(state.actions, onAction)

            InspectorSection(stringResource(Res.string.latest_message)) {
                CerealText(
                    // An idle task's status message ("Press start") stays in its status history, as on the row.
                    text = if (task.isIdle) stringResource(Res.string.task_ready) else task.message,
                    color = if (task.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }

            // Only a task with a proxy or dataset row of its own has values worth showing.
            if (task.configuration.isNotEmpty()) {
                InspectorSection(stringResource(Res.string.task_values)) {
                    val shape = RoundedCornerShape(8.dp)
                    CerealText(
                        text = task.configuration,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, CerealTheme.colorScheme.border, shape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = CerealTheme.colorScheme.contentSecondary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    )
                }
            }

            TaskLogSection(events = events, onClear = onClearLog)

            TaskArtifactsSection(artifacts = artifacts, onDownloadArtifact = onDownloadArtifact)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectorActions(
    actions: List<TaskInspectorAction>,
    onAction: (TaskInspectorAction) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            val variant =
                when (action) {
                    TaskInspectorAction.START -> CerealToolbarButtonVariant.Green
                    TaskInspectorAction.REPORT_ISSUE -> CerealToolbarButtonVariant.Ghost
                    TaskInspectorAction.STOP, TaskInspectorAction.RESTART -> CerealToolbarButtonVariant.Flat
                }
            CerealToolbarButton(variant = variant, onClick = { onAction(action) }) {
                val iconModifier = Modifier.size(ButtonDefaults.IconSize)
                when (action) {
                    TaskInspectorAction.STOP -> Icon(painterResource(Res.drawable.ic_stop), contentDescription = null, modifier = iconModifier)
                    TaskInspectorAction.START -> Icon(painterResource(Res.drawable.ic_play), contentDescription = null, modifier = iconModifier)
                    TaskInspectorAction.RESTART -> Icon(Icons.Filled.Refresh, contentDescription = null, modifier = iconModifier)
                    TaskInspectorAction.REPORT_ISSUE -> Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = iconModifier)
                }
                CerealText(
                    text =
                        stringResource(
                            when (action) {
                                TaskInspectorAction.STOP -> Res.string.stop
                                TaskInspectorAction.START -> Res.string.start
                                TaskInspectorAction.RESTART -> Res.string.restart
                                TaskInspectorAction.REPORT_ISSUE -> Res.string.report_issue
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun InspectorSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = label.uppercase(), contentPadding = PaddingValues())
        content()
    }
}
