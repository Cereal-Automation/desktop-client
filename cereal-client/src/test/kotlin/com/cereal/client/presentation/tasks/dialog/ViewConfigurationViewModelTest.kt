package com.cereal.client.presentation.tasks.dialog

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewConfigurationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val scriptPackageInstance: ScriptPackageInstance = mockk(relaxed = true)
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        ViewConfigurationViewModel(
            scope = CoroutineScope(dispatcher),
            scriptPackageInstance = scriptPackageInstance,
            dispatcherProvider = dispatcherProvider,
            getScriptConfigDefinitionInteractor = getScriptConfigDefinitionInteractor,
            errorResolver = errorResolver,
        )

    @Test
    fun `init flattens main and child configuration items into state`() {
        val main = listOf<ConfigurationItem>(mockk(), mockk())
        val children = mapOf("child" to listOf<ConfigurationItem>(mockk()))
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(GetScriptConfigDefinitionInteractor.Result(main, children)),
            )
        }

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.scriptConfigurationItems.value.size)
    }

    @Test
    fun `init leaves items empty and reports failure when interactor fails`() {
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Failure(Exception("boom")),
            )
        }

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.scriptConfigurationItems.value.isEmpty())
        verify { errorResolver.setError(any<Exception>()) }
    }
}
