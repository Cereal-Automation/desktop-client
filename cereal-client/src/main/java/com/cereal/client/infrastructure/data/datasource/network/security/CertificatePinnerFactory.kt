package com.cereal.client.infrastructure.data.datasource.network.security

import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Builds OkHttp [CertificatePinner]s from a list of SPKI pins.
 *
 * The marketplace API and the download/update channel sit behind Cloudflare/Google Trust
 * Services, which rotate the *leaf* certificate frequently. Pinning the leaf would brick the
 * client on every rotation, so callers should pin the **intermediate CA and/or root** instead:
 * OkHttp accepts a connection when ANY pinned hash matches ANY certificate in the presented
 * chain, so an intermediate pin plus a stable root pin survives leaf rotation while still
 * rejecting a chain anchored on an unexpected (e.g. attacker-injected) CA.
 *
 * Pins are supplied as a list so a backup pin can be shipped alongside the active one and the
 * set can be rotated by release (via `BuildConfig`/`ApplicationConfig`) without code changes.
 */
object CertificatePinnerFactory {
    /**
     * Returns a [CertificatePinner] that pins [url]'s host to every entry in [pins]
     * (each a `sha256/<base64>` SPKI hash), or `null` when [pins] is empty or [url] has no
     * parseable host. A `null` result leaves the client unpinned — call sites that require
     * pinning in production must assert [pins] is non-empty separately (see the startup
     * self-test in `App`).
     */
    fun create(
        url: String,
        pins: List<String>,
    ): CertificatePinner? {
        if (pins.isEmpty()) return null
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return CertificatePinner
            .Builder()
            .apply { pins.forEach { add(host, it) } }
            .build()
    }
}
