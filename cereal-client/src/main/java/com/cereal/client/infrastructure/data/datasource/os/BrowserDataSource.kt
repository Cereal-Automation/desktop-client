package com.cereal.client.infrastructure.data.datasource.os

import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

class BrowserDataSource {
    private val logger = LoggerFactory.getLogger(BrowserDataSource::class.java)

    fun attemptDesktopBrowse(url: String): Boolean {
        if (!Desktop.isDesktopSupported()) {
            return false
        }

        val desktop = Desktop.getDesktop()

        return if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            false
        } else {
            try {
                desktop.browse(URI(url))
                true
            } catch (ex: IOException) {
                logger.warn("Failed to open Desktop#browse $url", ex)
                false
            } catch (ex: URISyntaxException) {
                logger.warn("Failed to open Desktop#browse $url", ex)
                false
            }
        }
    }

    fun attemptXdgOpen(resource: String): Boolean {
        return try {
            val exec = Runtime.getRuntime().exec(arrayOf("xdg-open", resource))
            exec.waitFor()
            val returnCode = exec.exitValue()
            if (returnCode == 0) {
                return true
            }
            logger.warn("xdg-open $resource returned with error code $returnCode")
            false
        } catch (_: IOException) {
            // xdg-open not found
            false
        } catch (ex: InterruptedException) {
            logger.warn("Interrupted while waiting for xdg-open $resource to execute")
            false
        }
    }

    fun attemptDesktopOpen(directory: File): Boolean {
        if (!directory.attemptDesktopOpen()) {
            logger.warn("Failed to open Desktop#open $directory")
            return false
        }

        return true
    }
}

private fun File.attemptDesktopOpen(): Boolean {
    if (!Desktop.isDesktopSupported()) {
        return false
    }
    val desktop = Desktop.getDesktop()
    return if (!desktop.isSupported(Desktop.Action.OPEN)) {
        false
    } else {
        try {
            desktop.open(this)
            true
        } catch (_: IOException) {
            false
        }
    }
}
