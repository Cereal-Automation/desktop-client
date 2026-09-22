package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_action_add_script
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_action_create_group
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_message
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_title
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
