package com.cereal.client.application.task

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long the client waits on script code it calls outside a task run (form options, task actions). */
val SCRIPT_CALL_TIMEOUT: Duration = 15.seconds
