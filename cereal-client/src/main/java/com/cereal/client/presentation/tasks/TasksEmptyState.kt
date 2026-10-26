package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.new_task_setup
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_home_link
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_message
import com.cereal_automation.cereal_client.generated.resources.tasks_empty_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun TasksEmptyState(
    onNewTaskSetup: () -> Unit,
    onNavigateToHome: () -> Unit,
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
            onClick = onNewTaskSetup,
            text = stringResource(Res.string.new_task_setup),
            type = CerealButtonType.Primary,
        )
        CerealText(
            text = stringResource(Res.string.tasks_empty_home_link),
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color = CerealTheme.colorScheme.contentSecondary,
            modifier = Modifier.clickable(onClick = onNavigateToHome),
        )
    }
}
