package com.cereal.client.infrastructure.data.datasource.csv

import com.cereal.client.application.datasets.InvalidFileException
import com.github.doyaaaaaken.kotlincsv.dsl.context.InsufficientFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.util.CSVFieldNumDifferentException
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** The delimiters a spreadsheet export is likely to use, in preference order when counts tie. */
private val CANDIDATE_DELIMITERS = listOf(',', ';', '\t')

/**
 * The largest file an import will read. The whole file is decoded and parsed in memory at once, so the
 * ceiling is what turns "the user picked the 2 GB log file next to the export" into a sentence they can
 * act on instead of an `OutOfMemoryError` that takes the application down with it.
 */
private const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024

private const val MAX_FILE_SIZE_DESCRIPTION = "25 MB"

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

/**
 * What a file that is not valid UTF-8 is decoded as instead. Excel writes the Windows ANSI code page
 * unless the user explicitly picks "CSV UTF-8", and that export is the most common file this importer is
 * handed — decoding it as UTF-8 would replace every accented character with U+FFFD and import the
 * corruption without a word.
 */
private val NON_UTF8_FALLBACK_CHARSET: Charset = Charset.forName("windows-1252")

class CsvReader {
    /**
     * Reads [file] as a CSV with a header row, returning each data row as a header-keyed map.
     *
     * Every quirk a spreadsheet export has that still leaves the file with one unambiguous reading is
     * absorbed here: the delimiter is sniffed from the header line, a byte-order mark is stripped, bytes
     * that are not valid UTF-8 are decoded as Windows ANSI, header names are trimmed, columns without a
     * name and rows without a single filled cell are dropped, and a row that stops short of the header
     * is padded with empty cells.
     *
     * Anything with more than one reading is refused rather than guessed at — two columns with the same
     * name, or a row holding more cells than the header declares — because either guess imports data the
     * file does not contain.
     *
     * Performs no domain validation or type conversion; every structural failure surfaces as an
     * [InvalidFileException] whose message is written for the person holding the spreadsheet.
     */
    fun readRawRows(file: File): List<Map<String, String>> {
        val rows = parseRows(file.readImportableText())
        val header = rows.firstOrNull() ?: throw InvalidFileException("The file is empty.")
        val columnNames = header.toColumnNames()

        return rows
            .drop(1)
            .filterNot { cells -> cells.all { it.isBlank() } }
            .map { cells -> columnNames.keyCellsByColumn(cells) }
    }
}

private fun parseRows(contents: String): List<List<String>> =
    try {
        csvReader {
            delimiter = sniffDelimiter(contents)
            skipEmptyLine = true
            // A row that stops short of the header is padded, because a spreadsheet that omits trailing
            // empty cells still says exactly what it means. A row with *more* cells than the header keeps
            // the default of failing: extra cells mean the file is not shaped the way its header claims,
            // and both silently dropping and silently keeping them import a file nobody chose.
            insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.EMPTY_STRING
        }.readAll(contents)
    } catch (e: CSVFieldNumDifferentException) {
        throw InvalidFileException(
            "Row ${e.csvRowNum - 1} has ${e.fieldNumOnFailedRow} cells but the header declares ${e.fieldNum}. " +
                "A cell that contains the separator has to be wrapped in double quotes.",
            e,
        )
    } catch (e: MalformedCSVException) {
        throw InvalidFileException(e.message ?: "The file could not be read as a CSV.", e)
    }

/**
 * The header's column names, trimmed and still positionally aligned with each data row's cells.
 *
 * A column whose name is blank keeps its position — so the cells after it stay aligned — but is dropped
 * from the rows it would produce: a trailing delimiter on the header line is a spreadsheet artifact, not
 * a column anyone can name in a script configuration.
 */
private fun List<String>.toColumnNames(): List<String> {
    val names = map { it.trim() }
    val duplicates =
        names
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    if (duplicates.isNotEmpty()) {
        throw InvalidFileException(
            "The file has more than one column named ${duplicates.joinToString(", ") { "'$it'" }}.",
        )
    }
    return names
}

/**
 * Pairs each named column with the cell at its position. Missing trailing cells read as empty, so a short
 * row means "these fields are unset" rather than aborting the file — the importers decide whether an
 * unset field is allowed.
 */
private fun List<String>.keyCellsByColumn(cells: List<String>): Map<String, String> =
    withIndex()
        .filter { (_, name) -> name.isNotBlank() }
        .associate { (index, name) -> name to (cells.getOrNull(index) ?: "") }

/**
 * Reads the file's bytes as text, refusing up front the two cases that otherwise fail far from their
 * cause: a file that is gone or unreadable, and a file too large to hold in memory.
 */
private fun File.readImportableText(): String {
    if (!isFile) {
        throw InvalidFileException("'$name' is not a file that can be read.")
    }
    if (length() > MAX_FILE_SIZE_BYTES) {
        throw InvalidFileException("'$name' is larger than the $MAX_FILE_SIZE_DESCRIPTION an import can read.")
    }
    val bytes =
        try {
            readBytes()
        } catch (e: IOException) {
            throw InvalidFileException("'$name' could not be read: ${e.message ?: "unknown error"}.", e)
        }
    return bytes.decodeAsImportText()
}

/**
 * Decodes the file's bytes, honouring a byte-order mark and falling back to [NON_UTF8_FALLBACK_CHARSET]
 * when the bytes are not valid UTF-8.
 */
private fun ByteArray.decodeAsImportText(): String =
    when {
        startsWith(UTF8_BOM) -> withoutPrefix(UTF8_BOM).toString(StandardCharsets.UTF_8)
        startsWith(UTF16LE_BOM) -> withoutPrefix(UTF16LE_BOM).toString(StandardCharsets.UTF_16LE)
        startsWith(UTF16BE_BOM) -> withoutPrefix(UTF16BE_BOM).toString(StandardCharsets.UTF_16BE)
        else -> decodeStrictly(StandardCharsets.UTF_8) ?: toString(NON_UTF8_FALLBACK_CHARSET)
    }

/** The decoded text, or null when [charset] cannot represent these bytes without substituting characters. */
private fun ByteArray.decodeStrictly(charset: Charset): String? =
    try {
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.withoutPrefix(prefix: ByteArray): ByteArray = copyOfRange(prefix.size, size)

/**
 * Picks the delimiter that dominates the header line, ignoring anything inside quotes so a quoted
 * value containing a comma cannot outvote the real separator. Falls back to a comma when the header
 * holds none of the candidates — a single-column file is still a valid CSV.
 */
internal fun sniffDelimiter(contents: String): Char {
    val header = contents.lineSequence().firstOrNull { it.isNotBlank() } ?: return CANDIDATE_DELIMITERS.first()

    var inQuotes = false
    val counts = mutableMapOf<Char, Int>()
    header.forEach { character ->
        when {
            character == '"' -> inQuotes = !inQuotes
            !inQuotes && character in CANDIDATE_DELIMITERS -> counts[character] = (counts[character] ?: 0) + 1
        }
    }

    val dominant = CANDIDATE_DELIMITERS.maxByOrNull { counts[it] ?: 0 } ?: CANDIDATE_DELIMITERS.first()
    return if ((counts[dominant] ?: 0) > 0) dominant else CANDIDATE_DELIMITERS.first()
}
