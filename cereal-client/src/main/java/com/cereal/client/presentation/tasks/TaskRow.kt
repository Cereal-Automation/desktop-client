package com.cereal.client.presentation.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.presentation.notification.formatRelativeTime
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.ActivityIndicator
import com.cereal.client.presentation.view.CerealOutlinedIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.button.CerealToolbarButton
import com.cereal.client.presentation.view.button.CerealToolbarButtonVariant
import com.cereal.client.presentation.view.chip.Pill
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.continue_action
import com.cereal_automation.cereal_client.generated.resources.continue_task
import com.cereal_automation.cereal_client.generated.resources.ic_play
import com.cereal_automation.cereal_client.generated.resources.ic_stop
import com.cereal_automation.cereal_client.generated.resources.seconds_ago
import com.cereal_automation.cereal_client.generated.resources.start_task
import com.cereal_automation.cereal_client.generated.resources.stop_task
import com.cereal_automation.cereal_client.generated.resources.task_ready
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal val RowSelectedBg = Color(0xFF1F1F1F)

@Composable
internal fun TaskRow(
    taskItem: TaskUiModel,
    isSelected: Boolean,
    onStartTask: () -> Unit,
    onStopTask: () -> Unit,
    onRowClick: () -> Unit,
    onContinueClick: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (isSelected) RowSelectedBg else Color.Transparent)
                .clickable(
                    onClick = onRowClick,
                    // Neutral indication: a red ripple/focus overlay made the selected row look errored.
                    indication = ripple(),
                    interactionSource = interactionSource,
                ),
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .align(Alignment.CenterStart)
                        .background(accent),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CerealText(
                text = "#${taskItem.taskNumber}",
                modifier = Modifier.width(40.dp),
                color = accent,
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                textAlign = TextAlign.End,
            )

            Column(modifier = Modifier.weight(1f)) {
                TaskMessage(taskItem, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                if (taskItem.configuration.isNotEmpty()) {
                    CerealText(
                        text = taskItem.configuration,
                        color = CerealTheme.colorScheme.contentTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            TimeSince(taskItem.statusSince)

            if (taskItem.isRunning) {
                ActivityIndicator(size = 18.dp)
            }

            StatusChip(
                status = taskItem.status,
                isRunning = taskItem.isRunning,
                isError = taskItem.isError,
                isSuccess = taskItem.isSuccess,
            )

            TaskActionButton(taskItem, onStartTask, onStopTask, onContinueClick)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.BottomCenter)
                    .background(CerealTheme.colorScheme.surfaceRaised),
        )
    }
}

/** The task's status message, or "Ready" while it is idle. */
@Composable
internal fun TaskMessage(
    task: TaskUiModel,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    CerealText(
        // An idle task's status message ("Press start") stays in its status history.
        text = if (task.isIdle) stringResource(Res.string.task_ready) else task.message,
        color = if (task.isIdle) CerealTheme.colorScheme.contentTertiary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
        modifier = modifier,
    )
}

/** Continue while the task awaits it, else Stop while it runs, else Start. */
@Composable
internal fun TaskActionButton(
    task: TaskUiModel,
    onStartTask: () -> Unit,
    onStopTask: () -> Unit,
    onContinueClick: (() -> Unit)?,
) {
    if (task.id.userInteraction is UserInteraction.ContinueButton) {
        CerealToolbarButton(
            variant = CerealToolbarButtonVariant.Green,
            onClick = { onContinueClick?.invoke() },
        ) {
            Icon(
                painterResource(Res.drawable.ic_play),
                contentDescription = stringResource(Res.string.continue_task),
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            CerealText(text = stringResource(Res.string.continue_action))
        }
    } else if (task.isRunning) {
        TaskActionIcon(
            onClick = onStopTask,
            icon = Res.drawable.ic_stop,
            contentDescription = stringResource(Res.string.stop_task),
        )
    } else {
        TaskActionIcon(
            onClick = onStartTask,
            icon = Res.drawable.ic_play,
            contentDescription = stringResource(Res.string.start_task),
        )
    }
}

/** Time since [since]: seconds under a minute ("12s ago"), then [formatRelativeTime]; re-rendered every second. */
@OptIn(ExperimentalTime::class)
@Composable
internal fun TimeSince(since: Instant) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(since) {
        while (true) {
            now = Clock.System.now()
            delay(1.seconds)
        }
    }
    val elapsed = now - since
    CerealText(
        text =
            if (elapsed.inWholeMinutes < 1) {
                stringResource(Res.string.seconds_ago, elapsed.inWholeSeconds.coerceAtLeast(0))
            } else {
                formatRelativeTime(since.toEpochMilliseconds(), now.toEpochMilliseconds())
            },
        color = CerealTheme.colorScheme.contentTertiary,
        maxLines = 1,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun TaskActionIcon(
    onClick: () -> Unit,
    icon: org.jetbrains.compose.resources.DrawableResource,
    contentDescription: String,
) {
    CerealOutlinedIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun StatusChip(
    status: String,
    isRunning: Boolean,
    isError: Boolean,
    isSuccess: Boolean,
) {
    val (textColor, bgColor) =
        when {
            isError -> {
                MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            }

            isRunning -> {
                CerealTheme.colorScheme.warning to CerealTheme.colorScheme.warning.copy(alpha = 0.16f)
            }

            isSuccess -> {
                CerealTheme.colorScheme.success to CerealTheme.colorScheme.success.copy(alpha = 0.14f)
            }

            else -> {
                CerealTheme.colorScheme.contentSecondary to
                    CerealTheme.colorScheme.contentSecondary.copy(alpha = 0.10f)
            }
        }
    Pill(
        text = status,
        contentColor = textColor,
        backgroundColor = bgColor,
        leadingDot = isRunning,
    )
}
