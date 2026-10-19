package com.cereal.client.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.InputBg
import com.cereal.client.presentation.view.InputBorder
import com.cereal.client.presentation.view.InputPlaceholder
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.home_hide
import com.cereal_automation.cereal_client.generated.resources.home_optional
import com.cereal_automation.cereal_client.generated.resources.home_show
import org.jetbrains.compose.resources.stringResource

// Building blocks of Home's setup steps, sized after the design's home.css.

internal enum class StepBadge { Number, Done, Locked }

private val StepShape = RoundedCornerShape(12.dp)
private val ControlShape = RoundedCornerShape(8.dp)

/** Left indent of a step's body: past the badge, under the title. */
internal val StepBodyPadding = Modifier.padding(start = 62.dp, end = 22.dp, bottom = 22.dp)

internal val helpStyle: TextStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp)

/** A numbered setup step: badge, title and [subtitle] in the head, then the optional [body]. */
@Composable
internal fun HomeStep(
    number: Int,
    title: String,
    subtitle: @Composable () -> Unit,
    badge: StepBadge = StepBadge.Number,
    trailing: @Composable () -> Unit = {},
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(StepShape)
                .background(CerealTheme.colorScheme.cardDark)
                .border(1.dp, CerealTheme.colorScheme.border, StepShape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepNumber(number, badge)
            Column(modifier = Modifier.weight(1f)) {
                CerealText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
                    color = if (badge == StepBadge.Locked) CerealTheme.colorScheme.contentTertiary else MaterialTheme.colorScheme.onSurface,
                )
                subtitle()
            }
            trailing()
        }
        body?.let { Column(content = it) }
    }
}

@Composable
private fun StepNumber(
    number: Int,
    badge: StepBadge,
) {
    val color =
        when (badge) {
            StepBadge.Number -> MaterialTheme.colorScheme.primary
            StepBadge.Done -> CerealTheme.colorScheme.success
            StepBadge.Locked -> CerealTheme.colorScheme.contentSubtle
        }
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (badge == StepBadge.Locked) Color.Transparent else color.copy(alpha = BADGE_TINT))
                .border(1.dp, if (badge == StepBadge.Locked) CerealTheme.colorScheme.borderDark else color.copy(alpha = BADGE_BORDER), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (badge == StepBadge.Done) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        } else {
            CerealText(number.toString(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
internal fun StepSubtitle(text: String) {
    CerealText(
        text = text,
        modifier = Modifier.padding(top = 1.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        color = CerealTheme.colorScheme.contentTertiary,
    )
}

/** A label over its control, with an optional [trailing] (e.g. a "Get a key" link) pushed right. */
@Composable
internal fun StepField(
    label: String,
    optional: Boolean = false,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            CerealText(label, style = style, color = CerealTheme.colorScheme.contentSecondary)
            if (optional) {
                CerealText(stringResource(Res.string.home_optional), style = style.copy(fontWeight = FontWeight.Medium), color = CerealTheme.colorScheme.contentSubtle)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
        content()
    }
}

/**
 * The design's filled input: 38px single-line, 92px min for multi-line. [isPassword] masks the text and
 * adds a Show/Hide toggle once something is typed; [error] reddens the border (its message is the host's).
 */
@Composable
internal fun HomeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val border =
        when {
            isError -> MaterialTheme.colorScheme.primary.copy(alpha = ERROR_BORDER)
            focused -> CerealTheme.colorScheme.borderDark
            else -> InputBorder
        }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = if (singleLine) 13.5.sp else 14.sp, color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        modifier =
            modifier
                .then(if (singleLine) Modifier.height(38.dp) else Modifier.heightIn(min = 92.dp))
                .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(InputBg, ControlShape)
                        .border(1.dp, border, ControlShape)
                        .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 12.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) CerealText(placeholder, style = textStyle.copy(color = InputPlaceholder))
                    inner()
                }
                if (isPassword && value.isNotEmpty()) {
                    CerealText(
                        text = stringResource(if (revealed) Res.string.home_hide else Res.string.home_show),
                        modifier = Modifier.clickable(role = Role.Button) { revealed = !revealed },
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = CerealTheme.colorScheme.contentTertiary,
                    )
                }
            }
        },
    )
}

/** Help line under a field; red when it carries an error. */
@Composable
internal fun FieldHelp(
    text: String,
    isError: Boolean = false,
) {
    CerealText(text, style = helpStyle, color = if (isError) MaterialTheme.colorScheme.primary else CerealTheme.colorScheme.contentSubtle)
}

/** The tinted info box under the form. */
@Composable
internal fun HomeNote(content: @Composable () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ConfigBg, ControlShape)
                .border(1.dp, Divider, ControlShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(14.dp), tint = CerealTheme.colorScheme.contentSubtle)
        content()
    }
}

/** Segmented control: a well holding [options], the selected one raised. */
@Composable
internal fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .background(InputBg, ControlShape)
                .border(1.dp, InputBorder, ControlShape)
                .padding(3.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            val shape = RoundedCornerShape(6.dp)
            Box(
                modifier =
                    Modifier
                        .height(30.dp)
                        .clip(shape)
                        .then(if (isSelected) Modifier.background(FlatBg).border(1.dp, CerealTheme.colorScheme.borderDark, shape) else Modifier)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                CerealText(
                    option,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
    }
}

internal val FlatBg = Color(0xFF232323)
internal val Divider = Color(0xFF232323)
private val ConfigBg = Color(0xFF121212)
private const val BADGE_TINT = 0.12f
private const val BADGE_BORDER = 0.3f
private const val ERROR_BORDER = 0.6f
