package com.cereal.client.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.settings.ai.LlmPresetForm
import com.cereal.client.presentation.settings.ai.label
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.InputBg
import com.cereal.client.presentation.view.InputBorder
import com.cereal.client.presentation.view.InputPlaceholder
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.WarningBox
import com.cereal.client.presentation.view.table.PaneToolbar
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.home
import com.cereal_automation.cereal_client.generated.resources.home_ai_change
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect_hint
import com.cereal_automation.cereal_client.generated.resources.home_ai_connected
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_subtitle
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_title
import com.cereal_automation.cereal_client.generated.resources.home_check_every
import com.cereal_automation.cereal_client.generated.resources.home_check_every_minutes
import com.cereal_automation.cereal_client.generated.resources.home_composer_locked
import com.cereal_automation.cereal_client.generated.resources.home_composer_subtitle
import com.cereal_automation.cereal_client.generated.resources.home_composer_title
import com.cereal_automation.cereal_client.generated.resources.home_goal_label
import com.cereal_automation.cereal_client.generated.resources.home_goal_placeholder
import com.cereal_automation.cereal_client.generated.resources.home_install
import com.cereal_automation.cereal_client.generated.resources.home_installing
import com.cereal_automation.cereal_client.generated.resources.home_maintenance_banner
import com.cereal_automation.cereal_client.generated.resources.home_more_options
import com.cereal_automation.cereal_client.generated.resources.home_not_installed_title
import com.cereal_automation.cereal_client.generated.resources.home_optional
import com.cereal_automation.cereal_client.generated.resources.home_pitch
import com.cereal_automation.cereal_client.generated.resources.home_retry
import com.cereal_automation.cereal_client.generated.resources.home_run_label
import com.cereal_automation.cereal_client.generated.resources.home_run_once
import com.cereal_automation.cereal_client.generated.resources.home_run_once_meta
import com.cereal_automation.cereal_client.generated.resources.home_run_repeat
import com.cereal_automation.cereal_client.generated.resources.home_run_repeat_meta
import com.cereal_automation.cereal_client.generated.resources.home_start
import com.cereal_automation.cereal_client.generated.resources.home_start_schedule
import com.cereal_automation.cereal_client.generated.resources.home_start_url_label
import com.cereal_automation.cereal_client.generated.resources.home_start_url_placeholder
import com.cereal_automation.cereal_client.generated.resources.home_title_ready
import com.cereal_automation.cereal_client.generated.resources.home_title_setup
import com.cereal_automation.cereal_client.generated.resources.home_update_cereal
import com.cereal_automation.cereal_client.generated.resources.home_update_required_title
import com.cereal_automation.cereal_client.generated.resources.notification_warning_continue
import com.cereal_automation.cereal_client.generated.resources.notification_warning_message
import com.cereal_automation.cereal_client.generated.resources.notification_warning_title
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

object HomeTestTags {
    const val GOAL = "home_goal"
}

/**
 * The agent home: a two-step setup (connect a model, then the goal composer), or the install/update card
 * when the agent can't run. Recent goal runs and the scripts strip go below.
 */
@Composable
fun HomeScreen(
    onNavigateToMarketplace: () -> Unit = {},
    onOpenClientUpdate: () -> Unit = {},
    onOpenTasks: (focusTaskId: String?) -> Unit = {},
    onRunScript: (packageName: String) -> Unit = {},
    onNavigateToAiSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { KoinJavaComponent.get<HomeViewModel>(HomeViewModel::class.java, parameters = { parametersOf(scope) }) }
    val aiViewModel =
        remember { KoinJavaComponent.get<HomeAiProviderViewModel>(HomeAiProviderViewModel::class.java, parameters = { parametersOf(scope) }) }
    val installViewModel =
        remember { KoinJavaComponent.get<AgentInstallViewModel>(AgentInstallViewModel::class.java, parameters = { parametersOf(scope) }) }

    val aiState = aiViewModel.state.value
    LaunchedEffect(aiState) {
        if (aiState != HomeAiProviderState.Loading) viewModel.onAiProviderChanged(aiState is HomeAiProviderState.Configured)
    }

    HomeDialogs(viewModel, onNavigateToMarketplace)
    errorView(viewModel.errorAction)
    errorView(aiViewModel.errorAction)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val slot = viewModel.slot.value
                Intro(ready = slot == HomeComposerSlot.Composer)
                when (slot) {
                    HomeComposerSlot.NotInstalled -> {
                        InstallCard(installViewModel.installState.value, installViewModel::onInstall)
                    }

                    HomeComposerSlot.UpdateRequired -> {
                        UpdateRequiredCard(onOpenClientUpdate)
                    }

                    HomeComposerSlot.Composer -> {
                        if (installViewModel.isUnderMaintenance.value) MaintenanceBanner()
                        (aiState as? HomeAiProviderState.Configured)?.let { ConnectedStep(it.provider, onNavigateToAiSettings) }
                        GoalStep(viewModel)
                    }

                    HomeComposerSlot.NoAiProvider -> {
                        ConnectStep(aiViewModel)
                        LockedGoalStep()
                    }
                }
                Spacer(Modifier.height(CerealTheme.spacing.md))
                HomeActivitySections(onOpenTasks, onRunScript, onNavigateToMarketplace)
            }
        }
    }
}

@Composable
private fun Intro(ready: Boolean) {
    Column(
        modifier = Modifier.padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CerealText(
            text = stringResource(if (ready) Res.string.home_title_ready else Res.string.home_title_setup),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        CerealText(
            text = stringResource(Res.string.home_pitch),
            style = MaterialTheme.typography.bodyMedium,
            color = CerealTheme.colorScheme.contentTertiary,
        )
    }
}

@Composable
private fun SlotCard(
    title: String,
    content: @Composable () -> Unit,
) {
    CerealCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CerealTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            CerealText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun InstallCard(
    state: AgentInstallState,
    onInstall: () -> Unit,
) {
    SlotCard(stringResource(Res.string.home_not_installed_title)) {
        if (state is AgentInstallState.Failed) {
            CerealText(text = state.message.text(), color = MaterialTheme.colorScheme.error)
        }
        CerealButton(
            onClick = onInstall,
            enabled = state != AgentInstallState.Installing,
            text =
                stringResource(
                    when (state) {
                        AgentInstallState.Idle -> Res.string.home_install
                        AgentInstallState.Installing -> Res.string.home_installing
                        is AgentInstallState.Failed -> Res.string.home_retry
                    },
                ),
        )
    }
}

@Composable
private fun UpdateRequiredCard(onOpenClientUpdate: () -> Unit) {
    SlotCard(stringResource(Res.string.home_update_required_title)) {
        CerealButton(onClick = onOpenClientUpdate, text = stringResource(Res.string.home_update_cereal))
    }
}

@Composable
private fun MaintenanceBanner() {
    WarningBox(text = stringResource(Res.string.home_maintenance_banner))
}

/** Step 2 before a model is connected: collapsed, with nothing to focus. */
@Composable
private fun LockedGoalStep() {
    HomeStep(
        number = 2,
        badge = StepBadge.Locked,
        title = stringResource(Res.string.home_composer_title),
        subtitle = { StepSubtitle(stringResource(Res.string.home_composer_locked)) },
        trailing = {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.padding(top = 5.dp).size(15.dp),
                tint = CerealTheme.colorScheme.contentSubtle,
            )
        },
    )
}

@Composable
private fun GoalStep(viewModel: HomeViewModel) {
    val errors = viewModel.fieldErrors.value
    val repeat = viewModel.repeat.value
    HomeStep(
        number = 2,
        title = stringResource(Res.string.home_composer_title),
        subtitle = { StepSubtitle(stringResource(Res.string.home_composer_subtitle)) },
    ) {
        Column(modifier = StepBodyPadding, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StepField(stringResource(Res.string.home_goal_label)) {
                ComposerInput(
                    value = viewModel.goal.value,
                    onValueChange = viewModel::onGoalChange,
                    error = errors[ComposerField.GOAL]?.text(),
                    placeholder = stringResource(Res.string.home_goal_placeholder),
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.GOAL),
                )
            }
            StepField(stringResource(Res.string.home_start_url_label), optional = true) {
                ComposerInput(
                    value = viewModel.startUrl.value,
                    onValueChange = viewModel::onStartUrlChange,
                    error = errors[ComposerField.START_URL]?.text(),
                    placeholder = stringResource(Res.string.home_start_url_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            StepField(stringResource(Res.string.home_run_label)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Segmented(
                        options = listOf(stringResource(Res.string.home_run_once), stringResource(Res.string.home_run_repeat)),
                        selected = if (repeat) 1 else 0,
                        onSelect = { viewModel.onRepeatChange(it == 1) },
                    )
                    if (repeat) {
                        val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                        CerealText(stringResource(Res.string.home_check_every), style = style, color = CerealTheme.colorScheme.contentTertiary)
                        ComposerInput(
                            value = viewModel.checkEveryMinutes.value,
                            onValueChange = viewModel::onCheckEveryMinutesChange,
                            error = errors[ComposerField.CHECK_EVERY]?.text(),
                            modifier = Modifier.width(80.dp),
                        )
                        CerealText(stringResource(Res.string.home_check_every_minutes), style = style, color = CerealTheme.colorScheme.contentTertiary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CerealButton(
                    onClick = viewModel::onStart,
                    text = stringResource(if (repeat) Res.string.home_start_schedule else Res.string.home_start),
                    enabled = !viewModel.isStarting.value,
                )
                SettingsSmallButton(
                    text = stringResource(Res.string.home_more_options),
                    onClick = viewModel::onMoreOptions,
                    variant = SettingsButtonVariant.Ghost,
                )
                Spacer(Modifier.weight(1f))
                CerealText(
                    text = stringResource(if (repeat) Res.string.home_run_repeat_meta else Res.string.home_run_once_meta),
                    style = helpStyle,
                    color = CerealTheme.colorScheme.contentSubtle,
                )
            }
        }
    }
}

/** A composer input with the script validator's message under it. */
@Composable
private fun ComposerInput(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HomeInput(value, onValueChange, Modifier.fillMaxWidth(), placeholder, isError = error != null, singleLine = singleLine)
        error?.let { FieldHelp(it, isError = true) }
    }
}

@Composable
private fun HomeDialogs(
    viewModel: HomeViewModel,
    onNavigateToMarketplace: () -> Unit,
) {
    when (val dialog = viewModel.dialog.value) {
        null -> {}

        HomeDialog.NoNotificationChannelsWarning -> {
            ConfirmationDialog(
                title = stringResource(Res.string.notification_warning_title),
                message = stringResource(Res.string.notification_warning_message),
                confirmButtonText = stringResource(Res.string.notification_warning_continue),
                dismissButtonText = stringResource(Res.string.cancel),
                onConfirm = viewModel::onNoNotificationChannelsWarningAccepted,
                onDismiss = viewModel::onDialogDismissed,
            )
        }

        is HomeDialog.MoreOptions -> {
            ScriptSelectionDialog(
                scriptPackageGroup = dialog.prefill.group,
                initialScriptPackageInstance = dialog.prefill.instance,
                onScriptInstanceCreated = viewModel::onMoreOptionsInstanceCreated,
                onDismissRequest = viewModel::onDialogDismissed,
                onNavigateToMarketplace = {
                    viewModel.onDialogDismissed()
                    onNavigateToMarketplace()
                },
            )
        }
    }
}
