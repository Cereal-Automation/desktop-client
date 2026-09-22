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
)
