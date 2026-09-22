package com.cereal.client.presentation.view.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.theme.cerealColors
import com.cereal.client.presentation.view.CerealText

val PaneTablePadding = 25.dp
val PaneTableToolbarMinHeight = 70.dp
val PaneTableCornerRadius = 10.dp
val PaneTableRowMinHeight = 52.dp
val PaneTableRowPaddingHorizontal = 18.dp
val PaneTableRowPaddingVertical = 12.dp
val PaneTableActionAreaWidth = 100.dp
val PaneTableIconButtonSize = 32.dp
val PaneTableIconButtonCornerRadius = 6.dp
val PaneTableIconButtonGlyphSize = 16.dp

val PaneTableMetaIconColor = cerealColors.contentSubtle
val PaneTableRowDividerColor = Color(0xFF232323)
private val PaneTableHeaderBackground = Color(0xFF1A1A1A)
private val EncryptedBannerCircleSize = 44.dp
private val EncryptedBannerGlyphSize = 18.dp

data class PaneTableColumn(
    val title: String,
    val weight: Float,
)

/**
 * Full-height pane container with the standard background. When [title] is provided, a
 * [PaneToolbar] and divider are rendered above [content]; otherwise [content] takes the
 * full surface (used by drill-down views that provide their own header).
 */
@Composable
fun PaneScreen(
    title: String? = null,
    crumb: String? = null,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (title != null) {
            PaneToolbar(title = title, crumb = crumb, actions = toolbarActions)
            HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)
        }
        content()
    }
}

@Composable
fun PaneTableCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = PaneTablePadding, vertical = 16.dp)
                .clip(RoundedCornerShape(PaneTableCornerRadius))
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(PaneTableCornerRadius))
                .background(CerealTheme.colorScheme.cardDark),
    ) {
        content()
    }
}

@Composable
fun PaneTableHeader(
    columns: List<PaneTableColumn>,
    actionAreaWidth: androidx.compose.ui.unit.Dp = PaneTableActionAreaWidth,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PaneTableHeaderBackground)
                .padding(horizontal = PaneTableRowPaddingHorizontal, vertical = PaneTableRowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        columns.forEach { column ->
            CerealText(
                text = column.title.uppercase(),
                style =
                    CerealTypography.sectionLabel.copy(
                        fontSize = 11.sp,
                        letterSpacing = TextUnit(0.1f, TextUnitType.Em),
                    ),
                color = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.weight(column.weight),
            )
        }
        Spacer(modifier = Modifier.width(actionAreaWidth))
    }
    HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)
}

@Composable
fun PaneTableRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val clickModifier =
        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PaneTableRowMinHeight)
                .then(clickModifier)
                .padding(horizontal = PaneTableRowPaddingHorizontal, vertical = PaneTableRowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PaneTableRowDivider() {
    HorizontalDivider(thickness = 1.dp, color = PaneTableRowDividerColor)
}

@Composable
fun PaneIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(PaneTableIconButtonSize)
                .clip(RoundedCornerShape(PaneTableIconButtonCornerRadius))
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(PaneTableIconButtonCornerRadius))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CerealTheme.colorScheme.contentSecondary,
            modifier = Modifier.size(PaneTableIconButtonGlyphSize),
        )
    }
}

@Composable
fun PanePrimaryActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .heightIn(min = PaneTableIconButtonSize)
                .clip(RoundedCornerShape(PaneTableIconButtonCornerRadius))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(PaneTableIconButtonGlyphSize),
        )
        CerealText(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            color = Color.White,
        )
    }
}

@Composable
fun PaneToolbar(
    title: String,
    crumb: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PaneTableToolbarMinHeight)
                .padding(horizontal = PaneTablePadding, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CerealText(
            text = title,
            style = CerealTypography.paneTitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (crumb != null) {
            CerealText(
                text = crumb,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Header for a drill-down details pane: back arrow + title + optional crumb + trailing action slot.
 */
@Composable
fun PaneDetailsHeader(
    title: String,
    crumb: String?,
    backContentDescription: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PaneTableToolbarMinHeight)
                .padding(horizontal = PaneTablePadding, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaneIconActionButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = backContentDescription,
            onClick = onBack,
        )
        CerealText(
            text = title,
            style = CerealTypography.paneTitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (crumb != null) {
            CerealText(
                text = crumb,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Leading cell of a [PaneTableRow]: a meta icon + bold name in a weighted column.
 */
@Composable
fun RowScope.PaneTableNameCell(
    icon: ImageVector,
    text: String,
    weight: Float,
) {
    Row(
        modifier = Modifier.weight(weight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PaneTableMetaIconColor,
            modifier = Modifier.size(PaneTableIconButtonGlyphSize),
        )
        CerealText(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * Trailing action area of a [PaneTableRow], sized to align with the header's reserved slot.
 */
@Composable
fun PaneTableActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.width(PaneTableActionAreaWidth),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private val PaneMetaStripDividerColor = Color(0xFF232323)

/**
 * Horizontal strip of label/value pairs, shown under a [PaneDetailsHeader] to surface
 * summary stats for a drill-down view (e.g. endpoint counts on a proxy pool, record
 * counts on a dataset group). Renders as a bordered, rounded card sitting inside the
 * pane's horizontal padding. Place [PaneMetaCell]s as children, separated by
 * [PaneMetaDivider]s.
 */
@Composable
fun PaneMetaStrip(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PaneTablePadding, end = PaneTablePadding, top = 14.dp)
                .clip(RoundedCornerShape(PaneTableCornerRadius))
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(PaneTableCornerRadius))
                .background(CerealTheme.colorScheme.cardDark)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        content = content,
    )
}

@Composable
fun PaneMetaCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
    emphasis: Boolean = true,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CerealText(
            text = label.uppercase(),
            style =
                CerealTypography.sectionLabel.copy(
                    fontSize = 10.sp,
                    letterSpacing = TextUnit(0.1f, TextUnitType.Em),
                ),
            color = CerealTheme.colorScheme.contentSubtle,
        )
        if (emphasis) {
            CerealText(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                color = valueColor,
            )
        } else {
            CerealText(
                text = value,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                    ),
                color = CerealTheme.colorScheme.contentSecondary,
            )
        }
    }
}

data class SchemaColumn(
    val label: String,
    val type: String,
)

/**
 * Compact strip listing each column in a dataset's schema as a labeled pill, shown
 * between the meta strip and the records table on the dataset group detail page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchemaStrip(
    label: String,
    columns: List<SchemaColumn>,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PaneTablePadding)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(8.dp))
                .background(SchemaStripBackground)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.TableChart,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(12.dp),
            )
            CerealText(
                text = label.uppercase(),
                style =
                    CerealTypography.sectionLabel.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = TextUnit(0.1f, TextUnitType.Em),
                    ),
                color = CerealTheme.colorScheme.contentSubtle,
            )
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            columns.forEach { column ->
                SchemaPill(column)
            }
        }
    }
}

@Composable
fun PaneMetaDivider() {
    Box(
        modifier =
            Modifier
                .height(32.dp)
                .width(1.dp)
                .background(PaneMetaStripDividerColor),
    )
}

@Composable
private fun SchemaPill(column: SchemaColumn) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(SchemaPillBackground)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CerealText(
            text = column.label,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        CerealText(
            text = column.type.uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = TextUnit(0.04f, TextUnitType.Em),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            color = CerealTheme.colorScheme.warning,
        )
    }
}

private val SchemaStripBackground = Color(0xFF1A1A1A)
private val SchemaPillBackground = Color(0xFF232323)

@Composable
fun EncryptedBanner(
    title: String,
    subtitle: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PaneTablePadding, end = PaneTablePadding, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(EncryptedBannerCircleSize)
                    .clip(CircleShape)
                    .background(CerealTheme.colorScheme.cardDark)
                    .border(1.dp, CerealTheme.colorScheme.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(EncryptedBannerGlyphSize),
            )
        }
        Column {
            CerealText(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            CerealText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
    }
}
