package com.cereal.client.presentation.logs

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.domain.model.artifact.Artifact
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.theme.cerealColors
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.button.CerealSmallIconButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.auto_scroll
import com.cereal_automation.cereal_client.generated.resources.clear
import com.cereal_automation.cereal_client.generated.resources.collapse_output
import com.cereal_automation.cereal_client.generated.resources.copy_all_logs
import com.cereal_automation.cereal_client.generated.resources.download_artifact
import com.cereal_automation.cereal_client.generated.resources.expand_output
import com.cereal_automation.cereal_client.generated.resources.log_filter_all
import com.cereal_automation.cereal_client.generated.resources.log_filter_err
import com.cereal_automation.cereal_client.generated.resources.log_filter_info
import com.cereal_automation.cereal_client.generated.resources.log_filter_warn
import com.cereal_automation.cereal_client.generated.resources.no_artifacts
import com.cereal_automation.cereal_client.generated.resources.output
import com.cereal_automation.cereal_client.generated.resources.output_tab_artifacts
import com.cereal_automation.cereal_client.generated.resources.output_tab_logs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime

private val DrawerBg = cerealColors.backgroundDark
private val ToggleThumb = Color.White

internal const val CLEAR_BUTTON_TEST_TAG = "logOutputClearButton"
internal const val ARTIFACT_BADGE_TEST_TAG = "artifactUnseenBadge"

private val CollapsedHeight = 44.dp
private val ExpandedHeight = 260.dp
private val MinResizeHeight = CollapsedHeight + 48.dp
private val MaxResizeHeight = 600.dp // Arbitrary upper bound; fits typical 1080p desktop layouts

private enum class LogFilter { ALL, INFO, WARN, ERR }

private fun LogFilter.labelRes() =
    when (this) {
        LogFilter.ALL -> Res.string.log_filter_all
        LogFilter.INFO -> Res.string.log_filter_info
        LogFilter.WARN -> Res.string.log_filter_warn
        LogFilter.ERR -> Res.string.log_filter_err
    }

@Composable
fun LogOutputView(
    events: List<LoggingEvent>,
    onClear: () -> Unit,
    artifacts: List<Artifact> = emptyList(),
    onDownloadArtifact: (Artifact) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var tab by rememberSaveable { mutableStateOf(OutputTab.LOGS) }

    // Session-only "seen" tracking, keyed by artifact id so it stays correct across task switches
    // (ids are unique). Unseen = artifacts the user hasn't viewed yet; surfaced as a badge on the
    // Artifacts tab. Reset on app restart by design.
    var seenArtifactIds by remember { mutableStateOf(emptySet<String>()) }
    val unseenArtifactCount = artifacts.count { it.id !in seenArtifactIds }

    // Mark everything currently shown as seen whenever the artifacts are genuinely visible — i.e. the
    // Artifacts tab is selected and the drawer is expanded. Re-runs as new artifacts arrive while
    // viewing, so the badge stays cleared.
    LaunchedEffect(artifacts, tab, expanded) {
        if (tab == OutputTab.ARTIFACTS && expanded) {
            seenArtifactIds = artifacts.mapTo(mutableSetOf()) { it.id }
        }
    }

    var customExpandedHeight by remember { mutableStateOf(ExpandedHeight) }

    val density = LocalDensity.current

    val height by animateDpAsState(
        targetValue = if (expanded) customExpandedHeight else CollapsedHeight,
        label = "outputDrawerHeight",
    )

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

    val onCopyAll =
        remember(filteredEvents) {
            {
                val text = filteredEvents.joinToString("\n") { formatLogLine(it.value) }
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                coroutineScope.launch {
                    copied = true
                    delay(1500)
                    copied = false
                }
                Unit
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(DrawerBg),
    ) {
        ResizeDragHandle(
            enabled = expanded,
            borderColor = CerealTheme.colorScheme.border,
            onDrag = { dragAmountPx ->
                val dragDp = with(density) { dragAmountPx.toDp() }
                customExpandedHeight =
                    (customExpandedHeight - dragDp)
                        .coerceIn(MinResizeHeight, MaxResizeHeight)
            },
        )
        OutputHeader(
            expanded = expanded,
            onToggleExpanded = { expanded = !expanded },
            tab = tab,
            onTabChange = {
                tab = it
                // Selecting a tab while collapsed should reveal its content, not silently switch a
                // hidden tab. Applies to both pills so the two behave consistently.
                expanded = true
            },
            unseenArtifactCount = unseenArtifactCount,
            filter = filter,
            onFilterChange = { filter = it },
            autoScrollEnabled = autoScrollEnabled,
            onAutoScrollChange = { autoScrollEnabled = it },
            onClear = onClear,
            clearEnabled = events.isNotEmpty(),
            onCopyAll = onCopyAll,
            copyEnabled = filteredEvents.isNotEmpty(),
            copied = copied,
        )

        if (expanded) {
            when (tab) {
                OutputTab.LOGS -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp),
                        ) {
                            items(filteredEvents, key = { it.index }) { indexedEvent ->
                                SelectionContainer {
                                    LogLine(indexedEvent.value)
                                }
                            }
                        }

                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState = listState),
                        )
                    }
                }

                OutputTab.ARTIFACTS -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ArtifactList(artifacts = artifacts, onDownloadArtifact = onDownloadArtifact)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputHeader(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    tab: OutputTab,
    onTabChange: (OutputTab) -> Unit,
    unseenArtifactCount: Int,
    filter: LogFilter,
    onFilterChange: (LogFilter) -> Unit,
    autoScrollEnabled: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    clearEnabled: Boolean,
    onCopyAll: () -> Unit,
    copyEnabled: Boolean,
    copied: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CollapsedHeight)
                .clickable(
                    onClick = onToggleExpanded,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(if (expanded) Res.string.collapse_output else Res.string.expand_output),
            modifier = Modifier.size(14.dp),
            tint = CerealTheme.colorScheme.contentTertiary,
        )

        CerealText(
            text = stringResource(Res.string.output),
            color = Color.White,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = TextUnit(0.02f, TextUnitType.Em),
                ),
        )

        TabSelector(tab = tab, onTabChange = onTabChange, unseenArtifactCount = unseenArtifactCount)

        if (tab == OutputTab.LOGS) {
            FilterPills(filter = filter, onFilterChange = onFilterChange)
        }

        Spacer(Modifier.weight(1f))

        if (tab == OutputTab.LOGS) {
            AutoScrollControl(
                enabled = autoScrollEnabled,
                onChange = onAutoScrollChange,
            )

            CerealSmallIconButton(
                onClick = onClear,
                enabled = clearEnabled,
                size = 28.dp,
                cornerRadius = 6.dp,
                modifier = Modifier.testTag(CLEAR_BUTTON_TEST_TAG),
            ) {
                Icon(
                    imageVector = Icons.Filled.CleaningServices,
                    contentDescription = stringResource(Res.string.clear),
                    modifier = Modifier.size(14.dp),
                    tint = if (clearEnabled) CerealTheme.colorScheme.contentSecondary else CerealTheme.colorScheme.contentSubtle,
                )
            }

            CerealSmallIconButton(
                onClick = onCopyAll,
                enabled = copyEnabled,
                size = 28.dp,
                cornerRadius = 6.dp,
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                    contentDescription = stringResource(Res.string.copy_all_logs),
                    modifier = Modifier.size(14.dp),
                    tint =
                        when {
                            copied -> Color.White
                            copyEnabled -> CerealTheme.colorScheme.contentSecondary
                            else -> CerealTheme.colorScheme.contentSubtle
                        },
                )
            }
        }
    }
}

private enum class OutputTab { LOGS, ARTIFACTS }

@Composable
private fun TabSelector(
    tab: OutputTab,
    onTabChange: (OutputTab) -> Unit,
    unseenArtifactCount: Int,
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
        FilterPill(
            label = stringResource(Res.string.output_tab_logs),
            selected = tab == OutputTab.LOGS,
            onClick = { onTabChange(OutputTab.LOGS) },
        )
        FilterPill(
            label = stringResource(Res.string.output_tab_artifacts),
            selected = tab == OutputTab.ARTIFACTS,
            onClick = { onTabChange(OutputTab.ARTIFACTS) },
            badgeCount = unseenArtifactCount,
        )
    }
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
    badgeCount: Int = 0,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CerealText(
                text = label,
                color = if (selected) Color.White else CerealTheme.colorScheme.contentTertiary,
                style =
                    CerealTypography.controlLabel.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
            )
            if (badgeCount > 0) {
                UnseenBadge(count = badgeCount)
            }
        }
    }
}

@Composable
private fun UnseenBadge(count: Int) {
    Box(
        modifier =
            Modifier
                .testTag(ARTIFACT_BADGE_TEST_TAG)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary)
                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        CerealText(
            // Cap at "9+": past a handful the exact count stops mattering and a fixed width keeps the
            // tab strip from reflowing. The precise number is visible in the list once opened.
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            style =
                CerealTypography.controlLabel.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                ),
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
private fun ResizeDragHandle(
    enabled: Boolean,
    borderColor: Color,
    onDrag: (dragAmountPx: Float) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(borderColor),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .offset(y = (-3.5).dp)
                    .then(
                        if (enabled) {
                            Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                        } else {
                            Modifier
                        },
                    ).pointerInput(enabled) {
                        if (enabled) {
                            detectVerticalDragGestures { _, dragAmount ->
                                onDrag(dragAmount)
                            }
                        }
                    },
        )
    }
}

@Composable
private fun LogLine(event: LoggingEvent) {
    val glyph = glyphFor(event.priority)
    val glyphColor = glyphColorFor(event.priority)
    val timestamp = remember(event.timestamp) { TimestampFormatter.format(event.timestamp) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CerealText(
            text = timestamp,
            color = CerealTheme.colorScheme.contentSubtle,
            maxLines = 1,
            style = LogLineStyle,
        )
        CerealText(
            text = glyph,
            color = glyphColor,
            style = LogLineStyle.copy(fontWeight = FontWeight.Bold),
        )
        CerealText(
            text = event.message,
            color = CerealTheme.colorScheme.contentSecondary,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            style = LogLineStyle,
        )
    }
}

private val LogLineStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )

private fun formatLogLine(event: LoggingEvent): String {
    val timestamp = TimestampFormatter.format(event.timestamp)
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

@Composable
private fun ArtifactList(
    artifacts: List<Artifact>,
    onDownloadArtifact: (Artifact) -> Unit,
) {
    if (artifacts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CerealText(
                text = stringResource(Res.string.no_artifacts),
                color = CerealTheme.colorScheme.contentSubtle,
                style = CerealTypography.controlLabel,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 14.dp),
        ) {
            items(artifacts, key = { it.id }) { artifact ->
                ArtifactRow(artifact = artifact, onDownload = { onDownloadArtifact(artifact) })
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState),
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ArtifactRow(
    artifact: Artifact,
    onDownload: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = artifact.name,
                color = CerealTheme.colorScheme.contentSecondary,
                maxLines = 1,
                style = LogLineStyle,
            )
            CerealText(
                text = "${formatBytes(artifact.sizeBytes)} · ${TimestampFormatter.format(Date(artifact.createdAt.toEpochMilliseconds()))}",
                color = CerealTheme.colorScheme.contentSubtle,
                maxLines = 1,
                style = LogLineStyle.copy(fontSize = 11.sp),
            )
        }
        CerealSmallIconButton(
            onClick = onDownload,
            size = 28.dp,
            cornerRadius = 6.dp,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(Res.string.download_artifact),
                modifier = Modifier.size(14.dp),
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

private object TimestampFormatter {
    // DateTimeFormatter is thread-safe, unlike SimpleDateFormat.
    private val formatter: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .withZone(ZoneId.systemDefault())

    fun format(date: java.util.Date): String = formatter.format(date.toInstant())
}
