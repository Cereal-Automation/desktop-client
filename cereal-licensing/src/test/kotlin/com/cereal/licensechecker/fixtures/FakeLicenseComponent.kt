package com.cereal.licensechecker.fixtures

import com.cereal.sdk.component.license.HttpResponse
import com.cereal.sdk.component.license.LicenseComponent
import java.io.IOException

/**
 * Real in-memory [LicenseComponent] fake.
 *
 * The salt that [com.cereal.licensechecker.LicenseChecker] generates is private to the JVM, so
 * tests cannot know it up front. Instead this fake captures the salt the checker actually passes
 * and hands it to [responseFactory], allowing a test to build a response whose signature is
 * computed over the *real* salt the production code will verify against. This keeps the test honest
 * (it exercises the genuine signature path) without mocking the checker's internals.
 *
 * @param responseFactory builds the response for a given (scriptId, salt). Throwing here simulates
 *   transport failures.
 */
class FakeLicenseComponent(
    private val responseFactory: (scriptId: String, salt: String) -> HttpResponse,
) : LicenseComponent {
    var capturedScriptId: String? = null
        private set
    var capturedSalt: String? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): HttpResponse {
        capturedScriptId = publicScriptId
        capturedSalt = salt
        callCount++
        return responseFactory(publicScriptId, salt)
    }

    companion object {
        /** A component whose transport always fails with an [IOException]. */
        fun failing(message: String): FakeLicenseComponent = FakeLicenseComponent { _, _ -> throw IOException(message) }
    }
}
