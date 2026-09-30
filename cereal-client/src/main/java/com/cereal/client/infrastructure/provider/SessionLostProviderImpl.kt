package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.provider.SessionLostProvider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Fed by the marketplace client's authorization interceptor. Pure, so the in-memory graph binds it too. */
class SessionLostProviderImpl : SessionLostProvider {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun report() {
        events.tryEmit(Unit)
    }

    override fun observeSessionLost(): Flow<Unit> = events
}
