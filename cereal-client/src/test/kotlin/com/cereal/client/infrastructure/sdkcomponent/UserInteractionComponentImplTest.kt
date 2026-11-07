package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.notification.SendNotificationFromScriptInstanceInteractor
import com.cereal.client.domain.model.notification.DiscordNotificationData
import com.cereal.client.domain.model.notification.Notification
import com.cereal.client.domain.model.notification.NotificationResolver
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryRemoteApprovalProvider
import com.cereal.client.infrastructure.sdkcomponent.UserInteractionComponentImpl
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import com.cereal.sdk.component.userinteraction.UserInteractionComponent
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import fixtures.aTask
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class UserInteractionComponentImplTest {
    private val notificationHistory = InMemoryNotificationHistoryRepository()

    private fun component(
        tasksRepository: InMemoryTasksRepository,
        taskId: String,
        notificationProvider: NotificationProvider = InMemoryNotificationProvider(),
        settings: InMemoryNotificationSettingsRepository = InMemoryNotificationSettingsRepository(),
    ): UserInteractionComponent =
        UserInteractionComponentImpl(
            tasksRepository,
            taskId,
            CoroutinesDispatcherProvider(),
            SendNotificationFromScriptInstanceInteractor(
                notificationProvider,
                settings,
                notificationHistory,
                NotificationResolver(),
            ),
            scriptPackageInstance = null,
            // No remote approval here; TelegramRemoteApprovalTest covers it.
            InMemoryRemoteApprovalProvider(),
        )

    private suspend fun aStoredTask(tasksRepository: InMemoryTasksRepository): JobTask {
        val task = aTask(id = "confirm-task", packageName = "com.example.script", running = true)
        tasksRepository.addTask(task)
        return task
    }

    private suspend fun InMemoryTasksRepository.confirmation(taskId: String): UserInteraction.Confirmation =
        getAllTasks()
            .map { tasks -> tasks.firstOrNull { it.id == taskId }?.userInteraction }
            .filterIsInstance<UserInteraction.Confirmation>()
            .first()

    private suspend fun UserInteractionComponent.askToPlaceOrder(timeout: Duration = 15.minutes) =
        requestConfirmation(
            title = "AI Browser Agent",
            question = "Place order on amazon.com?",
            details = listOf("Clicks <b>Place your order</b>.", "The cart total is under the max."),
            url = "https://www.amazon.com/checkout",
            confirmLabel = "Yes, place order",
            declineLabel = "No, stop here",
            timeout = timeout,
        )

    @Test
    fun `requestConfirmation shows the confirmation and returns the answer given`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)
            val component = component(tasksRepository, task.id)

            val result = async { component.askToPlaceOrder() }
            val shown = tasksRepository.confirmation(task.id)
            shown.answer.complete(ConfirmationResult.Confirmed)

            assertEquals(ConfirmationResult.Confirmed, result.await())
            assertEquals("Place order on amazon.com?", shown.question)
            assertEquals(listOf("Clicks <b>Place your order</b>.", "The cart total is under the max."), shown.details)
            assertEquals("https://www.amazon.com/checkout", shown.url)
            assertEquals("Yes, place order", shown.confirmLabel)
            assertEquals("No, stop here", shown.declineLabel)
            assertNull(tasksRepository.getTask(task.id)?.userInteraction)
        }

    @Test
    fun `requestConfirmation expires when nobody answers in time and clears the row`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)

            val result = component(tasksRepository, task.id).askToPlaceOrder(timeout = 15.minutes)

            assertEquals(ConfirmationResult.Expired, result)
            assertNull(tasksRepository.getTask(task.id)?.userInteraction)
        }

    @Test
    fun `an infinite confirmation has no expiry and keeps waiting`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)

            val result = async { component(tasksRepository, task.id).askToPlaceOrder(timeout = Duration.INFINITE) }
            val shown = tasksRepository.confirmation(task.id)
            delay(365.days)

            assertNull(shown.expiresAt)
            assertTrue(result.isActive)
            shown.answer.complete(ConfirmationResult.Declined)
            assertEquals(ConfirmationResult.Declined, result.await())
        }

    @Test
    fun `stopping the task tears the confirmation down`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)

            val result = async { component(tasksRepository, task.id).askToPlaceOrder() }
            val shown = tasksRepository.confirmation(task.id)
            result.cancelAndJoin()

            assertNull(tasksRepository.getTask(task.id)?.userInteraction)
            // A late answer from the window can't win anymore.
            assertFalse(shown.answer.complete(ConfirmationResult.Confirmed))
        }

    @Test
    fun `a waiting confirmation sends a plain heads-up with the question and page link`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)

            component(tasksRepository, task.id).askToPlaceOrder()

            val headsUp = notificationHistory.observeByTaskId(task.id).first { it.isNotEmpty() }.single()
            assertEquals("Place order on amazon.com?", headsUp.title)
            assertTrue(headsUp.message.contains("https://www.amazon.com/checkout"))
        }

    @Test
    fun `the answer returns at once while the heads-up is still sending`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)
            val stuck =
                object : NotificationProvider {
                    override suspend fun sendNotification(notification: Notification) = awaitCancellation()
                }

            val result = async { component(tasksRepository, task.id, notificationProvider = stuck).askToPlaceOrder() }
            tasksRepository.confirmation(task.id).answer.complete(ConfirmationResult.Confirmed)

            assertEquals(ConfirmationResult.Confirmed, withTimeout(5.seconds) { result.await() })
        }

    @Test
    fun `the Discord heads-up shows the question as plain text and pings nobody`() =
        runTest {
            val tasksRepository = InMemoryTasksRepository()
            val task = aStoredTask(tasksRepository)
            val settings =
                InMemoryNotificationSettingsRepository().apply {
                    setDiscordWebhookEnabled(true)
                    setDiscordWebhookUrl("https://discord.com/api/webhooks/1/abc")
                }
            val sent = InMemoryNotificationProvider()

            val result =
                async {
                    component(tasksRepository, task.id, sent, settings).requestConfirmation(
                        title = "AI Browser Agent",
                        question = "Buy **now** @everyone [here](https://evil.example)?",
                        details = emptyList(),
                        url = "https://www.amazon.com/checkout",
                        confirmLabel = "Yes",
                        declineLabel = "No",
                        timeout = 15.minutes,
                    )
                }
            tasksRepository.confirmation(task.id)
            notificationHistory.observeByTaskId(task.id).first { it.isNotEmpty() }
            result.cancelAndJoin()

            val content =
                sent.sent
                    .filterIsInstance<DiscordNotificationData>()
                    .single()
                    .content!!
            assertEquals(
                "**Buy \\*\\*now\\*\\* @\u200Beveryone \\[here\\]\\(https://evil.example\\)?**\n" +
                    "<https://www.amazon.com/checkout>\nWaiting for your answer in Cereal.",
                content,
            )
        }

    @Test
    fun `test showUrl with valid URL and successful termination condition`() =
        runBlocking {
            // Arrange
            val tasksRepository =
                InMemoryTasksRepository(
                    userInteractionContinuation =
                        mapOf(
                            "foo" to WebResourceRequest("GET", emptyMap(), url = "https://google.com", null),
                        ),
                )

            val task = mockk<JobTask>(relaxed = true)
            every { task.id } returns "foo"
            tasksRepository.addTask(task)

            val component: UserInteractionComponent =
                component(tasksRepository, task.id)

            // Act
            val result =
                component.showUrl("Foo", "https://cereal-automation.com") {
                    true
                }

            // Assert
            assertNotNull(result)
            assertEquals(result.url, "https://google.com")
        }

    @Test
    fun `test showUrl with custom headers map`() =
        runBlocking {
            val tasksRepository = InMemoryTasksRepository()
            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "script-hdr"
            every { scriptInstance.createdAt } returns Instant.fromEpochMilliseconds(0)
            every { scriptInstance.configuration } returns emptyMap()
            every { scriptInstance.definition } returns mockk(relaxed = true)
            every { scriptInstance.packageInstance } returns mockk(relaxed = true)

            val task =
                JobTask(
                    id = "hdr-task",
                    scriptInstance = scriptInstance,
                    configuration = emptyMap(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            tasksRepository.addTask(task)
            val component: UserInteractionComponent =
                component(tasksRepository, task.id)

            val headers =
                mapOf(
                    "User-Agent" to "HeaderAgent/9.9",
                    "Accept-Language" to "en-US,en;q=0.9",
                )

            val resumeJob =
                launch {
                    repeat(200) {
                        val stored = tasksRepository.getTask("hdr-task")
                        val ui = stored?.userInteraction
                        if (ui is UserInteraction.Browser) {
                            assertEquals("HeaderAgent/9.9", ui.headers?.get("User-Agent"))
                            assertEquals("en-US,en;q=0.9", ui.headers?.get("Accept-Language"))
                            if (ui.shouldFinish(WebResourceRequest("GET", emptyMap(), "https://finish.com", null))) {
                                ui.continuation.resume(
                                    WebResourceRequest(
                                        "GET",
                                        emptyMap(),
                                        "https://finish.com",
                                        null,
                                    ),
                                )
                            }
                            return@launch
                        }
                        delay(10)
                    }
                    error("Browser interaction not set in time")
                }

            component.showUrl("Title", "https://initial.com", headers) { true }
            resumeJob.join()
        }

    @Test
    fun `test showContinueButton posts ContinueButton and resumes`() =
        runBlocking {
            // Arrange
            val tasksRepository = InMemoryTasksRepository()

            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "script-1"
            every { scriptInstance.createdAt } returns Instant.fromEpochMilliseconds(0)
            every { scriptInstance.configuration } returns emptyMap()
            every { scriptInstance.definition } returns mockk(relaxed = true)
            every { scriptInstance.packageInstance } returns mockk(relaxed = true)

            val task =
                JobTask(
                    id = "foo",
                    scriptInstance = scriptInstance,
                    configuration = emptyMap(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            tasksRepository.addTask(task)

            val component: UserInteractionComponent =
                component(tasksRepository, task.id)

            // Start a coroutine to wait until the ContinueButton is posted, then resume it
            val resumer =
                launch {
                    // Poll until the userInteraction is set to ContinueButton
                    repeat(200) {
                        val stored = tasksRepository.getTask("foo")
                        val ui = stored?.userInteraction
                        if (ui is UserInteraction.ContinueButton) {
                            ui.continuation.resume(Unit)
                            return@launch
                        }
                        delay(10)
                    }
                }

            // Act: should suspend until our resumer resumes the continuation
            withTimeout(5000) {
                component.showContinueButton()
            }

            // Ensure our resumer finished
            resumer.join()

            // Assert: the user interaction is a ContinueButton (it was posted)
            val stored = tasksRepository.getTask("foo")
            assertTrue(stored?.userInteraction is UserInteraction.ContinueButton)
        }

    @Test
    fun `test requestInput returns input string`() =
        runBlocking {
            // Arrange
            val tasksRepository =
                InMemoryTasksRepository(
                    textInputContinuation = mapOf("foo" to "User Input"),
                )

            val scriptInstance = mockk<ScriptInstance>(relaxed = true)
            every { scriptInstance.id } returns "script-id"

            val task =
                JobTask(
                    id = "foo",
                    scriptInstance = scriptInstance,
                    configuration = emptyMap(),
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            tasksRepository.addTask(task)

            val component: UserInteractionComponent =
                component(tasksRepository, task.id)

            // Act
            val result =
                component.requestInput(
                    title = "Input Title",
                    description = "Input Description",
                )

            // Assert
            assertEquals("User Input", result)
            val stored = tasksRepository.getTask("foo")
            assertTrue(stored?.userInteraction is UserInteraction.TextInput)
            val textInput = stored?.userInteraction as UserInteraction.TextInput
            assertEquals("Input Title", textInput.title)
            assertEquals("Input Description", textInput.description)
        }
}
