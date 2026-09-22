@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.discord.rpc

import com.cereal.client.domain.model.exception.InvalidDiscordPresenceException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class DiscordPresence(
    val state: String,
    val details: String,
    val startTimestamp: Instant,
    val endTimestamp: Instant,
    val largeImageKey: String,
    val largeImageText: String?,
    val smallImageKey: String?,
    val smallImageText: String?,
    val partyId: String?,
    val partySize: Int,
    val partyMax: Int,
    val matchSecret: String?,
    val joinSecret: String?,
    val spectateSecret: String?,
    val instance: Boolean,
) {
    init {
        requireMaxLength(state, MAX_STATE_LENGTH, "state")
        requireMaxLength(details, MAX_DETAILS_LENGTH, "details")
        requireMaxLength(largeImageKey, MAX_IMAGE_KEY_LENGTH, "largeImageKey")
        requireMaxLength(largeImageText, MAX_IMAGE_TEXT_LENGTH, "largeImageText")
        requireMaxLength(smallImageKey, MAX_IMAGE_KEY_LENGTH, "smallImageKey")
        requireMaxLength(smallImageText, MAX_IMAGE_TEXT_LENGTH, "smallImageText")
        requireMaxLength(partyId, MAX_SECRET_LENGTH, "partyId")
        requireMaxLength(matchSecret, MAX_SECRET_LENGTH, "matchSecret")
        requireMaxLength(joinSecret, MAX_SECRET_LENGTH, "joinSecret")
        requireMaxLength(spectateSecret, MAX_SECRET_LENGTH, "spectateSecret")
        if (partySize < 0) throw InvalidDiscordPresenceException("partySize cannot be negative")
        if (partyMax < 0) throw InvalidDiscordPresenceException("partyMax cannot be negative")
    }

    private fun requireMaxLength(
        value: String?,
        max: Int,
        field: String,
    ) {
        if (value != null && value.length > max) {
            throw InvalidDiscordPresenceException("$field cannot be longer than $max characters")
        }
    }

    companion object {
        private const val MAX_STATE_LENGTH = 128
        private const val MAX_DETAILS_LENGTH = 128
        private const val MAX_IMAGE_KEY_LENGTH = 32
        private const val MAX_IMAGE_TEXT_LENGTH = 128
        private const val MAX_SECRET_LENGTH = 128
    }
}
