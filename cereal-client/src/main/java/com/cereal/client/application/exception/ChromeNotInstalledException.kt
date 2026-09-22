package com.cereal.client.application.exception

class ChromeNotInstalledException(
    cause: Throwable? = null,
) : CerealException(
        message =
            "Google Chrome is required but could not be found on your system. " +
                "Please install Google Chrome and try again.",
        cause = cause,
    )
