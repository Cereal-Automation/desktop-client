package com.cereal.script.sample

import com.cereal.script.sample.modifier.ProxyKeyNullableStateModifier
import com.cereal.script.sample.modifier.TargetsStateModifier
import com.cereal.script.sample.modifier.WebsiteOptionOneStateModifier
import com.cereal.script.sample.modifier.WebsiteOptionTwoStateModifier
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.models.proxy.Proxy
import com.cereal.sdk.models.proxy.RandomProxy

// Sample configuration intentionally exposes one accessor per supported config-item
// type, so the function count legitimately exceeds the project threshold.
@Suppress("TooManyFunctions")
interface SampleConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(
        keyName = "StringKeyNullable",
        name = "String config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
    )
    fun keyStringNullable(): String?

    @ScriptConfigurationItem(
        keyName = "BooleanKeyNullable",
        name = "Boolean config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
    )
    fun keyBooleanNullable(): Boolean?

    @ScriptConfigurationItem(
        keyName = "IntegerKeyNullable",
        name = "Integer config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
    )
    fun keyIntegerNullable(): Int?

    @ScriptConfigurationItem(
        keyName = "FloatingKeyNullable",
        name = "Float config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
    )
    fun keyFloatNullable(): Float?

    @ScriptConfigurationItem(
        keyName = "DoubleKeyNullable",
        name = "Double config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
    )
    fun keyDoubleNullable(): Double?

    @ScriptConfigurationItem(
        keyName = "EnumKeyNullable",
        name = "Enum config item nullable",
        description = "Please select a website to run",
    )
    fun dropdownOptionNullable(): SampleConfigWebsite?

    @ScriptConfigurationItem(
        keyName = "Targets",
        name = "Targets",
        description = "A list of records: each row is one product to buy, with its own quantity and size.",
        stateModifier = TargetsStateModifier::class,
    )
    fun targets(): List<SampleTarget>?

    @ScriptConfigurationItem(
        keyName = "RandomProxyKey",
        name = "RandomProxy",
        description = "",
    )
    fun randomProxyKeyCSVNullable(): RandomProxy?

    @ScriptConfigurationItem(
        keyName = "StringKeyCSV",
        name = "String config item",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyStringCSV(): String?

    @ScriptConfigurationItem(
        keyName = "IntegerKeyCSV",
        name = "Integer config item",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyIntegerCSV(): Int?

    @ScriptConfigurationItem(
        keyName = "FloatingKeyCSV",
        name = "Float config item",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyFloatCSV(): Float?

    @ScriptConfigurationItem(
        keyName = "DoubleKeyCSV",
        name = "Double config item",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyDoubleCSV(): Double?

    @ScriptConfigurationItem(
        keyName = "StringKeyNullableCSV",
        name = "String config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyStringNullableCSV(): String?

    @ScriptConfigurationItem(
        keyName = "IntegerKeyNullableCSV",
        name = "Integer config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyIntegerNullableCSV(): Int?

    @ScriptConfigurationItem(
        keyName = "FloatingKeyNullableCSV",
        name = "Float config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionOneStateModifier::class,
    )
    fun keyFloatNullableCSV(): Float?

    @ScriptConfigurationItem(
        keyName = "DoubleKeyNullableCSV",
        name = "Double config item nullable",
        description = "A very long long looooong description text which should describe the function of this configuration",
        valuePerTask = true,
        stateModifier = WebsiteOptionTwoStateModifier::class,
    )
    fun keyDoubleNullableCSV(): Double?

    @ScriptConfigurationItem(
        keyName = "ProxyKeyNullable",
        name = "Proxy nullable",
        description = "",
        stateModifier = ProxyKeyNullableStateModifier::class,
    )
    fun proxyKeyNullableCSV(): Proxy?

    @ScriptConfigurationItem(
        keyName = "TestLogger",
        name = "Test Logger",
        description = "Enable testing of LoggerComponent",
    )
    fun testLogger(): Boolean?

    @ScriptConfigurationItem(
        keyName = "TestPreferences",
        name = "Test Preferences",
        description = "Enable testing of PreferenceComponent",
    )
    fun testPreferences(): Boolean?

    @ScriptConfigurationItem(
        keyName = "TestNotification",
        name = "Test Notification",
        description = "Enable testing of NotificationComponent",
    )
    fun testNotification(): Boolean?

    @ScriptConfigurationItem(
        keyName = "TestScriptLauncher",
        name = "Test Script Launcher",
        description = "Enable testing of ScriptLauncherComponent",
    )
    fun testScriptLauncher(): Boolean?

    @ScriptConfigurationItem(
        keyName = "TestUserInteraction",
        name = "Test User Interaction",
        description = "Enable testing of UserInteractionComponent",
    )
    fun testUserInteraction(): Boolean?

    @ScriptConfigurationItem(
        keyName = "TestLicense",
        name = "Test License",
        description = "Enable testing of LicenseComponent",
    )
    fun testLicense(): Boolean?

    @ScriptConfigurationItem(
        keyName = "TestArtifacts",
        name = "Test Artifacts",
        description = "Enable testing of ArtifactComponent (emits downloadable files)",
    )
    fun testArtifacts(): Boolean?

    @ScriptConfigurationItem(
        keyName = "CrashWithException",
        name = "Crash With Exception",
        description = "Enable to crash the script with an exception for demo purposes",
    )
    fun crashWithException(): Boolean?
}
