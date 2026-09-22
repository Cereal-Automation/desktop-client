package com.cereal.client.domain.model.notification

/**
 * The outcome of resolving one notification channel. The [NotificationResolver] returns one of
 * these per channel that is in play (enabled, or overridden by the script).
 */
sealed class ChannelResolution {
    abstract val channel: NotificationChannelType

    /**
     * A sendable payload for [channel]. [payloadPreview] is the public text recorded in history on
     * a successful send (e.g. Discord content, Telegram text).
     */
    data class Resolved(
        override val channel: NotificationChannelType,
        val data: Notification,
        val payloadPreview: String?,
    ) : ChannelResolution()

    /**
     * The channel could not be turned into a sendable payload — either a required field was absent
     * from the script, the override, and the global settings, or a resolved value failed the
     * channel's own validation. [reason] is the recorded failure message; nothing is sent.
     */
    data class MissingConfig(
        override val channel: NotificationChannelType,
        val reason: String,
    ) : ChannelResolution()
}
