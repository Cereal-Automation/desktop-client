package com.cereal.client.infrastructure.data.datasource.filesystem

import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.infrastructure.data.Settings
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileSystemProxyTemplateDataSourceTest {
    @TempDir
    lateinit var tempFolder: Path

    @Test
    fun `readFromTemplate should parse complete template correctly`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address:8080:user:pass")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(1, result.size)
        val proxy = result[0]
        assertEquals("address", proxy.address)
        assertEquals(8080, proxy.port)
        assertEquals("user", proxy.username)
        assertEquals("pass", proxy.password)
    }

    @Test
    fun `readFromTemplate should use default port if not provided`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address::user:pass")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(1, result.size)
        val proxy = result[0]
        assertEquals("address", proxy.address)
        assertEquals(Settings.DEFAULT_PROXY_PORT, proxy.port)
        assertEquals("user", proxy.username)
        assertEquals("pass", proxy.password)
    }

    @Test
    fun `readFromTemplate should handle missing username and password`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address:8080")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(1, result.size)
        val proxy = result[0]
        assertEquals("address", proxy.address)
        assertEquals(8080, proxy.port)
        assertEquals(null, proxy.username)
        assertEquals(null, proxy.password)
    }

    @Test
    fun `readFromTemplate should throw InvalidFileException for lines with too many parts`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address:8080:user:pass:extra")
        val dataSource = FileSystemProxyTemplateDataSource()

        assertFailsWith<InvalidFileException> {
            dataSource.readFromTemplate(file)
        }
    }

    @Test
    fun `readFromTemplate should process multiple valid lines`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address1:8080:user1:pass1\naddress2:8081:user2:pass2")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(2, result.size)
        val proxy1 = result[0]
        assertEquals("address1", proxy1.address)
        assertEquals(8080, proxy1.port)
        assertEquals("user1", proxy1.username)
        assertEquals("pass1", proxy1.password)

        val proxy2 = result[1]
        assertEquals("address2", proxy2.address)
        assertEquals(8081, proxy2.port)
        assertEquals("user2", proxy2.username)
        assertEquals("pass2", proxy2.password)
    }

    @Test
    fun `writeTemplate should write correct template to file`() {
        val file = tempFolder.resolve("template.txt").toFile()
        val dataSource = FileSystemProxyTemplateDataSource()

        dataSource.writeTemplate(file)

        assertTrue(file.exists())
        assertEquals(FileSystemProxyTemplateDataSource.TEMPLATE, file.readText())
    }

    @Test
    fun `readFromTemplate should handle only address`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("192.168.1.1")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(1, result.size)
        val proxy = result[0]
        assertEquals("192.168.1.1", proxy.address)
        assertEquals(Settings.DEFAULT_PROXY_PORT, proxy.port)
        assertEquals(null, proxy.username)
        assertEquals(null, proxy.password)
    }

    @Test
    fun `readFromTemplate should handle empty username and password as null`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address:8080::")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(1, result.size)
        val proxy = result[0]
        assertEquals("address", proxy.address)
        assertEquals(8080, proxy.port)
        assertEquals(null, proxy.username)
        assertEquals(null, proxy.password)
    }

    @Test
    fun `readFromTemplate should throw InvalidFileException for non-integer port`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address:invalid:user:pass")
        val dataSource = FileSystemProxyTemplateDataSource()

        val exception =
            assertFailsWith<InvalidFileException> {
                dataSource.readFromTemplate(file)
            }
        assertTrue(exception.message!!.contains("line 1"))
    }

    @Test
    fun `readFromTemplate should throw InvalidFileException with correct line number`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("valid:8080:user:pass\ninvalid:too:many:parts:here")
        val dataSource = FileSystemProxyTemplateDataSource()

        val exception =
            assertFailsWith<InvalidFileException> {
                dataSource.readFromTemplate(file)
            }

        assertTrue(exception.message!!.contains("line 2"))
        assertTrue(exception.message!!.contains(FileSystemProxyTemplateDataSource.TEMPLATE))
    }

    @Test
    fun `readFromTemplate should throw InvalidFileException for empty parts`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(0, result.size)
    }

    @Test
    fun `readFromTemplate should handle mixed valid and empty lines`() {
        val file = tempFolder.resolve("proxy.txt").toFile()
        file.writeText("address1:8080:user1:pass1\n\naddress2:8081:user2:pass2")
        val dataSource = FileSystemProxyTemplateDataSource()

        val result = dataSource.readFromTemplate(file)

        assertEquals(3, result.size)

        // First proxy
        assertEquals("address1", result[0].address)
        assertEquals(8080, result[0].port)

        // Empty line becomes empty address with default port
        assertEquals("", result[1].address)
        assertEquals(Settings.DEFAULT_PROXY_PORT, result[1].port)

        // Third proxy
        assertEquals("address2", result[2].address)
        assertEquals(8081, result[2].port)
    }
}
