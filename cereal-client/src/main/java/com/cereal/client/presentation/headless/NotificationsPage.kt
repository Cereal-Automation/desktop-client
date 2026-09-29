package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.notification.MarkNotificationsSeenInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationAttemptsInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationCenterInteractor
import com.cereal.client.application.interactor.notification.ObserveUnseenNotificationCountInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.notification.NotificationChannelType
import com.cereal.client.domain.model.notification.NotificationDeliveryStatus
import com.cereal.client.domain.model.notification.NotificationHistory
import com.cereal.client.domain.model.notification.NotificationHistoryAttempt
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.presentation.notification.formatRelativeTime
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tab `5`: the notification center. Newest-first rows with time, task, title, message and one mark
 * per channel attempt (`D✓ T✗ E-`); Enter expands a row to show its failed attempts' errors.
 *
 * Follows the desktop's rule (NotificationCenterViewModel): the label counts notifications newer than
 * `lastSeenAt`, and while this tab is active `lastSeenAt` is held at the newest notification.
 */
@OptIn(FlowPreview::class)
class NotificationsPage(
    private val scope: CoroutineScope,
    private val repaint: () -> Unit,
    getAuthenticatedUserInteractor: GetAuthenticatedUserInteractor,
    private val observeNotificationCenterInteractor: ObserveNotificationCenterInteractor,
    private val observeNotificationAttemptsInteractor: ObserveNotificationAttemptsInteractor,
    private val observeUnseenNotificationCountInteractor: ObserveUnseenNotificationCountInteractor,
    private val markNotificationsSeenInteractor: MarkNotificationsSeenInteractor,
    private val observeTasksInteractor: ObserveTasksInteractor,
) : TuiPage {
    private data class Row(
        val history: NotificationHistory,
        val task: String,
        val attempts: List<NotificationHistoryAttempt>,
    )

    @Volatile
    private var rows: List<Row> = emptyList()

    @Volatile
    private var unseen = 0

    @Volatile
    private var active = false

    @Volatile
    private var selectedId: String? = null

    @Volatile
    private var expandedId: String? = null

    override val title get() = if (unseen > 0) "$TITLE ($unseen)" else TITLE

    override val keys get() = if (rows.isEmpty()) "" else "↑↓ select · Enter errors"

    init {
        // The user-scoped stores throw before sign-in, so observe only while someone is signed in.
        scope.launch {
            getAuthenticatedUserInteractor(Interactor.None())
                .map { (it as? SuspendableResult.Success)?.value != null }
                .distinctUntilChanged()
                .collectLatest { signedIn ->
                    rows = emptyList()
                    unseen = 0
                    repaint()
                    if (signedIn) observe()
                }
        }
    }

    private suspend fun observe() =
        coroutineScope {
            launch {
                observeUnseenNotificationCountInteractor(Interactor.None()).collect { result ->
                    if (result is SuspendableResult.Success) {
                        unseen = result.value
                        repaint()
                    }
                }
            }
            // Attempts are recorded with their notification in one transaction and never change after.
            val attempts = mutableMapOf<String, List<NotificationHistoryAttempt>>()
            combine(
                observeNotificationCenterInteractor(Interactor.None()).map { it.valueOrEmpty() },
                observeTasksInteractor(Interactor.None()).map { it.valueOrEmpty() },
            ) { history, tasks -> history to tasks }
                .collectLatest { (history, tasks) ->
                    history.filter { attempts[it.id].isNullOrEmpty() }.forEach {
                        attempts[it.id] =
                            observeNotificationAttemptsInteractor(ObserveNotificationAttemptsInteractor.Params(it.id))
                                .first()
                                .valueOrEmpty()
                    }
                    rows = history.map { Row(it, taskLabel(tasks, it.taskId), attempts[it.id].orEmpty()) }
                    if (active) markNewestSeen()
                    repaint()
                }
        }

    override fun onActiveChanged(active: Boolean) {
        this.active = active
        if (active) markNewestSeen()
    }

    private fun markNewestSeen() {
        val newest = rows.maxOfOrNull { it.history.timestamp } ?: return
        scope.launch { markNotificationsSeenInteractor(MarkNotificationsSeenInteractor.Params(newest)) }
    }

    override fun body(
        width: Int,
        height: Int,
    ): List<String> {
        val rows = rows
        if (rows.isEmpty()) return listOf("", "  No notifications yet.")
        val selected = selectedIndex(rows)
        val now = System.currentTimeMillis()
        val lines = mutableListOf<String>()
        var selectedEnd = 0
        rows.forEachIndexed { i, row ->
            lines += rowLine(row, i == selected, width, now)
            if (row.history.id == expandedId) {
                val failed = row.attempts.filter { it.status == NotificationDeliveryStatus.FAILURE }
                lines +=
                    if (failed.isEmpty()) {
                        listOf("      All channels delivered.")
                    } else {
                        failed.map { "      ${it.channel.label()}: ${it.errorMessage ?: "failed"}" }
                    }
            }
            if (i == selected) selectedEnd = lines.size
        }
        // Scroll just far enough to keep the selected row (and its expansion) on screen.
        return lines.drop((selectedEnd - height).coerceAtLeast(0)).take(height)
    }

    override fun onKey(key: Key): Boolean {
        val rows = rows
        if (rows.isEmpty()) return false
        val selected = selectedIndex(rows)
        when (key) {
            Keys.Up -> selectedId = rows[(selected - 1).coerceAtLeast(0)].history.id
            Keys.Down -> selectedId = rows[(selected + 1).coerceAtMost(rows.lastIndex)].history.id
            Keys.Enter -> rows[selected].history.id.let { expandedId = if (expandedId == it) null else it }
            else -> return false
        }
        return true
    }

    private fun selectedIndex(rows: List<Row>) = rows.indexOfFirst { it.history.id == selectedId }.coerceAtLeast(0)

    private fun rowLine(
        row: Row,
        selected: Boolean,
        width: Int,
        now: Long,
    ): String {
        val head = "${if (selected) ">" else " "} ${formatRelativeTime(row.history.timestamp, now).padEnd(9)} ${row.task.fit(18)} "
        val marks = " " + marks(row.attempts)
        val text = listOfNotNull(row.history.title?.takeIf { it.isNotBlank() }, row.history.message).joinToString(": ")
        return head + text.fit((width - head.length - marks.length).coerceAtLeast(1)) + marks
    }

    private fun marks(attempts: List<NotificationHistoryAttempt>): String {
        val byChannel = attempts.associateBy { it.channel }
        val channels = MARKED_CHANNELS + listOfNotNull(NotificationChannelType.SYSTEM.takeIf { it in byChannel })
        return channels.joinToString(" ") { channel ->
            val mark =
                when (byChannel[channel]?.status) {
                    NotificationDeliveryStatus.SUCCESS -> "✓"
                    NotificationDeliveryStatus.FAILURE -> "✗"
                    null -> "-"
                }
            channel.label().first() + mark
        }
    }

    private companion object {
        const val TITLE = "Notifications"
        val MARKED_CHANNELS = listOf(NotificationChannelType.DISCORD, NotificationChannelType.TELEGRAM, NotificationChannelType.EMAIL)

        /** Script name and task number, numbered 1..n per script package instance by creation, as on the desktop. */
        fun taskLabel(
            tasks: List<Task>,
            taskId: String,
        ): String {
            val task = tasks.firstOrNull { it.id == taskId } ?: return ""
            val packageInstance = task.scriptInstance.getScriptPackageInstance()
            val number =
                tasks
                    .filter { it.scriptInstance.getScriptPackageInstance().id == packageInstance.id }
                    .sortedBy { it.createdAt }
                    .indexOfFirst { it.id == taskId } + 1
            return "${packageInstance.definition.manifest.name} #$number"
        }

        fun NotificationChannelType.label() = name.lowercase().replaceFirstChar { it.uppercase() }

        /** Pads or truncates to exactly [width] characters. */
        fun String.fit(width: Int) = HeadlessTui.truncate(this, width).padEnd(width)

        fun <T> SuspendableResult<List<T>, Exception>.valueOrEmpty(): List<T> = (this as? SuspendableResult.Success)?.value.orEmpty()
    }
}
