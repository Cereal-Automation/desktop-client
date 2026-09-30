package com.cereal.client.presentation.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.sdk.component.userinteraction.UserInteractionCanceledException
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.submit
import com.cereal_automation.cereal_client.generated.resources.task_title_with_number
import org.jetbrains.compose.resources.stringResource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Windows for interactions the UI completes itself. A browser prompt is owned by its task
 * (see `BrowserPromptProvider`), so it needs no window here; its status shows on the task row.
 */
@Composable
fun UserInteractionWindow(
    userInteraction: UserInteraction,
    taskNumber: Int? = null,
    closeWindow: () -> Unit,
) {
    if (userInteraction is UserInteraction.TextInput) {
        TextInputWindow(userInteraction, taskNumber, closeWindow)
    }
}

@Composable
private fun TextInputWindow(
    userInteraction: UserInteraction.TextInput,
    taskNumber: Int? = null,
    closeWindow: () -> Unit,
) {
    val windowState = rememberWindowState(width = 400.dp, height = 250.dp)
    var text by remember { mutableStateOf("") }

    Window(
        onCloseRequest = {
            userInteraction.continuation.resumeWithException(UserInteractionCanceledException())
            closeWindow()
        },
        title =
            taskNumber?.let { stringResource(Res.string.task_title_with_number, it, userInteraction.title) }
                ?: userInteraction.title,
        state = windowState,
        alwaysOnTop = true,
    ) {
        CerealTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CerealText(text = userInteraction.description)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    CerealButton(
                        onClick = {
                            userInteraction.continuation.resume(text)
                            closeWindow()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        CerealText(stringResource(Res.string.submit))
                    }
                }
            }
        }
    }
}
