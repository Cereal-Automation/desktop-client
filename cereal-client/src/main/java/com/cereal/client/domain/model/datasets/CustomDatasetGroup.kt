package com.cereal.client.domain.model.datasets

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The number of fields in a CustomDatasetItem always equals the amount of [CustomDatasetGroup.itemDefinitions].
 */
@OptIn(ExperimentalTime::class)
data class CustomDatasetGroup(
    override val id: String,
    override val name: String,
    override val numberOfItems: Int,
    val itemDefinitions: List<ScriptConfigurationItemDefinition>,
    override val items: Sequence<CustomDatasetItem>,
    // No default: equals/hashCode are ID-only, so a Clock.System.now() default would
    // silently mint a fresh timestamp every time a group is reconstructed.
    val createdAt: Instant,
) : Group<CustomDatasetItem> {
    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomDatasetGroup) return false

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
