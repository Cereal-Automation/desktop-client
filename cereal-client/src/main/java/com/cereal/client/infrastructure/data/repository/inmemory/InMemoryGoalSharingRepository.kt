package com.cereal.client.infrastructure.data.repository.inmemory

import com.cereal.client.domain.repository.GoalSharingRepository
import java.util.UUID

class InMemoryGoalSharingRepository(
    @Volatile private var enabled: Boolean = true,
    @Volatile private var noticeSeen: Boolean = false,
    private val installId: String = UUID.randomUUID().toString(),
) : GoalSharingRepository {
    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun isNoticeSeen(): Boolean = noticeSeen

    override fun setNoticeSeen() {
        noticeSeen = true
    }

    override fun installId(): String = installId
}
