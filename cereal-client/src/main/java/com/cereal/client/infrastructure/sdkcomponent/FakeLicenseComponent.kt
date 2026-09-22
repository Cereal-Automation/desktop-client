package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.sdk.component.license.HttpResponse
import com.cereal.sdk.component.license.LicenseComponent

/**
 * Always-licensed [LicenseComponent] used in the `mock` flavor so script instances can
 * run locally without contacting the marketplace API.
 */
class FakeLicenseComponent : LicenseComponent {
    override suspend fun checkScriptLicense(
        publicScriptId: String,
        salt: String,
    ): HttpResponse = AlwaysLicensedResponse

    private object AlwaysLicensedResponse : HttpResponse {
        private val payload = "{\"licensed\":true}".toByteArray()

        override val isSuccessful: Boolean = true
        override val code: Int = 200

        override fun header(
            name: String,
            defaultValue: String?,
        ): String? = defaultValue

        override fun body(): ByteArray = payload

        override fun close() {
            // No resources to release.
        }
    }
}
