package com.cereal.client.infrastructure.data.datasource.discord

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Core library binding for the official <a href="https://github.com/discordapp/discord-rpc" target="_blank">Discord RPC SDK</a>.
 * <br>Use {@link #INSTANCE} to access this library.
 *
 * <h1>Supported Architectures</h1>
 * <ul>
 *   <li>Windows x86</li>
 *   <li>Windows x86-64</li>
 *   <li>Linux x86-64</li>
 *   <li>Darwin</li>
 * </ul>
 */
@Suppress("FunctionName")
interface DiscordRPC : Library {
    /**
     * Initializes the library, supply with application details and event handlers.
     * Handlers are only called when the [.Discord_RunCallbacks] method is invoked!
     * <br></br>**Before closing the application it is recommended to call [.Discord_Shutdown]**
     *
     * @param applicationId
     * The ID for this RPC application,
     * retrieved from the [developer dashboard](https://discordappc.com/developers/applications/me)
     * @param handlers
     * Nullable instance of [net.runelite.discord.DiscordEventHandlers]
     * @param autoRegister
     * `true` to automatically call [.Discord_RegisterSteamGame] or [.Discord_Register]
     * @param steamId
     * Possible steam ID of the running game
     */
    fun Discord_Initialize(
        applicationId: String,
        handlers: DiscordEventHandlers?,
        autoRegister: Boolean,
        steamId: String?,
    )

    /**
     * Shuts the RPC connection down.
     * If not currently connected, this does nothing.
     */
    fun Discord_Shutdown()

    /**
     * Executes the registered handlers for currently queued events.
     * <br></br>If this is not called the handlers will not receive any events!
     *
     *
     * It is recommended to call this in a <u>2 second interval</u>
     */
    fun Discord_RunCallbacks()

    /**
     * Polls events from the RPC pipe and pushes the currently queued presence.
     * <br></br>This will be performed automatically if the attached binary
     * has an enabled IO thread (default)
     *
     *
     * **If the IO-Thread has been enabled this will not be supported!**
     */
    fun Discord_UpdateConnection()

    /**
     * Updates the currently set presence of the logged in user.
     * <br></br>Note that the client only updates its presence every **15 seconds**
     * and queues all additional presence updates.
     *
     * @param struct
     * The new presence to use
     *
     * @see net.runelite.discord.DiscordRichPresence
     */
    fun Discord_UpdatePresence(struct: DiscordRichPresence?)

    /**
     * Clears the currently set presence.
     */
    fun Discord_ClearPresence()

    /**
     * Responds to the given user with the specified reply type.
     *
     * <h1>Possible Replies</h1>
     *
     *  * [.DISCORD_REPLY_NO]
     *  * [.DISCORD_REPLY_YES]
     *  * [.DISCORD_REPLY_IGNORE]
     *
     *
     * @param userid
     * The id of the user to respond to
     * @param reply
     * The reply type
     *
     * @see club.minnced.discord.rpc.DiscordUser.userId DiscordUser.userId
     */
    fun Discord_Respond(
        userid: String,
        reply: Int,
    )

    /**
     * Updates the registered event handlers to the provided struct.
     *
     * @param handlers
     * The handlers to update to, or null
     */
    fun Discord_UpdateHandlers(handlers: DiscordEventHandlers?)

    /**
     * Registers the given application so it can be run by the discord client. `discord-<appid>://`
     *
     * @param applicationId
     * The ID of the application to register
     * @param command
     * The command for the application startup, or `null` to use the
     * current executable's path
     */
    fun Discord_Register(
        applicationId: String?,
        command: String?,
    )

    /**
     * Similar to [.Discord_Register] but uses the steam
     * game's installation path.
     *
     * @param applicationId
     * The ID of the application to register
     * @param steamId
     * The steam ID for the game
     */
    fun Discord_RegisterSteamGame(
        applicationId: String?,
        steamId: String?,
    )

    companion object {
        /**
         * Library instance.
         */
        @Suppress("DEPRECATION")
        val INSTANCE: DiscordRPC? = Native.loadLibrary("discord-rpc", DiscordRPC::class.java)

        /**
         * Used to decline a request via [.Discord_Respond]
         * @see .DISCORD_REPLY_YES
         */
        const val DISCORD_REPLY_NO: Int = 0

        /**
         * Used to accept a request via [.Discord_Respond]
         * @see .DISCORD_REPLY_NO
         */
        const val DISCORD_REPLY_YES: Int = 1

        /**
         * Currently unsused response, treated like NO.
         * Used with [.Discord_Respond]
         * @see .DISCORD_REPLY_NO
         */
        const val DISCORD_REPLY_IGNORE: Int = 2
    }
}
