package com.cereal.client.infrastructure.data.datasource.os

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.presentation.util.ImageUtil
import org.slf4j.LoggerFactory
import java.awt.AWTException
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.concurrent.TimeUnit

// [UNTESTED]
class LinuxNotificationDataSource(
    private val applicationConfig: ApplicationConfig,
) : NotificationDataSource {
    private val logger = LoggerFactory.getLogger(LinuxNotificationDataSource::class.java)
    private var trayIcon: TrayIcon? = null

    companion object {
        private const val DEFAULT_TIMEOUT = 10000
        private const val NOTIFICATION_PROCESS_WAIT_MS = 500L

        private fun shellEscape(value: String): String = value.replace('"', '\'')
    }

    override suspend fun createTrayIcon(
        image: Image,
        title: String,
    ) {
        if (!SystemTray.isSupported()) {
            return
        }
        val icon: BufferedImage? =
            ImageUtil.loadImageResource(WindowsNotificationDataSource::class.java, "/${applicationConfig.appIcon}")
        val systemTray = SystemTray.getSystemTray()
        trayIcon = TrayIcon(icon, title)
        trayIcon?.isImageAutoSize = true

        try {
            systemTray.add(trayIcon)
        } catch (ex: AWTException) {
            logger.debug("Unable to add system tray icon", ex)
            return
        }

        // Bring to front when tray icon is clicked
        trayIcon?.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    // todo: bring application to front
                    logger.info("Clicked application tray icon")
                }
            },
        )
    }

    override suspend fun sendNotification(
        title: String?,
        message: String,
    ) {
        val escapedMessage: String = shellEscape(message)
        val commands = mutableListOf<String>()
        commands.add("notify-send")
        title?.let {
            commands.add(shellEscape(title))
        }
        commands.add(escapedMessage)
//        commands.add("-i")
//        commands.add("") // todo: add absolute path of a notification image
        commands.add("-u")
        commands.add("normal")
        commands.add("-t")
        commands.add(DEFAULT_TIMEOUT.toString())

        try {
            val notificationProcess = sendCommand(commands)
            val exited = notificationProcess.waitFor(NOTIFICATION_PROCESS_WAIT_MS, TimeUnit.MILLISECONDS)
            if (exited && notificationProcess.exitValue() == 0) {
                return
            }
        } catch (ex: IOException) {
            logger.debug("error sending notification", ex)
        } catch (ex: InterruptedException) {
            logger.debug("error sending notification", ex)
        }

        // fall back to tray notification
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.NONE)
    }

    @Throws(IOException::class)
    private fun sendCommand(commands: List<String>): Process =
        ProcessBuilder(*commands.toTypedArray())
            .redirectErrorStream(true)
            .start()
}
