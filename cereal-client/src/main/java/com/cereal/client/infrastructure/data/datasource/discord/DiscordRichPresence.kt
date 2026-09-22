package com.cereal.client.infrastructure.data.datasource.discord

import com.sun.jna.Structure
import java.util.Objects

/*
typedef struct DiscordRichPresence {
    const char* state; // max 128 bytes
    const char* details; // max 128 bytes
    int64_t startTimestamp;
    int64_t endTimestamp;
    const char* largeImageKey; // max 32 bytes
    const char* largeImageText; // max 128 bytes
    const char* smallImageKey; // max 32 bytes
    const char* smallImageText; // max 128 bytes
    const char* partyId; // max 128 bytes
    int partySize;
    int partyMax;
    const char* matchSecret; // max 128 bytes
    const char* joinSecret; // max 128 bytes
    const char* spectateSecret; // max 128 bytes
    int8_t instance;
} DiscordRichPresence;
 */

/**
 * Struct binding for a RichPresence
 */
@Structure.FieldOrder(
    "state",
    "details",
    "startTimestamp",
    "endTimestamp",
    "largeImageKey",
    "largeImageText",
    "smallImageKey",
    "smallImageText",
    "partyId",
    "partySize",
    "partyMax",
    "matchSecret",
    "joinSecret",
    "spectateSecret",
    "instance",
)
class DiscordRichPresence(
    encoding: String = "UTF-8",
) : Structure() {
    /**
     * The user's current party status.
     * <br>Example: "Looking to Play", "Playing Solo", "In a Group"
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var state: String? = null

    /**
     * What the player is currently doing.
     * <br>Example: "Competitive - Captain's Mode", "In Queue", "Unranked PvP"
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var details: String? = null

    /**
     * Unix timestamp (seconds) for the start of the game.
     * <br>Example: 1507665886
     */
    @JvmField var startTimestamp: Long = 0

    /**
     * Unix timestamp (seconds) for the start of the game.
     * <br>Example: 1507665886
     */
    @JvmField var endTimestamp: Long = 0

    /**
     * Name of the uploaded image for the large profile artwork.
     * <br>Example: "default"
     *
     * <p><b>Maximum: 32 characters</b>
     */
    @JvmField var largeImageKey: String? = null

    /**
     * Tooltip for the largeImageKey.
     * <br>Example: "Blade's Edge Arena", "Numbani", "Danger Zone"
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var largeImageText: String? = null

    /**
     * Name of the uploaded image for the small profile artwork.
     * <br>Example: "rogue"
     *
     * <p><b>Maximum: 32 characters</b>
     */
    @JvmField var smallImageKey: String? = null

    /**
     * Tooltip for the smallImageKey.
     * <br>Example: "Rogue - Level 100"
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var smallImageText: String? = null

    /**
     * ID of the player's party, lobby, or group.
     * <br>Example: "ae488379-351d-4a4f-ad32-2b9b01c91657"
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var partyId: String? = null

    /**
     * Current size of the player's party, lobby, or group.
     * <br>Example: 1
     */
    @JvmField var partySize: Int = 0

    /**
     * Maximum size of the player's party, lobby, or group.
     * <br>Example: 5
     */
    @JvmField var partyMax: Int = 0

    /**
     * Unique hashed string for Spectate and Join.
     * Required to enable match interactive buttons in the user's presence.
     * <br>Example: "MmhuZToxMjMxMjM6cWl3amR3MWlqZA=="
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var matchSecret: String? = null

    /**
     * Unique hashed string for Spectate button.
     * This will enable the "Spectate" button on the user's presence if whitelisted.
     * <br>Example: "MTIzNDV8MTIzNDV8MTMyNDU0"
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var joinSecret: String? = null

    /**
     * Unique hashed string for chat invitations and Ask to Join.
     * This will enable the "Ask to Join" button on the user's presence if whitelisted.
     * <br>Example: "MTI4NzM0OjFpMmhuZToxMjMxMjM="
     *
     * <p><b>Maximum: 128 characters</b>
     */
    @JvmField var spectateSecret: String? = null

    /**
     * Marks the matchSecret as a game session with a specific beginning and end.
     * Boolean value of 0 or 1.
     * <br>Example: 1
     */
    @JvmField var instance: Byte = 0

    init {
        stringEncoding = encoding
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscordRichPresence) return false

        return startTimestamp == other.startTimestamp &&
            endTimestamp == other.endTimestamp &&
            partySize == other.partySize &&
            partyMax == other.partyMax &&
            instance == other.instance &&
            state == other.state &&
            details == other.details &&
            largeImageKey == other.largeImageKey &&
            largeImageText == other.largeImageText &&
            smallImageKey == other.smallImageKey &&
            smallImageText == other.smallImageText &&
            partyId == other.partyId &&
            matchSecret == other.matchSecret &&
            joinSecret == other.joinSecret &&
            spectateSecret == other.spectateSecret
    }

    override fun hashCode(): Int =
        Objects.hash(
            state,
            details,
            startTimestamp,
            endTimestamp,
            largeImageKey,
            largeImageText,
            smallImageKey,
            smallImageText,
            partyId,
            partySize,
            partyMax,
            matchSecret,
            joinSecret,
            spectateSecret,
            instance,
        )
}
