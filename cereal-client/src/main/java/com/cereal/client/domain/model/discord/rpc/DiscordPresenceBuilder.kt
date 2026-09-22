@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.discord.rpc

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DiscordPresenceBuilder {
    private var state: String? = null
    private var details: String? = null
    private var startTimestamp: Instant = Instant.fromEpochSeconds(0)
    private var endTimestamp: Instant = Instant.fromEpochSeconds(0)
    private var largeImageKey: String = "default"
    private var largeImageText: String? = null
    private var smallImageKey: String? = null
    private var smallImageText: String? = null
    private var partyId: String? = null
    private var partySize: Int = 0
    private var partyMax: Int = 0
    private var matchSecret: String? = null
    private var joinSecret: String? = null
    private var spectateSecret: String? = null
    private var instance: Boolean = false

    /**
     * The user's current party status.
     * Example: "Looking to Play", "Playing Solo", "In a Group"
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setState(state: String): DiscordPresenceBuilder {
        this.state = state
        return this
    }

    /**
     * What the player is currently doing.
     * Example: "Competitive - Captain's Mode", "In Queue", "Unranked PvP"
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setDetails(details: String): DiscordPresenceBuilder {
        this.details = details
        return this
    }

    /**
     * Unix timestamp (seconds) for the start of the game.
     */
    fun setStartTimestamp(startTimestamp: Instant): DiscordPresenceBuilder {
        this.startTimestamp = startTimestamp
        return this
    }

    /**
     * Unix timestamp (seconds) for the end of the game.
     */
    fun setEndTimestamp(endTimestamp: Instant): DiscordPresenceBuilder {
        this.endTimestamp = endTimestamp
        return this
    }

    /**
     * Name of the uploaded image for the large profile artwork.
     * Example: "default"
     *
     * <b>Maximum: 32 characters</b>
     */
    fun setLargeImageKey(largeImageKey: String): DiscordPresenceBuilder {
        this.largeImageKey = largeImageKey
        return this
    }

    /**
     * Tooltip for the largeImageKey.
     * Example: "Blade's Edge Arena", "Numbani", "Danger Zone"
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setLargeImageText(largeImageText: String): DiscordPresenceBuilder {
        this.largeImageText = largeImageText
        return this
    }

    /**
     * Name of the uploaded image for the small profile artwork.
     * Example: "rogue"
     *
     * <b>Maximum: 32 characters</b>
     */
    fun setSmallImageKey(smallImageKey: String): DiscordPresenceBuilder {
        this.smallImageKey = smallImageKey
        return this
    }

    /**
     * Tooltip for the smallImageKey.
     * Example: "Rogue - Level 100"
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setSmallImageText(smallImageText: String): DiscordPresenceBuilder {
        this.smallImageText = smallImageText
        return this
    }

    /**
     * ID of the player's party, lobby, or group.
     * Example: "ae488379-351d-4a4f-ad32-2b9b01c91657"
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setPartyId(partyId: String): DiscordPresenceBuilder {
        this.partyId = partyId
        return this
    }

    /**
     * Current size of the player's party, lobby, or group.
     * Example: 1
     */
    fun setPartySize(partySize: Int): DiscordPresenceBuilder {
        this.partySize = partySize
        return this
    }

    /**
     * Maximum size of the player's party, lobby, or group.
     * Example: 5
     */
    fun setPartyMax(partyMax: Int): DiscordPresenceBuilder {
        this.partyMax = partyMax
        return this
    }

    /**
     * Unique hashed string for Spectate and Join.
     * Required to enable match interactive buttons in the user's presence.
     * Example: "MmhuZToxMjMxMjM6cWl3amR3MWlqZA=="
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setMatchSecret(matchSecret: String): DiscordPresenceBuilder {
        this.matchSecret = matchSecret
        return this
    }

    /**
     * Unique hashed string for Spectate button.
     * This will enable the "Spectate" button on the user's presence if whitelisted.
     * Example: "MTIzNDV8MTIzNDV8MTMyNDU0"
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setJoinSecret(joinSecret: String): DiscordPresenceBuilder {
        this.joinSecret = joinSecret
        return this
    }

    /**
     * Unique hashed string for chat invitations and Ask to Join.
     * This will enable the "Ask to Join" button on the user's presence if whitelisted.
     * Example: "MTI4NzM0OjFpMmhuZToxMjMxMjM="
     *
     * <b>Maximum: 128 characters</b>
     */
    fun setSpectateSecret(spectateSecret: String): DiscordPresenceBuilder {
        this.spectateSecret = spectateSecret
        return this
    }

    /**
     * Marks the matchSecret as a game session with a specific beginning and end.
     */
    fun setInstance(instance: Boolean): DiscordPresenceBuilder {
        this.instance = instance
        return this
    }

    fun build(): DiscordPresence =
        DiscordPresence(
            state = this.state.orEmpty(),
            details = this.details.orEmpty(),
            startTimestamp = this.startTimestamp,
            endTimestamp = this.endTimestamp,
            largeImageKey = this.largeImageKey,
            largeImageText = this.largeImageText,
            smallImageKey = this.smallImageKey,
            smallImageText = this.smallImageText,
            partyId = this.partyId,
            partySize = this.partySize,
            partyMax = this.partyMax,
            matchSecret = this.matchSecret,
            joinSecret = this.joinSecret,
            spectateSecret = this.spectateSecret,
            instance = this.instance,
        )
}
