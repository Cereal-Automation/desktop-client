package com.cereal.client.domain.provider

import kotlinx.coroutines.flow.Flow

/**
 * Reports **session lost**: the marketplace rejected the signed-in session (a `401` on an
 * authenticated call), usually because the same account signed in elsewhere. Network and server
 * failures are not session lost.
 */
interface SessionLostProvider {
    fun observeSessionLost(): Flow<Unit>
}
