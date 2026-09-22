package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.marketplace.scriptCapacityText
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationView
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealFloatingActionButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.Dialog
import com.cereal.client.presentation.view.VerticalDivider
import com.cereal.client.presentation.view.button.SyncIconButton
import com.cereal.client.presentation.view.group.GroupedListView
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.ic_play
import com.cereal_automation.cereal_client.generated.resources.installed_scripts
import com.cereal_automation.cereal_client.generated.resources.no_installed_scripts
import com.cereal_automation.cereal_client.generated.resources.notification_warning_continue
import com.cereal_automation.cereal_client.generated.resources.notification_warning_message
import com.cereal_automation.cereal_client.generated.resources.notification_warning_title
import com.cereal_automation.cereal_client.generated.resources.script_capacity_label
import com.cereal_automation.cereal_client.generated.resources.search_marketplace_for_scripts
import com.cereal_automation.cereal_client.generated.resources.select_script_to_configure
import com.cereal_automation.cereal_client.generated.resources.start_script
import com.cereal_automation.cereal_client.generated.resources.start_task
import com.cereal_automation.cereal_client.generated.resources.warning
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
fun ScriptSelectionDialog(
    scriptPackageGroup: ScriptPackageGroup,
    initialScriptPackageInstance: ScriptPackageInstance? = null,
    initialPublicIdentifier: String? = null,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    viewModel: ScriptSelectionViewModel =
        remember {
            KoinJavaComponent.get(
                ScriptSelectionViewModel::class.java,
                parameters = { parametersOf(coroutineScope, scriptPackageGroup, initialScriptPackageInstance, initialPublicIdentifier) },
            )
        },
    onScriptInstanceCreated: ((scriptPackageInstance: ScriptPackageInstance) -> Unit),
    onDismissRequest: (() -> Unit),
    onNavigateToMarketplace: () -> Unit,
) {
    val scriptInstanceStarted = viewModel.scriptInstanceStarted.value
    LaunchedEffect(scriptInstanceStarted) {
        scriptInstanceStarted?.let {
            onScriptInstanceCreated(it)
            onDismissRequest()
        }
    }

    Dialog(
        title = stringResource(Res.string.installed_scripts),
        modifier =
            Modifier
                .width(1200.dp)
                .height(650.dp)
                .padding(0.dp),
        onDismissRequest = onDismissRequest,
    ) {
        val fab = @Composable {
            val updateRequired = viewModel.selectedScriptUpdateRequired.value
            CerealFloatingActionButton(
                onClick = {
                    if (viewModel.configurationViewState.value !is ScriptSelectionState.Config.Selected) {
                        return@CerealFloatingActionButton
                    }
                    if (updateRequired) {
                        return@CerealFloatingActionButton
                    }
                    if (viewModel.validate()) {
                        viewModel.launchScript()
                    }
                },
                containerColor =
                    if (updateRequired) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            ) {
                if (viewModel.scriptLaunchLoadState.value is LoadState.Loading) {
                    CerealCircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painterResource(Res.drawable.ic_play),
                        contentDescription = stringResource(Res.string.start_task),
                    )
                }
            }
        }

        Scaffold(
            bottomBar = {
                BottomAppBar(
                    actions = {
                        Row(Modifier.weight(0.35f)) {
                            SyncIconButton(viewModel.scriptRefreshState.value) {
                                viewModel.refreshScriptList()
                            }
                        }
                    },
                    floatingActionButton = fab,
                )
            },
        ) { innerPadding ->
            val overviewViewState by remember { viewModel.overviewViewState }

            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.weight(0.35f)) {
                    when (val state = overviewViewState) {
                        is ScriptSelectionState.Overview.Filled -> {
                            GroupedListView(
                                viewModel = state.viewModel,
                            )
                        }

                        is ScriptSelectionState.Overview.Empty -> {
                            NoScriptsAvailableView(onNavigateToMarketplace)
                        }
                    }
                }

                VerticalDivider()

                Column(Modifier.fillMaxHeight().weight(0.65f)) {
                    when (val viewState = viewModel.configurationViewState.value) {
                        is ScriptSelectionState.Config.None -> {
                            NoItemSelectedView()
                        }

                        is ScriptSelectionState.Config.Selected -> {
                            // Surface the entitled record cap before the run is configured/started;
                            // scriptCapacityText returns null for scripts with no capacity concept.
                            scriptCapacityText(viewModel.selectedScriptCapacity.value)?.let { capacityValue ->
                                ScriptCapacityRow(value = capacityValue)
                            }
                            ScriptConfigurationView(viewState.scriptConfigurationViewModel)
                        }
                    }
                }
            }
        }
    }

    errorView(viewModel.errorAction)
    StartScriptWarningConfirmationDialog(viewModel)
    NoNotificationChannelsWarningDialog(viewModel)
}

@Composable
private fun StartScriptWarningConfirmationDialog(viewModel: ScriptSelectionViewModel) {
    viewModel.startScriptWarningConfirmation.value?.let {
        AlertDialog(
            modifier = Modifier.widthIn(min = 400.dp),
            title = {
                CerealText(stringResource(Res.string.warning))
            },
            text = {
                CerealText(it)
            },
            confirmButton = {
                CerealTextButton(onClick = { viewModel.onStartScriptWarningConfirmationAccepted() }) {
                    CerealText(stringResource(Res.string.start_script))
                }
            },
            dismissButton = {
                CerealTextButton(onClick = { viewModel.onStartScriptWarningConfirmationDenied() }) {
                    CerealText(stringResource(Res.string.cancel))
                }
            },
            onDismissRequest = {
                viewModel.onStartScriptWarningConfirmationDenied()
            },
        )
    }
}

@Composable
private fun NoNotificationChannelsWarningDialog(viewModel: ScriptSelectionViewModel) {
    if (viewModel.showNoNotificationChannelsWarning.value) {
        ConfirmationDialog(
            title = stringResource(Res.string.notification_warning_title),
            message = stringResource(Res.string.notification_warning_message),
            confirmButtonText = stringResource(Res.string.notification_warning_continue),
            dismissButtonText = stringResource(Res.string.cancel),
            onConfirm = { viewModel.onNoNotificationChannelsWarningAccepted() },
            onDismiss = { viewModel.onNoNotificationChannelsWarningDismissed() },
        )
    }
}

@Composable
private fun ScriptCapacityRow(value: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CerealText(
            text = stringResource(Res.string.script_capacity_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CerealText(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NoItemSelectedView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CerealText(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.select_script_to_configure),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun NoScriptsAvailableView(
    onNavigateToMarketplace: () -> Unit,
    applicationConfig: ApplicationConfig = koinInject(),
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CerealText(
            text = stringResource(Res.string.no_installed_scripts),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
        )
        // White-label builds have no marketplace to browse — the Brand scripts auto-install,
        // so the "search marketplace" escape hatch is hidden (see docs/adr/0003).
        if (!applicationConfig.isBranded) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .clickable { onNavigateToMarketplace() }
                        .padding(4.dp),
            ) {
                CerealText(
                    text = stringResource(Res.string.search_marketplace_for_scripts),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
