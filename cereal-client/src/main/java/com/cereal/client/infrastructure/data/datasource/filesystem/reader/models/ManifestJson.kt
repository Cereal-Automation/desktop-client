package com.cereal.client.infrastructure.data.datasource.filesystem.reader.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the structure of a manifest JSON file for a cereal package.
 */
@Serializable
data class ManifestJson(
    /** The unique identifier of the package */
    @SerialName("package_name") val packageName: String,
    /** The display name of the package */
    @SerialName("name") val name: String,
    /** The version code of the package as a numeric value */
    @SerialName("version_code") val versionCode: Long,
    /** The fully qualified class name of the main script package. */
    @SerialName("script") val scriptPackageClass: String,
    /** List of fully qualified class names for child script packages, if any */
    @SerialName("child_scripts") val childScriptPackageClasses: List<String>? = null,
    /** Optional instructions to display at the top of the script configuration screen */
    @SerialName("instructions") val instructions: String? = null,
    /** The SDK version this script was built against */
    @SerialName("sdk_version") val sdkVersion: String? = null,
    /** Script features the script uses, as raw manifest values. Missing means none. */
    @SerialName("features") val features: List<String> = emptyList(),
    /** The entry scripts users pick from, replacing [scriptPackageClass] as an entry. Missing means that is the only entry. */
    @SerialName("variants") val variants: List<VariantJson> = emptyList(),
)

/**
 * An entry script of a package, listed in the manifest's `variants`. Required fields are nullable so a
 * missing key is rejected by the reader with a reason naming it, like a blank value.
 */
@Serializable
data class VariantJson(
    /** Stable id the client persists; unique within the package. */
    @SerialName("id") val id: String? = null,
    /** Display name; may change between releases. */
    @SerialName("name") val name: String? = null,
    /** Fully qualified class name of the variant's script. */
    @SerialName("script") val scriptClass: String? = null,
    /** Optional instructions to display at the top of the variant's configuration screen. */
    @SerialName("instructions") val instructions: String? = null,
)
