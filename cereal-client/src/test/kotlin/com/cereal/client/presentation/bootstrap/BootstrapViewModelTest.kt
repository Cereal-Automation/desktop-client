package com.cereal.client.presentation.bootstrap

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.app.DownloadLatestAppVersionInteractor
import com.cereal.client.application.interactor.app.InstallUpdateInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapProgress
import com.cereal.client.application.interactor.bootstrap.BootstrapState
import com.cereal.client.application.interactor.bootstrap.UserAction
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.domain.model.app.DownloadStatus
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BootstrapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val bootstrapInteractor: BootstrapInteractor = mockk(relaxed = true)
    private val downloadLatestAppVersionInteractor: DownloadLatestAppVersionInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)
    private val installUpdateInteractor: InstallUpdateInteractor = mockk(relaxed = true)
    private val openUrlInteractor: OpenUrlInteractor = mockk(relaxed = true)
    private val appUpdateProvider: AppUpdateProvider = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
        coEvery { bootstrapInteractor(any()) } returns flowOf()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        BootstrapViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = dispatcherProvider,
            bootstrapInteractor = bootstrapInteractor,
            downloadLatestAppVersionInteractor = downloadLatestAppVersionInteractor,
            errorResolver = errorResolver,
            installUpdateInteractor = installUpdateInteractor,
            openUrlInteractor = openUrlInteractor,
            appUpdateProvider = appUpdateProvider,
        )

    @Test
    fun `bootstrap updates progress and status from emitted progress`() {
        coEvery { bootstrapInteractor(any()) } returns
            flowOf(SuspendableResult.Success(BootstrapProgress(BootstrapState.BootingUp, 0.05f)))

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0.05f, viewModel.progress.value)
        assertEquals("Booting", viewModel.progressStatus.value)
        assertFalse(viewModel.windowClosed.value)
    }

    @Test
    fun `bootstrap marks window closed when finished`() {
        coEvery { bootstrapInteractor(any()) } returns
            flowOf(SuspendableResult.Success(BootstrapProgress(BootstrapState.Finished, 1.0f)))

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.windowClosed.value)
        assertFalse(viewModel.showLoadingIndicator.value)
    }

    @Test
    fun `bootstrap surfaces user action on interruption`() {
        coEvery { bootstrapInteractor(any()) } returns
            flowOf(
                SuspendableResult.Success(
                    BootstrapProgress(
                        BootstrapState.Interrupted(UserAction.AppUpdateAdvised, BootstrapInteractor.BootstrapSequenceIdentifier.BootingUp),
                        0.0f,
                    ),
                ),
            )

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UserAction.AppUpdateAdvised, viewModel.showUserAction.value)
    }

    @Test
    fun `onConfirmUserAction downloads and exposes the installer`() {
        val installer = File("installer.dmg")
        coEvery { downloadLatestAppVersionInteractor(any()) } returns
            flowOf(
                SuspendableResult.Success(DownloadStatus.Downloading(50)),
                SuspendableResult.Success(DownloadStatus.Finished(installer)),
            )

        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onConfirmUserAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.showUserAction.value)
        assertEquals(1f, viewModel.progress.value)
        assertEquals("Finished", viewModel.progressStatus.value)
        assertNotNull(viewModel.openInstaller.value)
    }

    @Test
    fun `installUpdate forwards the downloaded installer's expected sha to the interactor`() {
        val installer = File("installer.dmg")
        val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        coEvery { downloadLatestAppVersionInteractor(any()) } returns
            flowOf(SuspendableResult.Success(DownloadStatus.Finished(installer, sha256)))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onConfirmUserAction()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.installUpdate(installer)
        dispatcher.scheduler.advanceUntilIdle()

        // The digest from the release metadata must travel with the file so the installer is
        // re-verified right before launch.
        val params = slot<InstallUpdateInteractor.Params>()
        coVerify(exactly = 1) { installUpdateInteractor(capture(params), any()) }
        assertEquals(installer, params.captured.installer)
        assertEquals(sha256, params.captured.expectedSha256)
    }

    @Test
    fun `onDismissUserAction resumes bootstrap from the interrupted state`() {
        coEvery { bootstrapInteractor(any()) } returns
            flowOf(
                SuspendableResult.Success(
                    BootstrapProgress(
                        BootstrapState.Interrupted(UserAction.AppUpdateAdvised, BootstrapInteractor.BootstrapSequenceIdentifier.BootingUp),
                        0.0f,
                    ),
                ),
            )
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        // Now the next bootstrap run produces a normal progress emission.
        coEvery { bootstrapInteractor(any()) } returns
            flowOf(SuspendableResult.Success(BootstrapProgress(BootstrapState.Finishing, 0.9f)))

        viewModel.onDismissUserAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.showUserAction.value)
        assertEquals("Starting Cereal", viewModel.progressStatus.value)
    }

    @Test
    fun `installUpdate signals app exit when the installer launches`() {
        coEvery { installUpdateInteractor(any(), any()) } coAnswers {
            val onResult = secondArg<suspend (SuspendableResult<UpdateInstallResult, Exception>) -> Unit>()
            onResult(SuspendableResult.Success(UpdateInstallResult.Opened))
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.installUpdate(File("update.AppImage"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.shouldExitApp.value)
    }

    @Test
    fun `installUpdate surfaces the download location when the installer cannot be launched`() {
        val file = File("update.AppImage")
        coEvery { installUpdateInteractor(any(), any()) } coAnswers {
            val onResult = secondArg<suspend (SuspendableResult<UpdateInstallResult, Exception>) -> Unit>()
            onResult(SuspendableResult.Success(UpdateInstallResult.Revealed))
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.installUpdate(file)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.shouldExitApp.value)
        assertEquals(file, viewModel.installerLocation.value)
        assertNull(viewModel.openInstaller.value)
    }

    @Test
    fun `onDismissUserAction is a no-op without an interrupted state`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDismissUserAction()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.showUserAction.value)
    }
}
