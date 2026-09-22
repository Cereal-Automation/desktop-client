package com.cereal.client.infrastructure.provider

import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import com.cereal.client.infrastructure.data.datasource.csv.CsvWriter
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.models.ProxyTxt
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatasetFileProviderImplTest {
    private lateinit var repository: DatasetFileProviderImpl

    private val csvWriter = mockk<CsvWriter>(relaxed = true)
    private val fileSystemProxyTemplateDataSource = mockk<FileSystemProxyTemplateDataSource>(relaxed = true)

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        repository =
            DatasetFileProviderImpl(
                csvReader = CsvReader(),
                csvWriter = csvWriter,
                fileSystemProxyTemplateDataSource = fileSystemProxyTemplateDataSource,
            )
    }

    private fun createDefinition(
        key: String,
        position: Int,
    ): ScriptConfigurationItemDefinition =
        ScriptConfigurationItemDefinition(
            name = key,
            description = "desc",
            key = key,
            position = position,
            type = ConfigItemType.StringConfigItem,
            isNullable = false,
            stateModifier = null,
            isScriptIdentifier = false,
        )

    @Test
    fun `saveTemplate for Custom writes headers sorted by position and adds csv extension`() {
        val type =
            DatasetType.Custom(
                definitions =
                    listOf(
                        createDefinition("third", 2),
                        createDefinition("first", 0),
                        createDefinition("second", 1),
                    ),
            )
        val file = File(tempDir, "template")

        val fileSlot = slot<File>()
        val rowsSlot = slot<List<List<Any>>>()

        repository.saveTemplate(type, file)

        verify { csvWriter.write(capture(fileSlot), capture(rowsSlot)) }
        assertEquals("template.csv", fileSlot.captured.name)
        assertEquals(listOf(listOf("first", "second", "third")), rowsSlot.captured)
    }

    @Test
    fun `saveTemplate for Custom keeps existing csv extension`() {
        val type = DatasetType.Custom(definitions = listOf(createDefinition("a", 0)))
        val file = File(tempDir, "already.csv")

        val fileSlot = slot<File>()

        repository.saveTemplate(type, file)

        verify { csvWriter.write(capture(fileSlot), any()) }
        assertEquals("already.csv", fileSlot.captured.name)
    }

    @Test
    fun `saveTemplate for Proxy delegates to template datasource and adds txt extension`() {
        val file = File(tempDir, "proxies")

        val fileSlot = slot<File>()

        repository.saveTemplate(DatasetType.Proxy, file)

        verify { fileSystemProxyTemplateDataSource.writeTemplate(capture(fileSlot)) }
        assertEquals("proxies.txt", fileSlot.captured.name)
    }

    @Test
    fun `saveTemplate for Proxy keeps existing txt extension`() {
        val file = File(tempDir, "proxies.txt")

        val fileSlot = slot<File>()

        repository.saveTemplate(DatasetType.Proxy, file)

        verify { fileSystemProxyTemplateDataSource.writeTemplate(capture(fileSlot)) }
        assertEquals("proxies.txt", fileSlot.captured.name)
    }

    @Test
    fun `read for Custom counts data rows in a real csv file`() {
        val type = DatasetType.Custom(definitions = listOf(createDefinition("name", 0)))
        val file = File(tempDir, "data.csv")
        file.writeText("name\nAlice\nBob\nCarol\n")

        val result = repository.read(type, file)

        assertEquals(3, result.numberOfRecords)
    }

    @Test
    fun `read for Custom rejects a file holding only a header`() {
        val type = DatasetType.Custom(definitions = listOf(createDefinition("name", 0)))
        val file = File(tempDir, "empty.csv")
        file.writeText("name\n")

        assertFailsWith<InvalidFileException> { repository.read(type, file) }
    }

    @Test
    fun `read for Custom validates the whole file up front`() {
        // The dialog reports what is wrong before the user commits to importing, using the same mapping
        // the import itself performs.
        val type = DatasetType.Custom(definitions = listOf(createDefinition("name", 0)))
        val file = File(tempDir, "custom.csv")
        file.writeText("other\nAlice\n")

        val exception = assertFailsWith<InvalidFileException> { repository.read(type, file) }

        assertTrue(exception.message!!.contains("Missing required column(s): 'name'"), exception.message!!)
    }

    @Test
    fun `read for Custom throws InvalidFileException on malformed csv`() {
        val type = DatasetType.Custom(definitions = listOf(createDefinition("name", 0)))
        val file = File(tempDir, "malformed.csv")
        // Unterminated quoted field triggers MalformedCSVException from kotlincsv.
        file.writeText("name\n\"unterminated")

        assertFailsWith<InvalidFileException> {
            repository.read(type, file)
        }
    }

    @Test
    fun `read for Proxy counts records from template datasource`() {
        val file = File(tempDir, "proxies.txt")
        every { fileSystemProxyTemplateDataSource.readFromTemplate(file) } returns
            listOf(
                ProxyTxt(address = "1.1.1.1", port = 80, username = null, password = null),
                ProxyTxt(address = "2.2.2.2", port = 81, username = null, password = null),
            )

        val result = repository.read(DatasetType.Proxy, file)

        assertEquals(2, result.numberOfRecords)
        verify { fileSystemProxyTemplateDataSource.readFromTemplate(file) }
    }

    @Test
    fun `saveTemplate write target is placed alongside the original file`() {
        val type = DatasetType.Custom(definitions = listOf(createDefinition("a", 0)))
        val file = File(tempDir, "noext")

        val fileSlot = slot<File>()

        repository.saveTemplate(type, file)

        verify { csvWriter.write(capture(fileSlot), any()) }
        assertTrue(fileSlot.captured.parentFile == tempDir)
    }

    @Test
    fun `read for Custom sniffs a semicolon delimiter`() {
        val type = DatasetType.Custom(definitions = listOf(createDefinition("name", 0)))
        val file = File(tempDir, "semicolon.csv")
        file.writeText("name;city\nAlice;Rotterdam\nBob;Utrecht\n")

        val result = repository.read(type, file)

        assertEquals(2, result.numberOfRecords)
    }

    @Test
    fun `saveTemplate for List writes headers sorted by position`() {
        val type =
            DatasetType.ConfigList(
                definitions =
                    listOf(
                        createDefinition("third", 2),
                        createDefinition("first", 0),
                        createDefinition("second", 1),
                    ),
            )
        val file = File(tempDir, "template")

        val fileSlot = slot<File>()
        val rowsSlot = slot<List<List<Any>>>()

        repository.saveTemplate(type, file)

        verify { csvWriter.write(capture(fileSlot), capture(rowsSlot)) }
        assertEquals("template.csv", fileSlot.captured.name)
        assertEquals(listOf(listOf("first", "second", "third")), rowsSlot.captured)
    }

    @Test
    fun `read for List validates the whole file up front`() {
        val type = DatasetType.ConfigList(definitions = listOf(createDefinition("name", 0)))
        val file = File(tempDir, "objectlist.csv")
        file.writeText("name\nAlice\nBob\n")

        assertEquals(2, repository.read(type, file).numberOfRecords)

        file.writeText("other\nAlice\n")

        assertFailsWith<InvalidFileException> { repository.read(type, file) }
    }

    @Test
    fun `readRows returns raw header keyed rows`() {
        val file = File(tempDir, "raw.csv")
        file.writeText("a,b\n1,2\n")

        assertEquals(listOf(mapOf("a" to "1", "b" to "2")), repository.readRows(file))
    }
}
