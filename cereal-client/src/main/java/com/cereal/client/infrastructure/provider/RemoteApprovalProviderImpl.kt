package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.RemoteApprovalProvider
import com.cereal.client.infrastructure.data.notification.telegram.TelegramRemoteApproval

/** Remote approval over Telegram, the only channel with answer buttons today. */
class RemoteApprovalProviderImpl(
    private val telegram: TelegramRemoteApproval,
) : RemoteApprovalProvider {
    override suspend fun offer(
        overrides: ScriptNotificationOverrides?,
        confirmation: UserInteraction.Confirmation,
    ): ChannelAttempt? = telegram.offer(overrides, confirmation)

    override fun stripAll() = telegram.stripAll()
}
