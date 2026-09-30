package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.provider.BrowserPromptProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.provider.inmemory.InMemoryBrowserPromptProvider
import com.cereal.sdk.component.userinteraction.UserInteractionComponent
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.resume
import kotlin.time.Instant

class UserInteractionComponentImplTest {
    private val tasksRepository = InMemoryTasksRepository()

    private suspend fun component(
        browserPromptProvider: BrowserPromptProvider = InMemoryBrowserPromptProvider(),
        repository: InMemoryTasksRepository = tasksRepository,
    ): UserInteractionComponent {
        val scriptInstance = mockk<ScriptInstance>(relaxed = true)
        every { scriptInstance.id } returns "script-id"
        repository.addTask(
            JobTask(
                id = "foo",
                scriptInstance = scriptInstance,
                configuration = emptyMap(),
                createdAt = Instant.fromEpochMilliseconds(0),
            ),
        )
        return UserInteractionComponentImpl(repository, "foo", CoroutinesDispatcherProvider(), browserPromptProvider)
    }

    @Test
    fun `showUrl hands the prompt to the provider and returns its finishing request`() =
        runBlocking {
            val finish = WebResourceRequest("GET", emptyMap(), url = "https://google.com", null)
            val provider = InMemoryBrowserPromptProvider { finish }
            val headers = mapOf("User-Agent" to "HeaderAgent/9.9")

            val result = component(provider).showUrl("Foo", "https://cereal-automation.com", headers) { true }

            assertEquals(finish, result)
            val prompt = provider.prompts.single()
            assertEquals("Foo", prompt.title)
            assertEquals("https://cereal-automation.com", prompt.url)
            assertEquals(headers, prompt.headers)
            assertNull(prompt.html)
            assertNull(tasksRepository.getTask("foo")?.userInteraction, "prompt is no longer pending")
        }

    @Test
    fun `showHtml hands html to the provider without headers`() =
        runBlocking {
            val provider = InMemoryBrowserPromptProvider()

            component(provider).showHtml("Captcha", "<p>solve</p>") { true }

            val prompt = provider.prompts.single()
            assertEquals("<p>solve</p>", prompt.html)
            assertNull(prompt.url)
            assertNull(prompt.headers)
        }

    @Test
    fun `prompt is pending while the provider waits and cancelling the task cancels it`() =
        runBlocking {
            val cancelled = CompletableDeferred<Unit>()
            val provider =
                InMemoryBrowserPromptProvider {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            val component = component(provider)

            val task = async { component.showUrl("Login", "https://login.example") { true } }
            withTimeout(5000) {
                while (tasksRepository.getTask("foo")?.userInteraction == null) delay(10)
            }
            assertEquals(UserInteraction.Browser("Login"), tasksRepository.getTask("foo")?.userInteraction)

            task.cancel()
            withTimeout(5000) { cancelled.await() }
            task.join()
            assertNull(tasksRepository.getTask("foo")?.userInteraction)
        }

    @Test
    fun `test showContinueButton posts ContinueButton and resumes`() =
        runBlocking {
            val component = component()

            // Wait until the ContinueButton is posted, then resume it
            val resumer =
                launch {
                    repeat(200) {
                        val ui = tasksRepository.getTask("foo")?.userInteraction
                        if (ui is UserInteraction.ContinueButton) {
                            ui.continuation.resume(Unit)
                            return@launch
                        }
                        delay(10)
                    }
                }

            withTimeout(5000) {
                component.showContinueButton()
            }
            resumer.join()

            assertTrue(tasksRepository.getTask("foo")?.userInteraction is UserInteraction.ContinueButton)
        }

    @Test
    fun `test requestInput returns input string`() =
        runBlocking {
            val repository = InMemoryTasksRepository(textInputContinuation = mapOf("foo" to "User Input"))

            val result =
                component(repository = repository).requestInput(
                    title = "Input Title",
                    description = "Input Description",
                )

            assertEquals("User Input", result)
            val textInput = repository.getTask("foo")?.userInteraction as UserInteraction.TextInput
            assertEquals("Input Title", textInput.title)
            assertEquals("Input Description", textInput.description)
        }
}
