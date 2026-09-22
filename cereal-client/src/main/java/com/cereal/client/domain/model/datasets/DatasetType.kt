package com.cereal.client.domain.model.datasets

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition

/**
 * What is being imported from a file: the template that gets written, the way the file is read, and
 * the hints the import dialog shows.
 *
 * [ConfigList] is not a dataset — it is a list *configuration item* whose rows go straight into the
 * form. It rides on this type because it needs exactly the same import dialog, template download and
 * file-reading path. The name is knowingly inaccurate; renaming this abstraction and everything it
 * touches is tracked separately.
 *
 * `ConfigList` rather than `List`: this sealed class uses `List<…>` in its own constructor signatures,
 * which a nested `List` would shadow.
 */
sealed class DatasetType {
    data object Proxy : DatasetType()

    data class Custom(
        val definitions: List<ScriptConfigurationItemDefinition>,
    ) : DatasetType()

    /**
     * A list configuration item, described by its record's field definitions.
     *
     * Unlike [Custom], reading a file of this type parses and validates it in full up front, so the
     * dialog can list everything that is wrong before the user commits to importing.
     */
    data class ConfigList(
        val definitions: List<ScriptConfigurationItemDefinition>,
    ) : DatasetType()
}
