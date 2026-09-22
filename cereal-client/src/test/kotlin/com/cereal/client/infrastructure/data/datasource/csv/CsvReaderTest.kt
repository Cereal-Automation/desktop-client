package com.cereal.client.infrastructure.data.datasource.csv

import com.cereal.client.application.datasets.InvalidFileException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvReaderTest {
    @TempDir
    lateinit var tempDir: File

    private val reader = CsvReader()

    private fun csv(
        contents: String,
        name: String = "data.csv",
    ): File = File(tempDir, name).apply { writeText(contents) }

    // region Shape

    @Test
    fun `reads data rows keyed by their header`() {
        val file = csv("name,age\nAlice,30\nBob,25\n")

        assertEquals(
            listOf(
                mapOf("name" to "Alice", "age" to "30"),
                mapOf("name" to "Bob", "age" to "25"),
            ),
            reader.readRawRows(file),
        )
    }

    @Test
    fun `trims surrounding space from header names`() {
        val file = csv(" name , age \nAlice,30\n")

        assertEquals(listOf(mapOf("name" to "Alice", "age" to "30")), reader.readRawRows(file))
    }

    @Test
    fun `pads a row that stops short of the header with empty cells`() {
        // A hand-edited file that omits the trailing empty cells still says exactly what it means; the
        // importers decide whether an unset field is allowed.
        val file = csv("name,age,city\nAlice,30\n")

        assertEquals(listOf(mapOf("name" to "Alice", "age" to "30", "city" to "")), reader.readRawRows(file))
    }

    @Test
    fun `rejects a row holding more cells than the header declares`() {
        val file = csv("name,age\nAlice,30\nBob,25,Utrecht\n")

        val exception = assertFailsWith<InvalidFileException> { reader.readRawRows(file) }

        assertContains(exception.message!!, "Row 2")
        assertContains(exception.message!!, "3 cells")
        assertContains(exception.message!!, "declares 2")
    }

    @Test
    fun `drops rows in which every cell is blank`() {
        // Spreadsheets routinely leave a run of empty rows below the data.
        val file = csv("name,age\nAlice,30\n,\n , \n")

        assertEquals(listOf(mapOf("name" to "Alice", "age" to "30")), reader.readRawRows(file))
    }

    @Test
    fun `drops a column the header did not name`() {
        // A trailing delimiter on the header line is a spreadsheet artifact, not a column.
        val file = csv("name,age,\nAlice,30,\n")

        assertEquals(listOf(mapOf("name" to "Alice", "age" to "30")), reader.readRawRows(file))
    }

    @Test
    fun `keeps the cells after an unnamed column aligned with their own column`() {
        val file = csv("name,,city\nAlice,ignored,Rotterdam\n")

        assertEquals(listOf(mapOf("name" to "Alice", "city" to "Rotterdam")), reader.readRawRows(file))
    }

    @Test
    fun `rejects two columns with the same name`() {
        val file = csv("name,name\nAlice,Bob\n")

        val exception = assertFailsWith<InvalidFileException> { reader.readRawRows(file) }

        assertContains(exception.message!!, "more than one column named 'name'")
    }

    @Test
    fun `rejects a file that is empty`() {
        val file = csv("")

        val exception = assertFailsWith<InvalidFileException> { reader.readRawRows(file) }

        assertContains(exception.message!!, "The file is empty.")
    }

    @Test
    fun `returns no rows when the file holds only a header`() {
        // Whether a header-only file is an error is the importers' call, not the reader's.
        assertEquals(emptyList(), reader.readRawRows(csv("name,age\n")))
    }

    @Test
    fun `keeps a value that contains the delimiter inside quotes intact`() {
        val file = csv("name,city\n\"Alice, A.\",Rotterdam\n")

        assertEquals(listOf(mapOf("name" to "Alice, A.", "city" to "Rotterdam")), reader.readRawRows(file))
    }

    @Test
    fun `rejects a file whose quoting never closes`() {
        val file = csv("name\n\"unterminated")

        assertFailsWith<InvalidFileException> { reader.readRawRows(file) }
    }

    // endregion

    // region Delimiters

    @Test
    fun `reads a semicolon separated export`() {
        val file = csv("name;city\nAlice;Rotterdam\n")

        assertEquals(listOf(mapOf("name" to "Alice", "city" to "Rotterdam")), reader.readRawRows(file))
    }

    @Test
    fun `reads a tab separated export`() {
        val file = csv("name\tcity\nAlice\tRotterdam\n")

        assertEquals(listOf(mapOf("name" to "Alice", "city" to "Rotterdam")), reader.readRawRows(file))
    }

    @Test
    fun `sniffs the delimiter that dominates the header rather than one inside a quoted name`() {
        assertEquals(';', sniffDelimiter("\"last, first\";city\n"))
    }

    @Test
    fun `falls back to a comma when the header holds no candidate delimiter`() {
        assertEquals(',', sniffDelimiter("name\nAlice\n"))
    }

    // endregion

    // region Encoding

    @Test
    fun `strips a UTF-8 byte order mark from the first header name`() {
        val file = File(tempDir, "bom.csv")
        file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "name\nAlice\n".toByteArray())

        assertEquals(listOf(mapOf("name" to "Alice")), reader.readRawRows(file))
    }

    @Test
    fun `reads a UTF-16 export written with a byte order mark`() {
        val file = File(tempDir, "utf16.csv")
        file.writeBytes("name\nJosé\n".toByteArray(StandardCharsets.UTF_16))

        assertEquals(listOf(mapOf("name" to "José")), reader.readRawRows(file))
    }

    @Test
    fun `reads a Windows ANSI export without corrupting its accented characters`() {
        // The default Excel "CSV" export on a Western Windows machine. Decoding it as UTF-8 would turn
        // every accented character into U+FFFD and import the corruption without a word.
        val file = File(tempDir, "ansi.csv")
        file.writeBytes("name\nJosé\n".toByteArray(Charset.forName("windows-1252")))

        assertEquals(listOf(mapOf("name" to "José")), reader.readRawRows(file))
    }

    @Test
    fun `reads a UTF-8 export without a byte order mark`() {
        val file = File(tempDir, "utf8.csv")
        file.writeBytes("name\nJosé\n".toByteArray(StandardCharsets.UTF_8))

        assertEquals(listOf(mapOf("name" to "José")), reader.readRawRows(file))
    }

    // endregion

    // region File access

    @Test
    fun `rejects a file that does not exist`() {
        val exception = assertFailsWith<InvalidFileException> { reader.readRawRows(File(tempDir, "gone.csv")) }

        assertContains(exception.message!!, "'gone.csv' is not a file that can be read.")
    }

    @Test
    fun `rejects a directory chosen instead of a file`() {
        assertFailsWith<InvalidFileException> { reader.readRawRows(tempDir) }
    }

    @Test
    fun `rejects a file larger than an import can read`() {
        val file = File(tempDir, "huge.csv")
        file.outputStream().buffered().use { output ->
            val megabyte = ByteArray(1024 * 1024) { 'a'.code.toByte() }
            repeat(26) { output.write(megabyte) }
        }

        val exception = assertFailsWith<InvalidFileException> { reader.readRawRows(file) }

        assertTrue(exception.message!!.contains("larger than the 25 MB"), exception.message!!)
    }

    // endregion
}
