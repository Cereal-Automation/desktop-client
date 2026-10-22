package com.cereal.client.presentation.myscripts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.marketplace.RemoveScriptConfirmationDialog
import com.cereal.client.presentation.marketplace.ScriptBadge
import com.cereal.client.presentation.marketplace.ScriptDetailDialog
import com.cereal.client.presentation.marketplace.UpdateRequiredBadge
import com.cereal.client.presentation.marketplace.handle
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.table.PaneToolbar
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove
import com.cereal_automation.cereal_client.generated.resources.marketplace_remove_running_tasks_blocked
import com.cereal_automation.cereal_client.generated.resources.marketplace_removing
import com.cereal_automation.cereal_client.generated.resources.my_scripts
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_action_browse
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_message
import com.cereal_automation.cereal_client.generated.resources.my_scripts_empty_title
import com.cereal_automation.cereal_client.generated.resources.my_scripts_running_tasks
import com.cereal_automation.cereal_client.generated.resources.my_scripts_update_available
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
fun MyScriptsScreen(
    onOpenMarketplace: () -> Unit,
    onStartNewInstance: (publicIdentifier: String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<MyScriptsViewModel>(
                MyScriptsViewModel::class.java,
                parameters = { parametersOf(scope, onStartNewInstance) },
            )
        }
    val rows = viewModel.rows.value
    val confirmTarget = viewModel.confirmRemoveTarget.value

    viewModel.selectedScript.value?.let { script ->
        ScriptDetailDialog(
            script = script,
            onDismiss = viewModel::onDismissDetail,
            onStartNewInstance = viewModel::onStartNewInstance,
            onRemoved = viewModel::onDismissDetail,
        )
    }

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

        if (rows.isEmpty()) {
            EmptyState(onOpenMarketplace = onOpenMarketplace)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(CerealTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
            ) {
                items(rows, key = { it.scriptPackage.manifest.packageName }) { row ->
                    InstalledScriptRow(
                        row = row,
                        onClick = { viewModel.onRowClicked(row) },
                        onRemove = { viewModel.onRemoveClicked(row.scriptPackage) },
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
    row: MyScriptsViewModel.InstalledScriptRow,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val listing = row.listing
    CerealCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (row.isClickable) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
    ) {
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
                        modifier = Modifier.weight(1f, fill = false),
                        text = listing?.title ?: row.scriptPackage.manifest.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    listing?.developer?.let { dev ->
                        CerealText(
                            text = dev.handle,
                            style = MaterialTheme.typography.bodySmall,
                            color = CerealTheme.colorScheme.contentTertiary,
                            maxLines = 1,
                        )
                    }
                }
                val description = listing?.shortDescription
                if (description != null) {
                    CerealText(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CerealTheme.colorScheme.contentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (row.isListingLoading) {
                    DescriptionPlaceholder()
                }
            }
            StatusChip(row.status)
            when {
                row.isClickable -> {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = CerealTheme.colorScheme.contentTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Wait for the lookup before offering Uninstall: the row may still gain a listing.
                row.isListingLoading -> {}

                row.isRemoving -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                    ) {
                        CerealCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        CerealText(
                            text = stringResource(Res.string.marketplace_removing),
                            style = MaterialTheme.typography.bodySmall,
                            color = CerealTheme.colorScheme.contentSecondary,
                        )
                    }
                }

                else -> {
                    RemoveButton(hasRunningTasks = row.hasRunningTasks, onRemove = onRemove)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoveButton(
    hasRunningTasks: Boolean,
    onRemove: () -> Unit,
) {
    val button = @Composable {
        CerealTextButton(onClick = onRemove, enabled = !hasRunningTasks) {
            CerealText(text = stringResource(Res.string.marketplace_remove))
        }
    }
    if (!hasRunningTasks) {
        button()
        return
    }
    TooltipArea(
        tooltip = {
            Surface(modifier = Modifier.shadow(4.dp), shape = MaterialTheme.shapes.extraSmall) {
                CerealText(
                    text = stringResource(Res.string.marketplace_remove_running_tasks_blocked),
                    modifier = Modifier.padding(CerealTheme.spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        delayMillis = 0,
    ) {
        button()
    }
}

@Composable
private fun StatusChip(status: MyScriptsViewModel.InstalledScriptStatus?) {
    when (status) {
        MyScriptsViewModel.InstalledScriptStatus.CLIENT_UPDATE_REQUIRED -> {
            UpdateRequiredBadge()
        }

        MyScriptsViewModel.InstalledScriptStatus.UPDATE_AVAILABLE -> {
            ScriptBadge(
                text = stringResource(Res.string.my_scripts_update_available),
                containerColor = CerealTheme.colorScheme.info.copy(alpha = 0.12f),
                textColor = CerealTheme.colorScheme.info,
            )
        }

        MyScriptsViewModel.InstalledScriptStatus.RUNNING_TASKS -> {
            ScriptBadge(
                text = stringResource(Res.string.my_scripts_running_tasks),
                containerColor = CerealTheme.colorScheme.success.copy(alpha = 0.12f),
                textColor = CerealTheme.colorScheme.success,
            )
        }

        null -> {}
    }
}

@Composable
private fun DescriptionPlaceholder() {
    Box(
        modifier =
            Modifier
                .padding(vertical = CerealTheme.spacing.xxs)
                .fillMaxWidth(0.46f)
                .height(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(CerealTheme.colorScheme.border),
    )
}
