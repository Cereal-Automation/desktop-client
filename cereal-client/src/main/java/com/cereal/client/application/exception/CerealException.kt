package com.cereal.client.application.exception

open class CerealException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
