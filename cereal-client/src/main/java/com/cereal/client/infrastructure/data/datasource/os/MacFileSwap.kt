package com.cereal.client.infrastructure.data.datasource.os

import com.sun.jna.LastErrorException
import com.sun.jna.Library
import com.sun.jna.Native
import org.slf4j.LoggerFactory

/**
 * Atomically exchanges two filesystem paths on macOS via libc `renamex_np` with the `RENAME_SWAP`
 * flag. On APFS this swaps the two entries in a single atomic operation: after the call the paths
 * point at each other's previous contents, and there is never a moment where the target path does
 * not exist (unlike a delete-then-move). The running process keeps its open file handles to the
 * bundle it was launched from — the inode survives the swap — so it stays alive until it chooses to
 * exit.
 *
 * This is a genuine external edge (a native syscall available only on macOS), so it is isolated
 * behind [swap] and injected as a seam into [MacAppBundleInstaller]; there is no meaningful way to
 * unit-test the syscall itself. `renamex_np` availability on a live bundle is one of the per-OS
 * validation items that require a real signed build on a real Mac.
 */
internal object MacFileSwap {
    private val logger = LoggerFactory.getLogger(MacFileSwap::class.java)

    /** libc `RENAME_SWAP` flag (`sys/stdio.h`): atomically exchange `from` and `to`. */
    private const val RENAME_SWAP = 0x2

    private interface Libc : Library {
        // Mirrors the libc symbol name exactly (JNA resolves the native function by this name), so it
        // cannot follow Kotlin camelCase.
        @Suppress("FunctionNaming", "ktlint:standard:function-naming")
        @Throws(LastErrorException::class)
        fun renamex_np(
            from: String,
            to: String,
            flags: Int,
        ): Int
    }

    private val libc: Libc? by lazy {
        try {
            Native.load("c", Libc::class.java)
        } catch (e: UnsatisfiedLinkError) {
            logger.error("Could not load libc for renamex_np; macOS self-update swap unavailable", e)
            null
        }
    }

    /**
     * Swaps [from] and [to] atomically. Returns true on success; false when libc is unavailable or
     * the syscall fails (e.g. `ENOTSUP` on a non-APFS volume), letting the caller fall back.
     */
    fun swap(
        from: String,
        to: String,
    ): Boolean {
        val c = libc ?: return false
        return try {
            c.renamex_np(from, to, RENAME_SWAP) == 0
        } catch (e: LastErrorException) {
            logger.error("renamex_np(RENAME_SWAP) failed swapping $from and $to", e)
            false
        }
    }
}
