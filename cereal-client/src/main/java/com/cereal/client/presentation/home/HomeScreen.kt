package com.cereal.client.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.agent.GoalRunInput
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionDialog
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal.client.presentation.view.chip.Pill
import com.cereal.client.presentation.view.table.PaneToolbar
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.home
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
import com.cereal_automation.cereal_client.generated.resources.home_more_options
import com.cereal_automation.cereal_client.generated.resources.home_pitch
import com.cereal_automation.cereal_client.generated.resources.home_run_once
import com.cereal_automation.cereal_client.generated.resources.home_run_repeat
import com.cereal_automation.cereal_client.generated.resources.home_safety_line
import com.cereal_automation.cereal_client.generated.resources.home_start
import com.cereal_automation.cereal_client.generated.resources.home_start_url_placeholder
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
fun HomeScreen(onNavigateToMarketplace: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<HomeViewModel>(HomeViewModel::class.java, parameters = { parametersOf(scope) })
        }

    HomeDialogs(viewModel, onNavigateToMarketplace)
    errorView(viewModel.errorAction)

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
                    HomeComposerSlot.Composer -> GoalComposer(viewModel)
                }
            }
        }
    }
}

@Composable
private fun GoalComposer(viewModel: HomeViewModel) {
    val errors = viewModel.fieldErrors.value
    CerealCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(CerealTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            CerealText(
                text = stringResource(Res.string.home_composer_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ComposerField(
                value = viewModel.goal.value,
                onValueChange = viewModel::onGoalChange,
                placeholder = stringResource(Res.string.home_goal_placeholder),
                error = errors[GoalRunInput.KEY_GOAL],
                singleLine = false,
                modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.GOAL),
            )
            ComposerField(
                value = viewModel.startUrl.value,
                onValueChange = viewModel::onStartUrlChange,
                placeholder = stringResource(Res.string.home_start_url_placeholder),
                error = errors[GoalRunInput.KEY_START_URL],
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
            ) {
                val repeat = viewModel.repeat.value
                SettingsSmallButton(
                    text = stringResource(Res.string.home_run_once),
                    onClick = { viewModel.onRepeatChange(false) },
                    variant = if (repeat) SettingsButtonVariant.Flat else SettingsButtonVariant.Primary,
                )
                SettingsSmallButton(
                    text = stringResource(Res.string.home_run_repeat),
                    onClick = { viewModel.onRepeatChange(true) },
                    variant = if (repeat) SettingsButtonVariant.Primary else SettingsButtonVariant.Flat,
                )
                if (repeat) {
                    CerealText(
                        text = stringResource(Res.string.home_check_every),
                        modifier = Modifier.padding(start = CerealTheme.spacing.md),
                    )
                    ComposerField(
                        value = viewModel.checkEveryMinutes.value,
                        onValueChange = viewModel::onCheckEveryMinutesChange,
                        error = errors[GoalRunInput.KEY_REPEAT_INTERVAL],
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
                    enabled = !viewModel.isStarting.value,
                )
                CerealButton(
                    onClick = viewModel::onMoreOptions,
                    text = stringResource(Res.string.home_more_options),
                    type = CerealButtonType.Surface,
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
        examples.forEach { (label, goal) ->
            val goalText = stringResource(goal)
            Pill(
                text = stringResource(label),
                contentColor = CerealTheme.colorScheme.contentSecondary,
                backgroundColor = CerealTheme.colorScheme.cardDark,
                borderColor = CerealTheme.colorScheme.border,
                onClick = { viewModel.onGoalChange(goalText) },
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
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
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
                scriptPackageGroup = dialog.group,
                initialScriptPackageInstance = dialog.prefill,
                onScriptInstanceCreated = {},
                onDismissRequest = viewModel::onDialogDismissed,
                onNavigateToMarketplace = {
                    viewModel.onDialogDismissed()
                    onNavigateToMarketplace()
                },
            )
        }
    }
}
