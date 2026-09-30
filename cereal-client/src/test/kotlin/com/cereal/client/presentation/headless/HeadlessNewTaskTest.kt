package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptEntitlement
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.user.Subscription
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.di.modules.SandboxSampleData
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class HeadlessNewTaskTest {
    enum class Mode { MONITOR, CHECKOUT }

    /** Max price only shows (and is required) in CHECKOUT mode. */
    object ShownInCheckout : StateModifier {
        override fun getVisibility(scriptConfig: ScriptConfig): Visibility = if ((scriptConfig.valueForKey("mode") as? ScriptConfigValue.EnumScriptConfigValue)?.value == Mode.CHECKOUT) Visibility.VisibleRequired else Visibility.Hidden

        override fun getError(scriptConfig: ScriptConfig): String? = null
    }

    private fun item(
        position: Int,
        key: String,
        name: String,
        type: ConfigItemType,
        nullable: Boolean,
        default: ConfigValue? = null,
        stateModifier: StateModifier? = null,
    ) = ScriptConfigurationItemDefinition(name, "About $name.", key, position, type, nullable, stateModifier, false, default)

    @Suppress("UNCHECKED_CAST")
    private val items =
        listOf(
            item(0, "mode", "Mode", ConfigItemType.EnumConfigItem(Mode::class as KClass<Enum<*>>), false, ConfigValue.EnumValue(Mode.MONITOR)),
            item(1, "max_price", "Max price", ConfigItemType.DoubleConfigItem, true, stateModifier = ShownInCheckout),
            item(2, "api_key", "API key", ConfigItemType.SecretConfigItem, false),
            item(3, "retry_delay", "Retry delay", ConfigItemType.DoubleConfigItem, true, ConfigValue.DoubleValue(2.5)),
            item(4, "notify", "Notify", ConfigItemType.BooleanConfigItem, false),
            item(5, "account", "Account", ConfigItemType.StringConfigItem, false),
            item(6, "retries", "Retries", ConfigItemType.IntConfigItem, true),
            item(7, "proxy", "Proxy", ConfigItemType.ProxyConfigItem, true),
        )

    private val script =
        ScriptPackage(
            source = File("."),
            manifest = Manifest(packageName = "com.example.monitor", name = "Monitor", versionCode = 3, instructions = "Paste your API key first."),
            mainScript = MainScript(HeadlessTasksTest.RunForeverScript::class, ScriptConfigurationDefinition(ScriptConfiguration::class, items)),
            childScripts = emptyMap(),
        )

    private val licensed =
        InMemoryAuthProvider(subscriptions = listOf(Subscription("sub", ScriptEntitlement("com.example.monitor", "Monitor", null, null, null, null))))

    private val alpha = ScriptPackageGroup("g-alpha", "Alpha")

    private val threeProxies = SandboxSampleData.proxyGroups().first { it.numberOfItems == 3 }

    private fun existing(
        id: String,
        values: Map<String, ConfigValue>,
        concurrentTasks: Int = 1,
    ) = ScriptPackageInstance(id, values, emptyMap(), script, Clock.System.now(), concurrentTasks)

    private val filled =
        mapOf(
            "mode" to ConfigValue.EnumValue(Mode.CHECKOUT),
            "max_price" to ConfigValue.DoubleValue(180.0),
            "api_key" to ConfigValue.SecretValue(Secret("s3cret-key")),
            "retry_delay" to ConfigValue.DoubleValue(2.5),
            "notify" to ConfigValue.BooleanValue(true),
            "account" to ConfigValue.StringValue("me@example.com"),
        )

    /** Signed in, licensed for Monitor, the Default group on screen; [existing] instances sit in Alpha. */
    private fun newTaskTest(
        vararg existing: ScriptPackageInstance,
        channel: Boolean = true,
        block: suspend HeadlessTestScope.() -> Unit,
    ) = runHeadlessTest(
        seed = {
            loadModules(listOf(module { single<AuthProvider> { licensed } }), allowOverride = true)
            (get<ScriptRepository>() as InMemoryScriptRepository).seed(listOf(script))
            get<NotificationSettingsRepository>().setDiscordWebhookEnabled(channel)
            if (existing.isNotEmpty()) get<TasksRepository>().createScriptInstanceGroup(alpha)
        },
    ) {
        awaitText("Default  0/0 running")
        existing.forEach { get<ScriptInstanceRepository>().addScriptPackageInstance(alpha.id, it, MainScriptInstance("script-${it.id}", script.mainScript, it.mainConfiguration, it.createdAt, it)) }
        existing.firstOrNull()?.let { awaitText("Monitor") }
        block()
    }

    private suspend fun HeadlessTestScope.openForm() {
        press(CharKey('n'))
        awaitText("› Monitor")
        press(Keys.Enter)
        awaitText("› * Mode")
    }

    /** Moves the cursor onto the [label] row, awaiting each step (see HeadlessTasksTest). */
    private suspend fun HeadlessTestScope.moveTo(label: String) {
        repeat(ROWS) {
            val rows = screen()
            val cursor = rows.indexOfFirst { it.startsWith("› ") }
            val target = rows.indexOfFirst { " $label " in it }
            check(target >= 0) { "No row $label:\n" + rows.joinToString("\n") }
            if (cursor == target) return
            press(if (target < cursor) Keys.Up else Keys.Down)
            awaitScreen { now -> now.indexOfFirst { it.startsWith("› ") } != cursor }
        }
    }

    private fun HeadlessTestScope.row(label: String) = screen().first { " $label " in it }

    private suspend fun HeadlessTestScope.created(): List<ScriptPackageInstance> = get<ScriptInstanceRepository>().getScriptPackageInstances()

    @Test
    fun `n opens the script picker with versions and instructions, then a form with the defaults filled in`() =
        newTaskTest {
            press(CharKey('n'))
            awaitText("Step 1 of 2")
            val picker = awaitText("› Monitor")
            assertTrue(picker.any { it.startsWith("› Monitor") && it.endsWith("v3") }, picker.joinToString("\n"))
            press(CharKey('?'))
            awaitText("Paste your API key first.")
            press(CharKey('x'))
            awaitText("› Monitor")
            press(Keys.Enter)
            val form = awaitText("› * Mode")
            val text = form.joinToString("\n")
            assertTrue(row("Mode").endsWith("‹ MONITOR ›"), text)
            assertTrue(row("Retry delay").endsWith("2.5"), text)
            assertTrue(row("Notify").endsWith("[ ] no"), text)
            assertTrue(row("API key").trimStart().startsWith("*"), text)
            assertFalse(form.any { "Max price" in it }, text)
            assertTrue(form.any { "About Mode." in it }, text)
        }

    @Test
    fun `r re-syncs the installed scripts from the picker`() =
        newTaskTest {
            press(CharKey('n'))
            awaitText("› Monitor")
            press(CharKey('r'))
            awaitText("Scripts refreshed.")
        }

    @Test
    fun `a task is created and started end to end with Boolean, String, Secret, number and Enum items`() =
        newTaskTest(channel = false) {
            openForm()
            // Enum: CHECKOUT reveals the required Max price (live visibility and required marker).
            press(Keys.Right)
            awaitText("‹ CHECKOUT ›")
            assertTrue(row("Max price").trimStart().startsWith("*"))

            moveTo("Max price")
            press(Keys.Enter)
            awaitText("Max price (number) *")
            type("2,5")
            awaitText("> 2,5_")
            press(Keys.Enter)
            awaitText("! '2,5' is not a number.")
            press(Keys.Backspace, Keys.Backspace)
            awaitText("> 2_")
            type(".5")
            press(Keys.Enter)
            awaitScreen { rows -> rows.none { "(number)" in it } && row("Max price").endsWith("2.5") }

            moveTo("API key")
            press(Keys.Enter)
            awaitText("API key (secret) *")
            type("hunter2")
            awaitText("> *******_")
            press(Keys.Enter)
            awaitScreen { row("API key").endsWith(FieldForm.SECRET_SET) }
            // Re-editing starts empty; an empty submit keeps the value.
            press(Keys.Enter)
            awaitText("> _")
            press(Keys.Enter)
            awaitScreen { rows -> rows.none { "(secret)" in it } && row("API key").endsWith(FieldForm.SECRET_SET) }

            moveTo("Notify")
            press(Keys.Space)
            awaitScreen { row("Notify").endsWith("[x] yes") }

            moveTo("Account")
            press(Keys.Enter)
            type("me@example.com")
            press(Keys.Enter)
            awaitScreen { row("Account").endsWith("me@example.com") }

            moveTo("Retries")
            press(Keys.Enter)
            type("x")
            press(Keys.Enter)
            awaitText("! 'x' is not a whole number.")
            press(Keys.Backspace)
            type("3")
            press(Keys.Enter)
            awaitScreen { row("Retries").endsWith("3") }

            press(CharKey('s'))
            awaitText("No notification channel is set up")
            awaitText("Start anyway? [y/N]")
            press(CharKey('y'))
            awaitText("● #1  Running")

            val values = created().single().mainConfiguration
            assertEquals(ConfigValue.EnumValue(Mode.CHECKOUT), values["mode"])
            assertEquals(ConfigValue.DoubleValue(2.5), values["max_price"])
            assertEquals("hunter2", (values["api_key"] as ConfigValue.SecretValue).raw.reveal())
            assertEquals(ConfigValue.BooleanValue(true), values["notify"])
            assertEquals(ConfigValue.StringValue("me@example.com"), values["account"])
            assertEquals(ConfigValue.IntValue(3), values["retries"])
            assertFalse("hunter2" in terminal.buffer.toString(), "the secret reached the terminal")
        }

    @Test
    fun `s marks every problem, jumps to the first and starts nothing`() =
        newTaskTest {
            openForm()
            press(Keys.Right)
            awaitText("‹ CHECKOUT ›")
            press(CharKey('s'))
            val screen = awaitText("3 problem(s)")
            val text = screen.joinToString("\n")
            assertTrue(screen.any { it.startsWith("› * Max price") && it.endsWith("! required") }, text)
            assertTrue(row("API key").endsWith("! required"), text)
            assertTrue(row("Account").endsWith("! required"), text)
            assertFalse(row("Retries").contains("!"), text)
            // Problems re-evaluate live: back to MONITOR hides Max price and its problem.
            moveTo("Mode")
            press(Keys.Left)
            awaitScreen { rows -> rows.none { "Max price" in it } }
            assertTrue(created().isEmpty())
        }

    @Test
    fun `v views a configuration read only with secrets masked, and D duplicates it into a pre-filled form`() =
        newTaskTest(existing("pkg-1", filled)) {
            moveTo("Monitor")
            press(CharKey('v'))
            awaitText("configuration, read only")
            val view = awaitText(FieldForm.SECRET_SET)
            assertTrue(row("API key").endsWith(FieldForm.SECRET_SET), view.joinToString("\n"))
            assertTrue(row("Max price").endsWith("180.0"))
            press(Keys.Right)
            press(CharKey(' '))
            press(Keys.Enter)
            press(CharKey('s'))
            awaitScreen { row("Mode").endsWith("‹ CHECKOUT ›") && row("Notify").endsWith("[x] yes") }
            assertTrue(screen().none { "(secret)" in it || "problem" in it })

            press(CharKey('D'))
            awaitText(", duplicate")
            awaitText("› * Mode")
            moveTo("Account")
            press(Keys.Enter)
            awaitText("> me@example.com_")
            type("2")
            press(Keys.Enter)
            awaitScreen { row("Account").endsWith("me@example.com2") }
            press(CharKey('s'))
            awaitScreen { rows -> rows.any { "Monitor  " in it && "running" in it } && rows.none { ", duplicate" in it } }

            val all = created()
            assertEquals(2, all.size)
            val copy = all.first { it.id != "pkg-1" }
            assertEquals("s3cret-key", (copy.mainConfiguration["api_key"] as ConfigValue.SecretValue).raw.reveal())
            assertEquals(ConfigValue.StringValue("me@example.com2"), copy.mainConfiguration["account"])
            assertEquals(ConfigValue.StringValue("me@example.com"), all.first { it.id == "pkg-1" }.mainConfiguration["account"])
            assertFalse("s3cret-key" in terminal.buffer.toString(), "the secret reached the terminal")
        }

    @Test
    fun `c copies the values of an existing instance into the new form`() =
        newTaskTest(existing("pkg-1", filled)) {
            press(CharKey('n'))
            awaitText("› Monitor")
            press(Keys.Enter)
            awaitText("› * Mode")
            press(CharKey('c'))
            awaitText("Copy the values of:")
            awaitText("› Monitor")
            press(Keys.Enter)
            awaitScreen { row("Mode").endsWith("‹ CHECKOUT ›") }
            assertTrue(row("API key").endsWith(FieldForm.SECRET_SET))
            assertTrue(row("Account").endsWith("me@example.com"))
            assertTrue(row("Max price").endsWith("180.0"))
        }

    @Test
    fun `too few proxies for the concurrent tasks asks before starting`() =
        newTaskTest(existing("pkg-1", filled + ("proxy" to ConfigValue.ProxyGroupValue(threeProxies)), concurrentTasks = 5)) {
            moveTo("Monitor")
            press(CharKey('D'))
            awaitText(", duplicate")
            val form = awaitText(" Concurrent tasks ")
            assertTrue(row("Proxy").endsWith("(3 proxies)"), form.joinToString("\n"))
            assertTrue(row("Concurrent tasks").endsWith("5"))
            press(CharKey('s'))
            awaitText("some are shared by several tasks.")
            awaitText("Start anyway? [y/N]")
            press(CharKey('n'))
            awaitScreen { rows -> rows.none { "[y/N]" in it } }
            assertEquals(1, created().size)
            press(CharKey('s'))
            awaitText("Start anyway? [y/N]")
            press(CharKey('y'))
            awaitScreen { rows -> rows.none { ", duplicate" in it } }
            assertEquals(5, created().first { it.id != "pkg-1" }.numberOfConcurrentTasks)
        }

    private companion object {
        const val ROWS = 30
    }
}
