package com.cereal.client.presentation.headless

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.auth.UserAuthenticatingState
import com.cereal.client.application.interactor.app.CheckForUpdatesInteractor
import com.cereal.client.application.interactor.auth.AuthenticateInteractor
import com.cereal.client.application.interactor.auth.AuthenticateWithOAuthInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapInteractor.BootstrapSequenceIdentifier
import com.cereal.client.application.interactor.bootstrap.BootstrapState
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.application.interactor.task.StopAllRunningTasksInteractor
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.auth.PastedSignIn
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State holder for the TUI frame: tab bar, banner, body, two-line footer, and the quit flow.
 *
 * Kotter-free apart from the [Key] type, so the whole layout is a pure function of this state
 * ([frame]); [runTui] is the thin adapter that paints it and feeds keys in. Call [onChanged] after
 * any async state change so the adapter repaints.
 */
@OptIn(FlowPreview::class)
class HeadlessTui(
    private val scope: CoroutineScope,
    private val detachHint: String?,
    observeTasksInteractor: ObserveTasksInteractor,
    private val stopAllRunningTasksInteractor: StopAllRunningTasksInteractor,
    private val bootstrapInteractor: BootstrapInteractor,
    private val getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val authenticateInteractor: AuthenticateInteractor,
    config: ApplicationConfig,
    private val authenticateWithOAuthInteractor: AuthenticateWithOAuthInteractor,
    private val checkForUpdatesInteractor: CheckForUpdatesInteractor,
    /** The upgrade commands for this distribution, given the new version (see [UpdatePage.upgradeCommands]). */
    private val upgradeCommands: (version: String) -> List<String>,
    val tabs: List<TuiPage> = defaultTabs(),
) {
    private enum class QuitState { NONE, CONFIRMING, STOPPING }

    /** Set by the adapter; invoked whenever the frame needs repainting. */
    @Volatile
    var onChanged: () -> Unit = {}

    @Volatile
    private var selectedTab = 0

    @Volatile
    private var runningTasks = 0

    @Volatile
    private var quitState = QuitState.NONE

    private val quit = CompletableDeferred<Unit>()

    private val loginPage =
        LoginPage(
            config.marketplaceRegisterUrl,
            config.marketplaceForgotPasswordUrl,
            onSubmit = ::signIn,
            onSso = ::signInWith,
            onPaste = { pastedSignIn?.paste(it) },
            onCancelSso = { ssoJob?.cancel() },
        )

    @Volatile
    private var pastedSignIn: PastedSignIn? = null

    @Volatile
    private var ssoJob: Job? = null

    /** The pre-tab screen (boot status, login) in front of the tabs, or null once signed in. */
    @Volatile
    private var preTab: TuiPage? = StatusPage(STARTING)

    /** The newer version the banner advertises, or null when up to date (or blocked by a required update). */
    @Volatile
    private var availableUpdate: String? = null

    /** The `U` overlay with the upgrade commands, drawn over whatever screen is showing. */
    @Volatile
    private var upgradeOverlay: TuiPage? = null

    private var activePage: TuiPage? = null

    init {
        scope.launch {
            observeTasksInteractor(Interactor.None()).collect { result ->
                if (result is SuspendableResult.Success) {
                    runningTasks = result.value.count { it.status.isRunning() }
                    onChanged()
                }
            }
        }
        scope.launch { boot() }
    }

    /**
     * Checks for updates, then runs the bootstrap (no tray); a stored session lands on the tabs,
     * anything else on login. A required update stops here: the bootstrap never runs, so no session
     * is restored and no task starts, exactly as the desktop's interrupted bootstrap.
     */
    private suspend fun boot() {
        when (val update = checkForUpdates()) {
            is UpdateCheckResult.UpdateRequired -> {
                val version = update.version.version.toString()
                return show(UpdatePage.required(version, upgradeCommands(version)))
            }

            is UpdateCheckResult.UpdateAvailable -> {
                availableUpdate = update.version.version.toString()
            }

            else -> {}
        }
        var error: String? = null
        bootstrapInteractor(BootstrapInteractor.Params(startAt = BootstrapSequenceIdentifier.BootingUp, headless = true))
            .collect { result ->
                when (result) {
                    is SuspendableResult.Success -> show(StatusPage(result.value.state.statusLine()))
                    is SuspendableResult.Failure -> error = result.error.message
                }
            }
        val signedIn = (getAuthenticatedUserInteractor(Interactor.None()).first() as? SuspendableResult.Success)?.value != null
        if (error == null && signedIn) {
            show(null)
        } else {
            loginPage.showError(error)
            show(loginPage)
        }
    }

    /** The desktop's check, never followed by a download or install; a failed check counts as up to date. */
    private suspend fun checkForUpdates(): UpdateCheckResult? {
        var update: UpdateCheckResult? = null
        checkForUpdatesInteractor(Interactor.None()) { update = (it as? SuspendableResult.Success)?.value }
        return update
    }

    private fun signIn(
        email: String,
        password: String,
    ) {
        authenticate { authenticateInteractor(AuthenticateInteractor.Params(email, password)) }
    }

    /** Pasted sign-in: the link lands on the login page, and pastes are forwarded to the racing flow. */
    private fun signInWith(provider: OAuthProvider) {
        val pasted =
            PastedSignIn { url ->
                loginPage.showSignInUrl(url)
                onChanged()
            }
        pastedSignIn = pasted
        ssoJob = authenticate { authenticateWithOAuthInteractor(AuthenticateWithOAuthInteractor.Params(provider, pasted)) }
    }

    private fun authenticate(signIn: suspend () -> Flow<SuspendableResult<UserAuthenticatingState, Exception>>): Job =
        scope.launch {
            var error: String? = null
            signIn().collect { result ->
                when (result) {
                    is SuspendableResult.Success -> loginPage.showStatus(result.value.statusLine())
                    is SuspendableResult.Failure -> error = result.error.message
                }
                onChanged()
            }
            if (error == null) show(null) else loginPage.showError(error)
            onChanged()
        }

    private fun show(page: TuiPage?) {
        preTab = page
        updateActivePage()
        onChanged()
    }

    /** Tells pages when they come on or go off screen (a tab is on screen only while no pre-tab page is). */
    @Synchronized
    private fun updateActivePage() {
        val page = if (preTab == null) tabs[selectedTab] else null
        if (page === activePage) return
        activePage?.onActiveChanged(false)
        activePage = page
        page?.onActiveChanged(true)
    }

    fun frame(
        width: Int,
        height: Int,
    ): List<String> {
        val preTab = upgradeOverlay ?: preTab
        val page = preTab ?: tabs[selectedTab]
        val header = listOf(preTab?.title ?: tabBar(), banner())
        val footer =
            listOf(
                listOfNotNull(if (preTab == null) "1-${tabs.size} tabs" else null, page.keys)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                listOfNotNull("q quit", detachHint?.let { "detach: $it" }).joinToString(" · "),
            )
        val bodyHeight = (height - header.size - footer.size).coerceAtLeast(0)
        // A link line soft-wraps, so the blank rows it will cover are reserved right below it.
        val body = clip(page.body(width, bodyHeight).flatMap { listOf(it) + List(wrapRows(it, width)) { "" } }, bodyHeight)
        val padding = List(bodyHeight - body.size) { "" }
        return (header + body + padding + footer).take(height).map { if (linkUrl(it) != null) it else truncate(it, width) }
    }

    fun onKey(key: Key) {
        val char = (key as? CharKey)?.char
        when (quitState) {
            QuitState.STOPPING -> {
                return
            }

            QuitState.CONFIRMING -> {
                if (char == 'y' || char == 'Y') quit.complete(Unit) else quitState = QuitState.NONE
            }

            QuitState.NONE -> {
                if (upgradeOverlay != null && char != 'q') {
                    upgradeOverlay = null
                    return onChanged()
                }
                val preTab = preTab
                if ((preTab ?: tabs[selectedTab]).onKey(key)) return onChanged()
                when (char) {
                    in '1'..('0' + tabs.size) -> {
                        if (preTab == null) selectedTab = char!! - '1'
                        updateActivePage()
                    }

                    'U', 'u' -> {
                        availableUpdate?.let { upgradeOverlay = UpdatePage.commands(it, upgradeCommands(it)) }
                    }

                    'q' -> {
                        requestQuit()
                    }
                }
            }
        }
        onChanged()
    }

    /** Ctrl-C: same as `q`. */
    fun onInterrupt() {
        if (quitState == QuitState.NONE) requestQuit()
        onChanged()
    }

    suspend fun awaitQuit() = quit.await()

    /** Ends the session without asking (used by the harness and by process shutdown). */
    fun quitNow() {
        quit.complete(Unit)
    }

    /** Stops every running task through the normal stop path. Call once, after [awaitQuit]. */
    suspend fun shutdown() {
        quitState = QuitState.STOPPING
        onChanged()
        stopAllRunningTasksInteractor(Interactor.None())
    }

    private fun requestQuit() {
        if (runningTasks == 0) quit.complete(Unit) else quitState = QuitState.CONFIRMING
    }

    private fun tabBar(): String {
        val labels =
            tabs.mapIndexed { i, tab ->
                val label = "${i + 1} ${tab.title}"
                if (i == selectedTab) "[$label]" else label
            }
        val status = if (runningTasks > 0) " · $runningTasks running" else ""
        return labels.joinToString("  ") + status
    }

    /** The single banner line. Priority: the quit prompt, then an available update, then (#37) no channel. */
    private fun banner(): String =
        when (quitState) {
            QuitState.NONE -> {
                availableUpdate?.let { "Update $it available. Press U for the upgrade commands." } ?: ""
            }

            QuitState.CONFIRMING -> {
                "Quit and stop $runningTasks running task(s)? [y/N]" +
                    (detachHint?.let { " · detach instead: $it" } ?: "")
            }

            QuitState.STOPPING -> {
                "Stopping tasks…"
            }
        }

    companion object {
        private const val STARTING = "Starting Cereal…"

        private fun BootstrapState.statusLine(): String =
            when (this) {
                BootstrapState.MarketplaceUnreachable -> "Can't reach the marketplace, retrying…"
                BootstrapState.SynchronizeScripts -> "Synchronising scripts…"
                BootstrapState.RestoringTasks -> "Restoring tasks…"
                else -> STARTING
            }

        private fun UserAuthenticatingState.statusLine(): String =
            when (this) {
                UserAuthenticatingState.InitializingDiscord -> "Signing in…"
                is UserAuthenticatingState.SyncScripts -> "Synchronising scripts ($completed/$total)…"
                UserAuthenticatingState.RestoreTasks -> "Restoring tasks…"
            }

        fun defaultTabs(): List<TuiPage> = listOf("Tasks", "Waiting", "Proxies", "Settings", "Notifications").map { PlaceholderPage(it) }

        /** The multiplexer key that detaches without stopping anything, or null when there is none. */
        fun detachHintFor(environment: Map<String, String>): String? =
            when {
                environment.containsKey("TMUX") -> "tmux prefix + d"
                environment.containsKey("STY") -> "Ctrl-A d"
                environment["CEREAL_DISTRIBUTION"] == "docker" -> "Ctrl-P Ctrl-Q"
                else -> null
            }

        /** Truncates to [width] (never wraps), marking the cut with an ellipsis. */
        fun truncate(
            line: String,
            width: Int,
        ): String = if (line.length <= width) line else line.take((width - 1).coerceAtLeast(0)) + "…"

        /** The extra terminal rows a [linkLine] soft-wraps onto; 0 for ordinary lines. */
        private fun wrapRows(
            line: String,
            width: Int,
        ): Int = linkUrl(line)?.let { (it.length - 1).coerceAtLeast(0) / width.coerceAtLeast(1) } ?: 0

        /** Clips to [height] lines, keeping the head and the tail around a `…` marker. */
        fun clip(
            lines: List<String>,
            height: Int,
        ): List<String> {
            if (lines.size <= height) return lines
            if (height < 3) return lines.take(height)
            val head = (height - 1) / 2
            val tail = height - 1 - head
            return lines.take(head) + "…" + lines.takeLast(tail)
        }
    }
}
