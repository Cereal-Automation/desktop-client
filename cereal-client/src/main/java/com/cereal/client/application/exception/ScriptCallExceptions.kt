package com.cereal.client.application.exception

import kotlin.time.Duration

// These messages are for the logs; the presentation layer shows localized text per type (see userMessage).

/** Script code (a task action or a field's options `load`) threw; carries the script's own message. */
class ScriptCallException(
    cause: Exception,
) : CerealException(cause.message ?: cause::class.simpleName.orEmpty(), cause)

class TaskActionTimeoutException(
    val timeout: Duration,
) : CerealException("The action did not finish within ${timeout.inWholeSeconds} seconds.")

class FieldOptionsTimeoutException(
    val timeout: Duration,
) : CerealException("Loading the options took longer than ${timeout.inWholeSeconds} seconds.")

class StopTaskFirstException : CerealException("Stop the task first.")

class TaskActionAlreadyRunningException : CerealException("A task action is already running.")

class TaskActionInProgressException : CerealException("Wait for the task action to finish before starting the task.")
