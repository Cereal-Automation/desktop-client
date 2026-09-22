package com.cereal.client.application.task

import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.sdk.models.Secret as SdkSecret
import com.cereal.sdk.models.proxy.Proxy as SdkProxy

fun Proxy.toComponentProxy(): SdkProxy =
    SdkProxy(
        id.toString(),
        address,
        port,
        username,
        password,
    )

/**
 * Converts the domain credential into the SDK type a running script's configuration method returns.
 *
 * The plaintext crosses here via [Secret.reveal] and is immediately re-wrapped, so it is never held
 * unwrapped. Both types mask on `toString`, so the conversion preserves the display guarantee.
 */
fun Secret.toComponentSecret(): SdkSecret = SdkSecret(reveal())
