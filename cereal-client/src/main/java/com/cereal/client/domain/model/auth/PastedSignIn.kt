package com.cereal.client.domain.model.auth

import kotlinx.coroutines.channels.Channel

/**
 * Pasted sign-in: SSO without a local browser (headless mode). The sign-in flow hands the authorize
 * URL to [showUrl] instead of opening a browser, and every text passed to [paste] (the redirect URL
 * or its query string) races the loopback listener.
 */
class PastedSignIn(
    val showUrl: (String) -> Unit,
) {
    private val pastes = Channel<String>(Channel.UNLIMITED)

    fun paste(text: String) {
        pastes.trySend(text)
    }

    suspend fun awaitPaste(): String = pastes.receive()
}
