package com.cereal.client.infrastructure.data.datasource.os

import org.slf4j.LoggerFactory
import java.awt.Image
import java.io.IOException
import java.util.concurrent.TimeUnit

/*
* for the sake of beauty, this might need to be converted to push notifications with APNS
*/
class MacNotificationDataSource : NotificationDataSource {
    private val logger = LoggerFactory.getLogger(MacNotificationDataSource::class.java)

    companion object {
        // Environment variables used to pass the variable parts of the notification
        // into the AppleScript program. This avoids concatenating attacker-influenced
        // text into the AppleScript source, which would otherwise allow breaking out
        // of the string literal (e.g. via a trailing backslash or a double quote).
        private const val ENV_MESSAGE = "CEREAL_NOTIFICATION_MESSAGE"
        private const val ENV_TITLE = "CEREAL_NOTIFICATION_TITLE"
    }

    private val terminalNotifierAvailable: Boolean by lazy {
        return@lazy try {
            val exec = Runtime.getRuntime().exec(arrayOf("terminal-notifier", "-help"))
            return@lazy if (!exec.waitFor(2, TimeUnit.SECONDS)) {
                false
            } else {
                exec.exitValue() == 0
            }
        } catch (_: IOException) {
            false
        } catch (_: InterruptedException) {
            false
        }
    }

    override suspend fun createTrayIcon(
        image: Image,
        title: String,
    ) {
        // no-op — tray menus not supported on macOS in this implementation
    }

    override suspend fun sendNotification(
        title: String?,
        message: String,
    ) {
        val environment = mutableMapOf<String, String>()
        val commands = mutableListOf<String>()

        if (terminalNotifierAvailable) {
            // ProcessBuilder passes each argument as a separate element of the
            // process' argv vector, so the message/title cannot break out of
            // their argument and no escaping is required.
            commands.add("terminal-notifier")
            commands.add("-group")
            commands.add("com.cereal.launcher")
            commands.add("-sender")
            commands.add("com.cereal.launcher")
            commands.add("-message")
            commands.add(message)
            title?.let {
                commands.add("-title")
                commands.add(it)
            }
        } else {
            // Pass the variable parts via environment variables and read them back
            // inside AppleScript with `system attribute`, so nothing attacker-influenced
            // is concatenated into the AppleScript source.
            environment[ENV_MESSAGE] = message

            val script =
                buildString {
                    append("display notification (system attribute \"")
                    append(ENV_MESSAGE)
                    append("\")")
                    if (title != null) {
                        environment[ENV_TITLE] = title
                        append(" with title (system attribute \"")
                        append(ENV_TITLE)
                        append("\")")
                    }
                }

            commands.add("osascript")
            commands.add("-e")
            commands.add(script)
        }

        try {
            sendCommand(commands, environment)
        } catch (ex: IOException) {
            logger.warn("error sending notification", ex)
        }
    }

    @Throws(IOException::class)
    private fun sendCommand(
        commands: List<String>,
        environment: Map<String, String>,
    ): Process =
        ProcessBuilder(*commands.toTypedArray())
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
}
