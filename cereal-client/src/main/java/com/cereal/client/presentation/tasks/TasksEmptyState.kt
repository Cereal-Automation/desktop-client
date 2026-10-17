package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_action_add_script
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_action_create_group
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_message
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_title
import com.cereal_automation.cereal_client.generated.resources.tasks_no_script_selected_message
import com.cereal_automation.cereal_client.generated.resources.tasks_no_script_selected_title
import com.cereal_automation.cereal_client.generated.resources.tasks_no_tasks_message
import com.cereal_automation.cereal_client.generated.resources.tasks_no_tasks_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun TasksEmptyState(
    onCreateGroup: () -> Unit,
    onAddScript: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CerealTheme.colorScheme.cardDark)
                .padding(CerealTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md, Alignment.CenterVertically),
    ) {
        CerealText(
            text = stringResource(Res.string.tasks_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        CerealText(
            text = stringResource(Res.string.tasks_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = CerealTheme.colorScheme.contentSecondary,
            textAlign = TextAlign.Center,
        )
        CerealButton(
            onClick = onCreateGroup,
            text = stringResource(Res.string.tasks_empty_action_create_group),
            type = CerealButtonType.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        CerealButton(
            onClick = onAddScript,
            text = stringResource(Res.string.tasks_empty_action_add_script),
            type = CerealButtonType.Surface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Shown in the tasks pane when no script is selected, or the selected script has no tasks. */
@Composable
internal fun TasksDetailEmptyState(scriptSelected: Boolean) {
    val title = if (scriptSelected) Res.string.tasks_no_tasks_title else Res.string.tasks_no_script_selected_title
    val message = if (scriptSelected) Res.string.tasks_no_tasks_message else Res.string.tasks_no_script_selected_message
    Column(
        modifier = Modifier.fillMaxSize().padding(CerealTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CerealTheme.spacing.md, Alignment.CenterVertically),
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
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = CerealTheme.colorScheme.contentSubtle,
                modifier = Modifier.size(28.dp),
            )
        }
        CerealText(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        CerealText(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = CerealTheme.colorScheme.contentSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }
}
