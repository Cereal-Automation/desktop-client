package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VideoEmbedTest {
    @Test
    fun `should create valid VideoEmbed with URL`() {
        val video = VideoEmbed(url = "https://example.com/video.mp4")
        assertNotNull(video)
    }

    @Test
    fun `should fail when URL is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                VideoEmbed(url = "   ")
            }
        assertEquals("Video URL cannot be blank", exception.message)
    }

    @Test
    fun `should fail when URL is not HTTP or HTTPS`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                VideoEmbed(url = "ftp://example.com/video.mp4")
            }
        assertEquals("Video URL must be a valid HTTP or HTTPS URL", exception.message)
    }

    @Test
    fun `should accept valid HTTPS URL`() {
        val video = VideoEmbed(url = "https://example.com/video.mp4")
        assertNotNull(video)
    }

    @Test
    fun `should fail when height is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                VideoEmbed(url = "https://example.com/video.mp4", height = 0)
            }
        assertEquals("Video height must be positive", exception.message)
    }

    @Test
    fun `should fail when height is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                VideoEmbed(url = "https://example.com/video.mp4", height = -720)
            }
        assertEquals("Video height must be positive", exception.message)
    }

    @Test
    fun `should fail when width is zero`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                VideoEmbed(url = "https://example.com/video.mp4", width = 0)
            }
        assertEquals("Video width must be positive", exception.message)
    }

    @Test
    fun `should fail when width is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                VideoEmbed(url = "https://example.com/video.mp4", width = -1280)
            }
        assertEquals("Video width must be positive", exception.message)
    }

    @Test
    fun `should accept valid dimensions`() {
        val video =
            VideoEmbed(
                url = "https://example.com/video.mp4",
                height = 720,
                width = 1280,
            )
        assertNotNull(video)
    }

    @Test
    fun `should create video with all fields`() {
        val video =
            VideoEmbed(
                url = "https://example.com/video.mp4",
                height = 1080,
                width = 1920,
            )
        assertNotNull(video)
    }
}
