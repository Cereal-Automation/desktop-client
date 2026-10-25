package com.cereal.client.presentation.logs

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SectionLabel
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.artifacts
import com.cereal_automation.cereal_client.generated.resources.auto_scroll
import com.cereal_automation.cereal_client.generated.resources.clear
import com.cereal_automation.cereal_client.generated.resources.copy_all_logs
import com.cereal_automation.cereal_client.generated.resources.download_artifact
import com.cereal_automation.cereal_client.generated.resources.log
import com.cereal_automation.cereal_client.generated.resources.log_filter_all
import com.cereal_automation.cereal_client.generated.resources.log_filter_err
import com.cereal_automation.cereal_client.generated.resources.log_filter_info
import com.cereal_automation.cereal_client.generated.resources.log_filter_warn
import com.cereal_automation.cereal_client.generated.resources.no_output_yet
import com.cereal_automation.cereal_client.generated.resources.none_yet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime

internal const val CLEAR_BUTTON_TEST_TAG = "logOutputClearButton"

private val ToggleThumb = Color.White

/** The log box lives in the inspector's scrolling column, so it needs a bounded height of its own. */
private val LogBoxMaxHeight = 320.dp

private enum class LogFilter { ALL, INFO, WARN, ERR }

private fun LogFilter.labelRes() =
    when (this) {
        LogFilter.ALL -> Res.string.log_filter_all
        LogFilter.INFO -> Res.string.log_filter_info
        LogFilter.WARN -> Res.string.log_filter_warn
        LogFilter.ERR -> Res.string.log_filter_err
    }

/** A task's log: "LOG" with clear and copy, the ALL/INFO/WARN/ERR filter and auto-scroll, then the lines. */
@Composable
fun TaskLogSection(
    events: List<LoggingEvent>,
    onClear: () -> Unit,
) {
    var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }

    // Pair each event with its index in the append-only source list. That index is a stable,
    // unique identity to key the LazyColumn on (LoggingEvent has no id and identical lines can
    // recur, so a content-based key would risk duplicate-key crashes).
    val filteredEvents =
        remember(events, filter) {
            val indexed = events.withIndex()
            when (filter) {
                LogFilter.ALL -> indexed.toList()
                LogFilter.INFO -> indexed.filter { it.value.priority == LoggingPriority.INFO }
                LogFilter.WARN -> indexed.filter { it.value.priority == LoggingPriority.WARNING }
                LogFilter.ERR -> indexed.filter { it.value.priority == LoggingPriority.ERROR }
            }
        }

    val listState = rememberLazyListState()
    LaunchedEffect(filteredEvents.size, autoScrollEnabled) {
        if (autoScrollEnabled && filteredEvents.isNotEmpty()) {
            listState.animateScrollToItem(filteredEvents.lastIndex)
        }
    }

    var copied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val onCopyAll = {
        val text = filteredEvents.joinToString("\n") { formatLogLine(it.value) }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        coroutineScope.launch {
            copied = true
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
        Unit
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(text = stringResource(Res.string.log).uppercase(), contentPadding = PaddingValues())
            Spacer(Modifier.weight(1f))
            val clearEnabled = events.isNotEmpty()
            CerealIconButton(onClick = onClear, enabled = clearEnabled, modifier = Modifier.size(32.dp).testTag(CLEAR_BUTTON_TEST_TAG)) {
                Icon(
                    imageVector = Icons.Filled.CleaningServices,
                    contentDescription = stringResource(Res.string.clear),
                    modifier = Modifier.size(14.dp),
                    tint = if (clearEnabled) CerealTheme.colorScheme.contentTertiary else CerealTheme.colorScheme.contentSubtle,
                )
            }
            val copyEnabled = filteredEvents.isNotEmpty()
            CerealIconButton(onClick = onCopyAll, enabled = copyEnabled, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = stringResource(Res.string.copy_all_logs),
                    modifier = Modifier.size(14.dp),
                    tint =
                        when {
                            copied -> Color.White
                            copyEnabled -> CerealTheme.colorScheme.contentTertiary
                            else -> CerealTheme.colorScheme.contentSubtle
                        },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterPills(filter = filter, onFilterChange = { filter = it })
            Spacer(Modifier.weight(1f))
            AutoScrollControl(enabled = autoScrollEnabled, onChange = { autoScrollEnabled = it })
        }

        val boxShape = RoundedCornerShape(8.dp)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(boxShape)
                    .background(CerealTheme.colorScheme.backgroundDark)
                    .border(1.dp, CerealTheme.colorScheme.border, boxShape),
        ) {
            if (filteredEvents.isEmpty()) {
                EmptyHint(stringResource(Res.string.no_output_yet), Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = LogBoxMaxHeight),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filteredEvents, key = { it.index }) { indexedEvent ->
                        SelectionContainer {
                            LogLine(indexedEvent.value)
                        }
                    }
                }
            }
        }
    }
}

/** A task's artifacts, each with a download button; "None yet" while there are none. */
@Composable
fun TaskArtifactsSection(
    artifacts: List<Artifact>,
    onDownloadArtifact: (Artifact) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = stringResource(Res.string.artifacts).uppercase(), contentPadding = PaddingValues())
        if (artifacts.isEmpty()) {
            EmptyHint(stringResource(Res.string.none_yet))
        }
        artifacts.forEach { artifact ->
            ArtifactRow(artifact = artifact, onDownload = { onDownloadArtifact(artifact) })
        }
    }
}

@Composable
private fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    CerealText(
        text = text,
        modifier = modifier,
        color = CerealTheme.colorScheme.contentSubtle,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
    )
}

@Composable
private fun FilterPills(
    filter: LogFilter,
    onFilterChange: (LogFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(5.dp))
                .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogFilter.entries.forEach { option ->
            FilterPill(
                label = stringResource(option.labelRes()),
                selected = filter == option,
                onClick = { onFilterChange(option) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) CerealTheme.colorScheme.border else Color.Transparent)
                .clickable(
                    onClick = onClick,
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    interactionSource = interactionSource,
                ).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            text = label,
            color = if (selected) Color.White else CerealTheme.colorScheme.contentTertiary,
            style = CerealTypography.controlLabel.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun AutoScrollControl(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clickable(
                    onClick = { onChange(!enabled) },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CerealText(
            text = stringResource(Res.string.auto_scroll),
            color = CerealTheme.colorScheme.contentTertiary,
            style = CerealTypography.controlLabel.copy(fontWeight = FontWeight.Normal),
        )
        PillToggle(enabled = enabled)
    }
}

@Composable
private fun PillToggle(enabled: Boolean) {
    val trackColor = if (enabled) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.border
    val thumbOffset by animateDpAsState(
        targetValue = if (enabled) 18.dp else 2.dp,
        label = "pillToggleThumb",
    )
    Box(
        modifier =
            Modifier
                .width(36.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(trackColor),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset, y = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(ToggleThumb),
        )
    }
}

@Composable
private fun LogLine(event: LoggingEvent) {
    val timestamp = remember(event.timestamp) { TimeFormatter.format(event.timestamp.toInstant()) }
    val style = CerealTypography.logLine.copy(fontSize = 12.sp, lineHeight = 18.sp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CerealText(text = timestamp, color = CerealTheme.colorScheme.contentSubtle, maxLines = 1, style = style)
        CerealText(text = glyphFor(event.priority), color = glyphColorFor(event.priority), style = style.copy(fontWeight = FontWeight.Bold))
        CerealText(
            text = event.message,
            color = CerealTheme.colorScheme.contentSecondary,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
            style = style,
        )
    }
}

private fun formatLogLine(event: LoggingEvent): String {
    val timestamp = TimestampFormatter.format(event.timestamp.toInstant())
    return "$timestamp [${labelFor(event.priority)}] ${event.message}"
}

private fun labelFor(priority: LoggingPriority): String =
    when (priority) {
        LoggingPriority.ERROR -> "ERROR"
        LoggingPriority.WARNING -> "WARN"
        LoggingPriority.INFO -> "INFO"
        LoggingPriority.DEBUG -> "DEBUG"
    }

private fun glyphFor(priority: LoggingPriority): String =
    when (priority) {
        LoggingPriority.ERROR -> "✗"
        LoggingPriority.WARNING -> "!"
        LoggingPriority.INFO -> "›"
        LoggingPriority.DEBUG -> "·"
    }

@Composable
private fun glyphColorFor(priority: LoggingPriority): Color =
    when (priority) {
        LoggingPriority.ERROR -> MaterialTheme.colorScheme.error
        LoggingPriority.WARNING -> CerealTheme.colorScheme.warning
        LoggingPriority.INFO -> CerealTheme.colorScheme.info
        LoggingPriority.DEBUG -> CerealTheme.colorScheme.contentTertiary
    }

@OptIn(ExperimentalTime::class)
@Composable
private fun ArtifactRow(
    artifact: Artifact,
    onDownload: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, CerealTheme.colorScheme.border, shape)
                .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = artifact.name,
                color = CerealTheme.colorScheme.contentSecondary,
                maxLines = 1,
                style = CerealTypography.logLine,
            )
            CerealText(
                text = "${formatBytes(artifact.sizeBytes)} · ${TimeFormatter.format(Date(artifact.createdAt.toEpochMilliseconds()).toInstant())}",
                color = CerealTheme.colorScheme.contentSubtle,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        CerealIconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(Res.string.download_artifact),
                modifier = Modifier.size(16.dp),
                tint = CerealTheme.colorScheme.contentSecondary,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_UNIT) return "$bytes B"
    val kb = bytes / BYTES_PER_UNIT.toDouble()
    if (kb < BYTES_PER_UNIT) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / BYTES_PER_UNIT
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

private const val BYTES_PER_UNIT = 1024
private const val COPIED_FEEDBACK_MS = 1500L

// DateTimeFormatter is thread-safe, unlike SimpleDateFormat. Lines show the time; a copied log keeps the date.
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()).withZone(ZoneId.systemDefault())
private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).withZone(ZoneId.systemDefault())
