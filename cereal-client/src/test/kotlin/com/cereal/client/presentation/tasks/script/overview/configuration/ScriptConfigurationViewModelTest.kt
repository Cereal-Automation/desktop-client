package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.files.ReadCustomDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadListFileInteractor
import com.cereal.client.application.interactor.files.ReadProxyFileInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.tasks.script.overview.configuration.model.FileImportConfig
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ScriptConfigurationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val scriptPackage: ScriptPackage = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val getScriptConfigDefinitionInteractor: GetScriptConfigDefinitionInteractor = mockk(relaxed = true)
    private val getScriptPackageInstancesByPackageNameInteractor: GetScriptPackageInstancesByPackageNameInteractor = mockk(relaxed = true)
    private val readCustomDatasetFileInteractor: ReadCustomDatasetFileInteractor = mockk(relaxed = true)
    private val readProxyFileInteractor: ReadProxyFileInteractor = mockk(relaxed = true)
    private val readListFileInteractor: ReadListFileInteractor = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(GetScriptConfigDefinitionInteractor.Result(emptyList(), emptyMap())),
            )
        }
        coEvery { getScriptPackageInstancesByPackageNameInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<List<ScriptPackageInstance>, Exception>) -> Unit>()(
                SuspendableResult.Success(emptyList()),
            )
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(initialInstance: ScriptPackageInstance? = null) =
        ScriptConfigurationViewModel(
            scriptPackage = scriptPackage,
            initialScriptPackageInstance = initialInstance,
            scope = CoroutineScope(dispatcher),
            errorResolver = errorResolver,
            dispatcherProvider = dispatcherProvider,
            getScriptConfigDefinitionInteractor = getScriptConfigDefinitionInteractor,
            getScriptPackageInstancesByPackageNameInteractor = getScriptPackageInstancesByPackageNameInteractor,
            readCustomDatasetFileInteractor = readCustomDatasetFileInteractor,
            readProxyFileInteractor = readProxyFileInteractor,
            readListFileInteractor = readListFileInteractor,
        )

    @Test
    fun `init builds the configuration form`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.scriptConfigurationForm.value)
        assertFalse(viewModel.showConcurrentTasks.value)
    }

    @Test
    fun `init loads existing instances`() {
        val instance = mockk<ScriptPackageInstance>(relaxed = true)
        coEvery { getScriptPackageInstancesByPackageNameInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<List<ScriptPackageInstance>, Exception>) -> Unit>()(
                SuspendableResult.Success(listOf(instance)),
            )
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.existingInstances.value.size)
    }

    @Test
    fun `init reports an error when the definition fails to load`() {
        coEvery { getScriptConfigDefinitionInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Failure(RuntimeException("boom")),
            )
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.scriptConfigurationForm.value)
        verify { errorResolver.setError(any<Exception>()) }
    }

    @Test
    fun `validate passes for a default valid form`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.validate())
    }

    @Test
    fun `onCloseImportFromFileDialog clears the file import config`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onCloseImportFromFileDialog()

        assertNull(viewModel.fileImportConfig.value)
    }

    @Test
    fun `onDatasetFileSelected reads a proxy file for a proxy import`() {
        val proxyGroup = ProxyGroup("g1", "Group 1", 0, emptySequence())
        coEvery { readProxyFileInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ReadProxyFileInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(ReadProxyFileInteractor.Result(proxyGroup)),
            )
        }
        val state = DropDownFieldState<ProxyGroup>(emptyList())
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.fileImportConfig.value =
            FileImportConfig(
                datasetType = DatasetType.Proxy,
                configurationItem = mockk<ConfigurationItem>(relaxed = true),
                formFieldState = state,
            )

        viewModel.onDatasetFileSelected(java.io.File("proxies.txt"))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { readProxyFileInteractor(any(), any()) }
        assertEquals(proxyGroup, state.selectedValue)
        assertNull(viewModel.fileImportConfig.value)
    }

    @Test
    fun `onDatasetFileSelected reads a custom dataset file for a custom import`() {
        val customGroup =
            CustomDatasetGroup("c1", "Custom 1", 0, emptyList(), emptySequence(), Instant.fromEpochMilliseconds(0))
        coEvery { readCustomDatasetFileInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<ReadCustomDatasetFileInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(ReadCustomDatasetFileInteractor.Result(customGroup)),
            )
        }
        val state = DropDownFieldState<CustomDatasetGroup>(emptyList())
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.fileImportConfig.value =
            FileImportConfig(
                datasetType = DatasetType.Custom(emptyList()),
                configurationItem = mockk<ConfigurationItem>(relaxed = true),
                formFieldState = state,
            )

        viewModel.onDatasetFileSelected(java.io.File("data.csv"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(customGroup, state.selectedValue)
        assertNull(viewModel.fileImportConfig.value)
    }

    @Test
    fun `copyFromInstance applies values onto the loaded form`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val instance = mockk<ScriptPackageInstance>(relaxed = true)

        // Should not throw with an empty (default) loaded form.
        viewModel.copyFromInstance(instance)

        assertNotNull(viewModel.scriptConfigurationForm.value)
    }
}
