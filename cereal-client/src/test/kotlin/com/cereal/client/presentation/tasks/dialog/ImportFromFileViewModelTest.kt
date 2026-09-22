package com.cereal.client.presentation.tasks.dialog

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.datasets.InvalidFileException
import com.cereal.client.application.interactor.files.DownloadDatasetTemplateFileInteractor
import com.cereal.client.application.interactor.files.OpenDatasetFileInteractor
import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ImportFromFileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val openDatasetFileInteractor: OpenDatasetFileInteractor = mockk(relaxed = true)
    private val downloadDatasetFileInteractor: DownloadDatasetTemplateFileInteractor = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(datasetType: DatasetType) =
        ImportFromFileViewModel(
            scope = CoroutineScope(dispatcher),
            datasetType = datasetType,
            dispatcherProvider = dispatcherProvider,
            errorResolver = errorResolver,
            openDatasetFileInteractor = openDatasetFileInteractor,
            downloadDatasetFileInteractor = downloadDatasetFileInteractor,
        )

    @Test
    fun `init sets CSV file type for custom datasets`() {
        val viewModel = createViewModel(DatasetType.Custom(emptyList()))

        assertEquals("CSV", viewModel.fileTypeName.value)
    }

    @Test
    fun `init sets CSV file type for lists`() {
        val viewModel = createViewModel(DatasetType.ConfigList(emptyList()))

        assertEquals("CSV", viewModel.fileTypeName.value)
    }

    @Test
    fun `init sets proxy list file type for proxy datasets`() {
        val viewModel = createViewModel(DatasetType.Proxy)

        assertEquals("proxy list", viewModel.fileTypeName.value)
    }

    @Test
    fun `onOpenFile stores the selected file on success`() {
        val file = File("data.csv")
        coEvery { openDatasetFileInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<OpenDatasetFileInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(OpenDatasetFileInteractor.Result(mockk())),
            )
        }
        val viewModel = createViewModel(DatasetType.Proxy)

        viewModel.onOpenFile(file)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.selectedFile.value)
        assertEquals(file, viewModel.selectedFile.value?.file)
        assertNull(viewModel.fileErrorMessage.value)
    }

    @Test
    fun `onOpenFile surfaces invalid file message on InvalidFileException`() {
        coEvery { openDatasetFileInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<OpenDatasetFileInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Failure(InvalidFileException("missing column")),
            )
        }
        val viewModel = createViewModel(DatasetType.Proxy)

        viewModel.onOpenFile(File("bad.csv"))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.selectedFile.value)
        assertTrue(viewModel.fileErrorMessage.value!!.contains("missing column"))
    }

    @Test
    fun `onOpenFile surfaces the error message on other errors`() {
        coEvery { openDatasetFileInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<OpenDatasetFileInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Failure(RuntimeException("Something went wrong. Please try again.")),
            )
        }
        val viewModel = createViewModel(DatasetType.Proxy)

        viewModel.onOpenFile(File("bad.csv"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Something went wrong. Please try again.", viewModel.fileErrorMessage.value)
    }

    @Test
    fun `onOpenFile surfaces a friendly fallback when the error has no message`() {
        coEvery { openDatasetFileInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<OpenDatasetFileInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Failure(RuntimeException()),
            )
        }
        val viewModel = createViewModel(DatasetType.Proxy)

        viewModel.onOpenFile(File("bad.csv"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("We couldn't read that file. Please check it and try again.", viewModel.fileErrorMessage.value)
    }

    @Test
    fun `onDownloadTemplate invokes the download interactor`() {
        val file = File("template.csv")
        val viewModel = createViewModel(DatasetType.Proxy)

        viewModel.onDownloadTemplate(file)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { downloadDatasetFileInteractor(any(), any()) }
    }
}
