package com.cereal.client.application.interactor.script.validator

import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.asGroup
import com.cereal.client.domain.model.script.configuration.groupedConfigurationDefinition

/**
 * Computes the number of dataset records a single run of [scriptPackage] will process, for start-time
 * capacity enforcement (see `CapacityExceededException`).
 *
 * **Aggregation rule (run-wide figure):** the record count is the **main script's grouped dataset**
 * size — `mainScript.configuration.groupedConfigurationDefinition()` resolved against the configured
 * value's [Group.numberOfItems] — or `0` when the main script has no grouped dataset (nothing to cap).
 * The capacity unit maps to the records of that primary dataset, the same figure
 * [ScriptConcurrencyValidator] treats as the run's record pool.
 *
 * Child grouped datasets are intentionally **excluded**: they are secondary configurations, and summing
 * heterogeneous datasets against a single "records" entitlement would over-count. Revisit here if the
 * entitlement's unit is ever redefined to span child datasets.
 */
class CapacityValidator(
    private val scriptPackage: ScriptPackage,
    private val mainScriptConfiguration: ScriptConfigurationValues,
) {
    fun runRecordCount(): Int =
        scriptPackage.mainScript.configuration
            .groupedConfigurationDefinition()
            ?.let { definition -> mainScriptConfiguration[definition.key]?.asGroup }
            ?.numberOfItems
            ?: 0
}
