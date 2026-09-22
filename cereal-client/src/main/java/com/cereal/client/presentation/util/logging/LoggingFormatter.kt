package com.cereal.client.presentation.util.logging

import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import java.text.SimpleDateFormat
import java.util.Locale

fun LoggingEvent.formatted(): String {
    val dateFormatted = SimpleDateFormat("dd/MM/yyy HH:mm:ss").format(this.timestamp)
    return "[${this.tag}][${this.priority.formatted().uppercase(Locale.getDefault())}][$dateFormatted] ${this.message}"
}

fun LoggingPriority.formatted(): String =
    when (this) {
        LoggingPriority.ERROR -> "Error"
        LoggingPriority.WARNING -> "Warning"
        LoggingPriority.INFO -> "Info"
        LoggingPriority.DEBUG -> "Debug"
    }
