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

class WindowsNotificationDataSource(
    private val applicationConfig: ApplicationConfig,
) : NotificationDataSource {
    private val logger = LoggerFactory.getLogger(WindowsNotificationDataSource::class.java)
    private var trayIcon: TrayIcon? = null

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
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.NONE)
    }
}
