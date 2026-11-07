package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.RemoteApprovalProvider

/** No remote approval channel: confirmations are answered in the window only. */
class InMemoryRemoteApprovalProvider : RemoteApprovalProvider {
    override suspend fun offer(
        overrides: ScriptNotificationOverrides?,
        confirmation: UserInteraction.Confirmation,
    ): ChannelAttempt? = null

    override fun stripAll() = Unit
}
