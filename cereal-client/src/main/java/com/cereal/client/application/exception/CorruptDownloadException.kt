package com.cereal.client.application.exception

import java.io.IOException

/**
 * Thrown when a downloaded file's SHA-256 does not match the expected hash from the signed release
 * metadata (see [com.cereal.client.infrastructure.data.datasource.network.FileDownloader]).
 *
 * Extends [IOException] so the download stream's existing cleanup (delete the partial file, close the
 * flow) still applies, but is deliberately a distinct type: unlike an ordinary [IOException] (timeouts,
 * connectivity), a hash mismatch means the bytes that arrived are wrong — a truncated download or a
 * stale/incorrect file served by the CDN — not a connectivity problem. The interactor error mapping
 * matches this type explicitly so the user is told to retry the download rather than to "check your
 * connection". Like a transient network error, it is not reported to Sentry.
 */
class CorruptDownloadException(
    message: String,
) : IOException(message)
