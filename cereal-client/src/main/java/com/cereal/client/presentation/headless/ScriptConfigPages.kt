package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.NumberOfConcurrentTasksConflictException
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.script.SyncScriptsOnScriptSelectionInteractor
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.containsValidData
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.domain.model.script.configuration.isGroup
import com.cereal.client.domain.model.script.configuration.isValidReturnType
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.tasks.script.overview.configuration.RawScriptConfigValues
import com.cereal.client.presentation.tasks.script.overview.configuration.model.ConfigurationItemsForm
import com.cereal.client.presentation.tasks.script.overview.configuration.validate
import com.cereal.client.presentation.view.fields.validator.IntStringFieldValidator
import com.cereal.client.presentation.view.fields.validator.isRequired
import com.cereal.sdk.statemodifier.Visibility
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The script configuration screens opened from the Tasks tab: `n` new task (script picker, then the
 * form), `v` view (read only) and `D` duplicate (a pre-filled new-task form). Each returns a
 * [TuiPage] the Tasks tab shows in place of its tree; [close] is called with the new instance (or
 * null) when the screen is done.
 */
@Suppress("LongParameterList")
class ScriptConfigPages(
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
    private val getScripts: GetScriptsInteractor,
    private val syncScripts: SyncScriptsOnScriptSelectionInteractor,
    private val getConfigDefinition: GetScriptConfigDefinitionInteractor,
    private val getInstances: GetScriptPackageInstancesByPackageNameInteractor,
    private val hasChannels: HasNotificationChannelsConfiguredInteractor,
    private val startScript: StartScriptInteractor,
    private val pickers: ConfigPickers,
) {
    fun newTask(
        group: ScriptPackageGroup,
        close: (ScriptPackageInstance?) -> Unit,
    ): TuiPage = PickerPage(group, close)

    fun duplicate(
        group: ScriptPackageGroup,
        source: ScriptPackageInstance,
        close: (ScriptPackageInstance?) -> Unit,
    ): TuiPage = FormPage(group, source.definition, source, readOnly = false, close)

    fun view(
        group: ScriptPackageGroup,
        instance: ScriptPackageInstance,
        close: (ScriptPackageInstance?) -> Unit,
    ): TuiPage = FormPage(group, instance.definition, instance, readOnly = true, close)

    /** Step 1: pick an installed script. */
    private inner class PickerPage(
        private val group: ScriptPackageGroup,
        private val close: (ScriptPackageInstance?) -> Unit,
    ) : TuiPage {
        private val list = RowList<ScriptPackage>()

        @Volatile private var scripts: List<ScriptPackage>? = null

        @Volatile private var notice: String? = null

        @Volatile private var instructions: ScriptPackage? = null

        @Volatile private var form: TuiPage? = null

        private val observation: Job =
            scope.launch {
                getScripts(Interactor.None()).collect { result ->
                    when (result) {
                        is SuspendableResult.Success -> scripts = result.value
                        is SuspendableResult.Failure -> notice = result.error.message
                    }
                    changed()
                }
            }

        override val title = "Tasks"

        override val keys: String
            get() =
                form?.keys ?: if (instructions != null) {
                    "any key back"
                } else {
                    "↑↓ select · Enter configure · r refresh · ? instructions · Esc cancel"
                }

        override fun body(
            width: Int,
            height: Int,
        ): List<String> {
            form?.let { return it.body(width, height) }
            instructions?.let { script ->
                val text = script.manifest.instructions?.takeIf { it.isNotBlank() } ?: "This script has no instructions."
                return listOf(" ${script.manifest.name}: instructions", "") + text.lines().map { "  $it" }
            }
            val scripts = scripts
            list.rows = scripts.orEmpty().map { RowList.Row(it.manifest.packageName, "${it.manifest.name.padEnd(NAME_WIDTH)} v${it.manifest.versionCode}", it) }
            val head = listOf(" New task in group ${group.name}. Step 1 of 2: pick an installed script", "")
            val rows =
                when {
                    scripts == null -> listOf("  Loading scripts…")
                    scripts.isEmpty() -> listOf("  No installed scripts. Buy scripts on the desktop or the web, then press r.")
                    else -> list.render(width, height - head.size - 2)
                }
            return head + rows + listOfNotNull("", notice?.let { "  $it" })
        }

        // Key dispatch: one branch per key binding; splitting it would scatter the bindings.
        @Suppress("ReturnCount")
        override fun onKey(key: Key): Boolean {
            form?.let { return it.onKey(key) }
            if (instructions != null) {
                instructions = null
                return true
            }
            notice = null
            if (list.onKey(key)) return true
            val char = (key as? CharKey)?.char
            val script = list.selected
            when {
                key == Keys.Escape -> {
                    finish(null)
                }

                key == Keys.Enter && script != null -> {
                    form = FormPage(group, script, null, readOnly = false, ::finish)
                }

                char == 'r' -> {
                    notice = "Refreshing scripts…"
                    scope.launch {
                        syncScripts(Interactor.None()) { result ->
                            notice = (result as? SuspendableResult.Failure)?.error?.let { it.message ?: it.toString() } ?: "Scripts refreshed."
                            changed()
                        }
                    }
                }

                char == '?' && script != null -> {
                    instructions = script
                }

                else -> {
                    return false
                }
            }
            return true
        }

        private fun finish(created: ScriptPackageInstance?) {
            observation.cancel()
            close(created)
        }
    }

    /**
     * Step 2 (or view / duplicate): the configuration form. [source] pre-fills it; with [readOnly]
     * it is the read-only view whose only way to change anything is `D`.
     */
    private inner class FormPage(
        private val group: ScriptPackageGroup,
        private val script: ScriptPackage,
        private val source: ScriptPackageInstance?,
        private val readOnly: Boolean,
        private val close: (ScriptPackageInstance?) -> Unit,
    ) : TuiPage {
        @Volatile private var config: ScriptConfigForm? = null

        @Volatile private var notice: String? = null

        /** A `[y/N]` question; [yes] runs on `y`. */
        @Volatile private var confirm: Pair<List<String>, () -> Unit>? = null

        @Volatile private var copyFrom: RowList<ScriptPackageInstance>? = null

        @Volatile private var duplicate: TuiPage? = null

        /** A picker, List rows or the notification overrides, opened with Enter on a field. */
        @Volatile private var sub: TuiPage? = null

        @Volatile private var starting = false

        init {
            scope.launch {
                getConfigDefinition(GetScriptConfigDefinitionInteractor.Params(script, source)) { result ->
                    when (result) {
                        is SuspendableResult.Success -> config = ScriptConfigForm(script, result.value, source, readOnly, ::open)
                        is SuspendableResult.Failure -> notice = result.error.message
                    }
                    changed()
                }
            }
        }

        override val title = "Tasks"

        override val keys: String
            get() {
                duplicate?.let { return it.keys }
                sub?.let { return it.keys }
                val form = config?.form
                return when {
                    confirm != null -> "y confirm · any other key cancels"
                    copyFrom != null -> "↑↓ move · Enter copy · Esc cancel"
                    form?.editing == true -> form.keys
                    readOnly -> "↑↓ move · D duplicate · Esc back"
                    else -> "Enter edit · Space toggle · ←→ choose · s start · c copy · Esc cancel"
                }
            }

        // Each overlay/state renders its own screen and returns early; nesting them would obscure the layout.
        @Suppress("ReturnCount")
        override fun body(
            width: Int,
            height: Int,
        ): List<String> {
            duplicate?.let { return it.body(width, height) }
            sub?.let { return it.body(width, height) }
            val name = "${script.manifest.name} v${script.manifest.versionCode}"
            val head =
                listOf(
                    if (readOnly) {
                        " ${source?.label() ?: name} (${group.name}): configuration, read only   * required"
                    } else {
                        " New task in ${group.name} / $name${if (source != null) ", duplicate" else ""}   * required"
                    },
                    "",
                )
            copyFrom?.let { return head + listOf("  Copy the values of:") + it.render(width, height - head.size - 1) }
            val config = config ?: return head + listOf(notice?.let { "  ! $it" } ?: "  Loading the configuration…")
            val bottom =
                confirm?.let { (lines, _) -> lines.mapIndexed { i, line -> "  $line${if (i == lines.lastIndex) " [y/N]" else ""}" } }
                    ?: listOfNotNull(notice?.let { "  ! $it" })
            config.form.rows = config.rows()
            return head + config.form.render(width, height - head.size - bottom.size) + bottom
        }

        // Key dispatch: one branch per key binding; splitting it would scatter the bindings.
        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        override fun onKey(key: Key): Boolean {
            duplicate?.let { return it.onKey(key) }
            sub?.let { return it.onKey(key) }
            val config = config
            confirm?.let { (_, yes) ->
                confirm = null
                if (key == CharKey('y') || key == CharKey('Y')) yes()
                return true
            }
            copyFrom?.let { list ->
                if (!list.onKey(key) && (key == Keys.Enter || key == Keys.Escape)) {
                    copyFrom = null
                    if (key == Keys.Enter) list.selected?.let { config?.copy(it) }
                }
                return true
            }
            if (config == null) {
                if (key != Keys.Escape) return false
                close(null)
                return true
            }
            if (config.form.onKey(key)) return true
            val char = (key as? CharKey)?.char
            notice = null
            when {
                key == Keys.Escape -> close(null)
                readOnly && char == 'D' -> duplicate = FormPage(group, script, source, readOnly = false, close)
                readOnly -> return false
                char == 's' -> start(config)
                char == 'c' -> openCopy()
                else -> return false
            }
            return true
        }

        /** Enter on a proxy, Task data, List or notification overrides row. */
        private fun open(target: ScriptConfigForm.Target) {
            val config = config ?: return
            val close = { sub = null }
            val set = { value: ConfigValue? -> config.set(target.key, value) }
            val definition = target.item?.definition
            val value = target.value
            sub =
                when (val type = definition?.type) {
                    null -> {
                        pickers.overrides(config.overrides, close)
                    }

                    ConfigItemType.ProxyConfigItem, ConfigItemType.ProxyGroupConfigItem -> {
                        pickers.proxies(script.manifest, definition.isNullable, set, close)
                    }

                    is ConfigItemType.GroupedConfigItem -> {
                        val visible = type.items.filter { it.stateModifier?.getVisibility(RawScriptConfigValues(target.section.values)) != Visibility.Hidden }
                        pickers.datasets(script.manifest, visible, definition.isNullable, (value as? ConfigValue.CustomDatasetGroupValue)?.raw, set, close)
                    }

                    is ConfigItemType.ListConfigItem -> {
                        pickers.list(definition.name, type.items, (value as? ConfigValue.ListValue)?.raw ?: ListRows.EMPTY, set, close)
                    }

                    else -> {
                        null
                    }
                }
        }

        private fun openCopy() {
            scope.launch {
                getInstances(GetScriptPackageInstancesByPackageNameInteractor.Params(script.manifest.packageName)) { result ->
                    when (result) {
                        is SuspendableResult.Success -> {
                            val instances = result.value.filter { it.definition.manifest.packageName == script.manifest.packageName }.sortedBy { it.createdAt }
                            if (instances.isEmpty()) {
                                notice = "There is no other ${script.manifest.name} to copy from."
                            } else {
                                copyFrom = RowList<ScriptPackageInstance>().apply { rows = instances.map { RowList.Row(it.id, it.label(), it) } }
                            }
                        }

                        is SuspendableResult.Failure -> {
                            notice = result.error.message
                        }
                    }
                    changed()
                }
            }
        }

        /** `s`: validate, mark every problem and jump to the first; then the desktop's checks, then start. */
        private fun start(config: ScriptConfigForm) {
            if (starting) return
            val problems = config.validate()
            if (problems > 0) {
                notice = "$problems problem(s). Fix the rows marked ! first."
                return
            }
            starting = true
            scope.launch {
                hasChannels(HasNotificationChannelsConfiguredInteractor.Params(config.overrides.overrides())) { result ->
                    when (result) {
                        is SuspendableResult.Failure -> {
                            fail(result.error.message)
                        }

                        is SuspendableResult.Success -> {
                            if (result.value) {
                                create(config, ignoreConflicts = false)
                            } else {
                                ask(listOf(NO_CHANNEL_WARNING, "Start anyway?")) { create(config, ignoreConflicts = false) }
                            }
                        }
                    }
                }
            }
        }

        private suspend fun create(
            config: ScriptConfigForm,
            ignoreConflicts: Boolean,
        ) {
            starting = true
            val params =
                StartScriptInteractor.Params(
                    groupId = group.id,
                    scriptPackage = script,
                    mainScriptConfiguration = config.mainValues(),
                    childConfigurations = config.childValues(),
                    numberOfConcurrentTasks = config.concurrentTasks,
                    ignoreConcurrencyConflicts = ignoreConflicts,
                    notificationOverrides = config.overrides.overrides(),
                )
            startScript(params) { result ->
                when (result) {
                    is SuspendableResult.Success -> {
                        // The Tasks page starts it, so a start failure shows there, not on this closed form.
                        close(result.value)
                    }

                    is SuspendableResult.Failure -> {
                        val error = result.error
                        if (error is NumberOfConcurrentTasksConflictException) {
                            ask(conflictLines(error) + "Start anyway?") { create(config, ignoreConflicts = true) }
                        } else {
                            fail(error.message)
                        }
                    }
                }
            }
            changed()
        }

        private fun ask(
            lines: List<String>,
            yes: suspend () -> Unit,
        ) {
            starting = false
            confirm = lines to { scope.launch { yes() } }
            changed()
        }

        private fun fail(message: String?) {
            starting = false
            notice = message
            changed()
        }
    }

    companion object {
        private const val NAME_WIDTH = 30
        private const val NO_CHANNEL_WARNING = "No notification channel is set up, so this script can't notify you."

        private fun conflictLines(error: NumberOfConcurrentTasksConflictException): List<String> =
            listOfNotNull(
                error.numberOfConcurrentTasksLimited?.let {
                    "'${it.definition.name}' has ${it.numberOfRecords} record(s): concurrent tasks are limited to ${it.numberOfRecords}."
                },
            ) +
                error.recordsUsedByMultipleTasks.map {
                    "'${it.definition.name}' has ${it.numberOfRecords} record(s): some are shared by several tasks."
                }

        fun ScriptPackageInstance.label(): String {
            val identifier = definition.mainScript.configuration.getScriptIdentifierValue(mainConfiguration)
            return definition.manifest.name + (identifier?.let { " ($it)" } ?: "")
        }
    }
}

/**
 * The values behind a script configuration form (main script + child scripts, and the concurrent
 * tasks count when the script has a group item), turned into [FieldForm] rows with live required
 * markers, visibility and, once [validate] ran, problems.
 */
private class ScriptConfigForm(
    script: ScriptPackage,
    result: GetScriptConfigDefinitionInteractor.Result,
    source: ScriptPackageInstance?,
    readOnly: Boolean,
    private val open: (Target) -> Unit,
) {
    /** What Enter opened: an item's picker or rows, or (no [item]) the notification overrides. */
    class Target(
        val key: String,
        val section: Section,
        val item: ConfigurationItem?,
        val value: ConfigValue?,
    )

    class Section(
        val id: String,
        val title: String,
        val definition: ScriptConfigurationDefinition,
        val items: List<ConfigurationItem>,
        val values: MutableMap<String, ConfigValue>,
    )

    val sections: List<Section> =
        listOf(Section(MAIN, script.manifest.name, script.mainScript.configuration, result.mainConfiguration, initialValues(result.mainConfiguration))) +
            result.childConfigurations.map { (key, items) ->
                val child = script.childScripts.getValue(key)
                Section(key, "${child.name} (child script)", child.configuration, items, initialValues(items))
            }

    private val showConcurrentTasks = sections.any { section -> section.items.any { it.definition.type.isGroup } }

    @Volatile var concurrentTasks: Int = source?.numberOfConcurrentTasks ?: 1
        private set

    val overrides = NotificationOverridesForm(source?.notificationOverrides, readOnly)

    @Volatile private var validated = false

    val form = FieldForm(readOnly) { field, value -> set(field.key, value) }

    fun mainValues(): Map<String, ConfigValue> = sections.first().values.toMap()

    fun childValues(): Map<String, Map<String, ConfigValue>> = sections.drop(1).associate { it.id to it.values.toMap() }

    fun rows(): List<FieldForm.Row> {
        val problems = if (validated) problems() else emptyMap()
        return sections.flatMap { section ->
            val cfg = RawScriptConfigValues(section.values)
            listOf(FieldForm.Header("h:${section.id}", section.title)) +
                section.items
                    .filter { it.definition.stateModifier?.getVisibility(cfg) != Visibility.Hidden }
                    .map { item ->
                        val key = fieldKey(section, item)
                        FieldForm.Field(
                            key = key,
                            label = item.definition.name,
                            value = section.values[item.storageKey()],
                            editor = editor(section, item),
                            required = item.definition.isRequired(cfg),
                            problem = problems[key],
                            description = item.definition.description,
                            display = display(item, section.values[item.storageKey()]),
                        )
                    } +
                if (section.id == MAIN) mainRows(section, problems) else emptyList()
        }
    }

    /** The main section's own rows: "Concurrent tasks" (with a group item) and "Notification overrides". */
    private fun mainRows(
        section: Section,
        problems: Map<String, String>,
    ): List<FieldForm.Row> =
        listOfNotNull(
            FieldForm
                .Field(
                    CONCURRENT,
                    "Concurrent tasks",
                    ConfigValue.IntValue(concurrentTasks),
                    FieldForm.Editor.Line("whole number, 1-${ConfigurationItemsForm.MAX_CONCURRENT_TASKS}", ::parseConcurrentTasks),
                    required = true,
                ).takeIf { showConcurrentTasks },
            FieldForm.Field(
                OVERRIDES,
                "Notification overrides",
                null,
                FieldForm.Editor.Open { open(Target(OVERRIDES, section, null, null)) },
                problem = problems[OVERRIDES],
                description = "Send this script's notifications to other Discord, Telegram or Email settings instead.",
                display = overrides.summary(),
            ),
        )

    /** Marks every problem and moves the cursor to the first; returns the number of problems. */
    fun validate(): Int {
        validated = true
        val problems = problems()
        rows().firstOrNull { it.key in problems }?.let {
            form.rows = rows()
            form.focus(it.key)
        }
        return problems.size
    }

    /** Takes over every value [source] has for an item of this script, as the desktop's copy does. */
    fun copy(source: ScriptPackageInstance) {
        sections.forEach { section ->
            val values = if (section.id == MAIN) source.mainConfiguration else source.childConfigurations[section.id] ?: return@forEach
            section.items.forEach { item ->
                values[item.storageKey()]?.takeIf { item.definition.isValidReturnType(it) }?.let {
                    section.values[item.storageKey()] = it
                }
            }
        }
        concurrentTasks = source.numberOfConcurrentTasks
    }

    fun set(
        key: String,
        value: ConfigValue?,
    ) {
        if (key == CONCURRENT) {
            (value as? ConfigValue.IntValue)?.let { concurrentTasks = it.raw }
            return
        }
        val (sectionId, itemKey) = key.split("/", limit = 2)
        val values = sections.first { it.id == sectionId }.values
        if (value == null) values.remove(itemKey) else values[itemKey] = value
    }

    /** Field key → problem, for the visible fields; the domain validation catches the rest. */
    private fun problems(): Map<String, String> =
        sections
            .flatMap { section ->
                val cfg = RawScriptConfigValues(section.values)
                val invalid =
                    section.definition
                        .validate(section.values)
                        .map { it.key }
                        .toSet()
                section.items
                    .filter { it.definition.stateModifier?.getVisibility(cfg) != Visibility.Hidden }
                    .mapNotNull { item ->
                        val definition = item.definition
                        val value = section.values[item.storageKey()]
                        val problem =
                            when {
                                value == null && definition.isRequired(cfg) -> "required"
                                value != null && !definition.containsValidData(value) -> "required"
                                else -> definition.stateModifier?.getError(cfg) ?: "invalid".takeIf { definition.key in invalid }
                            }
                        problem?.let { fieldKey(section, item) to it }
                    }
            }.toMap() + if (overrides.incomplete) mapOf(OVERRIDES to "incomplete") else emptyMap()

    private fun editor(
        section: Section,
        item: ConfigurationItem,
    ): FieldForm.Editor =
        when (item.definition.type) {
            ConfigItemType.ProxyConfigItem, ConfigItemType.ProxyGroupConfigItem, is ConfigItemType.GroupedConfigItem, is ConfigItemType.ListConfigItem -> {
                FieldForm.Editor.Open { open(Target(fieldKey(section, item), section, item, section.values[item.storageKey()])) }
            }

            else -> {
                valueEditor(item.definition, item.options)
            }
        }

    private fun display(
        item: ConfigurationItem,
        value: ConfigValue?,
    ): String? =
        when (item.definition.type) {
            ConfigItemType.ProxyConfigItem, ConfigItemType.ProxyGroupConfigItem -> {
                (value as? ConfigValue.ProxyGroupValue)?.raw?.let { "${it.name} (${it.numberOfItems} proxies)" }
            }

            is ConfigItemType.GroupedConfigItem -> {
                (value as? ConfigValue.CustomDatasetGroupValue)?.raw?.let { "${it.name} (${it.numberOfItems} records)" }
            }

            is ConfigItemType.ListConfigItem -> {
                (value as? ConfigValue.ListValue)?.raw?.rows?.let { if (it.isEmpty()) "no rows" else "${it.size} row(s)" } ?: "no rows"
            }

            else -> {
                null
            }
        }

    companion object {
        private const val MAIN = "main"
        private const val CONCURRENT = "cereal/concurrent"
        private const val OVERRIDES = "cereal/overrides"
        private val GROUPED_KEY = ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key

        private fun fieldKey(
            section: Section,
            item: ConfigurationItem,
        ) = "${section.id}/${item.storageKey()}"

        /** Task data is stored under one application key, not the item's own. */
        private fun ConfigurationItem.storageKey(): String = if (definition.type is ConfigItemType.GroupedConfigItem) GROUPED_KEY else definition.key

        /** Pre-filled values; a Boolean the script can't leave unset starts as "no", as the desktop's switch does. */
        private fun initialValues(items: List<ConfigurationItem>): MutableMap<String, ConfigValue> =
            items
                .mapNotNull { item ->
                    val value = item.value ?: ConfigValue.BooleanValue(false).takeIf { item.definition.type == ConfigItemType.BooleanConfigItem }
                    value?.let { item.storageKey() to it }
                }.toMap(mutableMapOf())

        private fun parseConcurrentTasks(text: String): ConfigValue {
            IntStringFieldValidator(minValue = 1, maxValue = ConfigurationItemsForm.MAX_CONCURRENT_TASKS).validate(text.trim())?.let {
                throw IllegalArgumentException(it)
            }
            return ConfigValue.IntValue(text.trim().toInt())
        }
    }
}
