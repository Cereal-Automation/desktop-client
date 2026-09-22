package com.cereal.client.presentation.util

import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

object ImageUtil {
    private val logger = LoggerFactory.getLogger(ImageUtil::class.java)

    /**
     * Reads an image resource from a given path relative to a given class.
     * This method is primarily shorthand for the synchronization and error handling required for
     * loading image resources from the classpath.
     *
     * @param c    The class to be referenced for the package path.
     * @param path The path, relative to the given class.
     * @return A [BufferedImage] of the loaded image resource from the given path.
     */
    fun loadImageResource(
        clazz: Class<*>,
        path: String,
    ): BufferedImage? {
        try {
            synchronized(ImageIO::class.java) { return ImageIO.read(clazz.getResourceAsStream(path)) }
        } catch (e: IllegalArgumentException) {
            val filePath: String =
                if (path.startsWith("/")) {
                    path
                } else {
                    clazz.getPackage().name.replace('.', '/') + "/" + path
                }
            logger.warn("Failed to load image from class: {}, path: {}", clazz.name, filePath)
            throw IllegalArgumentException(path, e)
        } catch (e: IOException) {
            throw RuntimeException(path, e)
        }
    }
}
