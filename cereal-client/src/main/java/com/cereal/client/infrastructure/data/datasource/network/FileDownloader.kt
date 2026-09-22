import com.cereal.client.application.exception.CorruptDownloadException
import com.cereal.client.infrastructure.data.datasource.network.security.CertificatePinnerFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * @param sslPins SPKI pins (`sha256/<base64>`) applied to the host of each download [url]. The
 *   installer is fetched from the same Cloudflare/Google-fronted downloads host as the update
 *   metadata, so pinning here closes the MITM gap on the binary download (issue #490). Empty
 *   disables pinning (tests/mock).
 */
class FileDownloader(
    private val sslPins: List<String> = emptyList(),
) {
    private val logger = LoggerFactory.getLogger(FileDownloader::class.java)

    /**
     * Downloads [url] and streams the response body straight to [destination].
     *
     * Progress events are emitted as [DownloadProgress.Downloading] while bytes are transferred.
     * On completion [DownloadProgress.Finished] carries the written [destination] file.
     *
     * @param maxSizeBytes Hard cap on the response body size. The download is aborted and
     *   [destination] is deleted if the server-advertised `Content-Length` already exceeds this
     *   value, or if more than [maxSizeBytes] bytes are actually received. Defaults to 2 GB.
     * @param expectedSha256 Optional lowercase hex SHA-256 the downloaded bytes must match. When
     *   provided, the digest is computed while streaming and the download is rejected (and
     *   [destination] deleted) on mismatch, preventing a tampered installer from being launched
     *   (see issue #484).
     */
    fun download(
        url: String,
        destination: File,
        expectedSha256: String? = null,
        maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    ) = callbackFlow {
        val request =
            Request
                .Builder()
                .url(url)
                .build()

        val client =
            OkHttpClient
                .Builder()
                .readTimeout(1, TimeUnit.HOURS)
                .apply {
                    // Pin the download host so a compromised CA can't swap the installer binary.
                    CertificatePinnerFactory.create(url, sslPins)?.let { certificatePinner(it) }
                }.build()

        val call = client.newCall(request)

        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    close(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (!response.isSuccessful) {
                        close(IOException("Unexpected response code $response"))
                        return
                    }

                    val body = response.body
                    val contentLength = body.contentLength()

                    if (contentLength > maxSizeBytes) {
                        close(
                            IOException(
                                "Content-Length $contentLength exceeds max allowed size $maxSizeBytes",
                            ),
                        )
                        return
                    }

                    try {
                        // Blocking IO: runs inside OkHttp's dispatcher thread, which is fine for
                        // callbackFlow — we must NOT switch to Dispatchers.IO here because we
                        // cannot suspend inside onResponse.
                        var bytesWritten = 0L
                        val inputStream = body.byteStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }

                        destination.outputStream().use { outputStream ->
                            while (true) {
                                val read = inputStream.read(buffer)
                                if (read == -1) break

                                bytesWritten += read

                                if (bytesWritten > maxSizeBytes) {
                                    outputStream.close()
                                    destination.delete()
                                    close(
                                        IOException(
                                            "Download exceeded max allowed size $maxSizeBytes bytes",
                                        ),
                                    )
                                    return
                                }

                                digest?.update(buffer, 0, read)
                                outputStream.write(buffer, 0, read)

                                if (contentLength > 0) {
                                    trySendBlocking(
                                        DownloadProgress.Downloading(bytesWritten, contentLength),
                                    ).onFailure { _ ->
                                        // Downstream cancelled — stop writing.
                                        outputStream.close()
                                        destination.delete()
                                        close()
                                        return
                                    }
                                }
                            }
                        }

                        if (digest != null) {
                            val actualSha256 =
                                digest.digest().joinToString("") { byte ->
                                    "%02x".format(byte.toInt() and HEX_BYTE_MASK)
                                }
                            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                                // A CorruptDownloadException (an IOException subtype) so the surrounding
                                // catch deletes the file and closes the flow with the error (same cleanup
                                // as other failures), while the distinct type lets the interactor error
                                // mapping tell the user to retry rather than "check your connection" — the
                                // bytes are wrong (truncated, or a stale/incorrect file from the CDN), not
                                // a connectivity problem.
                                throw CorruptDownloadException(
                                    "SHA-256 mismatch: expected $expectedSha256 but was $actualSha256",
                                )
                            }
                        }

                        trySendBlocking(DownloadProgress.Finished(destination))
                            .onFailure { _ ->
                                // Downstream cancelled after the file was fully written; clean up.
                                destination.delete()
                            }
                        close()
                    } catch (e: IOException) {
                        destination.delete()
                        logger.error("Error streaming download to ${destination.path}", e)
                        close(e)
                    }
                }
            },
        )

        awaitClose { call.cancel() }
    }

    sealed class DownloadProgress {
        data class Downloading(
            val bytesRead: Long,
            val contentLength: Long,
        ) : DownloadProgress()

        data class Finished(
            val file: File,
        ) : DownloadProgress()
    }

    private companion object {
        /** 2 GB — large enough for any realistic installer. */
        const val DEFAULT_MAX_SIZE_BYTES: Long = 2L * 1024 * 1024 * 1024

        /** 8 KiB read buffer. */
        const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

        /** Mask to convert a signed byte to its unsigned value when hex-encoding the digest. */
        const val HEX_BYTE_MASK: Int = 0xFF
    }
}
