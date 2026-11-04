package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.repository.DiagnosticsSharingRepository
import java.util.UUID

class InMemoryDiagnosticsSharingRepository(
    @Volatile private var enabled: Boolean = true,
    private val installId: String = UUID.randomUUID().toString(),
) : DiagnosticsSharingRepository {
    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun installId(): String = installId
}
