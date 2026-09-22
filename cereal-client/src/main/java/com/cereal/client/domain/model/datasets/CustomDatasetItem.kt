package com.cereal.client.domain.model.datasets

import com.cereal.client.domain.model.script.configuration.ConfigValue
import java.util.UUID

/**
 * A single record within a [CustomDatasetGroup].
 *
 * @property fields the record's values keyed by configuration item key. Values use the canonical typed
 *   [ConfigValue] model (a null value means the field is present in the schema but unset for this
 *   record) rather than an untyped `Any?`.
 */
data class CustomDatasetItem(
    val id: UUID,
    val fields: Map<String, ConfigValue?>,
) {
    init {
        require(fields.isNotEmpty()) { "Fields map must not be empty" }
    }
}
