package com.cereal.client.infrastructure.data.notification.telegram

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.cereal.sdk.component.notification.telegram.model.TelegramMessage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger
    private lateinit var client: TelegramHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = TelegramHttpClient(baseUrl = server.url("").toString().trimEnd('/'))

        logger = LoggerFactory.getLogger(TelegramHttpClient::class.java) as Logger
        logAppender =
            ListAppender<ILoggingEvent>().also {
                it.start()
                logger.addAppender(it)
            }
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
        server.shutdown()
    }

    @Test
    fun `sendMessage should not log message text at debug level`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))
            val sensitiveText = "Order confirmed: 42 Main St, John Doe, card ending 1234"

            client.sendMessage(
                botToken = "fake-bot-token",
                telegramMessage =
                    TelegramMessage(
                        chatId = "987654321",
                        text = sensitiveText,
                    ),
            )

            val allLogMessages = logAppender.list.map { it.formattedMessage }
            allLogMessages.forEach { message ->
                assertFalse(
                    message.contains(sensitiveText),
                    "Log must not contain sensitive message text, but found: $message",
                )
            }
        }

    @Test
    fun `sendMessage should not dump full TelegramMessage object to debug log`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))

            client.sendMessage(
                botToken = "fake-bot-token",
                telegramMessage =
                    TelegramMessage(
                        chatId = "987654321",
                        text = "Hello",
                    ),
            )

            val debugMessages =
                logAppender.list
                    .filter { it.level == Level.DEBUG }
                    .map { it.formattedMessage }

            debugMessages.forEach { message ->
                assertFalse(
                    message.contains("TelegramMessage("),
                    "Log must not dump the full TelegramMessage object: $message",
                )
            }
        }

    @Test
    fun `sendMessage should not log response body on permanent failure`() =
        runTest {
            val sensitiveResponseBody = """{"ok":false,"error_code":400,"description":"chat not found"}"""
            server.enqueue(MockResponse().setResponseCode(400).setBody(sensitiveResponseBody))

            client.sendMessage(
                botToken = "fake-bot-token",
                telegramMessage = TelegramMessage(chatId = "999", text = "Hi"),
            )

            val warnMessages =
                logAppender.list
                    .filter { it.level == Level.WARN }
                    .map { it.formattedMessage }

            assertTrue(warnMessages.isNotEmpty(), "Expected a WARN log on failure")
            warnMessages.forEach { message ->
                assertFalse(
                    message.contains("chat not found"),
                    "Failure log must not include response body, but found: $message",
                )
                assertTrue(
                    message.contains("400") || message.contains("statusCode"),
                    "Failure log should include the status code",
                )
            }
        }
}
