package com.cereal.client.domain.model.script.configuration

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import kotlin.reflect.KClass

enum class ApplicationScriptConfigurationKeys(
    val key: String,
    val type: KClass<*>,
) {
    @Deprecated("Replaced by a dedicated property: MainScriptInstance.numberOfConcurrentTasks")
    KEY_NUMBER_OF_TASKS("cereal_number_of_tasks", Int::class),

    KEY_CUSTOM_DATASET("cereal_custom_dataset", CustomDatasetGroup::class),
}
