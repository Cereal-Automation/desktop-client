package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.customdataset.DeleteCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetGroupsInteractor
import com.cereal.client.application.interactor.files.ReadCustomDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadListFileInteractor
import com.cereal.client.application.interactor.files.ReadProxyFileInteractor
import com.cereal.client.application.interactor.proxy.GetProxyGroupsInteractor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.EmailOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.notification.TelegramOverrides
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ListRow
import com.cereal.client.domain.model.script.configuration.ListRows
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.script.configuration.compareTo
import com.cereal.client.domain.model.script.configuration.describeRejection
import com.cereal.client.domain.model.script.configuration.parseValue
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The sub-screens the new-task form opens with Enter: the proxy group and Task data pickers (each
 * with `+ new` from a paste or a file in the data [volume]), a List item's rows, and the
 * notification overrides. Each returns a [TuiPage] the form shows in its place until [close].
 */
@Suppress("LongParameterList")
class ConfigPickers(
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
    private val volume: File,
    private val getProxyGroups: GetProxyGroupsInteractor,
    private val readProxyFile: ReadProxyFileInteractor,
    private val getDatasetGroups: GetCustomDatasetGroupsInteractor,
    private val readDatasetFile: ReadCustomDatasetFileInteractor,
    private val deleteDataset: DeleteCustomDatasetGroupInteractor,
    private val readListFile: ReadListFileInteractor,
) {
    fun proxies(
        manifest: Manifest,
        nullable: Boolean,
        set: (ConfigValue?) -> Unit,
        close: () -> Unit,
    ): TuiPage =
        GroupPicker(
            heading = "Pick a proxy group",
            noun = "proxy list",
            groups = { getProxyGroups(Interactor.None()) },
            key = ProxyGroup::id,
            label = { "${it.name}  (${it.numberOfItems} proxies)" },
            nullable = nullable,
            pastePrompt = listOf("One proxy per line: ${FileSystemProxyTemplateDataSource.TEMPLATE}"),
            import = { source -> source.import(volume, readProxyFile) { ReadProxyFileInteractor.Params(it, manifest) }.map { it.group } },
            delete = null,
            choose = { group ->
                set(group?.let { ConfigValue.ProxyGroupValue(it) })
                close()
            },
            close = close,
        )

    /** Only the datasets whose fields match [definitions] (the item's visible fields) are offered. */
    fun datasets(
        manifest: Manifest,
        definitions: List<ScriptConfigurationItemDefinition>,
        nullable: Boolean,
        current: CustomDatasetGroup?,
        set: (ConfigValue?) -> Unit,
        close: () -> Unit,
    ): TuiPage =
        GroupPicker(
            heading = "Pick a Task data dataset (one record per task)",
            noun = "CSV",
            groups = { getDatasetGroups(Interactor.None()) },
            key = CustomDatasetGroup::id,
            label = { "${it.name}  (${it.numberOfItems} records)" },
            nullable = nullable,
            filter = { definitions.compareTo(it.itemDefinitions).isEmpty() },
            pastePrompt = csvPrompt(definitions),
            import = { source ->
                source.import(volume, readDatasetFile) { ReadCustomDatasetFileInteractor.Params(it, manifest, definitions) }.map { it.group }
            },
            delete = { group ->
                var error: String? = null
                deleteDataset(DeleteCustomDatasetGroupInteractor.Params(group)) { error = (it as? SuspendableResult.Failure)?.error?.message }
                if (error == null && group.id == current?.id) set(null)
                error
            },
            choose = { group ->
                set(group?.let { ConfigValue.CustomDatasetGroupValue(it) })
                close()
            },
            close = close,
        )

    /** A List item's rows: every change goes to [set] right away (null once the last row is gone). */
    fun list(
        title: String,
        definitions: List<ScriptConfigurationItemDefinition>,
        current: ListRows,
        set: (ConfigValue?) -> Unit,
        close: () -> Unit,
    ): TuiPage = ListRowsPage(title, definitions, current, set, close)

    fun overrides(
        form: NotificationOverridesForm,
        close: () -> Unit,
    ): TuiPage =
        object : TuiPage {
            override val title = "Tasks"
            override val keys get() = if (form.form.editing) form.form.keys else "${form.form.keys} · Esc back"

            override fun body(
                width: Int,
                height: Int,
            ): List<String> {
                form.form.rows = form.rows()
                return listOf(" Notification overrides: send this script's notifications here instead of the global settings", "") +
                    form.form.render(width, height - 2)
            }

            override fun onKey(key: Key): Boolean {
                if (form.form.onKey(key)) return true
                if (key != Keys.Escape) return false
                close()
                return true
            }
        }

    /** A picker over groups, then `+ new from pasted …` / `+ new from a file in the volume`. */
    private inner class GroupPicker<G : Any>(
        private val heading: String,
        private val noun: String,
        groups: suspend () -> Flow<SuspendableResult<List<G>, Exception>>,
        private val key: (G) -> String,
        private val label: (G) -> String,
        private val nullable: Boolean,
        private val filter: (G) -> Boolean = { true },
        private val pastePrompt: List<String>,
        private val import: suspend (ImportSource) -> Result<G>,
        /** Deletes a group (`d`, after confirming); returns the error, if any. Null: no `d`. */
        private val delete: (suspend (G) -> String?)?,
        private val choose: (G?) -> Unit,
        private val close: () -> Unit,
    ) : TuiPage {
        private val list = RowList<Any>()

        /** Setting it refreshes the list, so a key handled before the next render sees the same rows. */
        @Volatile private var loaded: List<G>? = null
            set(value) {
                field = value
                list.rows =
                    (if (nullable) listOf(RowList.Row<Any>(NONE, "(none)", NONE)) else emptyList()) +
                    value.orEmpty().map { RowList.Row<Any>(key(it), label(it), it) } +
                    listOf(RowList.Row<Any>(PASTE, "+ new from pasted $noun", PASTE), RowList.Row<Any>(FILE, "+ new from a file in the volume", FILE))
            }

        @Volatile private var notice: String? = null

        @Volatile private var confirm: G? = null

        @Volatile private var input: ImportInput? = null

        private val observation: Job =
            scope.launch {
                groups().collect { result ->
                    when (result) {
                        is SuspendableResult.Success -> loaded = result.value.filter(filter)
                        is SuspendableResult.Failure -> notice = result.error.message
                    }
                    changed()
                }
            }

        override val title = "Tasks"

        override val keys: String
            get() =
                input?.keys ?: if (confirm != null) {
                    "y delete · any other key cancels"
                } else {
                    "↑↓ select · Enter choose${if (delete != null) " · d delete" else ""} · Esc back"
                }

        override fun body(
            width: Int,
            height: Int,
        ): List<String> {
            input?.let { return listOf(" $heading: new from ${if (list.selected == PASTE) "a paste" else "a file in the volume"}", "") + it.render(width, height - 2) }
            val groups = loaded
            val head = listOf(" $heading", "")
            val bottom =
                confirm?.let { listOf("", "  Delete ${label(it)}? It can't be undone. [y/N]") }
                    ?: notice
                        ?.lines()
                        ?.map { "  ! $it" }
                        ?.let { listOf("") + it }
                        .orEmpty()
            if (groups == null) return head + "  Loading…"
            return head + list.render(width, height - head.size - bottom.size) + bottom
        }

        override fun onKey(key: Key): Boolean {
            input?.let { return it.onKey(key) }
            confirm?.let { group ->
                confirm = null
                if (key == CharKey('y') || key == CharKey('Y')) {
                    scope.launch {
                        notice = delete?.invoke(group)
                        changed()
                    }
                }
                return true
            }
            if (loaded == null && key != Keys.Escape) return false
            notice = null
            if (list.onKey(key)) return true
            val selected = list.selected
            when {
                key == Keys.Escape -> {
                    finish(null, cancel = true)
                }

                key == Keys.Enter && (selected == PASTE || selected == FILE) -> {
                    input = ImportInput(selected == PASTE, pastePrompt, ::runImport) { input = null }
                }

                key == Keys.Enter -> {
                    @Suppress("UNCHECKED_CAST")
                    finish(selected.takeUnless { it == NONE } as G?, cancel = false)
                }

                key == CharKey('d') && delete != null && selected != null && selected !is String -> {
                    @Suppress("UNCHECKED_CAST")
                    confirm = selected as G
                }

                else -> {
                    return false
                }
            }
            return true
        }

        private fun runImport(source: ImportSource) {
            scope.launch {
                import(source)
                    .onSuccess { finish(it, cancel = false) }
                    .onFailure { input?.failed("Nothing was imported.\n${importError(it)}") }
                changed()
            }
        }

        private fun finish(
            group: G?,
            cancel: Boolean,
        ) {
            observation.cancel()
            if (cancel) close() else choose(group)
        }
    }

    /** `a` add, `Enter` edit, `d` delete, `i` / `f` import a pasted CSV / a file (replacing after a confirmation). */
    private inner class ListRowsPage(
        private val title0: String,
        private val definitions: List<ScriptConfigurationItemDefinition>,
        initial: ListRows,
        private val set: (ConfigValue?) -> Unit,
        private val close: () -> Unit,
    ) : TuiPage {
        private val list = RowList<Int>()

        /** Setting it refreshes the list, so a key handled before the next render sees the same rows. */
        @Volatile private var rows: List<ListRow> = initial.rows
            set(value) {
                field = value
                list.rows = value.mapIndexed { i, row -> RowList.Row(i.toString(), "${i + 1}. ${describe(row)}", i) }
            }

        init {
            rows = initial.rows
        }

        @Volatile private var editing: RowEditor? = null

        @Volatile private var input: ImportInput? = null

        @Volatile private var replace: ListRows? = null

        @Volatile private var notice: String? = null

        override val title = "Tasks"

        override val keys: String
            get() =
                editing?.keys ?: input?.keys ?: if (replace != null) {
                    "y replace · any other key cancels"
                } else {
                    "a add · Enter edit · d delete · i paste CSV · f CSV file · Esc back"
                }

        override fun body(
            width: Int,
            height: Int,
        ): List<String> {
            editing?.let { return it.body(width, height) }
            input?.let { return listOf(" $title0: import rows (replaces the list)", "") + it.render(width, height - 2) }
            val head = listOf(" $title0: ${rows.size} row(s)", "")
            val bottom =
                replace?.let { listOf("", "  Replace the ${rows.size} existing row(s) with ${it.rows.size} imported? [y/N]") }
                    ?: notice
                        ?.lines()
                        ?.map { "  ! $it" }
                        ?.let { listOf("") + it }
                        .orEmpty()
            val body = if (rows.isEmpty()) listOf("  No rows yet. Press a to add one, or i / f to import a CSV.") else list.render(width, height - head.size - bottom.size)
            return head + body + bottom
        }

        override fun onKey(key: Key): Boolean {
            editing?.let { return it.onKey(key) }
            input?.let { return it.onKey(key) }
            replace?.let { imported ->
                replace = null
                if (key == CharKey('y') || key == CharKey('Y')) update(imported.rows)
                return true
            }
            notice = null
            if (list.onKey(key)) return true
            val char = (key as? CharKey)?.char
            val at = list.selected
            when {
                key == Keys.Escape -> close()
                char == 'a' -> editing = RowEditor(null, ListRow(emptyMap()))
                key == Keys.Enter && at != null -> rows.getOrNull(at)?.let { editing = RowEditor(at, it) }
                char == 'd' && at != null -> update(rows.filterIndexed { i, _ -> i != at })
                char == 'i' || char == 'f' -> input = ImportInput(char == 'i', csvPrompt(definitions), ::runImport) { input = null }
                else -> return false
            }
            return true
        }

        private fun runImport(source: ImportSource) {
            scope.launch {
                source
                    .import(volume, readListFile) { ReadListFileInteractor.Params(it, definitions) }
                    .onSuccess {
                        input = null
                        if (rows.isEmpty()) update(it.rows.rows) else replace = it.rows
                    }.onFailure { input?.failed("Nothing was imported.\n${importError(it)}") }
                changed()
            }
        }

        private fun update(rows: List<ListRow>) {
            this.rows = rows
            set(if (rows.isEmpty()) null else ConfigValue.ListValue(ListRows(rows)))
        }

        private fun describe(row: ListRow): String =
            definitions
                .mapNotNull { d -> row.fields[d.key]?.let { "${d.name}: ${if (it is ConfigValue.SecretValue) FieldForm.SECRET_SET else it.raw}" } }
                .joinToString(", ")
                .ifEmpty { "(empty)" } +
                if (definitions.any { !it.isNullable && row.fields[it.key] == null }) "  ! incomplete" else ""

        /** One row in the field-list form; Esc keeps it (an untouched new row is dropped). */
        private inner class RowEditor(
            private val index: Int?,
            start: ListRow,
        ) {
            private val values = start.fields.toMutableMap()

            private val form =
                FieldForm { field, value ->
                    if (value == null) values.remove(field.key) else values[field.key] = value
                }

            val keys get() = if (form.editing) form.keys else "${form.keys} · Esc done"

            fun body(
                width: Int,
                height: Int,
            ): List<String> {
                form.rows =
                    definitions.sortedBy { it.position }.map { d ->
                        FieldForm.Field(
                            key = d.key,
                            label = d.name,
                            value = values[d.key],
                            editor = valueEditor(d, enumOptions(d)),
                            required = !d.isNullable,
                            problem = if (!d.isNullable && values[d.key] == null) "required" else null,
                            description = d.description,
                        )
                    }
                return listOf(" $title0: ${if (index == null) "new row" else "row ${index + 1}"}   * required", "") + form.render(width, height - 2)
            }

            fun onKey(key: Key): Boolean {
                if (form.onKey(key)) return true
                if (key != Keys.Escape) return false
                editing = null
                val row = ListRow(values.toMap())
                when {
                    index != null -> update(rows.mapIndexed { i, old -> if (i == index) row else old })
                    values.isNotEmpty() -> update(rows + row)
                }
                return true
            }
        }
    }

    private companion object {
        const val NONE = "(none)"
        const val PASTE = "+paste"
        const val FILE = "+file"

        fun csvPrompt(definitions: List<ScriptConfigurationItemDefinition>) =
            listOf(
                "CSV with a header row of the item keys, then one record per line:",
                definitions.sortedBy { it.position }.joinToString(",") { it.key },
            )

        fun enumOptions(definition: ScriptConfigurationItemDefinition): List<ConfigValue> =
            (definition.type as? ConfigItemType.EnumConfigItem)
                ?.enumType
                ?.java
                ?.enumConstants
                ?.map { ConfigValue.EnumValue(it) }
                .orEmpty()
    }
}

/** The editor for a plain value item (Boolean, Secret, String, numbers, Enum); [FieldForm.Editor.None] for anything else. */
internal fun valueEditor(
    definition: ScriptConfigurationItemDefinition,
    options: List<ConfigValue>,
): FieldForm.Editor =
    when (definition.type) {
        ConfigItemType.BooleanConfigItem -> FieldForm.Editor.Toggle
        ConfigItemType.SecretConfigItem -> FieldForm.Editor.Secret
        ConfigItemType.StringConfigItem -> FieldForm.Editor.Line("text") { ConfigValue.StringValue(it) }
        ConfigItemType.IntConfigItem -> FieldForm.Editor.Line("whole number") { parseNumber(definition, it) }
        ConfigItemType.FloatConfigItem, ConfigItemType.DoubleConfigItem -> FieldForm.Editor.Line("number") { parseNumber(definition, it) }
        is ConfigItemType.EnumConfigItem -> FieldForm.Editor.Cycle(options, definition.isNullable)
        else -> FieldForm.Editor.None
    }

/** The existing parsing, with its rejection sentence ("'2,5' is not a number."). */
private fun parseNumber(
    definition: ScriptConfigurationItemDefinition,
    text: String,
): ConfigValue? =
    try {
        definition.parseValue(text)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException(definition.type.describeRejection(text))
    }

/**
 * The per-script notification overrides behind the form's "Notification overrides" row: a toggle per
 * channel (Discord, Telegram, Email) revealing that channel's fields, as on the desktop.
 */
class NotificationOverridesForm(
    initial: ScriptNotificationOverrides?,
    readOnly: Boolean,
) {
    private val values = mutableMapOf<String, ConfigValue>()

    val form = FieldForm(readOnly) { field, value -> if (value == null) values.remove(field.key) else values[field.key] = value }

    init {
        initial?.discordOverrides?.let {
            put(DISCORD, true)
            put("discord.url", it.webhookUrl)
        }
        initial?.telegramOverrides?.let {
            put(TELEGRAM, true)
            values["telegram.token"] = ConfigValue.SecretValue(Secret(it.botToken))
            put("telegram.chat", it.chatId)
        }
        values["email.tls"] = ConfigValue.BooleanValue(true)
        initial?.emailOverrides?.let {
            put(EMAIL, true)
            put("email.host", it.smtpHost)
            values["email.port"] = ConfigValue.IntValue(it.smtpPort)
            put("email.user", it.username)
            values["email.password"] = ConfigValue.SecretValue(Secret(it.password))
            put("email.from", it.from)
            put("email.to", it.to)
            put("email.tls", it.useTls)
        }
    }

    /** "none", or the overridden channels; with a problem while an enabled channel is missing a value. */
    fun summary(): String = listOf(DISCORD to "Discord", TELEGRAM to "Telegram", EMAIL to "Email").filter { on(it.first) }.joinToString(", ") { it.second }.ifEmpty { "none" }

    val incomplete: Boolean get() = fields().any { problem(it) != null }

    fun rows(): List<FieldForm.Row> =
        fields().flatMap { field ->
            listOfNotNull(
                HEADERS[field.key]?.let { FieldForm.Header("h:${field.key}", it) },
                field.copy(problem = problem(field)),
            )
        }

    /** "required" when missing, else the desktop settings' check of the value, if it has one. */
    private fun problem(field: FieldForm.Field): String? {
        if (field.required && field.value == null) return "required"
        val value = field.value?.raw?.let { (it as? Secret)?.reveal() ?: it.toString() } ?: return null
        return CHECKS[field.key]?.problem(value)
    }

    fun overrides(): ScriptNotificationOverrides? {
        val discord = if (on(DISCORD)) DiscordOverrides(text("discord.url")) else null
        val telegram = if (on(TELEGRAM)) TelegramOverrides(secret("telegram.token"), text("telegram.chat")) else null
        val email =
            if (on(EMAIL)) {
                EmailOverrides(
                    smtpHost = text("email.host"),
                    smtpPort = (values["email.port"] as? ConfigValue.IntValue)?.raw ?: DEFAULT_SMTP_PORT,
                    username = text("email.user"),
                    password = secret("email.password"),
                    from = text("email.from"),
                    to = text("email.to"),
                    useTls = on("email.tls"),
                )
            } else {
                null
            }
        return ScriptNotificationOverrides(discord, telegram, email).takeIf { it.hasAnyOverrides() }
    }

    private fun fields(): List<FieldForm.Field> =
        listOf(toggle(DISCORD, "Override Discord")) +
            (if (on(DISCORD)) listOf(line("discord.url", "Webhook URL")) else emptyList()) +
            toggle(TELEGRAM, "Override Telegram") +
            (if (on(TELEGRAM)) listOf(secretField("telegram.token", "Bot token"), line("telegram.chat", "Chat ID")) else emptyList()) +
            toggle(EMAIL, "Override Email") +
            if (on(EMAIL)) {
                listOf(
                    line("email.host", "SMTP host"),
                    FieldForm.Field("email.port", "SMTP port", values["email.port"], FieldForm.Editor.Line("whole number", ::parsePort), required = true),
                    line("email.user", "Username"),
                    secretField("email.password", "Password"),
                    line("email.from", "From"),
                    line("email.to", "To"),
                    toggle("email.tls", "Use TLS"),
                )
            } else {
                emptyList()
            }

    private fun put(
        key: String,
        value: Any,
    ) {
        values[key] = if (value is Boolean) ConfigValue.BooleanValue(value) else ConfigValue.StringValue(value.toString())
    }

    private fun on(key: String) = (values[key] as? ConfigValue.BooleanValue)?.raw == true

    private fun text(key: String) = (values[key] as? ConfigValue.StringValue)?.raw.orEmpty()

    private fun secret(key: String) = (values[key] as? ConfigValue.SecretValue)?.raw?.reveal().orEmpty()

    private fun toggle(
        key: String,
        label: String,
    ) = FieldForm.Field(key, label, values[key], FieldForm.Editor.Toggle)

    private fun line(
        key: String,
        label: String,
    ) = FieldForm.Field(key, label, values[key], FieldForm.Editor.Line("text") { ConfigValue.StringValue(it) }, required = true)

    private fun secretField(
        key: String,
        label: String,
    ) = FieldForm.Field(key, label, values[key], FieldForm.Editor.Secret, required = true)

    private companion object {
        const val DISCORD = "discord"
        const val TELEGRAM = "telegram"
        const val EMAIL = "email"
        const val DEFAULT_SMTP_PORT = 587
        val CHECKS =
            mapOf(
                "discord.url" to NotificationField.DISCORD_WEBHOOK_URL,
                "telegram.token" to NotificationField.TELEGRAM_BOT_TOKEN,
                "telegram.chat" to NotificationField.TELEGRAM_CHAT_ID,
                "email.from" to NotificationField.FROM,
                "email.to" to NotificationField.TO,
            )
        const val MAX_PORT = 65535
        val HEADERS = mapOf(DISCORD to "Discord", TELEGRAM to "Telegram", EMAIL to "Email")

        fun parsePort(text: String): ConfigValue {
            val port = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: throw IllegalArgumentException("'$text' is not a port (1-$MAX_PORT).")
            return ConfigValue.IntValue(port)
        }
    }
}
