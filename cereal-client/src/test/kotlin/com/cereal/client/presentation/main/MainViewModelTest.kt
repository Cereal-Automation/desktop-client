package com.cereal.client.presentation.main

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.interactor.app.CheckForUpdatesInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.notification.ObserveUnseenNotificationCountInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.navigation.Root
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor = mockk(relaxed = true)
    private val applicationConfig: ApplicationConfig = mockk(relaxed = true)
    private val checkForUpdatesInteractor: CheckForUpdatesInteractor = mockk(relaxed = true)
    private val menuReselectionCoordinator = MenuReselectionCoordinator()
    private val getScriptsInteractor: GetScriptsInteractor = mockk(relaxed = true)
    private val observeTasksInteractor: ObserveTasksInteractor = mockk(relaxed = true)
    private val observeUnseenNotificationCountInteractor: ObserveUnseenNotificationCountInteractor = mockk(relaxed = true)

    private fun user() = User(id = "1", name = "Jane", email = "e", encryptionKey = "k", accessToken = "t")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { applicationConfig.versionName } returns "1.2.3"
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Success(user()))
        coEvery { getScriptsInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
        coEvery { observeTasksInteractor(any()) } returns flowOf(SuspendableResult.Success(emptyList()))
        coEvery { observeUnseenNotificationCountInteractor(any()) } returns flowOf(SuspendableResult.Success(0))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        MainViewModel(
            dispatcherProvider = dispatcherProvider,
            getAuthenticatedUserInteractor = getAuthenticatedUserInteractor,
            applicationConfig = applicationConfig,
            checkForUpdatesInteractor = checkForUpdatesInteractor,
            menuReselectionCoordinator = menuReselectionCoordinator,
            getScriptsInteractor = getScriptsInteractor,
            observeTasksInteractor = observeTasksInteractor,
            observeUnseenNotificationCountInteractor = observeUnseenNotificationCountInteractor,
        )

    @Test
    fun `init exposes the app version and a full menu`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1.2.3", viewModel.appVersion)
        assertEquals(7, viewModel.menuItems.value.size)
        assertTrue(viewModel.menuItems.value.any { it.route is Root.Routing.Marketplace })
    }

    @Test
    fun `unseen notifications produce a badge on the notification center menu item`() {
        coEvery { observeUnseenNotificationCountInteractor(any()) } returns flowOf(SuspendableResult.Success(3))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val notifications = viewModel.menuItems.value.first { it.route is Root.Routing.NotificationCenter }
        assertEquals(3, notifications.badge)
    }

    @Test
    fun `a white-label build hides the marketplace from the menu`() {
        every { applicationConfig.isBranded } returns true
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.menuItems.value.size)
        assertTrue(viewModel.menuItems.value.none { it.route is Root.Routing.Marketplace })
    }

    @Test
    fun `init applies the username as the profile title override`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val profile = viewModel.menuItems.value.first { it.route is Root.Routing.Profile }
        assertEquals("Jane", profile.titleOverride)
    }

    @Test
    fun `init reflects the active script count`() {
        coEvery { getScriptsInteractor(any()) } returns
            flowOf(SuspendableResult.Success(listOf(mockk<ScriptPackage>(), mockk<ScriptPackage>())))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.activeScriptCount.value)
    }

    @Test
    fun `running tasks produce a badge on the tasks menu item`() {
        val task = mockk<Task>(relaxed = true)
        every { task.status } returns TaskStatus.Running(timestamp = Clock.System.now())
        coEvery { observeTasksInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(task)))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val tasks = viewModel.menuItems.value.first { it.route is Root.Routing.Tasks }
        assertEquals(1, tasks.badge)
    }

    @Test
    fun `an available update sets the notification dot on settings`() {
        val version = Version(version = SemVer(2, 0, 0), minRequiredVersion = SemVer(1, 0, 0), downloadUrl = "https://x")
        coEvery { checkForUpdatesInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<UpdateCheckResult, Exception>) -> Unit>()(
                SuspendableResult.Success(UpdateCheckResult.UpdateAvailable(version)),
            )
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val settings = viewModel.menuItems.value.first { it.route is Root.Routing.ApplicationSettings }
        assertTrue(settings.hasNotificationDot)
    }

    @Test
    fun `onMenuItemClicked marks the chosen route as selected`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val marketplace = viewModel.menuItems.value.first { it.route is Root.Routing.Marketplace }

        viewModel.onMenuItemClicked(marketplace)

        assertTrue(
            viewModel.menuItems.value
                .first { it.route is Root.Routing.Marketplace }
                .selected,
        )
    }

    @Test
    fun `onMenuItemReselected notifies the coordinator`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val received = mutableListOf<Root.Routing>()
        val job = CoroutineScope(dispatcher).launch { menuReselectionCoordinator.events.collect { received.add(it) } }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onMenuItemReselected(Root.Routing.Marketplace)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(received.contains(Root.Routing.Marketplace))
        job.cancel()
    }
}
