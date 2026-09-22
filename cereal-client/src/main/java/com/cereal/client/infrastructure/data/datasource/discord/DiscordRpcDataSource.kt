package com.cereal.client.infrastructure.data.datasource.discord

import com.cereal.client.domain.model.discord.rpc.DiscordPresence
import com.sun.jna.NativeLibrary
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

// Catching Error here is intentional: loading the native Discord library can fail with a linkage Error.
@Suppress("TooGenericExceptionCaught")
class DiscordRpcDataSource internal constructor(
    private val discordRpc: DiscordRPC?,
    private val discordEventHandlers: DiscordEventHandlers?,
    private val executorServiceFactory: () -> ScheduledExecutorService,
) {
    private val logger = LoggerFactory.getLogger(DiscordRpcDataSource::class.java)
    private var executorService: ScheduledExecutorService? = null
    private var lastKnowPresence: DiscordPresence? = null

    fun initialize() {
        if (discordEventHandlers == null) {
            return
        }

        logger.info("Initializing Discord RPC service.")
        discordEventHandlers.ready = this.ready
        discordEventHandlers.disconnected = this.disconnected
        discordEventHandlers.errored = this.errored
        discordEventHandlers.joinGame = this.joinGame
        discordEventHandlers.spectateGame = this.spectateGame
        discordEventHandlers.joinRequest = this.joinRequest
        discordRpc?.let { discordRpc ->
            discordRpc.Discord_Initialize(DiscordComponentConfig.DISCORD_APP_ID, discordEventHandlers, true, null)
            // A ScheduledExecutorService cannot be restarted after shutdown, so a previous close()
            // (e.g. on logout) permanently terminates it. Because this data source is an app-wide
            // singleton, a subsequent initialize() (e.g. on re-login) would otherwise schedule onto the
            // dead executor and throw RejectedExecutionException. Always start from a fresh executor.
            executorService?.shutdownNow()
            executorService =
                executorServiceFactory().also { executor ->
                    executor.scheduleAtFixedRate(discordRpc::Discord_RunCallbacks, 0, 2, TimeUnit.SECONDS)
                }
        }
    }

    fun close() {
        executorService?.shutdownNow()
        executorService = null
        discordRpc?.Discord_Shutdown()
    }

    private val ready =
        object : DiscordEventHandlers.OnReady {
            override fun accept(user: DiscordUser) {
                logger.info("Discord RPC service is ready with user ${user.username}.")
            }
        }

    private val disconnected =
        object : DiscordEventHandlers.OnStatus {
            override fun accept(
                errorCode: Int,
                message: String,
            ) {
                logger.debug("Discord disconnected $errorCode: $message")
            }
        }

    private val errored =
        object : DiscordEventHandlers.OnStatus {
            override fun accept(
                errorCode: Int,
                message: String,
            ) {
                logger.debug("Discord error: $errorCode - $message")
            }
        }

    private val joinGame =
        object : DiscordEventHandlers.OnGameUpdate {
            override fun accept(secret: String) {
                logger.debug("Discord join game: $secret")
            }
        }

    private val spectateGame =
        object : DiscordEventHandlers.OnGameUpdate {
            override fun accept(secret: String) {
                logger.debug("Discord spectate game: $secret")
            }
        }

    private val joinRequest =
        object : DiscordEventHandlers.OnJoinRequest {
            override fun accept(request: DiscordUser) {
                logger.info("Discord join request: ${request.username}.")
            }
        }

    /**
     * Updates the currently set presence of the logged in user.
     * <br></br>Note that the client only updates its presence every **15 seconds**
     * and queues all additional presence updates.
     *
     * @param discordPresence The new presence to use
     */
    fun updatePresence(discordPresence: DiscordPresence) {
        if (discordRpc == null) {
            return
        }

        lastKnowPresence = discordPresence

        val discordRichPresence = discordPresence.mapToDiscordRichPresence()
        logger.debug("Sending presence update {}", discordPresence)
        discordRpc.Discord_UpdatePresence(discordRichPresence)
    }

    fun restorePresence() {
        lastKnowPresence?.let {
            updatePresence(it)
        }
    }

    /**
     * Clears the currently set presence.
     */
    fun clearPresence() {
        discordRpc?.Discord_ClearPresence()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DiscordRpcDataSource::class.java)

        /**
         * Builds a [DiscordRpcDataSource] wired to the native Discord RPC library. Loading the native
         * library can fail with a linkage [Error] (e.g. on unsupported platforms); when it does, Discord
         * support is disabled and the returned data source becomes a no-op.
         */
        fun create(): DiscordRpcDataSource {
            var discordRpc: DiscordRPC? = null
            var discordEventHandlers: DiscordEventHandlers? = null
            try {
                registerBundledLibrarySearchPath()
                discordRpc = DiscordRPC.INSTANCE
                discordEventHandlers = DiscordEventHandlers()
            } catch (error: Error) {
                logger.warn("Failed to load Discord library, Discord support will be disabled.", error)
            }
            return DiscordRpcDataSource(
                discordRpc = discordRpc,
                discordEventHandlers = discordEventHandlers,
                executorServiceFactory = { Executors.newSingleThreadScheduledExecutor() },
            )
        }

        /**
         * Point JNA at the native library that ships inside the (code-signed) Compose application resources
         * directory instead of letting JNA extract a copy from the jar to a temporary directory.
         *
         * On macOS the released app runs with a hardened runtime; an ad-hoc signed library extracted to a temp
         * path is rejected at load time, while the copy bundled in the app resources is signed with the app's
         * identity and loads correctly. On other platforms this is a no-op fallback to JNA's default behaviour.
         */
        private fun registerBundledLibrarySearchPath() {
            val resourcesDir = System.getProperty("compose.application.resources.dir") ?: return
            if (File(resourcesDir).isDirectory) {
                NativeLibrary.addSearchPath("discord-rpc", resourcesDir)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
fun DiscordPresence.mapToDiscordRichPresence(): DiscordRichPresence {
    val discordRichPresence = DiscordRichPresence()
    discordRichPresence.state = this.state
    discordRichPresence.details = this.details
    discordRichPresence.startTimestamp = this.startTimestamp.epochSeconds
    discordRichPresence.endTimestamp = this.endTimestamp.epochSeconds
    discordRichPresence.largeImageKey = this.largeImageKey
    discordRichPresence.largeImageText = this.largeImageText
    discordRichPresence.smallImageKey = this.smallImageKey
    discordRichPresence.smallImageText = this.smallImageText
    discordRichPresence.partyId = this.partyId
    discordRichPresence.partySize = this.partySize
    discordRichPresence.partyMax = this.partyMax
    discordRichPresence.matchSecret = this.matchSecret
    discordRichPresence.joinSecret = this.joinSecret
    discordRichPresence.spectateSecret = this.spectateSecret
    discordRichPresence.instance = (if (this.instance) 1 else 0).toByte()
    return discordRichPresence
}
