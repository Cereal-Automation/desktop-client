package com.cereal.client.application.task

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a one-off call into script code (field options, task actions) may take before it is abandoned. */
val SCRIPT_CALL_TIMEOUT: Duration = 15.seconds
