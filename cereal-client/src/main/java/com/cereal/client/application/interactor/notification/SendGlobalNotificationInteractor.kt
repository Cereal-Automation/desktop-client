package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.notification.ChannelResolution
import com.cereal.client.domain.model.notification.NotificationResolver
import com.cereal.client.domain.model.notification.ScriptNotification
import com.cereal.client.domain.provider.NotificationProvider
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sends a client notification that belongs to no task (session lost, the restart report) to the
 * global channels: the same config snapshot and resolver as a script send, without overrides. Each
 * attempt is logged, not recorded: the notification history stays task-bound (ADR-0005). A failing
 * channel never skips the others.
 */
class SendGlobalNotificationInteractor(
    private val notificationProvider: NotificationProvider,
    private val globalNotificationConfigReader: GlobalNotificationConfigReader,
    private val notificationResolver: NotificationResolver,
) : Interactor<Unit, SendGlobalNotificationInteractor.Params>() {
    private val logger = LoggerFactory.getLogger(SendGlobalNotificationInteractor::class.java)

    override suspend fun run(params: Params) {
        val resolutions =
            notificationResolver.resolve(
                request = ScriptNotification(params.title, params.message, plainText = true),
                overrides = null,
                config = globalNotificationConfigReader.read(),
            )
        for (resolution in resolutions) {
            when (resolution) {
                is ChannelResolution.Resolved -> {
                    try {
                        notificationProvider.sendNotification(resolution.data)
                        logger.info("Sent \"{}\" via {}", params.title, resolution.channel)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn("Could not send \"{}\" via {}: {}", params.title, resolution.channel, e.message)
                    }
                }

                is ChannelResolution.MissingConfig -> {
                    logger.warn("Could not send \"{}\" via {}: {}", params.title, resolution.channel, resolution.reason)
                }
            }
        }
    }

    data class Params(
        val title: String,
        val message: String,
    )
}
