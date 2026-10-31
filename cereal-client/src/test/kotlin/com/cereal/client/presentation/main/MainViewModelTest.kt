package com.cereal.client.presentation.main

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.app.CheckForUpdatesInteractor
import com.cereal.client.application.interactor.app.ObserveSidebarCollapsedInteractor
import com.cereal.client.application.interactor.app.SetSidebarCollapsedInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.notification.ObserveUnseenNotificationCountInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.app.Version
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.domain.model.user.User
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.navigation.Root
import com.github.kittinunf.result.coroutines.SuspendableResult
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
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
    private val tasksRepository = InMemoryTasksRepository()
    private val observeTasksInteractor = ObserveTasksInteractor(tasksRepository)
    private val observeUnseenNotificationCountInteractor: ObserveUnseenNotificationCountInteractor = mockk(relaxed = true)
    private val preferenceRepository = InMemoryApplicationPreferenceRepository()

    private fun user() = User(id = "1", name = "Jane", email = "e", encryptionKey = "k", accessToken = "t")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getAuthenticatedUserInteractor(any()) } returns flowOf(SuspendableResult.Success(user()))
        coEvery { observeUnseenNotificationCountInteractor(any()) } returns flowOf(SuspendableResult.Success(0))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(preferences: ApplicationPreferenceRepository = preferenceRepository) =
        MainViewModel(
            dispatcherProvider = dispatcherProvider,
            getAuthenticatedUserInteractor = getAuthenticatedUserInteractor,
            applicationConfig = applicationConfig,
            checkForUpdatesInteractor = checkForUpdatesInteractor,
            menuReselectionCoordinator = menuReselectionCoordinator,
            observeTasksInteractor = observeTasksInteractor,
            observeUnseenNotificationCountInteractor = observeUnseenNotificationCountInteractor,
            observeSidebarCollapsedInteractor = ObserveSidebarCollapsedInteractor(preferences),
            setSidebarCollapsedInteractor = SetSidebarCollapsedInteractor(preferences),
        )

    @Test
    fun `init exposes a full menu`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(9, viewModel.menuItems.value.size)
        assertTrue(viewModel.menuItems.value.any { it.route is Root.Routing.Marketplace })
    }

    @Test
    fun `Home is the first Agent item and selected on landing`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val first = viewModel.menuItems.value.first()
        assertEquals(Root.Routing.Home, first.route)
        assertEquals(MenuSection.WORKSPACE, first.section)
        assertTrue(first.selected)
    }

    @Test
    fun `unseen notifications produce a badge on the notification center menu item`() {
        coEvery { observeUnseenNotificationCountInteractor(any()) } returns flowOf(SuspendableResult.Success(3))
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val notifications = viewModel.menuItems.value.first { it.route is Root.Routing.NotificationCenter }
        assertEquals(3, notifications.badge)
        assertFalse(notifications.isBadgeAttention)
    }

    @Test
    fun `a white-label build keeps landing on Tasks and has no Home item`() {
        every { applicationConfig.isBranded } returns true
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Root.Routing.Tasks(), viewModel.landingRoute)
        assertTrue(viewModel.menuItems.value.none { it.route is Root.Routing.Home })
        assertTrue(
            viewModel.menuItems.value
                .first { it.route is Root.Routing.Tasks }
                .selected,
        )
    }

    @Test
    fun `a white-label build hides the ready-made scripts section`() {
        every { applicationConfig.isBranded } returns true
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, viewModel.menuItems.value.size)
        assertTrue(viewModel.menuItems.value.none { it.section == MenuSection.READY_MADE_SCRIPTS })
    }

    @Test
    fun `the sidebar starts expanded and toggling it saves the choice`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.isSidebarCollapsed.value)

        viewModel.onToggleSidebar()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isSidebarCollapsed.value)
        assertTrue(runBlocking { preferenceRepository.isSidebarCollapsed().first() })
    }

    @Test
    fun `a sidebar toggle that fails to save is undone`() {
        val failingPreferences =
            object : ApplicationPreferenceRepository by preferenceRepository {
                override suspend fun setSidebarCollapsed(collapsed: Boolean): Unit = throw CerealException("not saved")
            }
        val viewModel = createViewModel(failingPreferences)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleSidebar()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isSidebarCollapsed.value)
    }

    @Test
    fun `logging out resets the sidebar to expanded`() {
        val authenticatedUser = MutableStateFlow<SuspendableResult<User?, Exception>>(SuspendableResult.Success(user()))
        coEvery { getAuthenticatedUserInteractor(any()) } returns authenticatedUser
        runBlocking { preferenceRepository.setSidebarCollapsed(true) }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.isSidebarCollapsed.value)

        authenticatedUser.value = SuspendableResult.Success(null)
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isSidebarCollapsed.value)
    }

    @Test
    fun `a saved collapsed sidebar is restored`() {
        runBlocking { preferenceRepository.setSidebarCollapsed(true) }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isSidebarCollapsed.value)
    }

    @Test
    fun `init applies the username as the profile title override`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val profile = viewModel.menuItems.value.first { it.route is Root.Routing.Profile }
        assertEquals("Jane", profile.titleOverride)
    }

    private fun task(
        status: TaskStatus,
        userInteraction: UserInteraction? = null,
    ): JobTask =
        JobTask(
            id = UUID.randomUUID().toString(),
            scriptInstance = aScriptInstance(aScriptPackageInstance("setup", "com.example.script")),
            configuration = emptyMap(),
            statusHistory = listOf(status),
            userInteraction = userInteraction,
            createdAt = Clock.System.now(),
        )

    private fun tasksMenuItemFor(vararg tasks: JobTask): MenuItemUiModel {
        runBlocking { tasksRepository.addAllTasks(tasks.toList()) }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel.menuItems.value.first { it.route is Root.Routing.Tasks }
    }

    @Test
    fun `running tasks produce a neutral running count badge on the tasks menu item`() {
        val now = Clock.System.now()

        val tasks =
            tasksMenuItemFor(
                task(TaskStatus.Running(timestamp = now)),
                task(TaskStatus.Running(timestamp = now)),
                task(TaskStatus.Idle(timestamp = now)),
            )

        assertEquals(2, tasks.badge)
        assertFalse(tasks.isBadgeAttention)
    }

    @Test
    fun `idle tasks produce no badge on the tasks menu item`() {
        val tasks = tasksMenuItemFor(task(TaskStatus.Idle(timestamp = Clock.System.now())))

        assertNull(tasks.badge)
    }

    @Test
    fun `an errored task turns the tasks menu badge into an attention badge`() {
        val now = Clock.System.now()

        val tasks =
            tasksMenuItemFor(
                task(TaskStatus.Running(timestamp = now)),
                task(TaskStatus.Error("Failed", timestamp = now)),
            )

        assertEquals(1, tasks.badge)
        assertTrue(tasks.isBadgeAttention)
    }

    @Test
    fun `a task waiting for user input turns the tasks menu badge into an attention badge`() {
        val tasks =
            tasksMenuItemFor(
                task(TaskStatus.Running(timestamp = Clock.System.now()), UserInteraction.ContinueButton(mockk(relaxed = true))),
            )

        assertEquals(1, tasks.badge)
        assertTrue(tasks.isBadgeAttention)
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
