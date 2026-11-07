package com.cereal.client.domain.provider

import com.cereal.client.domain.model.notification.ChannelAttempt
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.task.UserInteraction

/** Remote approval: a waiting confirmation offered with answer buttons on the task's notification channel. */
interface RemoteApprovalProvider {
    /**
     * Sends [confirmation] with Confirm and Decline buttons on the task's channel (per [overrides]). A press completes
     * its `answer`; once the answer completes, by any means, the buttons are stripped with how it ended.
     * Returns the send attempt, or null when the task has no channel that takes buttons.
     */
    suspend fun offer(
        overrides: ScriptNotificationOverrides?,
        confirmation: UserInteraction.Confirmation,
    ): ChannelAttempt?

    /** At quit: strips every message still waiting or being stripped, blocking for at most a few seconds. */
    fun stripAll()
}
