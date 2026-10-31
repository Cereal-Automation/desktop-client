package com.cereal.client.presentation.view

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.theme.cerealColors
import com.cereal.client.presentation.view.fields.FormField
import com.cereal.client.presentation.view.fields.state.TextFieldState

// All dimensions below are the design CSS px × 1.25 (project's px→dp/sp ratio).
private val SectionBorder = cerealColors.border

enum class SettingsButtonVariant { Flat, Primary, Ghost }

@Composable
fun SettingsSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SettingsButtonVariant = SettingsButtonVariant.Flat,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val contentPadding = if (leading != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding
    val content: @Composable RowScope.() -> Unit = {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        }
        CerealText(text = text)
    }
    when (variant) {
        SettingsButtonVariant.Flat -> {
            CerealButton(onClick, modifier, enabled = enabled, type = CerealButtonType.Neutral, contentPadding = contentPadding, content = content)
        }

        SettingsButtonVariant.Primary -> {
            CerealButton(onClick, modifier, enabled = enabled, type = CerealButtonType.Primary, contentPadding = contentPadding, content = content)
        }

        SettingsButtonVariant.Ghost -> {
            CerealOutlinedButton(onClick, modifier, enabled, type = CerealButtonType.Neutral, contentPadding = contentPadding, content = content)
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    noBorder: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (noBorder) {
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = SectionBorder,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f,
                            )
                        }.padding(top = 5.dp, bottom = 15.dp), // 4/12 × 1.25
            ) {
                CerealText(
                    text = title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 0.3.sp,
                            color = Color.White,
                        ),
                )
            }
            // 6px × 1.25
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                content()
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp)) // 12px × 1.25
                    .border(1.dp, SectionBorder, RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(CerealTheme.colorScheme.surfaceHeader)
                        .padding(horizontal = 22.dp, vertical = 18.dp), // 18/14 × 1.25
            ) {
                CerealText(
                    text = title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 0.3.sp,
                            color = Color.White,
                        ),
                )
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    description: String? = null,
    isLoading: Boolean = false,
    showNotificationDot: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Body2Text(title)
                if (showNotificationDot) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            description?.let {
                ClickableUrlText(it, color = CerealTheme.colorScheme.contentTertiary)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.End,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                content()
            }
        }
    }
}

@Composable
fun SettingsRowIcon(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(35.dp) // 28px × 1.25
                .clip(RoundedCornerShape(8.dp)) // 6px × 1.25
                .background(CerealTheme.colorScheme.inputBackground)
                .border(1.dp, cerealColors.border, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun SettingsSectionRow(
    title: String,
    description: String,
    isLoading: Boolean = false,
    showNotificationDot: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val dividerColor = CerealTheme.colorScheme.divider
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f,
                    )
                }.heightIn(min = 70.dp) // 56px × 1.25
                .padding(horizontal = 22.dp, vertical = 18.dp), // 18/14 × 1.25
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsRowIcon(content = icon)
            Spacer(modifier = Modifier.width(20.dp)) // 16px × 1.25
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CerealText(
                    text = title,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        ),
                )
                if (showNotificationDot) {
                    Spacer(modifier = Modifier.width(10.dp)) // 8px × 1.25
                    Box(
                        modifier =
                            Modifier
                                .size(16.dp) // 13px × 1.25
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(9.dp) // 7px × 1.25
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(4.dp)) // 3px × 1.25
            ClickableUrlText(
                text = description,
                color = CerealTheme.colorScheme.contentTertiary,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp, // 16px × 1.25
                    ),
            )
        }
        Spacer(modifier = Modifier.width(20.dp)) // 16px × 1.25
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            trailing()
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSectionRow(title = title, description = description) {
        CerealToggle(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    description: String,
    actionText: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    showNotificationDot: Boolean = false,
    isPrimary: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    SettingsSectionRow(
        title = title,
        description = description,
        isLoading = isLoading,
        showNotificationDot = showNotificationDot,
        icon = icon,
    ) {
        SettingsSmallButton(
            text = actionText,
            onClick = onClick,
            enabled = enabled,
            variant = if (isPrimary) SettingsButtonVariant.Primary else SettingsButtonVariant.Flat,
        )
    }
}

@Composable
fun ConfigurationPanel(content: @Composable ColumnScope.() -> Unit) {
    val dividerColor = CerealTheme.colorScheme.divider
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.surfaceSunken)
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f,
                    )
                }.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp), // 18/18/18/16 × 1.25
        verticalArrangement = Arrangement.spacedBy(18.dp), // 14px × 1.25
    ) {
        content()
    }
}

@Composable
fun TestButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        SettingsSmallButton(
            text = text,
            onClick = onClick,
            variant = SettingsButtonVariant.Ghost,
        )
    }
}

internal val InputBorder = cerealColors.border

@Composable
fun SettingsFieldRow(
    label: String,
    description: String,
    help: (@Composable () -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp), // 16px × 1.25
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CerealText(
                    text = label,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                )
                if (help != null) {
                    Spacer(modifier = Modifier.width(10.dp)) // 8px × 1.25
                    help()
                }
            }
            Spacer(modifier = Modifier.size(4.dp)) // 3px × 1.25
            CerealText(
                text = description,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp, // 16.5px × 1.25 ≈ 20
                        color = CerealTheme.colorScheme.contentTertiary,
                    ),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            control()
        }
    }
}

@Composable
fun SettingsTextInput(
    state: TextFieldState<*>,
    placeholder: String = "",
    enabled: Boolean = true,
    isPassword: Boolean = false,
) {
    FormField(textError = state.error) {
        CerealTextField(
            value = state.text,
            onValueChange = { state.onValueChange(it) },
            placeholder = placeholder.ifEmpty { null },
            isError = state.showErrors(),
            enabled = enabled,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            // Validators run on focus change (see TextFieldState); without this they never fire.
            modifier = Modifier.fillMaxWidth().onFocusChanged { state.onFocusChange(it.isFocused) },
        )
    }
}
