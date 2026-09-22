package com.cereal.client.infrastructure.data.datasource.discord

import com.sun.jna.Callback
import com.sun.jna.Structure
import java.util.Objects

/*
typedef struct DiscordEventHandlers {
    void (*ready)(DiscordUser*);
    void (*disconnected)(int errorCode, const char* message);
    void (*errored)(int errorCode, const char* message);
    void (*joinGame)(const char* joinSecret);
    void (*spectateGame)(const char* spectateSecret);
    void (*joinRequest)(const DiscordUser* request);
} DiscordEventHandlers;
 */

/**
 * Struct containing handlers for RPC events
 * <br>Provided handlers can be null.
 */
@Structure.FieldOrder(
    "ready",
    "disconnected",
    "errored",
    "joinGame",
    "spectateGame",
    "joinRequest",
)
class DiscordEventHandlers : Structure() {
    /**
     * Handler function for the ready event
     */
    interface OnReady : Callback {
        fun accept(user: DiscordUser)
    }

    /**
     * Handler function for the exceptional events (error, disconnect)
     */
    interface OnStatus : Callback {
        fun accept(
            errorCode: Int,
            message: String,
        )
    }

    /**
     * Handler function for game update events (joinGame, spectateGame)
     */
    interface OnGameUpdate : Callback {
        fun accept(secret: String)
    }

    /**
     * Handler function for user join requests
     */
    interface OnJoinRequest : Callback {
        fun accept(request: DiscordUser)
    }

    /**
     * Called when the RPC connection has been established
     */
    @JvmField var ready: OnReady? = null

    /**
     * Called when the RPC connection has been severed
     */
    @JvmField var disconnected: OnStatus? = null

    /**
     * Called when an internal error is caught within the SDK
     */
    @JvmField var errored: OnStatus? = null

    /**
     * Called when the logged in user joined a game
     */
    @JvmField var joinGame: OnGameUpdate? = null

    /**
     * Called when the logged in user joined to spectate a game
     */
    @JvmField var spectateGame: OnGameUpdate? = null

    /**
     * Called when another discord user wants to join the game of the logged in user
     */
    @JvmField var joinRequest: OnJoinRequest? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscordEventHandlers) return false

        return ready == other.ready &&
            disconnected == other.disconnected &&
            errored == other.errored &&
            joinGame == other.joinGame &&
            spectateGame == other.spectateGame &&
            joinRequest == other.joinRequest
    }

    override fun hashCode(): Int =
        Objects.hash(
            ready,
            disconnected,
            errored,
            joinGame,
            spectateGame,
            joinRequest,
        )
}
