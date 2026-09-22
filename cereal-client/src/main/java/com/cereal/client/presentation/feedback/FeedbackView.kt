package com.cereal.client.presentation.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealSnackbarHost
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.close
import org.jetbrains.compose.resources.stringResource

@Composable
fun feedbackView(feedbackAction: FeedbackAction) {
    val snackbarHostState = remember { SnackbarHostState() }
    val closeText = stringResource(Res.string.close)

    LaunchedEffect(feedbackAction) {
        when (feedbackAction) {
            is FeedbackAction.Error -> {
                snackbarHostState.showSnackbar(
                    message = feedbackAction.message,
                    actionLabel = closeText,
                )
                feedbackAction.dismiss()
            }

            is FeedbackAction.Success -> {
                snackbarHostState.showSnackbar(
                    message = feedbackAction.message,
                    actionLabel = closeText,
                )
                feedbackAction.dismiss()
            }

            FeedbackAction.None -> {
                // No snackbar to display
            }
        }
    }

    val containerColor =
        when (feedbackAction) {
            is FeedbackAction.Success -> MaterialTheme.colorScheme.primary
            is FeedbackAction.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surface
        }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        CerealSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp),
            containerColor = containerColor,
        )
    }
}
