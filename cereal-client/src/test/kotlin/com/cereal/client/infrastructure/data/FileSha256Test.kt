package com.cereal.client.infrastructure.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class FileSha256Test {
    @Test
    fun `hash of an empty file is the SHA-256 of no input`(
        @TempDir tmp: Path,
    ) {
        val file = File(tmp.toFile(), "empty").apply { createNewFile() }

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            FileSha256.hash(file),
        )
    }

    @Test
    fun `hash matches a known SHA-256 test vector`(
        @TempDir tmp: Path,
    ) {
        val file = File(tmp.toFile(), "abc").apply { writeText("abc") }

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileSha256.hash(file),
        )
    }
}
