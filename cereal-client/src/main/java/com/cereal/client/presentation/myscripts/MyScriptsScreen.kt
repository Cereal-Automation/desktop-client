package com.cereal.client.presentation.myscripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.presentation.marketplace.RemoveScriptConfirmationDialog
import com.cereal.client.presentation.marketplace.UpdateRequiredBadge
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove_running_tasks_blocked
import com.cereal_automation.cereal_client.generated.resources.my_scripts
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_action_browse
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_title
import com.cereal_automation.cereal_client.generated.resources.script_update_required_message
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
fun MyScriptsScreen(onOpenMarketplace: () -> Unit) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<MyScriptsViewModel>(
                MyScriptsViewModel::class.java,
                parameters = { parametersOf(scope) },
            )
        }
    val scripts = viewModel.installedScripts.value
    val confirmTarget = viewModel.confirmRemoveTarget.value

    if (confirmTarget != null) {
        RemoveScriptConfirmationDialog(
            scriptName = confirmTarget.manifest.name,
            onConfirm = viewModel::onRemoveConfirmed,
            onDismiss = viewModel::onRemoveDismissed,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(CerealTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.lg),
    ) {
        CerealText(
            text = stringResource(Res.string.my_scripts),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (scripts.isEmpty()) {
            EmptyState(onOpenMarketplace = onOpenMarketplace)
        } else {
            val running = viewModel.packagesWithRunningTasks.value
            LazyColumn(verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
                val updateRequired = viewModel.updateRequiredPackages.value
                items(scripts, key = { it.manifest.packageName }) { pkg ->
                    InstalledScriptRow(
                        scriptPackage = pkg,
                        isRemoving = viewModel.removingPackages.value.contains(pkg.manifest.packageName),
                        hasRunningTasks = running.contains(pkg.manifest.packageName),
                        updateRequired = updateRequired.contains(pkg.manifest.packageName),
                        onRemove = { viewModel.onRemoveClicked(pkg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenMarketplace: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            CerealText(
                text = stringResource(Res.string.my_scripts_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = CerealTheme.colorScheme.contentSecondary,
            )
            CerealButton(
                onClick = onOpenMarketplace,
                text = stringResource(Res.string.my_scripts_empty_action_browse),
                type = CerealButtonType.Primary,
            )
        }
    }
}

@Composable
private fun InstalledScriptRow(
    scriptPackage: ScriptPackage,
    isRemoving: Boolean,
    hasRunningTasks: Boolean,
    updateRequired: Boolean,
    onRemove: () -> Unit,
) {
    CerealCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CerealTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                ) {
                    CerealText(
                        text = scriptPackage.manifest.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (updateRequired) {
                        UpdateRequiredBadge()
                    }
                }
                CerealText(
                    text = scriptPackage.manifest.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentSecondary,
                )
            }
            if (updateRequired) {
                CerealText(
                    text = stringResource(Res.string.script_update_required_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.warning,
                )
            } else if (hasRunningTasks) {
                CerealText(
                    text = stringResource(Res.string.marketplace_remove_running_tasks_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentSecondary,
                )
            }
            CerealTextButton(
                onClick = onRemove,
                enabled = !isRemoving && !hasRunningTasks,
            ) {
                CerealText(text = stringResource(Res.string.marketplace_remove))
            }
        }
    }
}
