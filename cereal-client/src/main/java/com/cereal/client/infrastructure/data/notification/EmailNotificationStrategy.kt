package com.cereal.client.infrastructure.data.notification

import com.cereal.client.domain.model.notification.EmailNotificationData
import com.cereal.client.domain.model.notification.NotificationStrategy
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Strategy for sending notifications via Email using SMTP.
 */
class EmailNotificationStrategy : NotificationStrategy<EmailNotificationData> {
    private val logger = LoggerFactory.getLogger(EmailNotificationStrategy::class.java)

    override suspend fun send(data: EmailNotificationData) {
        val smtpHost = data.smtpHost
        val smtpPort = data.smtpPort
        val username = data.username
        val password = data.password
        val from = data.from
        val to = data.to
        val useTls = data.useTls ?: false

        try {
            withContext(Dispatchers.IO) {
                val properties =
                    Properties().apply {
                        put("mail.smtp.host", smtpHost)
                        put("mail.smtp.port", smtpPort.toString())
                        put("mail.smtp.auth", "true")
                        if (useTls) {
                            put("mail.smtp.starttls.enable", "true")
                        }
                    }

                val session =
                    Session.getInstance(
                        properties,
                        object : Authenticator() {
                            override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(username, password)
                        },
                    )

                val message =
                    MimeMessage(session).apply {
                        setFrom(InternetAddress(from))
                        setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                        subject = data.subject
                        setText(data.body)
                    }

                Transport.send(message)
                logger.info("Email notification sent successfully to $to")
            }
        } catch (e: Exception) {
            logger.error("Failed to send email notification", e)
            throw e
        }
    }
}
