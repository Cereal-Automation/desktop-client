package com.cereal.client.presentation.myscripts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.cereal.client.presentation.view.table.PaneToolbar
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove_running_tasks_blocked
import com.cereal_automation.cereal_client.generated.resources.my_scripts
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_action_browse
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_message
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

    Column(modifier = Modifier.fillMaxSize()) {
        PaneToolbar(title = stringResource(Res.string.my_scripts))
        HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)

        if (scripts.isEmpty()) {
            EmptyState(onOpenMarketplace = onOpenMarketplace)
        } else {
            val running = viewModel.packagesWithRunningTasks.value
            LazyColumn(
                contentPadding = PaddingValues(CerealTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
            ) {
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
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CerealTheme.colorScheme.cardDark)
                    .border(1.dp, CerealTheme.colorScheme.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(28.dp),
            )
        }
        CerealText(
            text = stringResource(Res.string.my_scripts_empty_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        CerealText(
            modifier = Modifier.widthIn(max = 420.dp),
            text = stringResource(Res.string.my_scripts_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = CerealTheme.colorScheme.contentSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CerealTheme.spacing.xxs))
        CerealButton(
            onClick = onOpenMarketplace,
            text = stringResource(Res.string.my_scripts_empty_action_browse),
            type = CerealButtonType.Primary,
        )
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CerealTheme.spacing.lg, vertical = CerealTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xxs)) {
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
