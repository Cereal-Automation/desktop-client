package com.cereal.client.domain.model.logging

import java.util.Date

data class LoggingEvent(
    val priority: LoggingPriority,
    val tag: String,
    val message: String,
    val timestamp: Date,
)
