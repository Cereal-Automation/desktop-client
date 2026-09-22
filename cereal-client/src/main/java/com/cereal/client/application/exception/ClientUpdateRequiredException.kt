package com.cereal.client.application.exception

class ClientUpdateRequiredException(
    val sdkVersion: String,
) : CerealException(
        "This script requires a newer version of Cereal. Please update your Cereal client.",
    )
