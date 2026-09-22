package com.cereal.client.application.exception

class ScriptInstanceNotFoundException(
    message: String = "We couldn't find that script. Try refreshing, or reinstall it from the marketplace.",
) : CerealException(message)
