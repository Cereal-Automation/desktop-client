package com.cereal.client.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.settings.ai.LlmPresetForm
import com.cereal.client.presentation.settings.ai.labels
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.WarningBox
import com.cereal.client.presentation.view.chip.Pill
import com.cereal.client.presentation.view.table.PaneToolbar
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.home
import com.cereal_automation.cereal_client.generated.resources.home_ai_change
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect
import com.cereal_automation.cereal_client.generated.resources.home_ai_connect_hint
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_subtitle
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_title
import com.cereal_automation.cereal_client.generated.resources.home_ai_using
import com.cereal_automation.cereal_client.generated.resources.home_check_every
import com.cereal_automation.cereal_client.generated.resources.home_check_every_minutes
import com.cereal_automation.cereal_client.generated.resources.home_composer_title
import com.cereal_automation.cereal_client.generated.resources.home_example_buy
import com.cereal_automation.cereal_client.generated.resources.home_example_buy_goal
import com.cereal_automation.cereal_client.generated.resources.home_example_read_value
import com.cereal_automation.cereal_client.generated.resources.home_example_read_value_goal
import com.cereal_automation.cereal_client.generated.resources.home_example_restock
import com.cereal_automation.cereal_client.generated.resources.home_example_restock_goal
import com.cereal_automation.cereal_client.generated.resources.home_goal_placeholder
import com.cereal_automation.cereal_client.generated.resources.home_install
import com.cereal_automation.cereal_client.generated.resources.home_installing
import com.cereal_automation.cereal_client.generated.resources.home_maintenance_banner
import com.cereal_automation.cereal_client.generated.resources.home_more_options
import com.cereal_automation.cereal_client.generated.resources.home_not_installed_title
import com.cereal_automation.cereal_client.generated.resources.home_pitch
import com.cereal_automation.cereal_client.generated.resources.home_retry
import com.cereal_automation.cereal_client.generated.resources.home_run_once
import com.cereal_automation.cereal_client.generated.resources.home_run_repeat
import com.cereal_automation.cereal_client.generated.resources.home_safety_line
import com.cereal_automation.cereal_client.generated.resources.home_start
import com.cereal_automation.cereal_client.generated.resources.home_start_url_placeholder
import com.cereal_automation.cereal_client.generated.resources.home_update_cereal
import com.cereal_automation.cereal_client.generated.resources.home_update_required_title
import com.cereal_automation.cereal_client.generated.resources.notification_warning_continue
import com.cereal_automation.cereal_client.generated.resources.notification_warning_message
import com.cereal_automation.cereal_client.generated.resources.notification_warning_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

object HomeTestTags {
    const val GOAL = "home_goal"
}

private val examples: List<Pair<StringResource, StringResource>> =
    listOf(
        Res.string.home_example_restock to Res.string.home_example_restock_goal,
        Res.string.home_example_read_value to Res.string.home_example_read_value_goal,
        Res.string.home_example_buy to Res.string.home_example_buy_goal,
    )

/**
 * The agent home: the pitch, then the composer slot. Recent goal runs and the scripts strip go
 * below the slot.
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
        PaneToolbar(title = stringResource(Res.string.home))
        HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(CerealTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.lg),
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.lg),
            ) {
                CerealText(
                    text = stringResource(Res.string.home_pitch),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                when (viewModel.slot.value) {
                    HomeComposerSlot.NotInstalled -> {
                        InstallCard(installViewModel.installState.value, installViewModel::onInstall)
                    }

                    HomeComposerSlot.UpdateRequired -> {
                        UpdateRequiredCard(onOpenClientUpdate)
                    }

                    HomeComposerSlot.Composer -> {
                        if (installViewModel.isUnderMaintenance.value) MaintenanceBanner()
                        GoalComposer(viewModel, enabled = true)
                        (aiState as? HomeAiProviderState.Configured)?.let { AiProviderLine(it.provider, onNavigateToAiSettings) }
                    }

                    HomeComposerSlot.NoAiProvider -> {
                        AiSetupCard(aiViewModel)
                        DisabledComposer(viewModel)
                    }
                }
                Spacer(Modifier.height(CerealTheme.spacing.md))
                HomeActivitySections(onOpenTasks, onRunScript, onNavigateToMarketplace)
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalComposer(
    viewModel: HomeViewModel,
    enabled: Boolean,
) {
    val errors = viewModel.fieldErrors.value
    CerealCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CerealTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            CerealText(
                text = stringResource(Res.string.home_composer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ComposerField(
                value = viewModel.goal.value,
                onValueChange = viewModel::onGoalChange,
                placeholder = stringResource(Res.string.home_goal_placeholder),
                error = errors[ComposerField.GOAL]?.text(),
                enabled = enabled,
                singleLine = false,
                modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.GOAL),
            )
            ComposerField(
                value = viewModel.startUrl.value,
                onValueChange = viewModel::onStartUrlChange,
                placeholder = stringResource(Res.string.home_start_url_placeholder),
                error = errors[ComposerField.START_URL]?.text(),
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
            ) {
                val repeat = viewModel.repeat.value
                RunModeOption(stringResource(Res.string.home_run_once), selected = !repeat, enabled = enabled) { viewModel.onRepeatChange(false) }
                RunModeOption(stringResource(Res.string.home_run_repeat), selected = repeat, enabled = enabled) { viewModel.onRepeatChange(true) }
                if (repeat) {
                    CerealText(
                        text = stringResource(Res.string.home_check_every),
                        modifier = Modifier.padding(start = CerealTheme.spacing.md),
                    )
                    ComposerField(
                        value = viewModel.checkEveryMinutes.value,
                        onValueChange = viewModel::onCheckEveryMinutesChange,
                        error = errors[ComposerField.CHECK_EVERY]?.text(),
                        enabled = enabled,
                        modifier = Modifier.width(120.dp),
                    )
                    CerealText(text = stringResource(Res.string.home_check_every_minutes))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
            ) {
                CerealButton(
                    onClick = viewModel::onStart,
                    text = stringResource(Res.string.home_start),
                    enabled = enabled && !viewModel.isStarting.value,
                )
                CerealButton(
                    onClick = viewModel::onMoreOptions,
                    text = stringResource(Res.string.home_more_options),
                    enabled = enabled,
                    type = CerealButtonType.Surface,
                )
            }
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
    ) {
        examples.forEach { (label, goal) ->
            val goalText = stringResource(goal)
            Pill(
                text = stringResource(label),
                contentColor = CerealTheme.colorScheme.contentSecondary,
                backgroundColor = CerealTheme.colorScheme.cardDark,
                borderColor = CerealTheme.colorScheme.border,
                onClick = if (enabled) ({ viewModel.onGoalChange(goalText) }) else null,
            )
        }
    }
    CerealText(
        text = stringResource(Res.string.home_safety_line),
        style = MaterialTheme.typography.bodySmall,
        color = CerealTheme.colorScheme.contentTertiary,
    )
}

@Composable
private fun AiSetupCard(viewModel: HomeAiProviderViewModel) {
    CerealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
            Column(
                modifier = Modifier.padding(start = CerealTheme.spacing.xl, top = CerealTheme.spacing.xl, end = CerealTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
            ) {
                CerealText(
                    text = stringResource(Res.string.home_ai_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CerealText(
                    text = stringResource(Res.string.home_ai_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CerealTheme.colorScheme.contentSecondary,
                )
            }
            LlmPresetForm(form = viewModel.setup, onSelectPreset = viewModel::onSelectPreset)
            Row(
                modifier = Modifier.padding(start = CerealTheme.spacing.xl, end = CerealTheme.spacing.xl, bottom = CerealTheme.spacing.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
            ) {
                CerealButton(
                    onClick = viewModel::onConnect,
                    text = stringResource(Res.string.home_ai_connect),
                    enabled = !viewModel.isConnecting.value,
                )
                val error = viewModel.connectError.value?.text()
                CerealText(
                    text = error ?: stringResource(Res.string.home_ai_connect_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else CerealTheme.colorScheme.contentTertiary,
                )
            }
        }
    }
}

/** The composer greyed out under the setup card: visible, but disabled, so neither mouse nor keyboard reaches it. */
@Composable
private fun DisabledComposer(viewModel: HomeViewModel) {
    Column(
        modifier = Modifier.alpha(DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.lg),
    ) {
        GoalComposer(viewModel, enabled = false)
    }
}

@Composable
private fun AiProviderLine(
    provider: AiProviderUiModel,
    onNavigateToAiSettings: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs)) {
        val style = MaterialTheme.typography.bodySmall
        CerealText(
            text = stringResource(Res.string.home_ai_using, stringResource(provider.preset.labels().first), provider.model) + " ·",
            style = style,
            color = CerealTheme.colorScheme.contentTertiary,
        )
        CerealText(
            text = stringResource(Res.string.home_ai_change),
            modifier = Modifier.clickable(role = Role.Button, onClick = onNavigateToAiSettings),
            style = style,
            color = CerealTheme.colorScheme.link,
        )
    }
}

private const val DISABLED_ALPHA = 0.4f

/** One option of the Once/Repeat radio group: filled when selected; red stays reserved for Start. */
@Composable
private fun RunModeOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsSmallButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        variant = if (selected) SettingsButtonVariant.Flat else SettingsButtonVariant.Ghost,
        modifier =
            Modifier.semantics {
                this.selected = selected
                role = Role.RadioButton
            },
    )
}

@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
    )
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
