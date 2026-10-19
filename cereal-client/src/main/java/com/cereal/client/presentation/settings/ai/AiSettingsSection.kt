package com.cereal.client.presentation.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSectionCard
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai
import com.cereal_automation.cereal_client.generated.resources.ai_connection_connected
import com.cereal_automation.cereal_client.generated.resources.ai_connection_testing
import com.cereal_automation.cereal_client.generated.resources.ai_remove
import com.cereal_automation.cereal_client.generated.resources.ai_test_connection
import com.cereal_automation.cereal_client.generated.resources.save
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

/** Settings → AI: pick a preset, fill its fields, then Save (no test needed) or Remove the config. */
@Composable
fun AiSettingsSection(vm: AiSettingsViewModel = rememberAiSettingsViewModel()) {
    val hasPreset = vm.form.preset.value != null
    SettingsSectionCard(title = stringResource(Res.string.ai)) {
        LlmPresetForm(vm.form, onSelectPreset = vm::selectPreset)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = vm.connectionStatus.value
            CerealText(
                text = status.text(),
                modifier = Modifier.weight(1f),
                color = if (status is ConnectionStatus.Failed) MaterialTheme.colorScheme.error else CerealTheme.colorScheme.contentTertiary,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            )
            if (hasPreset) {
                SettingsSmallButton(text = stringResource(Res.string.ai_remove), onClick = vm::remove, variant = SettingsButtonVariant.Ghost)
                SettingsSmallButton(
                    text = stringResource(Res.string.ai_test_connection),
                    onClick = vm::testConnection,
                    enabled = status != ConnectionStatus.Testing,
                )
                SettingsSmallButton(
                    text = stringResource(Res.string.save),
                    onClick = vm::save,
                    variant = SettingsButtonVariant.Primary,
                )
            }
        }
    }
}

@Composable
fun rememberAiSettingsViewModel(): AiSettingsViewModel {
    val coroutineScope = rememberCoroutineScope()
    return remember {
        KoinJavaComponent.get(AiSettingsViewModel::class.java, parameters = { parametersOf(coroutineScope) })
    }
}

@Composable
private fun ConnectionStatus.text(): String =
    when (this) {
        ConnectionStatus.Idle -> ""
        ConnectionStatus.Testing -> stringResource(Res.string.ai_connection_testing)
        is ConnectionStatus.Connected -> pluralStringResource(Res.plurals.ai_connection_connected, count, count)
        is ConnectionStatus.Failed -> message.text()
    }
