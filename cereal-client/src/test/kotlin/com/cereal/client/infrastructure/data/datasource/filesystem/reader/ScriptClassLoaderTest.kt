package com.cereal.client.infrastructure.data.datasource.filesystem.reader

import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ScriptClassLoaderTest {
    @Test
    fun `a script reads files bundled in its own jar`(
        @TempDir tempDir: File,
    ) {
        val jarFile = File(tempDir, "script.jar")
        JarOutputStream(jarFile.outputStream()).use { out ->
            out.putNextEntry(JarEntry("autoconsent/rules.json"))
            out.write("{}".toByteArray())
            out.closeEntry()
        }
        val loader = ScriptClassLoader(jarFile, Encryption.getEncryptionKey("app-key", "user-key"), Encryption())

        assertEquals("{}", loader.getResourceAsStream("autoconsent/rules.json")?.reader()?.readText())
        assertNull(loader.getResourceAsStream("absent.json"))
    }
}
