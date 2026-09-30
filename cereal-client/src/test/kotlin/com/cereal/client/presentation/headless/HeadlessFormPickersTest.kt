package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.datasets.toDatasetItems
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ApplicationScriptConfigurationKeys
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import com.cereal.client.infrastructure.data.datasource.csv.CsvWriter
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.provider.DatasetFileProviderImpl
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.sdk.ScriptConfiguration
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import java.util.Collections
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class HeadlessFormPickersTest {
    private fun item(
        position: Int,
        key: String,
        name: String,
        type: ConfigItemType,
        nullable: Boolean,
    ) = ScriptConfigurationItemDefinition(name, "About $name.", key, position, type, nullable, null, false, null)

    private val email = item(0, "email", "Email", ConfigItemType.StringConfigItem, false)
    private val size = item(1, "size", "Size", ConfigItemType.StringConfigItem, true)
    private val tagName = item(0, "name", "Name", ConfigItemType.StringConfigItem, false)
    private val tagQty = item(1, "qty", "Qty", ConfigItemType.IntConfigItem, true)

    private val items =
        listOf(
            item(0, "proxy", "Proxy", ConfigItemType.ProxyConfigItem, true),
            item(1, "accounts", "Accounts", ConfigItemType.GroupedConfigItem(listOf(email, size)), true),
            item(2, "tags", "Tags", ConfigItemType.ListConfigItem(ScriptConfiguration::class, listOf(tagName, tagQty)), true),
        )

    private val script =
        ScriptPackage(
            source = File("."),
            manifest = Manifest(packageName = "com.example.monitor", name = "Monitor", versionCode = 3),
            mainScript = MainScript(HeadlessTasksTest.RunForeverScript::class, ScriptConfigurationDefinition(ScriptConfiguration::class, items)),
            childScripts = emptyMap(),
        )

    private val licensed =
        InMemoryAuthProvider(subscriptions = listOf(Subscription("sub", ScriptEntitlement("com.example.monitor", "Monitor", null, null, null, null))))

    private fun dataset(
        id: String,
        name: String,
        definitions: List<ScriptConfigurationItemDefinition>,
        count: Int,
    ) = CustomDatasetGroup(id, name, count, definitions, emptySequence(), Clock.System.now())

    private val matching = dataset("ds-1", "Accounts EU", listOf(email, size), 4)
    private val other = dataset("ds-2", "Unrelated", listOf(item(0, "sku", "SKU", ConfigItemType.StringConfigItem, false)), 7)

    /** Every file an import read, so the tests can check the pasted temp files are gone. */
    private val readFiles: MutableList<File> = Collections.synchronizedList(mutableListOf())

    /** The in-memory datasets, with the real CSV parsing behind [readFromFile]. */
    private inner class CsvDatasets(
        private val inner: InMemoryCustomDatasetRepository = InMemoryCustomDatasetRepository(listOf(matching, other)),
    ) : CustomDatasetRepository by inner {
        override suspend fun readFromFile(
            file: File,
            definitions: List<ScriptConfigurationItemDefinition>,
        ): List<CustomDatasetItem> {
            readFiles += file
            return try {
                definitions.toDatasetItems(CsvReader().readRawRows(file))
            } catch (e: com.cereal.client.domain.model.datasets.InvalidDatasetFileException) {
                throw com.cereal.client.application.datasets
                    .InvalidFileException(e.message ?: "", e)
            }
        }
    }

    private fun pickerTest(block: suspend HeadlessTestScope.() -> Unit) =
        runHeadlessTest(
            seed = {
                val csvFiles =
                    object : DatasetFileProvider by DatasetFileProviderImpl(CsvReader(), CsvWriter(), FileSystemProxyTemplateDataSource()) {
                        override fun readRows(file: File): List<Map<String, String>> {
                            readFiles += file
                            return CsvReader().readRawRows(file)
                        }
                    }
                loadModules(
                    listOf(
                        module {
                            single<AuthProvider> { licensed }
                            single<CustomDatasetRepository> { CsvDatasets() }
                            single<DatasetFileProvider> { csvFiles }
                        },
                    ),
                    allowOverride = true,
                )
                (get<ScriptRepository>() as InMemoryScriptRepository).seed(listOf(script))
                get<NotificationSettingsRepository>().setDiscordWebhookEnabled(true)
            },
        ) {
            awaitText("Default  0/0 running")
            press(CharKey('n'))
            awaitText("› Monitor")
            press(Keys.Enter)
            awaitText("›   Proxy")
            block()
        }

    private suspend fun HeadlessTestScope.moveTo(label: String) {
        repeat(ROWS) {
            val rows = screen()
            val cursor = rows.indexOfFirst { it.startsWith("› ") }
            val target = rows.indexOfFirst { " $label " in it || it.endsWith(" $label") }
            check(target >= 0) { "No row $label:\n" + rows.joinToString("\n") }
            if (cursor == target) return
            press(if (target < cursor) Keys.Up else Keys.Down)
            awaitScreen { now -> now.indexOfFirst { it.startsWith("› ") } != cursor }
        }
    }

    private fun HeadlessTestScope.row(label: String) = screen().firstOrNull { " $label " in it }.orEmpty()

    /** Types [lines] into an open paste box, then ends it with Ctrl-D. */
    private suspend fun HeadlessTestScope.paste(vararg lines: String) {
        lines.forEachIndexed { i, line ->
            if (i > 0) press(Keys.Enter)
            type(line)
            awaitText("│ ${line}_")
        }
        press(Keys.Eof)
    }

    private suspend fun HeadlessTestScope.start(): ScriptPackageInstance {
        press(CharKey('s'))
        // Created, not necessarily running: the in-memory proxy groups hold no proxies to run with.
        awaitText(" #1  ")
        return get<ScriptInstanceRepository>().getScriptPackageInstances().single()
    }

    private fun pastedTempFiles() = readFiles.filter { it.name.startsWith("cereal-paste-") }

    @Test
    fun `the proxy picker lists groups with counts and a paste creates and selects a new group`() =
        pickerTest {
            press(Keys.Enter)
            val picker = awaitText("+ new from pasted proxy list")
            val groups = get<ProxyRepository>().getProxyGroups().first()
            val text = picker.joinToString("\n")
            assertTrue(picker.any { it.startsWith("› (none)") }, text)
            groups.forEach { group -> assertTrue(picker.any { "${group.name}  (${group.numberOfItems} proxies)" in it }, text) }
            assertTrue(picker.any { "+ new from a file in the volume" in it }, text)

            moveTo("+ new from pasted proxy list")
            press(Keys.Enter)
            awaitText(FileSystemProxyTemplateDataSource.TEMPLATE)
            paste("10.0.0.1:8080", "10.0.0.2:8080:user:pass")
            awaitScreen { row("Proxy").contains("Imported for Monitor") }

            // The in-memory repository keeps the new group but not its proxies; the chosen value has them.
            val created = get<ProxyRepository>().getProxyGroups().first() - groups.toSet()
            val chosen = (start().mainConfiguration["proxy"] as ConfigValue.ProxyGroupValue).raw
            assertEquals(created.single(), chosen)
            assertEquals(2, chosen.numberOfItems)
            assertTrue(File(System.getProperty("java.io.tmpdir")).listFiles().orEmpty().none { it.name.startsWith("cereal-paste-") })
        }

    @Test
    fun `a bad proxy paste reports the line and imports nothing`() =
        pickerTest {
            val before = get<ProxyRepository>().getProxyGroups().first()
            press(Keys.Enter)
            awaitText("+ new from pasted proxy list")
            moveTo("+ new from pasted proxy list")
            press(Keys.Enter)
            paste("10.0.0.1:8080", "10.0.0.2:http")
            awaitText("! Nothing was imported.")
            awaitText("! Line 2: The port must be a valid number")
            assertEquals(before, get<ProxyRepository>().getProxyGroups().first())
            press(Keys.Escape)
            awaitText("+ new from pasted proxy list")
            press(Keys.Escape)
            awaitScreen { row("Proxy").endsWith("not set") }
        }

    @Test
    fun `Task data offers only matching datasets, shows the header row and imports a file from the volume`() =
        pickerTest {
            moveTo("Accounts")
            press(Keys.Enter)
            val picker = awaitText("Accounts EU  (4 records)")
            assertFalse(picker.any { "Unrelated" in it }, picker.joinToString("\n"))

            moveTo("+ new from pasted CSV")
            press(Keys.Enter)
            awaitText("email,size")
            press(Keys.Escape)
            awaitText("+ new from a file in the volume")

            val csv = File.createTempFile("accounts", ".csv").apply { writeText("email,size\na@example.com,M\nb@example.com,\n") }
            try {
                moveTo("+ new from a file in the volume")
                press(Keys.Enter)
                awaitText("Path in the data volume:")
                type(csv.absolutePath)
                press(Keys.Enter)
                awaitScreen { row("Accounts").contains("Imported for Monitor") }
            } finally {
                csv.delete()
            }
            assertTrue(csv.path in readFiles.map { it.path }.toString())
            val chosen = start().mainConfiguration[ApplicationScriptConfigurationKeys.KEY_CUSTOM_DATASET.key] as ConfigValue.CustomDatasetGroupValue
            assertEquals(2, chosen.raw.numberOfItems)
        }

    @Test
    fun `a bad Task data paste reports the line, imports nothing and leaves no temp file`() =
        pickerTest {
            moveTo("Accounts")
            press(Keys.Enter)
            awaitText("+ new from pasted CSV")
            moveTo("+ new from pasted CSV")
            press(Keys.Enter)
            paste("email,size", "a@example.com,M", ",L")
            awaitText("! Nothing was imported.")
            awaitText("! Line 3: column 'email': a value is required but the cell is empty.")
            assertEquals(listOf(matching, other), get<CustomDatasetRepository>().getDatasetGroups().first())
            assertTrue(pastedTempFiles().isNotEmpty())
            assertTrue(pastedTempFiles().none { it.exists() }, "a pasted temp file was left behind")

            press(Keys.Escape)
            awaitText("+ new from pasted CSV")
            press(Keys.Escape)
            awaitScreen { row("Accounts").endsWith("not set") }
        }

    @Test
    fun `d deletes a dataset from the picker only after confirming`() =
        pickerTest {
            moveTo("Accounts")
            press(Keys.Enter)
            awaitText("Accounts EU  (4 records)")
            moveTo("Accounts EU  (4 records)")
            press(CharKey('d'))
            awaitText("Delete Accounts EU  (4 records)? It can't be undone. [y/N]")
            press(CharKey('n'))
            awaitScreen { rows -> rows.none { "[y/N]" in it } }
            assertEquals(2, get<CustomDatasetRepository>().getDatasetGroups().first().size)

            press(CharKey('d'))
            awaitText("[y/N]")
            press(CharKey('y'))
            awaitScreen { rows -> rows.none { "Accounts EU" in it } }
            assertEquals(listOf(other), get<CustomDatasetRepository>().getDatasetGroups().first())
        }

    @Test
    fun `List rows are added, imported after confirming the replace, deleted and saved with the task`() =
        pickerTest {
            moveTo("Tags")
            press(Keys.Enter)
            awaitText("No rows yet.")
            press(CharKey('a'))
            awaitText("Tags: new row")
            press(Keys.Enter)
            type("Bob")
            press(Keys.Enter)
            awaitScreen { row("Name").endsWith("Bob") }
            press(Keys.Escape)
            awaitText("1. Name: Bob")

            press(CharKey('i'))
            awaitText("name,qty")
            paste("name,qty", "Ann,2", "Cid,x")
            awaitText("! Nothing was imported.")
            awaitText("! Line 3: column 'qty': 'x' is not a whole number.")
            press(Keys.Backspace)
            type("3")
            awaitText("│ Cid,3_")
            press(Keys.Eof)
            awaitText("Replace the 1 existing row(s) with 2 imported? [y/N]")
            press(CharKey('y'))
            awaitText("2. Name: Cid, Qty: 3")
            assertTrue(screen().any { "1. Name: Ann, Qty: 2" in it })

            press(CharKey('d'))
            awaitScreen { rows -> rows.none { "Ann" in it } }
            press(Keys.Escape)
            awaitScreen { row("Tags").endsWith("1 row(s)") }
            assertTrue(pastedTempFiles().none { it.exists() }, "a pasted temp file was left behind")

            val rows = (start().mainConfiguration["tags"] as ConfigValue.ListValue).raw.rows
            assertEquals(listOf(mapOf("name" to ConfigValue.StringValue("Cid"), "qty" to ConfigValue.IntValue(3))), rows.map { it.fields })
        }

    @Test
    fun `notification overrides open as a sub-form and reach the new task`() =
        pickerTest {
            moveTo("Notification overrides")
            assertTrue(row("Notification overrides").endsWith("none"))
            press(Keys.Enter)
            awaitText("Override Discord")
            press(Keys.Space)
            awaitText("Webhook URL")
            press(Keys.Escape)
            awaitScreen { row("Notification overrides").endsWith("Discord") }
            press(CharKey('s'))
            awaitText("1 problem(s)")
            assertTrue(row("Notification overrides").endsWith("! incomplete"))

            press(Keys.Enter)
            awaitText("Override Discord")
            moveTo("Webhook URL")
            press(Keys.Enter)
            type("https://discord.com/api/webhooks/1/hook")
            press(Keys.Enter)
            awaitScreen { "https://discord.com/api/web" in row("Webhook URL") && "!" !in row("Webhook URL") }
            press(Keys.Escape)
            awaitScreen { row("Notification overrides").endsWith("Discord") }
            val overrides = start().notificationOverrides
            assertEquals("https://discord.com/api/webhooks/1/hook", overrides?.discordOverrides?.webhookUrl)
        }

    private companion object {
        const val ROWS = 30
    }
}
