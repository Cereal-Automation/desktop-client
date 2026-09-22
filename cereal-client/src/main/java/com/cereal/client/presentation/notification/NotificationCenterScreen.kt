package com.cereal.client.presentation.notification

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.notification_center_couldnt_deliver
import com.cereal_automation.cereal_client.generated.resources.notification_center_delivered
import com.cereal_automation.cereal_client.generated.resources.notification_center_delivery_label
import com.cereal_automation.cereal_client.generated.resources.notification_center_delivery_summary
import com.cereal_automation.cereal_client.generated.resources.notification_center_empty_message
import com.cereal_automation.cereal_client.generated.resources.notification_center_empty_title
import com.cereal_automation.cereal_client.generated.resources.notification_center_failed
import com.cereal_automation.cereal_client.generated.resources.notification_center_no_channels_message
import com.cereal_automation.cereal_client.generated.resources.notification_center_no_channels_title
import com.cereal_automation.cereal_client.generated.resources.notification_center_open_task
import com.cereal_automation.cereal_client.generated.resources.notification_center_recorded_only
import com.cereal_automation.cereal_client.generated.resources.notification_center_summary
import com.cereal_automation.cereal_client.generated.resources.notification_center_unknown_task
import com.cereal_automation.cereal_client.generated.resources.notification_channel_discord
import com.cereal_automation.cereal_client.generated.resources.notification_channel_email
import com.cereal_automation.cereal_client.generated.resources.notification_channel_system
import com.cereal_automation.cereal_client.generated.resources.notification_channel_telegram
import com.cereal_automation.cereal_client.generated.resources.notifications
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
fun NotificationCenterScreen(onOpenTask: (taskId: String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<NotificationCenterViewModel>(
                NotificationCenterViewModel::class.java,
                parameters = { parametersOf(scope) },
            )
        }
    NotificationCenterContent(viewModel = viewModel, onOpenTask = onOpenTask)
}

@Composable
private fun NotificationCenterContent(
    viewModel: NotificationCenterViewModel,
    onOpenTask: (taskId: String) -> Unit,
) {
    val notifications by viewModel.notifications
    val isLoading by viewModel.isLoading
    val unseenCount by viewModel.unseenCount
    val expandedId by viewModel.expandedId
    val expandedAttempts by viewModel.expandedAttempts

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        NotificationCenterToolbar(unseenCount = unseenCount, totalCount = notifications.size)

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize())
            }

            notifications.isEmpty() -> {
                NotificationCenterEmptyState()
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationRow(
                            item = item,
                            expanded = item.id == expandedId,
                            attempts = if (item.id == expandedId) expandedAttempts else emptyList(),
                            onToggle = { viewModel.onToggleExpand(item.id) },
                            onOpenTask = { onOpenTask(item.taskId) },
                        )
                        HorizontalDivider(color = CerealTheme.colorScheme.progressTrack)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCenterToolbar(
    unseenCount: Int,
    totalCount: Int,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CerealText(
                text = stringResource(Res.string.notifications),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            CerealText(
                text = stringResource(Res.string.notification_center_summary, unseenCount, totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        HorizontalDivider(color = CerealTheme.colorScheme.border)
    }
}

@Composable
private fun NotificationCenterEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CerealTheme.colorScheme.cardDark)
                    .border(1.dp, CerealTheme.colorScheme.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(28.dp),
            )
        }
        CerealText(
            text = stringResource(Res.string.notification_center_empty_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        CerealText(
            text = stringResource(Res.string.notification_center_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = CerealTheme.colorScheme.contentSecondary,
        )
    }
}

@Composable
private fun NotificationRow(
    item: NotificationCenterUiModel,
    expanded: Boolean,
    attempts: List<NotificationDeliveryUiModel>,
    onToggle: () -> Unit,
    onOpenTask: () -> Unit,
) {
    val titleLine = if (!item.title.isNullOrBlank()) item.title else item.message
    val showMessageLine = !item.title.isNullOrBlank()
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (expanded) CerealTheme.colorScheme.cardDark else MaterialTheme.colorScheme.background)
                .clickable(
                    onClick = onToggle,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.width(10.dp).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                if (item.unseen) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CerealText(
                        text = titleLine,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (item.unseen) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CerealText(
                        text = item.relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = CerealTheme.colorScheme.contentSubtle,
                        maxLines = 1,
                    )
                }
                if (showMessageLine) {
                    Spacer(Modifier.height(3.dp))
                    CerealText(
                        text = item.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CerealTheme.colorScheme.contentSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    CerealText(
                        text = item.taskName.ifBlank { stringResource(Res.string.notification_center_unknown_task) },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CerealTheme.colorScheme.contentTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(16.dp).rotate(chevronRotation),
            )
        }

        if (expanded) {
            NotificationDeliveryPanel(
                attempts = attempts,
                taskName = item.taskName,
                onOpenTask = onOpenTask,
            )
        }
    }
}

@Composable
fun NotificationDeliveryPanel(
    attempts: List<NotificationDeliveryUiModel>,
    taskName: String,
    onOpenTask: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 46.dp, end = 24.dp, top = 4.dp, bottom = 20.dp)) {
        CerealText(
            text = stringResource(Res.string.notification_center_delivery_label).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = CerealTheme.colorScheme.contentTertiary,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (attempts.isEmpty()) {
            NoChannelsNotice()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attempts.forEach { DeliveryChannelRow(it) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OpenTaskButton(taskName = taskName, onClick = onOpenTask)
            Spacer(Modifier.weight(1f))
            CerealText(
                text = deliverySummary(attempts),
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentSubtle,
            )
        }
    }
}

@Composable
private fun NoChannelsNotice() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(CerealTheme.colorScheme.cardDark)
                .border(1.dp, CerealTheme.colorScheme.border, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = CerealTheme.colorScheme.contentTertiary,
            modifier = Modifier.size(18.dp),
        )
        Column {
            CerealText(
                text = stringResource(Res.string.notification_center_no_channels_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CerealTheme.colorScheme.contentSecondary,
            )
            Spacer(Modifier.height(3.dp))
            CerealText(
                text = stringResource(Res.string.notification_center_no_channels_message),
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
    }
}

@Composable
private fun DeliveryChannelRow(attempt: NotificationDeliveryUiModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(CerealTheme.colorScheme.backgroundDark)
                .border(1.dp, CerealTheme.colorScheme.border, MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(CerealTheme.colorScheme.cardDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = attempt.channel.icon(),
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = stringResource(attempt.channel.labelResource()),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!attempt.delivered && !attempt.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                CerealText(
                    text = "${stringResource(Res.string.notification_center_couldnt_deliver)} ${attempt.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
        DeliveryStatusChip(delivered = attempt.delivered)
    }
}

@Composable
private fun DeliveryStatusChip(delivered: Boolean) {
    val color = if (delivered) CerealTheme.colorScheme.success else MaterialTheme.colorScheme.primary
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (delivered) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        CerealText(
            text = stringResource(if (delivered) Res.string.notification_center_delivered else Res.string.notification_center_failed),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun OpenTaskButton(
    taskName: String,
    onClick: () -> Unit,
) {
    val label =
        if (taskName.isBlank()) {
            stringResource(Res.string.notification_center_open_task)
        } else {
            "${stringResource(Res.string.notification_center_open_task)} · $taskName"
        }
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, CerealTheme.colorScheme.borderDark, MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        CerealText(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun deliverySummary(attempts: List<NotificationDeliveryUiModel>): String =
    if (attempts.isEmpty()) {
        stringResource(Res.string.notification_center_recorded_only)
    } else {
        stringResource(
            Res.string.notification_center_delivery_summary,
            attempts.count { it.delivered },
            attempts.size,
        )
    }

private fun NotificationChannelType.labelResource(): StringResource =
    when (this) {
        NotificationChannelType.DISCORD -> Res.string.notification_channel_discord
        NotificationChannelType.TELEGRAM -> Res.string.notification_channel_telegram
        NotificationChannelType.EMAIL -> Res.string.notification_channel_email
        NotificationChannelType.SYSTEM -> Res.string.notification_channel_system
    }

private fun NotificationChannelType.icon(): ImageVector =
    when (this) {
        NotificationChannelType.DISCORD -> Icons.Filled.Forum
        NotificationChannelType.TELEGRAM -> Icons.AutoMirrored.Filled.Send
        NotificationChannelType.EMAIL -> Icons.Filled.Email
        NotificationChannelType.SYSTEM -> Icons.Filled.DesktopWindows
    }
