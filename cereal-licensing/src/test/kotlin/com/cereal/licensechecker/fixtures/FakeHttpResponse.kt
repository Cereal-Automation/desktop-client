package com.cereal.licensechecker.fixtures

import com.cereal.sdk.component.license.HttpResponse

/**
 * Real in-memory [HttpResponse] fake. Backed by plain fields so tests can assert on observable
 * behaviour (resulting [com.cereal.licensechecker.LicenseState]) instead of verifying call
 * sequences against a mock.
 *
 * @param body the raw response body bytes the checker will read and parse.
 * @param headers header lookup table; [header] returns the matching value or the supplied default.
 * @param isSuccessful whether the HTTP call is considered successful.
 * @param code the HTTP status code.
 */
class FakeHttpResponse(
    private val body: ByteArray,
    private val headers: Map<String, String> = emptyMap(),
    override val isSuccessful: Boolean = true,
    override val code: Int = 200,
) : HttpResponse {
    var closed: Boolean = false
        private set

    override fun header(
        name: String,
        defaultValue: String?,
    ): String? = headers[name] ?: defaultValue

    override fun body(): ByteArray = body

    override fun close() {
        closed = true
    }
}
