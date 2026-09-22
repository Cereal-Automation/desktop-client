package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.provider.LoggerProvider

/**
 * No-op [LoggerProvider] for the `mock` flavor and tests.
 */
class InMemoryLoggerProvider : LoggerProvider {
    override fun error(
        tag: String,
        message: String?,
        throwable: Throwable?,
        vararg args: Any?,
    ) = Unit

    override fun warning(
        tag: String,
        message: String?,
        vararg args: Any?,
    ) = Unit

    override fun info(
        tag: String,
        message: String?,
        vararg args: Any?,
    ) = Unit

    override fun debug(
        tag: String,
        message: String?,
        vararg args: Any?,
    ) = Unit
}
