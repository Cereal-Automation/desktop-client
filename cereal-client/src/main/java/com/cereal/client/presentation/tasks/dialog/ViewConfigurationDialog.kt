package com.cereal.client.presentation.tasks.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.Body2Text
import com.cereal.client.presentation.view.CaptionText
import com.cereal.client.presentation.view.Dialog
import com.cereal.client.presentation.view.SettingsItem
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.configuration_for
import com.cereal_automation.cereal_client.generated.resources.version_code
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
fun ViewConfigurationDialog(
    scriptPackageInstance: ScriptPackageInstance,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    viewModel: ViewConfigurationViewModel =
        remember {
            KoinJavaComponent.get(
                ViewConfigurationViewModel::class.java,
                parameters = { parametersOf(coroutineScope, scriptPackageInstance) },
            )
        },
    onDismissRequest: (() -> Unit),
) {
    Dialog(
        title = stringResource(Res.string.configuration_for, scriptPackageInstance.definition.manifest.name),
        modifier =
            Modifier
                .width(1200.dp)
                .height(650.dp)
                .padding(0.dp),
        onDismissRequest = onDismissRequest,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                viewModel.scriptConfigurationItems.value
                    .filter { it.value != null }
                    .forEach { configItem ->
                        Spacer(Modifier.height(16.dp))
                        SettingsItem(
                            title = configItem.definition.name,
                            description = configItem.definition.description,
                        ) {
                            Body2Text(configItem.value?.raw.toString())
                        }
                    }

                scriptPackageInstance.notificationOverrides?.let { overrides ->
                    NotificationOverridesSummary(overrides)
                }
            }

            CaptionText(
                "${stringResource(Res.string.version_code)}: ${scriptPackageInstance.definition.manifest.versionCode}",
                color = CerealTheme.colorScheme.contentTertiary,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}
