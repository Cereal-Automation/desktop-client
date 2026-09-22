package com.cereal.client.presentation.settings.state

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiscordWebhookUrlValidatorTest {
    private val validator = DiscordWebhookUrlValidator()

    @Test
    fun `accepts a valid discord webhook url`() {
        assertNull(validator.validate("https://discord.com/api/webhooks/123456/token"))
    }

    @Test
    fun `rejects a null value`() {
        assertNotNull(validator.validate(null))
    }

    @Test
    fun `rejects a malformed url`() {
        assertNotNull(validator.validate("not a url"))
    }

    @Test
    fun `rejects a non-https url`() {
        assertNotNull(validator.validate("http://discord.com/api/webhooks/123/token"))
    }

    @Test
    fun `rejects a non-discord host`() {
        assertNotNull(validator.validate("https://example.com/api/webhooks/123/token"))
    }

    @Test
    fun `rejects a discord url that is not a webhook path`() {
        assertNotNull(validator.validate("https://discord.com/channels/123"))
    }
}
