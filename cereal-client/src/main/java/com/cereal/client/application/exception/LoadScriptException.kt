package com.cereal.client.application.exception

class LoadScriptException(
    val packageName: String,
) : CerealException("Unable to load script from disk.")
