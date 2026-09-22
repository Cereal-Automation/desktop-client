@file:OptIn(ExperimentalTime::class)

package com.cereal.client.domain.model.discord.rpc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DiscordPresenceBuilderTest {
    @Test
    fun `build with no setters uses defaults`() {
        val presence = DiscordPresenceBuilder().build()

        assertEquals("", presence.state)
        assertEquals("", presence.details)
        assertEquals(Instant.fromEpochSeconds(0), presence.startTimestamp)
        assertEquals(Instant.fromEpochSeconds(0), presence.endTimestamp)
        assertEquals("default", presence.largeImageKey)
        assertNull(presence.largeImageText)
        assertNull(presence.smallImageKey)
        assertNull(presence.smallImageText)
        assertNull(presence.partyId)
        assertEquals(0, presence.partySize)
        assertEquals(0, presence.partyMax)
        assertNull(presence.matchSecret)
        assertNull(presence.joinSecret)
        assertNull(presence.spectateSecret)
        assertFalse(presence.instance)
    }

    @Test
    fun `build maps every configured field onto the presence`() {
        val start = Instant.fromEpochSeconds(1_000)
        val end = Instant.fromEpochSeconds(2_000)

        val presence =
            DiscordPresenceBuilder()
                .setState("Playing Solo")
                .setDetails("In Queue")
                .setStartTimestamp(start)
                .setEndTimestamp(end)
                .setLargeImageKey("large")
                .setLargeImageText("Large tooltip")
                .setSmallImageKey("small")
                .setSmallImageText("Small tooltip")
                .setPartyId("party-1")
                .setPartySize(2)
                .setPartyMax(5)
                .setMatchSecret("match")
                .setJoinSecret("join")
                .setSpectateSecret("spectate")
                .setInstance(true)
                .build()

        assertEquals("Playing Solo", presence.state)
        assertEquals("In Queue", presence.details)
        assertEquals(start, presence.startTimestamp)
        assertEquals(end, presence.endTimestamp)
        assertEquals("large", presence.largeImageKey)
        assertEquals("Large tooltip", presence.largeImageText)
        assertEquals("small", presence.smallImageKey)
        assertEquals("Small tooltip", presence.smallImageText)
        assertEquals("party-1", presence.partyId)
        assertEquals(2, presence.partySize)
        assertEquals(5, presence.partyMax)
        assertEquals("match", presence.matchSecret)
        assertEquals("join", presence.joinSecret)
        assertEquals("spectate", presence.spectateSecret)
        assertTrue(presence.instance)
    }

    @Test
    fun `every setter returns the same builder instance for chaining`() {
        val builder = DiscordPresenceBuilder()

        assertSame(builder, builder.setState("s"))
        assertSame(builder, builder.setDetails("d"))
        assertSame(builder, builder.setStartTimestamp(Instant.fromEpochSeconds(1)))
        assertSame(builder, builder.setEndTimestamp(Instant.fromEpochSeconds(2)))
        assertSame(builder, builder.setLargeImageKey("lk"))
        assertSame(builder, builder.setLargeImageText("lt"))
        assertSame(builder, builder.setSmallImageKey("sk"))
        assertSame(builder, builder.setSmallImageText("st"))
        assertSame(builder, builder.setPartyId("p"))
        assertSame(builder, builder.setPartySize(1))
        assertSame(builder, builder.setPartyMax(2))
        assertSame(builder, builder.setMatchSecret("m"))
        assertSame(builder, builder.setJoinSecret("j"))
        assertSame(builder, builder.setSpectateSecret("sp"))
        assertSame(builder, builder.setInstance(true))
    }
}
