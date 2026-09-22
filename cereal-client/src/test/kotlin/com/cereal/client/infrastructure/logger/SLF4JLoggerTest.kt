package com.cereal.client.infrastructure.logger

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.logger.KOIN_TAG
import org.koin.core.logger.Level
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SLF4JLoggerTest {
    private lateinit var mockLogger: Logger

    @Before
    fun setUp() {
        mockkStatic("org.slf4j.LoggerFactory")
        mockLogger = mockk(relaxed = true)
        every { LoggerFactory.getLogger(KOIN_TAG) } returns mockLogger
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `INFO threshold should log INFO and ERROR but not DEBUG`() {
        val logger = SLF4JLogger(Level.INFO)

        logger.display(Level.DEBUG, "debug-message")
        verify(exactly = 0) { mockLogger.debug("debug-message") }

        logger.display(Level.INFO, "info-message")
        verify(exactly = 1) { mockLogger.info("info-message") }

        logger.display(Level.ERROR, "error-message")
        verify(exactly = 1) { mockLogger.error("error-message") }
    }

    @Test
    fun `DEBUG threshold should log DEBUG`() {
        val logger = SLF4JLogger(Level.DEBUG)

        logger.display(Level.DEBUG, "debug-message")
        verify(exactly = 1) { mockLogger.debug("debug-message") }
    }

    @Test
    fun `NONE level should map to ERROR and be logged when threshold allows`() {
        val logger = SLF4JLogger(Level.INFO)

        logger.display(Level.NONE, "none-message")
        verify(exactly = 1) { mockLogger.error("none-message") }
    }
}
