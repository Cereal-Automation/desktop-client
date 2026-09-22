package com.cereal.client.application.interactor.settings.notifications

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.domain.model.notification.DiscordEmbed
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.SystemNotificationData
import com.cereal.client.domain.model.notification.TelegramNotificationData
import com.cereal.client.domain.model.notification.TelegramParseMode
import com.cereal.client.domain.provider.NotificationProvider

class SendNotificationTestMessageInteractor(
    private val applicationConfig: ApplicationConfig,
    private val notificationRepository: NotificationProvider,
) : Interactor<Unit, SendNotificationTestMessageInteractor.Params>() {
    override suspend fun run(params: Params) {
        val notification =
            when (params) {
                is Params.System -> {
                    SystemNotificationData(
                        title = "Desktop Notification Test",
                        message = "This is a test notification from Cereal! Your desktop notifications are working correctly.",
                    )
                }

                is Params.Discord -> {
                    DiscordNotificationData(
                        username = applicationConfig.name,
                        webhookUrl = params.webhookUrl,
                        embeds =
                            listOf(
                                DiscordEmbed(
                                    title = "Success!",
                                    description =
                                        "This is a test from Cereal using your webhook! " +
                                            "By seeing this message, it means your webhook is setup correctly.",
                                    color = "6613812",
                                ),
                            ),
                    )
                }

                is Params.Telegram -> {
                    TelegramNotificationData(
                        text = "🔔 *Telegram Test*\n\nThis is a test message from Cereal!\n\n✅ Your Telegram integration is working correctly.",
                        parseMode = TelegramParseMode.MARKDOWN,
                        botToken = params.botToken,
                        chatId = params.chatId,
                    )
                }

                is Params.Email -> {
                    EmailNotificationData(
                        subject = "Cereal Email Test",
                        body = "📧 Email Test\n\nThis is a test email from Cereal!\n\n✅ Your email integration is working correctly.",
                        smtpHost = params.smtpHost,
                        smtpPort = params.smtpPort,
                        username = params.username,
                        password = params.password,
                        from = params.from,
                        to = params.to,
                        useTls = params.useTls,
                    )
                }
            }

        notificationRepository.sendNotification(notification)
    }

    sealed class Params : SensitiveParams {
        object System : Params()

        data class Discord(
            val webhookUrl: String,
        ) : Params()

        data class Telegram(
            val botToken: String,
            val chatId: String,
        ) : Params()

        data class Email(
            val smtpHost: String,
            val smtpPort: Int,
            val username: String,
            val password: String,
            val from: String,
            val to: String,
            val useTls: Boolean,
        ) : Params()
    }
}
