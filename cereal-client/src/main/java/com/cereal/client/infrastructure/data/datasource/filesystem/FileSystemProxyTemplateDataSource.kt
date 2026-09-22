package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.infrastructure.data.Settings
import com.cereal.client.infrastructure.data.datasource.filesystem.models.ProxyTxt
import java.io.File

class FileSystemProxyTemplateDataSource {
    companion object {
        const val TEMPLATE = "<address>:<port>:<username>:<password>"
        private const val MAX_PROXY_PARTS = 4
        private const val PASSWORD_INDEX = 3
    }

    fun writeTemplate(file: File) {
        file.writeText(TEMPLATE)
    }

    fun readFromTemplate(file: File): List<ProxyTxt> =
        file.readLines().mapIndexed { index, line ->
            val parts = line.split(":")
            if (parts.size !in 1..MAX_PROXY_PARTS) {
                throw InvalidFileException("The content on line ${index + 1} doesn't conform to the structure: $TEMPLATE")
            }
            val port = parts.getOrNull(1)
            if (!port.isNullOrEmpty() && port.toIntOrNull() == null) {
                throw InvalidFileException("The port on line ${index + 1} must be a valid number")
            }
            createProxyTxt(parts)
        }

    private fun createProxyTxt(parts: List<String>): ProxyTxt =
        ProxyTxt(
            address = parts[0],
            port = parts.getOrNull(1)?.toIntOrNull() ?: Settings.DEFAULT_PROXY_PORT,
            username = parts.getOrNull(2)?.takeIf { it.isNotEmpty() },
            password = parts.getOrNull(PASSWORD_INDEX)?.takeIf { it.isNotEmpty() },
        )
}
