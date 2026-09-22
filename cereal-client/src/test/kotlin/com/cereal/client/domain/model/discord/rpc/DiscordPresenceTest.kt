@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.discord.rpc

import com.cereal.client.domain.model.exception.InvalidDiscordPresenceException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DiscordPresenceTest {
    private fun presence(
        state: String = "state",
        details: String = "details",
        largeImageKey: String = "large",
        largeImageText: String? = null,
        smallImageKey: String? = null,
        smallImageText: String? = null,
        partyId: String? = null,
        partySize: Int = 0,
        partyMax: Int = 0,
        matchSecret: String? = null,
        joinSecret: String? = null,
        spectateSecret: String? = null,
    ) = DiscordPresence(
        state = state,
        details = details,
        startTimestamp = Instant.fromEpochSeconds(0),
        endTimestamp = Instant.fromEpochSeconds(3600),
        largeImageKey = largeImageKey,
        largeImageText = largeImageText,
        smallImageKey = smallImageKey,
        smallImageText = smallImageText,
        partyId = partyId,
        partySize = partySize,
        partyMax = partyMax,
        matchSecret = matchSecret,
        joinSecret = joinSecret,
        spectateSecret = spectateSecret,
        instance = false,
    )

    @Test
    fun `should create valid presence with values within limits`() {
        val data = presence(state = "Idle...", details = "Playing")
        assertNotNull(data)
        assertEquals("Idle...", data.state)
    }

    @Test
    fun `should allow empty state and details`() {
        val data = presence(state = "", details = "")
        assertNotNull(data)
    }

    @Test
    fun `should allow null optional fields`() {
        val data =
            presence(
                largeImageText = null,
                smallImageKey = null,
                smallImageText = null,
                partyId = null,
                matchSecret = null,
                joinSecret = null,
                spectateSecret = null,
            )
        assertNotNull(data)
    }

    @Test
    fun `should fail when state exceeds 128 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(state = "a".repeat(129))
            }
        assertEquals("state cannot be longer than 128 characters", exception.message)
    }

    @Test
    fun `should fail when details exceeds 128 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(details = "a".repeat(129))
            }
        assertEquals("details cannot be longer than 128 characters", exception.message)
    }

    @Test
    fun `should fail when largeImageKey exceeds 32 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(largeImageKey = "a".repeat(33))
            }
        assertEquals("largeImageKey cannot be longer than 32 characters", exception.message)
    }

    @Test
    fun `should fail when smallImageKey exceeds 32 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(smallImageKey = "a".repeat(33))
            }
        assertEquals("smallImageKey cannot be longer than 32 characters", exception.message)
    }

    @Test
    fun `should fail when largeImageText exceeds 128 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(largeImageText = "a".repeat(129))
            }
        assertEquals("largeImageText cannot be longer than 128 characters", exception.message)
    }

    @Test
    fun `should fail when partyId exceeds 128 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(partyId = "a".repeat(129))
            }
        assertEquals("partyId cannot be longer than 128 characters", exception.message)
    }

    @Test
    fun `should fail when matchSecret exceeds 128 characters`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(matchSecret = "a".repeat(129))
            }
        assertEquals("matchSecret cannot be longer than 128 characters", exception.message)
    }

    @Test
    fun `should fail when partySize is negative`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(partySize = -1)
            }
        assertEquals("partySize cannot be negative", exception.message)
    }

    @Test
    fun `should fail when partyMax is negative`() {
        val exception =
            assertThrows<InvalidDiscordPresenceException> {
                presence(partyMax = -1)
            }
        assertEquals("partyMax cannot be negative", exception.message)
    }
}
