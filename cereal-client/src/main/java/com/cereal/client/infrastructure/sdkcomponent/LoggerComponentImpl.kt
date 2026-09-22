package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.domain.provider.LoggerProvider
import com.cereal.sdk.component.logger.LoggerComponent

class LoggerComponentImpl(
    private val logTag: String,
    private val loggerProvider: LoggerProvider,
) : LoggerComponent {
    override fun error(
        message: String?,
        throwable: Throwable?,
        vararg args: Any?,
    ) {
        loggerProvider.error(
            tag = logTag,
            message = message,
            throwable = throwable,
            args = args,
        )
    }

    override fun warn(
        message: String?,
        vararg args: Any?,
    ) {
        loggerProvider.warning(
            tag = logTag,
            message = message,
            args = args,
        )
    }

    override fun info(
        message: String?,
        vararg args: Any?,
    ) {
        loggerProvider.info(
            tag = logTag,
            message = message,
            args = args,
        )
    }

    override fun debug(
        message: String?,
        vararg args: Any?,
    ) {
        loggerProvider.debug(
            tag = logTag,
            message = message,
            args = args,
        )
    }
}
