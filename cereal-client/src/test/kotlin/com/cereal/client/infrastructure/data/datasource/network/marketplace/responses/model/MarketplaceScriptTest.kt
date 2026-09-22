package com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarketplaceScriptTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maintenanceMode is true when maintenance_mode is true in JSON`() {
        val raw =
            """
            {
              "id": "1",
              "public_identifier": "test-script",
              "title": "Test Script",
              "maintenance_mode": true
            }
            """.trimIndent()

        val script = json.decodeFromString<MarketplaceScript>(raw)

        assertTrue(script.maintenanceMode)
    }

    @Test
    fun `maintenanceMode defaults to false when field is absent from JSON`() {
        val raw =
            """
            {
              "id": "1",
              "public_identifier": "test-script",
              "title": "Test Script"
            }
            """.trimIndent()

        val script = json.decodeFromString<MarketplaceScript>(raw)

        assertFalse(script.maintenanceMode)
    }
}
