package com.cereal.client.domain.provider

/**
 * User-facing logging facade exposed to scripts: formats messages and emits them to the logging
 * sink. This is a *provider* (an outbound integration with the logging backend); persistence of
 * the resulting events is owned by [com.cereal.client.domain.repository.LogEventRepository].
 */
interface LoggerProvider {
    fun error(
        tag: String,
        message: String? = null,
        throwable: Throwable? = null,
        vararg args: Any?,
    )

    fun warning(
        tag: String,
        message: String? = null,
        vararg args: Any?,
    )

    fun info(
        tag: String,
        message: String? = null,
        vararg args: Any?,
    )

    fun debug(
        tag: String,
        message: String? = null,
        vararg args: Any?,
    )
}
