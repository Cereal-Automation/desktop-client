package com.cereal.client.presentation.proxy.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_auth_subtitle
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_back
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_button
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_button_verifying
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_cancel
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_continue
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_keychain_note
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_provider_subtitle
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_soon
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_step_connect
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_step_provider
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_title
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_token_hint
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_token_label
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_token_placeholder
import com.cereal_automation.cereal_client.generated.resources.proxies_connect_verifying
import org.jetbrains.compose.resources.stringResource

private val DialogWidth = 560.dp

/**
 * The connect/replace wizard modal: a Provider step (picker) and a Connect step (masked token field
 * with inline validation feedback). Proxy syncing is out of scope for this slice, so the wizard ends on
 * a successful connect (the section refreshes and the modal closes).
 */
@Composable
fun ConnectProviderDialog(
    state: ConnectWizardState.Open,
    options: List<ProviderOptionUiModel>,
    onClose: () -> Unit,
    onProviderSelected: (com.cereal.client.domain.model.proxy.ProxyVendor) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onTokenChanged: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onConnect: () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
        onDismissRequest = onClose,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .width(DialogWidth)
                        .border(1.dp, CerealTheme.colorScheme.borderDark, RoundedCornerShape(14.dp))
                        .background(CerealTheme.colorScheme.cardDark, RoundedCornerShape(14.dp)),
            ) {
                val selected = options.firstOrNull { it.provider == state.selectedProvider }
                Header(selected, state.step, onClose)
                Stepper(state.step)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    when (state.step) {
                        ConnectWizardStep.PROVIDER -> ProviderStep(options, state.selectedProvider, onProviderSelected)
                        ConnectWizardStep.CONNECT -> ConnectStep(state, selected, onTokenChanged, onToggleTokenVisibility, onConnect)
                    }
                }
                Footer(state, onClose, onBack, onContinue, onConnect)
            }
        }
    }
}

@Composable
private fun Header(
    selected: ProviderOptionUiModel?,
    step: ConnectWizardStep,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selected != null) {
            BrandTile(selected.brandMark, selected.brandColorArgb, 40.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = if (step == ConnectWizardStep.PROVIDER || selected == null) stringResource(Res.string.proxies_connect_title) else selected.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            CerealText(
                text =
                    if (step == ConnectWizardStep.PROVIDER) {
                        stringResource(Res.string.proxies_connect_provider_subtitle)
                    } else {
                        stringResource(Res.string.proxies_connect_auth_subtitle)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
        }
        CerealIconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.proxies_connect_cancel), tint = CerealTheme.colorScheme.contentTertiary)
        }
    }
}

@Composable
private fun Stepper(step: ConnectWizardStep) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.backgroundDark)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepChip(1, stringResource(Res.string.proxies_connect_step_provider), active = true, done = step == ConnectWizardStep.CONNECT)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .height(1.dp)
                    .background(CerealTheme.colorScheme.border),
        )
        StepChip(2, stringResource(Res.string.proxies_connect_step_connect), active = step == ConnectWizardStep.CONNECT, done = false)
    }
}

@Composable
private fun StepChip(
    index: Int,
    label: String,
    active: Boolean,
    done: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(
                        if (active || done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            } else {
                CerealText(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (active) Color.White else CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
        CerealText(
            text = label,
            style = CerealTypography.controlLabel,
            color = if (active || done) MaterialTheme.colorScheme.onBackground else CerealTheme.colorScheme.contentTertiary,
        )
    }
}

@Composable
private fun ProviderStep(
    options: List<ProviderOptionUiModel>,
    selectedProvider: com.cereal.client.domain.model.proxy.ProxyVendor,
    onProviderSelected: (com.cereal.client.domain.model.proxy.ProxyVendor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            ProviderOptionRow(option, option.provider == selectedProvider, onProviderSelected)
        }
        KeychainNote()
    }
}

@Composable
private fun ProviderOptionRow(
    option: ProviderOptionUiModel,
    selected: Boolean,
    onProviderSelected: (com.cereal.client.domain.model.proxy.ProxyVendor) -> Unit,
) {
    val borderColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            else -> CerealTheme.colorScheme.border
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                    RoundedCornerShape(10.dp),
                ).then(if (option.available) Modifier.clickable { onProviderSelected(option.provider) } else Modifier)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandTile(option.brandMark, option.brandColorArgb, 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            CerealText(
                text = option.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (option.available) MaterialTheme.colorScheme.onBackground else CerealTheme.colorScheme.contentTertiary,
            )
            CerealText(text = option.tagline, style = MaterialTheme.typography.bodySmall, color = CerealTheme.colorScheme.contentTertiary)
        }
        if (!option.available) {
            SoonTag()
        } else if (selected) {
            Box(
                modifier = Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun SoonTag() {
    Box(
        modifier =
            Modifier
                .border(1.dp, CerealTheme.colorScheme.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        CerealText(text = stringResource(Res.string.proxies_connect_soon), style = CerealTypography.statusChipLabel, color = CerealTheme.colorScheme.contentSubtle)
    }
}

@Composable
private fun ConnectStep(
    state: ConnectWizardState.Open,
    selected: ProviderOptionUiModel?,
    onTokenChanged: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onConnect: () -> Unit,
) {
    val providerName = selected?.name ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary, modifier = Modifier.size(14.dp))
            CerealText(text = stringResource(Res.string.proxies_connect_token_label), style = CerealTypography.controlLabel, color = MaterialTheme.colorScheme.onBackground)
        }

        val isError = state.tokenStatus == ConnectTokenStatus.ERROR
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError,
            enabled = state.tokenStatus != ConnectTokenStatus.VALIDATING,
            placeholder = { CerealText(stringResource(Res.string.proxies_connect_token_placeholder), color = CerealTheme.colorScheme.contentTertiary) },
            visualTransformation = if (state.showToken) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = {
                when (state.tokenStatus) {
                    ConnectTokenStatus.VALIDATING -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }

                    ConnectTokenStatus.ERROR -> {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }

                    ConnectTokenStatus.IDLE -> {
                        CerealIconButton(onClick = onToggleTokenVisibility) {
                            Icon(
                                if (state.showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = CerealTheme.colorScheme.contentTertiary,
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = CerealTheme.colorScheme.border,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
        )

        when {
            state.tokenStatus == ConnectTokenStatus.ERROR && state.errorMessage != null -> {
                InlineMessage(state.errorMessage, MaterialTheme.colorScheme.error)
            }

            state.tokenStatus == ConnectTokenStatus.VALIDATING -> {
                InlineMessage(stringResource(Res.string.proxies_connect_verifying, providerName), CerealTheme.colorScheme.contentTertiary)
            }

            else -> {
                InlineMessage(stringResource(Res.string.proxies_connect_token_hint, providerName), CerealTheme.colorScheme.contentTertiary)
            }
        }

        KeychainNote()
    }
}

@Composable
private fun InlineMessage(
    text: String,
    color: Color,
) {
    CerealText(text = text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun KeychainNote() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Security, contentDescription = null, tint = CerealTheme.colorScheme.contentTertiary, modifier = Modifier.size(15.dp))
        CerealText(
            text = stringResource(Res.string.proxies_connect_keychain_note),
            style = MaterialTheme.typography.bodySmall,
            color = CerealTheme.colorScheme.contentTertiary,
        )
    }
}

@Composable
private fun Footer(
    state: ConnectWizardState.Open,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onConnect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.backgroundDark)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.step) {
            ConnectWizardStep.PROVIDER -> {
                CerealTextButton(onClick = onClose, type = CerealButtonType.Surface) {
                    CerealText(stringResource(Res.string.proxies_connect_cancel), style = CerealTypography.controlLabel)
                }
            }

            ConnectWizardStep.CONNECT -> {
                CerealTextButton(onClick = if (state.replaceMode) onClose else onBack, type = CerealButtonType.Surface) {
                    CerealText(
                        if (state.replaceMode) stringResource(Res.string.proxies_connect_cancel) else stringResource(Res.string.proxies_connect_back),
                        style = CerealTypography.controlLabel,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        when (state.step) {
            ConnectWizardStep.PROVIDER -> {
                CerealButton(onClick = onContinue, text = stringResource(Res.string.proxies_connect_continue), type = CerealButtonType.Primary)
            }

            ConnectWizardStep.CONNECT -> {
                CerealButton(
                    onClick = onConnect,
                    text =
                        if (state.tokenStatus == ConnectTokenStatus.VALIDATING) {
                            stringResource(Res.string.proxies_connect_button_verifying)
                        } else {
                            stringResource(Res.string.proxies_connect_button)
                        },
                    enabled = state.token.isNotBlank() && state.tokenStatus != ConnectTokenStatus.VALIDATING,
                    type = CerealButtonType.Primary,
                )
            }
        }
    }
}
