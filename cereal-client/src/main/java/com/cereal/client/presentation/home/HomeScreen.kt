package com.cereal.client.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.settings.ai.label
import com.cereal.client.presentation.tasks.script.overview.ScriptEntryPicker
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionDialog
import com.cereal.client.presentation.tasks.script.overview.configuration.ConfigField
import com.cereal.client.presentation.tasks.script.overview.configuration.FormFieldContent
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.client.presentation.view.ConfirmationDialog
import com.cereal.client.presentation.view.InputPlaceholder
import com.cereal.client.presentation.view.WarningBox
import com.cereal.client.presentation.view.table.PaneToolbar
import com.cereal.client.presentation.view.text
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.home
import com.cereal_automation.cereal_client.generated.resources.home_ai_change
import com.cereal_automation.cereal_client.generated.resources.home_ai_connected
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_subtitle
import com.cereal_automation.cereal_client.generated.resources.home_ai_setup_title
import com.cereal_automation.cereal_client.generated.resources.home_ai_show
import com.cereal_automation.cereal_client.generated.resources.home_composer_locked
import com.cereal_automation.cereal_client.generated.resources.home_composer_subtitle
import com.cereal_automation.cereal_client.generated.resources.home_composer_title
import com.cereal_automation.cereal_client.generated.resources.home_install
import com.cereal_automation.cereal_client.generated.resources.home_installing
import com.cereal_automation.cereal_client.generated.resources.home_maintenance_banner
import com.cereal_automation.cereal_client.generated.resources.home_more_options
import com.cereal_automation.cereal_client.generated.resources.home_not_installed_title
import com.cereal_automation.cereal_client.generated.resources.home_pitch
import com.cereal_automation.cereal_client.generated.resources.home_retry
import com.cereal_automation.cereal_client.generated.resources.home_start
import com.cereal_automation.cereal_client.generated.resources.home_title_ready
import com.cereal_automation.cereal_client.generated.resources.home_title_setup
import com.cereal_automation.cereal_client.generated.resources.home_update_cereal
import com.cereal_automation.cereal_client.generated.resources.home_update_required_title
import com.cereal_automation.cereal_client.generated.resources.notification_warning_continue
import com.cereal_automation.cereal_client.generated.resources.notification_warning_message
import com.cereal_automation.cereal_client.generated.resources.notification_warning_title
import com.cereal_automation.cereal_client.generated.resources.secret_field_hide
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

object HomeTestTags {
    const val FIELDS = "home_fields"
}

/**
 * The agent home: a two-step setup (connect a model, then the goal composer), or the install/update card
 * when the agent can't run. Recent runs go below.
 */
@Composable
fun HomeScreen(
    onNavigateToMarketplace: () -> Unit = {},
    onOpenClientUpdate: () -> Unit = {},
    onOpenTasks: (focusTaskId: String?) -> Unit = {},
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
                    .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val slot = viewModel.slot.value
                if (slot != HomeComposerSlot.Loading) Intro(ready = slot == HomeComposerSlot.Composer)
                when (slot) {
                    HomeComposerSlot.Loading -> {}

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
                HomeActivitySections(onOpenTasks)
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

private enum class StepBadge { Number, Done, Locked }

/** A numbered setup step: badge, title and [subtitle] in the head, then the optional [body]. */
@Composable
private fun HomeStep(
    number: Int,
    title: String,
    subtitle: @Composable () -> Unit,
    badge: StepBadge = StepBadge.Number,
    trailing: @Composable () -> Unit = {},
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    CerealCard(modifier = Modifier.fillMaxWidth()) {
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
            CerealText(number.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
private fun StepSubtitle(text: String) {
    CerealText(text = text, style = MaterialTheme.typography.bodyMedium, color = CerealTheme.colorScheme.contentTertiary)
}

/** Step 1 with no provider yet: the preset form and Connect. */
@Composable
private fun ConnectStep(viewModel: HomeAiProviderViewModel) {
    HomeStep(
        number = 1,
        title = stringResource(Res.string.home_ai_setup_title),
        subtitle = { StepSubtitle(stringResource(Res.string.home_ai_setup_subtitle)) },
    ) {
        HomeConnectForm(viewModel)
    }
}

/** Step 1 done: the provider and model, with Change opening Settings → AI. */
@Composable
private fun ConnectedStep(
    provider: AiProviderUiModel,
    onNavigateToAiSettings: () -> Unit,
) {
    HomeStep(
        number = 1,
        badge = StepBadge.Done,
        title = stringResource(Res.string.home_ai_connected),
        subtitle = {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CerealText(
                    stringResource(provider.preset.label()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(Modifier.size(3.dp).clip(CircleShape).background(CerealTheme.colorScheme.contentSubtle))
                CerealText(provider.model, style = MaterialTheme.typography.bodyMedium, color = CerealTheme.colorScheme.contentSecondary)
            }
        },
        trailing = {
            CerealButton(onClick = onNavigateToAiSettings, text = stringResource(Res.string.home_ai_change), type = CerealButtonType.Surface)
        },
    )
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
    HomeStep(
        number = 2,
        title = stringResource(Res.string.home_composer_title),
        subtitle = { StepSubtitle(stringResource(Res.string.home_composer_subtitle)) },
    ) {
        Column(
            modifier = Modifier.padding(start = 62.dp, end = 22.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val entryOptions = viewModel.entryOptions.value
            val selectedEntry = viewModel.selectedEntry.value
            if (entryOptions.size > 1 && selectedEntry != null) {
                ScriptEntryPicker(
                    options = entryOptions,
                    selected = selectedEntry,
                    enabled = true,
                    onSelect = viewModel::selectEntry,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The picked script's own fields sit below a rule, as one form.
                HorizontalDivider(thickness = 1.dp, color = FormDivider)
            }
            Column(
                modifier = Modifier.testTag(HomeTestTags.FIELDS),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                viewModel.composerFields().forEach { (item, state) ->
                    ConfigField(item.definition.name, item.definition.description) { FormFieldContent(item, state) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
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
}

@Composable
internal fun StepField(
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            CerealText(label, style = style, color = CerealTheme.colorScheme.contentSecondary)
            trailing?.let {
                Spacer(Modifier.weight(1f))
                it()
            }
        }
        content()
    }
}

private const val BADGE_TINT = 0.12f
private const val BADGE_BORDER = 0.3f

// The design's divider token, between the script picker and the picked script's fields.
private val FormDivider = Color(0xFF232323)

@Composable
internal fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    help: String? = null,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CerealTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            isError = error != null,
            singleLine = singleLine,
            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon =
                if (isPassword && value.isNotEmpty()) {
                    {
                        CerealText(
                            text = stringResource(if (revealed) Res.string.secret_field_hide else Res.string.home_ai_show),
                            modifier = Modifier.clickable(role = Role.Button) { revealed = !revealed },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CerealTheme.colorScheme.contentTertiary,
                        )
                    }
                } else {
                    null
                },
        )
        val note = error ?: help
        note?.let { CerealText(it, style = MaterialTheme.typography.bodySmall, color = if (error != null) MaterialTheme.colorScheme.error else CerealTheme.colorScheme.contentSubtle) }
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
