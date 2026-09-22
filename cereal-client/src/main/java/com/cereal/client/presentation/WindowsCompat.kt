package com.cereal.client.presentation

import java.io.File

/**
 * Two issues cause [java.io.tmpdir] to be unsafe for the sqlite-bundled JNI DLL on Windows:
 *
 * 1. **Non-ASCII usernames** (e.g. o, e) — [java.io.tmpdir] resolves to a path that LoadLibraryW
 *    can't handle, producing UnsatisfiedLinkError: "Can't find dependent libraries".
 *
 * 2. **Windows Application Control (WDAC/AppLocker)** — policies that block executable content
 *    from %TEMP% prevent loading the extracted .tmp/.dll, producing UnsatisfiedLinkError:
 *    "An Application Control policy has blocked this file".
 *
 * Redirecting to %LOCALAPPDATA%\Cereal\temp (a user-writable directory outside the system %TEMP%
 * tree) resolves both. Must be called before [com.cereal.client.App.initialize].
 *
 * Note: this mutates the JVM-global [java.io.tmpdir] property, which affects all libraries that
 * create temporary files (not just sqlite-bundled).
 */
internal fun ensureSafeWindowsTempDir() {
    if (!System.getProperty("os.name", "").lowercase().startsWith("win")) return

    val localAppData =
        System.getenv("LOCALAPPDATA")
            ?: System.getenv("PROGRAMDATA")
            ?: "C:\\ProgramData"

    val safeTemp = File(localAppData, "Cereal\\temp")

    if (!safeTemp.exists() && !safeTemp.mkdirs()) {
        throw RuntimeException("Unable to create temp directory: ${safeTemp.absolutePath}")
    }
    if (!safeTemp.canWrite()) {
        throw RuntimeException("Temp directory is not writable: ${safeTemp.absolutePath}")
    }

    // Clean up stale native library temp files left by previous runs
    safeTemp
        .listFiles { _, name -> name.startsWith("androidx_sqliteJni") && name.endsWith(".tmp") }
        ?.forEach { it.delete() }

    System.setProperty("java.io.tmpdir", safeTemp.absolutePath)
}
