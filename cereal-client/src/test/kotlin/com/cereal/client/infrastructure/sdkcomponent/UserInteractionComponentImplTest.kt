package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.sdkcomponent.UserInteractionComponentImpl
import com.cereal.sdk.component.userinteraction.UserInteractionComponent
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.resume
import kotlin.time.Instant

class UserInteractionComponentImplTest {
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
                UserInteractionComponentImpl(tasksRepository, task.id, CoroutinesDispatcherProvider())

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
                UserInteractionComponentImpl(tasksRepository, task.id, CoroutinesDispatcherProvider())

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
                UserInteractionComponentImpl(tasksRepository, task.id, CoroutinesDispatcherProvider())

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
                UserInteractionComponentImpl(tasksRepository, task.id, CoroutinesDispatcherProvider())

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
