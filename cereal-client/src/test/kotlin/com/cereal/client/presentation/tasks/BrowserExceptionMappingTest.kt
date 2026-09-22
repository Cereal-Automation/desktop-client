package com.cereal.client.presentation.tasks

import com.cereal.client.application.exception.ChromeNotInstalledException
import dev.kdriver.core.exceptions.BrowserExecutableNotFoundException
import dev.kdriver.core.exceptions.NoBrowserExecutablePathException
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class BrowserExceptionMappingTest {
    @Test
    fun `mapBrowserException maps NoBrowserExecutablePathException to ChromeNotInstalledException`() {
        val input = NoBrowserExecutablePathException()
        val result = mapBrowserException(input)
        assertIs<ChromeNotInstalledException>(result)
        assertSame(input, result.cause)
    }

    @Test
    fun `mapBrowserException maps BrowserExecutableNotFoundException to ChromeNotInstalledException`() {
        val input = BrowserExecutableNotFoundException()
        val result = mapBrowserException(input)
        assertIs<ChromeNotInstalledException>(result)
        assertSame(input, result.cause)
    }

    @Test
    fun `mapBrowserException returns null for unrelated exceptions`() {
        val input = RuntimeException("unrelated")
        val result = mapBrowserException(input)
        assertNull(result)
    }
}
