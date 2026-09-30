package com.cereal.client.presentation.headless

import com.cereal.client.application.interactor.task.AnswerUserInteractionInteractor
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.DiscordOverrides
import com.cereal.client.domain.model.notification.ScriptNotificationOverrides
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.domain.provider.SessionLostProvider
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.provider.SessionLostProviderImpl
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.cereal.client.infrastructure.sdkcomponent.UserInteractionComponentImpl
import com.cereal.sdk.ScriptConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.HeadlessTestScope
import testutil.runHeadlessTest
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class HeadlessClientNotificationsTest {
    private val group = ScriptPackageGroup("g-alpha", "Alpha")
    private val pkg =
        ScriptPackageInstance(
            "pkg-monitor",
            emptyMap(),
            emptyMap(),
            ScriptPackage(
                source = File("."),
                manifest = Manifest(packageName = "com.example.monitor", name = "Monitor", versionCode = 1),
                mainScript =
                    MainScript(
                        HeadlessTasksTest.RunForeverScript::class,
                        ScriptConfigurationDefinition(ScriptConfiguration::class, emptyList()),
                    ),
                childScripts = emptyMap(),
            ),
            Clock.System.now(),
            numberOfConcurrentTasks = 1,
            notificationOverrides = ScriptNotificationOverrides(discordOverrides = DiscordOverrides(OVERRIDE_WEBHOOK)),
        )
    private val script = MainScriptInstance("script-monitor", pkg.definition.mainScript, emptyMap(), pkg.createdAt, pkg)
    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    private fun task(
        id: String,
        createdAt: Instant,
    ) = JobTask(id, script, emptyMap(), listOf(TaskStatus.Idle(timestamp = createdAt)), null, createdAt)

    private val HeadlessTestScope.sent get() = sentNotifications()

    private suspend fun HeadlessTestScope.awaitSent(count: Int) {
        val deadline = System.currentTimeMillis() + 2.seconds.inWholeMilliseconds
        while (sent.size < count) {
            check(System.currentTimeMillis() < deadline) { "sent ${sent.size} of $count: $sent" }
            delay(10)
        }
    }

    /** Discord on globally; the package overrides its webhook. Tasks t1, t2 are added once the tabs are up. */
    private fun waitingTest(block: suspend HeadlessTestScope.() -> Unit) =
        runHeadlessTest(
            seed = {
                get<NotificationSettingsRepository>().setDiscordWebhookEnabled(true)
                get<NotificationSettingsRepository>().setDiscordWebhookUrl(GLOBAL_WEBHOOK)
                get<TasksRepository>().createScriptInstanceGroup(group)
            },
        ) {
            awaitText("1-5 tabs")
            val linker = get<ScopeLinker>()
            linker.linkScriptInstanceToPackage(script)
            get<ScriptInstanceRepository>().addScriptPackageInstance(group.id, pkg, script)
            val tasks = listOf(task("t1", t0), task("t2", t0 + 1.seconds))
            tasks.forEach(linker::linkTaskToScriptInstance)
            get<TasksRepository>().addAllTasks(tasks)
            awaitText("Monitor")
            block()
        }

    private fun HeadlessTestScope.ask(
        taskId: String,
        request: suspend UserInteractionComponentImpl.() -> Unit,
    ) = CoroutineScope(Dispatchers.Default).async { UserInteractionComponentImpl(get(), taskId, get(), get()).request() }

    @Test
    fun `a new interaction sends one waiting notification through the overrides, recorded against the task`() =
        waitingTest {
            val answered = ask("t2") { requestInput("SMS code", "Enter the code we texted you") }
            awaitSent(1)
            // Unrelated updates of the waiting task don't send again.
            get<TasksRepository>().addStatusHistory("t2", TaskStatus.Running("Still waiting", Clock.System.now()))
            delay(200)
            assertEquals(1, sent.size, sent.toString())
            val discord = sent.single() as DiscordNotificationData
            assertEquals(OVERRIDE_WEBHOOK, discord.webhookUrl)
            assertEquals("**Task waiting for you**\nMonitor #2 on test-host is waiting for you (text input).", discord.content)
            val history = get<NotificationHistoryRepository>().observeByTaskId("t2").first()
            assertEquals(listOf("Task waiting for you"), history.map { it.title })

            // Answering and asking again is a new interaction: one more notification.
            get<AnswerUserInteractionInteractor>()(AnswerUserInteractionInteractor.Params.Text("t2", "1234"))
            answered.await()
            ask("t2") { showContinueButton() }
            awaitSent(2)
            assertEquals("**Task waiting for you**\nMonitor #2 on test-host is waiting for you (continue).", (sent[1] as DiscordNotificationData).content)
        }

    @Test
    fun `a browser prompt's notification leaves out the prompt title`() =
        waitingTest {
            get<TasksRepository>().setUserInteraction("t1", UserInteraction.Browser("Sign in with hunter2"))
            awaitSent(1)
            val content = (sent.single() as DiscordNotificationData).content!!
            assertEquals("**Task waiting for you**\nMonitor #1 on test-host is waiting for you (browser prompt).", content)
            assertFalse("hunter2" in content)
        }

    @Test
    fun `a session lost while signed in sends one global notification and records nothing`() =
        runHeadlessTest(
            seed = {
                get<NotificationSettingsRepository>().setDiscordWebhookEnabled(true)
                get<NotificationSettingsRepository>().setDiscordWebhookUrl(GLOBAL_WEBHOOK)
            },
        ) {
            awaitText("1-5 tabs")
            (get<SessionLostProvider>() as SessionLostProviderImpl).report()
            awaitText(HeadlessTui.SESSION_LOST)
            val discord = sent.single() as DiscordNotificationData
            assertEquals(GLOBAL_WEBHOOK, discord.webhookUrl)
            assertTrue(discord.content!!.startsWith("**Session lost**\nHeadless mode on test-host stopped all tasks"), discord.content)
            assertEquals(emptyList<Any>(), get<NotificationHistoryRepository>().observeRecent(10).first())
        }

    @Test
    fun `a session found dead while signed out sends nothing`() =
        runHeadlessTest(
            seed = {
                get<NotificationSettingsRepository>().setDiscordWebhookEnabled(true)
                get<NotificationSettingsRepository>().setDiscordWebhookUrl(GLOBAL_WEBHOOK)
                get<SessionRepository>().setSessionUser(null)
            },
        ) {
            awaitText("Sign in to Cereal")
            (get<SessionLostProvider>() as SessionLostProviderImpl).report()
            delay(200)
            assertEquals(emptyList<Any>(), sent)
        }

    private companion object {
        const val GLOBAL_WEBHOOK = "https://discord.com/api/webhooks/1/global"
        const val OVERRIDE_WEBHOOK = "https://discord.com/api/webhooks/2/override"
    }
}
