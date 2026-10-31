package com.cereal.client.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.SettingsButtonVariant
import com.cereal.client.presentation.view.SettingsSmallButton
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.home_all_in_tasks
import com.cereal_automation.cereal_client.generated.resources.home_recent_runs
import com.cereal_automation.cereal_client.generated.resources.home_recent_runs_empty
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

/** Recent goal runs; independent of the composer slot's state. */
@Composable
fun HomeActivitySections(
    onOpenTasks: (focusTaskId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<HomeActivityViewModel>(HomeActivityViewModel::class.java, parameters = { parametersOf(scope) })
        }

    Section(
        title = stringResource(Res.string.home_recent_runs),
        action = stringResource(Res.string.home_all_in_tasks),
        onAction = { onOpenTasks(null) },
        empty = stringResource(Res.string.home_recent_runs_empty).takeIf { viewModel.recentRuns.value.isEmpty() },
    ) {
        viewModel.recentRuns.value.forEachIndexed { index, run ->
            if (index > 0) RowDivider()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpenTasks(run.taskId) }
                        .padding(CerealTheme.spacing.md),
            ) {
                CerealText(
                    text = run.goal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CerealText(
                    text = listOfNotNull(run.status, run.mode, run.site, run.cost).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = CerealTheme.colorScheme.contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    action: String,
    onAction: () -> Unit,
    empty: String?,
    items: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CerealText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            SettingsSmallButton(text = action, onClick = onAction, variant = SettingsButtonVariant.Ghost)
        }
        CerealCard(modifier = Modifier.fillMaxWidth()) {
            if (empty != null) {
                CerealText(
                    text = empty,
                    color = CerealTheme.colorScheme.contentTertiary,
                    modifier = Modifier.padding(CerealTheme.spacing.md),
                )
            } else {
                Column {
                    items()
                }
            }
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)
