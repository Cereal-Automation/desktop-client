package com.cereal.client.presentation.error

import androidx.compose.runtime.Composable
import com.cereal.client.application.exception.FieldOptionsTimeoutException
import com.cereal.client.application.exception.StopTaskFirstException
import com.cereal.client.application.exception.TaskActionAlreadyRunningException
import com.cereal.client.application.exception.TaskActionInProgressException
import com.cereal.client.application.exception.TaskActionTimeoutException
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.field_options_timeout
import com.cereal_automation.cereal_client.generated.resources.task_action_already_running
import com.cereal_automation.cereal_client.generated.resources.task_action_in_progress
import com.cereal_automation.cereal_client.generated.resources.task_action_stop_task_first
import com.cereal_automation.cereal_client.generated.resources.task_action_timeout
import org.jetbrains.compose.resources.stringResource

/** The localized text for an application exception that has one, or null to fall back to its message. */
@Composable
fun localizedErrorMessage(error: Exception): String? =
    when (error) {
        is TaskActionTimeoutException -> stringResource(Res.string.task_action_timeout, error.timeout.inWholeSeconds)
        is FieldOptionsTimeoutException -> stringResource(Res.string.field_options_timeout, error.timeout.inWholeSeconds)
        is StopTaskFirstException -> stringResource(Res.string.task_action_stop_task_first)
        is TaskActionAlreadyRunningException -> stringResource(Res.string.task_action_already_running)
        is TaskActionInProgressException -> stringResource(Res.string.task_action_in_progress)
        else -> null
    }
