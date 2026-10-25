package com.cereal.client.presentation.tasks.script.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.marketplace.scriptCapacityText
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationView
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.VerticalDivider
import com.cereal.client.presentation.view.button.SyncIconButton
import com.cereal.client.presentation.view.group.GroupedListView
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_warning_continue
import com.cereal_automation.cereal_client.generated.resources.ai_warning_message
import com.cereal_automation.cereal_client.generated.resources.ai_warning_title
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.close
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
    initialRequest: ScriptRequest? = null,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    viewModel: ScriptSelectionViewModel =
        remember {
            KoinJavaComponent.get(
                ScriptSelectionViewModel::class.java,
                parameters = { parametersOf(coroutineScope, scriptPackageGroup, initialScriptPackageInstance, initialRequest) },
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

    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false),
        onDismissRequest = onDismissRequest,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                    .padding(40.dp), // 32px × 1.25
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = 1175.dp) // 940px × 1.25
                        .heightIn(max = 800.dp) // 640px × 1.25
                        .fillMaxSize()
                        .clip(DialogShape)
                        .border(1.dp, CerealTheme.colorScheme.borderDark, DialogShape)
                        .background(CerealTheme.colorScheme.cardDark),
            ) {
                when (val state = viewModel.overviewViewState.value) {
                    is ScriptSelectionState.Overview.Filled -> {
                        ScriptRail(count = state.viewModel.state.value.size, viewModel = viewModel) {
                            GroupedListView(viewModel = state.viewModel)
                        }
                    }

                    is ScriptSelectionState.Overview.Empty -> {
                        ScriptRail(count = 0, viewModel = viewModel) {
                            NoScriptsAvailableView(onNavigateToMarketplace)
                        }
                    }

                    // Nothing to choose from, so the configuration takes the full width.
                    is ScriptSelectionState.Overview.Single -> {}
                }

                val configState = viewModel.configurationViewState.value
                Column(Modifier.fillMaxHeight().weight(1f)) {
                    ConfigHeader(
                        title =
                            (configState as? ScriptSelectionState.Config.Selected)?.scriptConfigurationViewModel?.scriptName
                                ?: stringResource(Res.string.installed_scripts),
                        onClose = onDismissRequest,
                    )
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        when (configState) {
                            is ScriptSelectionState.Config.None -> {
                                NoItemSelectedView()
                            }

                            is ScriptSelectionState.Config.Selected -> {
                                ScriptConfigurationView(configState.scriptConfigurationViewModel)
                            }
                        }
                    }
                    ConfigFooter(
                        // Surface the entitled record cap before the run is configured/started;
                        // scriptCapacityText returns null for scripts with no capacity concept.
                        capacity = scriptCapacityText(viewModel.selectedScriptCapacity.value),
                        enabled = configState is ScriptSelectionState.Config.Selected && !viewModel.selectedScriptUpdateRequired.value,
                        isLaunching = viewModel.scriptLaunchLoadState.value is LoadState.Loading,
                        onStart = {
                            if (viewModel.validate()) {
                                viewModel.launchScript()
                            }
                        },
                    )
                }
            }
        }
    }

    errorView(viewModel.errorAction)
    StartScriptWarningConfirmationDialog(viewModel)
    NoAiProviderWarningDialog(viewModel)
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
private fun NoAiProviderWarningDialog(viewModel: ScriptSelectionViewModel) {
    if (viewModel.showNoAiProviderWarning.value) {
        ConfirmationDialog(
            title = stringResource(Res.string.ai_warning_title),
            message = stringResource(Res.string.ai_warning_message),
            confirmButtonText = stringResource(Res.string.ai_warning_continue),
            dismissButtonText = stringResource(Res.string.cancel),
            onConfirm = { viewModel.onNoAiProviderWarningAccepted() },
            onDismiss = { viewModel.onNoAiProviderWarningDismissed() },
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

private val DialogShape = RoundedCornerShape(20.dp) // 16px × 1.25

@Composable
private fun ScriptRail(
    count: Int,
    viewModel: ScriptSelectionViewModel,
    list: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(310.dp) // 248px × 1.25
                .fillMaxHeight()
                .background(CerealTheme.colorScheme.backgroundDark),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CerealText(
                text = stringResource(Res.string.installed_scripts),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            if (count > 0) {
                CerealText(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CerealTheme.colorScheme.contentSubtle,
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            list()
        }
        HorizontalDivider(color = DividerColor)
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            SyncIconButton(viewModel.scriptRefreshState.value) {
                viewModel.refreshScriptList()
            }
        }
    }
    VerticalDivider(color = DividerColor)
}

@Composable
private fun ConfigHeader(
    title: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 25.dp, end = 15.dp, top = 22.dp, bottom = 20.dp), // 20/18/16px × 1.25
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small), // 40px × 1.25
            contentAlignment = Alignment.Center,
        ) {
            CerealText(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        CerealText(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        CerealIconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.close), tint = CerealTheme.colorScheme.contentTertiary)
        }
    }
    HorizontalDivider(color = DividerColor)
}

@Composable
private fun ConfigFooter(
    capacity: String?,
    enabled: Boolean,
    isLaunching: Boolean,
    onStart: () -> Unit,
) {
    HorizontalDivider(color = DividerColor)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CerealTheme.colorScheme.backgroundDark)
                .padding(horizontal = 25.dp, vertical = 17.dp), // 20/14px × 1.25
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (capacity != null) {
            CerealText(
                text = stringResource(Res.string.script_capacity_label),
                style = MaterialTheme.typography.bodySmall,
                color = CerealTheme.colorScheme.contentTertiary,
            )
            CerealText(
                text = capacity,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = CerealTheme.colorScheme.contentSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        CerealButton(
            onClick = onStart,
            enabled = enabled && !isLaunching,
            type = CerealButtonType.Primary,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            if (isLaunching) {
                CerealCircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painterResource(Res.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            CerealText(stringResource(Res.string.start_task))
        }
    }
}

private val DividerColor = Color(0xFF232323)

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
