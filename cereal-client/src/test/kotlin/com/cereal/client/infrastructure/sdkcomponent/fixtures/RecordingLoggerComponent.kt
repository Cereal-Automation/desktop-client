package com.cereal.client.infrastructure.sdkcomponent.fixtures

import com.cereal.sdk.component.logger.LoggerComponent

/** Task log that keeps every line as `LEVEL message` for assertions. */
class RecordingLoggerComponent : LoggerComponent {
    val lines = mutableListOf<String>()

    override fun error(
        message: String?,
        throwable: Throwable?,
        vararg args: Any?,
    ) {
        lines += "ERROR $message"
    }

    override fun warn(
        message: String?,
        vararg args: Any?,
    ) {
        lines += "WARN $message"
    }

    override fun info(
        message: String?,
        vararg args: Any?,
    ) {
        lines += "INFO $message"
    }

    override fun debug(
        message: String?,
        vararg args: Any?,
    ) {
        lines += "DEBUG $message"
    }
}
