package com.cereal.client.infrastructure.data.notification.telegram.mapper

import com.cereal.client.infrastructure.data.notification.telegram.mapper.TelegramParseModeMapper
import com.cereal.sdk.component.notification.telegram.model.TelegramParseMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramParseModeMapperTest {
    @Test
    fun `toApiString converts HTML correctly`() {
        assertEquals("HTML", TelegramParseModeMapper.toApiString(TelegramParseMode.HTML))
    }

    @Test
    fun `toApiString converts MARKDOWN correctly`() {
        assertEquals("Markdown", TelegramParseModeMapper.toApiString(TelegramParseMode.MARKDOWN))
    }

    @Test
    fun `toApiString converts MARKDOWN_V2 correctly`() {
        assertEquals("MarkdownV2", TelegramParseModeMapper.toApiString(TelegramParseMode.MARKDOWN_V2))
    }

    @Test
    fun `toApiString returns null for null input`() {
        assertNull(TelegramParseModeMapper.toApiString(null))
    }

    @Test
    fun `fromApiString converts HTML correctly`() {
        assertEquals(TelegramParseMode.HTML, TelegramParseModeMapper.fromApiString("HTML"))
    }

    @Test
    fun `fromApiString converts Markdown correctly`() {
        assertEquals(TelegramParseMode.MARKDOWN, TelegramParseModeMapper.fromApiString("Markdown"))
    }

    @Test
    fun `fromApiString converts MarkdownV2 correctly`() {
        assertEquals(TelegramParseMode.MARKDOWN_V2, TelegramParseModeMapper.fromApiString("MarkdownV2"))
    }

    @Test
    fun `fromApiString returns null for invalid input`() {
        assertNull(TelegramParseModeMapper.fromApiString("Invalid"))
    }

    @Test
    fun `fromApiString returns null for null input`() {
        assertNull(TelegramParseModeMapper.fromApiString(null))
    }
}
