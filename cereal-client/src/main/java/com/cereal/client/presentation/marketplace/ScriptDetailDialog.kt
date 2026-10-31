package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.presentation.authenticate.AuthenticationDialog
import com.cereal.client.presentation.authenticate.AuthenticationDialogMode
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealIconButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.WarningBox
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.close
import com.cereal_automation.cereal_client.generated.resources.marketplace_release_notes
import com.cereal_automation.cereal_client.generated.resources.marketplace_status_maintenance
import com.cereal_automation.cereal_client.generated.resources.marketplace_tags
import com.cereal_automation.cereal_client.generated.resources.marketplace_updated
import com.cereal_automation.cereal_client.generated.resources.marketplace_version
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScriptDetailDialog(
    script: MarketplaceScript,
    onDismiss: () -> Unit,
    onStartNewInstance: (publicIdentifier: String) -> Unit = {},
    onRemoved: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<ScriptDetailViewModel>(
                ScriptDetailViewModel::class.java,
                parameters = { parametersOf(scope, onStartNewInstance) },
            )
        }
    val installState = viewModel.installState.value
    val showAuthDialog = remember { mutableStateOf(false) }
    val authDialogMode = remember { mutableStateOf(AuthenticationDialogMode.LOGIN) }

    if (showAuthDialog.value) {
        AuthenticationDialog(
            initialMode = authDialogMode.value,
            onDismissRequest = {
                showAuthDialog.value = false
                // Re-check install state in case the user just logged in
                viewModel.onScriptLoaded(script)
            },
        )
    }

    if (viewModel.confirmRemoveDialog.value) {
        RemoveScriptConfirmationDialog(
            scriptName = script.title,
            onConfirm = { viewModel.onRemoveConfirmed(onRemoved) },
            onDismiss = { viewModel.onRemoveDismissed() },
        )
    }

    LaunchedEffect(script.id) {
        viewModel.onScriptLoaded(script)
    }
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss,
    ) {
        // Full-window scrim: dims the marketplace behind the dialog, and a click on it dismisses.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            CerealCard(
                modifier =
                    Modifier
                        .widthIn(max = 720.dp)
                        .heightIn(max = 800.dp)
                        .padding(horizontal = 16.dp)
                        // Swallow clicks so they don't fall through to the dismissing scrim.
                        .clickable(interactionSource = null, indication = null) {}
                        .onKeyEvent { event ->
                            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                                onDismiss()
                                true
                            } else {
                                false
                            }
                        },
            ) {
                Column {
                    // Title bar
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.xs),
                            ) {
                                CerealText(
                                    text = script.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        },
                        actions = {
                            CerealIconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, stringResource(Res.string.close))
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                    )

                    HorizontalDivider(color = CerealTheme.colorScheme.borderDark)

                    // Scrollable content
                    Column(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(CerealTheme.spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.lg),
                    ) {
                        if (script.maintenanceMode) {
                            WarningBox(text = stringResource(Res.string.marketplace_status_maintenance))
                        }

                        // Hero: icon + meta
                        ScriptDetailHero(script)

                        // Description
                        script.description?.let { desc ->
                            if (desc.isNotBlank()) {
                                DetailSection(title = null) {
                                    MarkdownText(
                                        text = desc,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // Tags
                        if (script.tags.isNotEmpty()) {
                            DetailSection(title = stringResource(Res.string.marketplace_tags)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm),
                                ) {
                                    script.tags.forEach { tag ->
                                        TagChip(tag)
                                    }
                                }
                            }
                        }

                        // Release notes
                        script.latestRelease?.releaseNotes?.let { notes ->
                            if (notes.isNotBlank()) {
                                DetailSection(title = stringResource(Res.string.marketplace_release_notes)) {
                                    CerealText(
                                        text = notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CerealTheme.colorScheme.contentSecondary,
                                    )
                                }
                            }
                        }

                        // Metadata table
                        DetailSection(title = null) {
                            Column(verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
                                script.latestRelease?.let { release ->
                                    MetaRow(
                                        label = stringResource(Res.string.marketplace_version),
                                        value = release.versionName,
                                    )
                                }
                                script.updatedAt?.let { updated ->
                                    MetaRow(
                                        label = stringResource(Res.string.marketplace_updated),
                                        value = formatMarketplaceDate(updated),
                                    )
                                }
                            }
                        }
                    }

                    // Sticky bottom actions
                    ScriptDetailActions(
                        script = script,
                        installState = installState,
                        onInstall = { viewModel.onInstall(script) },
                        onOpenUrl = viewModel::onOpenUrl,
                        onLoginClick = {
                            authDialogMode.value = AuthenticationDialogMode.LOGIN
                            showAuthDialog.value = true
                        },
                        onRegisterClick = {
                            authDialogMode.value = AuthenticationDialogMode.REGISTRATION
                            showAuthDialog.value = true
                        },
                        onStartNewInstance = viewModel::onStartNewInstance,
                        onRemove = viewModel::onRemoveClicked,
                        onRemoveEnabled = !viewModel.hasRunningTasks.value,
                    )
                }
            }
        }
    }
}
