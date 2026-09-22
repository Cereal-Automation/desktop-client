package com.cereal.script.sample

import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.ScriptConfigurationListItem

/**
 * Sample record type for a complex-list configuration item: one row per product the user wants to buy.
 * Exercises every permitted field type, including a nullable one.
 */
interface SampleTarget : ScriptConfigurationListItem {
    @ScriptConfigurationItem(
        keyName = "sku",
        name = "SKU",
        description = "Product identifier to look for",
        position = 0,
    )
    fun sku(): String

    @ScriptConfigurationItem(
        keyName = "quantity",
        name = "Quantity",
        description = "How many of this product to buy",
        position = 1,
    )
    fun quantity(): Int

    @ScriptConfigurationItem(
        keyName = "size",
        name = "Size",
        description = "Which size to pick",
        position = 2,
    )
    fun size(): SampleTargetSize

    @ScriptConfigurationItem(
        keyName = "maxPrice",
        name = "Max price",
        description = "Skip this product above this price. Leave blank for no cap.",
        position = 3,
    )
    fun maxPrice(): Double?

    @ScriptConfigurationItem(
        keyName = "notify",
        name = "Notify",
        description = "Send a notification when this product is bought",
        position = 4,
    )
    fun notify(): Boolean?
}

enum class SampleTargetSize {
    SMALL,
    MEDIUM,
    LARGE,
}
