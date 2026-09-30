package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.script.GetScriptsInGroupInteractor
import com.cereal.client.application.interactor.script.ScriptInstanceInGroup
import com.cereal.client.application.interactor.task.ChangeScriptPackageInstanceGroupInteractor
import com.cereal.client.application.interactor.task.CreateScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.DeleteScriptInstanceInteractor
import com.cereal.client.application.interactor.task.DeleteTaskGroupInteractor
import com.cereal.client.application.interactor.task.EditScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.GetTaskGroupsInteractor
import com.cereal.client.application.interactor.task.ObserveTaskLogInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.StartTaskInteractor
import com.cereal.client.application.interactor.task.StopTaskInteractor
import com.cereal.client.application.interactor.task.StopTasksInScriptPackageInstanceInteractor
import com.cereal.client.domain.model.logging.LoggingEvent
import com.cereal.client.domain.model.logging.LoggingPriority
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.task.UserInteraction
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

/**
 * Tab `1`: the desktop's group > script > task tree on a [RowList], with start/stop, task detail
 * (logs merged with status history), and an action menu for groups and scripts.
 *
 * [changed] asks the frame to repaint after an async update.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TasksPage(
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
    private val getTaskGroups: GetTaskGroupsInteractor,
    private val getOrCreateDefaultTaskGroup: GetOrCreateDefaultTaskGroupInteractor,
    private val getScriptsInGroup: GetScriptsInGroupInteractor,
    private val observeTasks: ObserveTasksInteractor,
    private val observeTaskLog: ObserveTaskLogInteractor,
    private val startTask: StartTaskInteractor,
    private val stopTask: StopTaskInteractor,
    private val startScript: StartAllTasksInScriptPackageInstanceInteractor,
    private val stopScript: StopTasksInScriptPackageInstanceInteractor,
    private val createGroup: CreateScriptInstanceGroupInteractor,
    private val renameGroup: EditScriptInstanceGroupInteractor,
    private val deleteGroup: DeleteTaskGroupInteractor,
    private val moveScript: ChangeScriptPackageInstanceGroupInteractor,
    private val deleteScript: DeleteScriptInstanceInteractor,
) : TuiPage {
    private data class GroupNode(
        val group: ScriptPackageGroup,
        val scripts: List<ScriptInstanceInGroup>,
    )

    /** A tree row: exactly one of the three levels. */
    private sealed interface Node {
        data class Group(
            val group: ScriptPackageGroup,
        ) : Node

        data class Script(
            val pkg: ScriptPackageInstance,
        ) : Node

        data class TaskRow(
            val task: Task,
            val number: Int,
        ) : Node
    }

    private enum class LogFilter(
        val lowest: LoggingPriority,
    ) {
        ALL(LoggingPriority.DEBUG),
        INFO(LoggingPriority.INFO),
        WARN(LoggingPriority.WARNING),
        ERR(LoggingPriority.ERROR),
    }

    private class Detail(
        val taskId: String,
    ) {
        @Volatile var filter = LogFilter.ALL

        @Volatile var stackExpanded = false

        @Volatile var log: List<LoggingEvent> = emptyList()
    }

    /** Modal state drawn over the tree or the detail. */
    private sealed interface Overlay {
        class Choice(
            val title: String,
            val list: RowList<() -> Unit>,
        ) : Overlay

        class Input(
            val prompt: String,
            var text: String,
            val submit: (String) -> Unit,
        ) : Overlay

        class Confirm(
            val question: String,
            val yes: () -> Unit,
        ) : Overlay
    }

    override val title = "Tasks"

    private val list = RowList<Node>()

    @Volatile private var groups: List<GroupNode> = emptyList()

    @Volatile private var tasks: List<Task> = emptyList()

    @Volatile private var detail: Detail? = null

    @Volatile private var overlay: Overlay? = null

    @Volatile private var notice: String? = null

    private var observation: Job? = null
    private var logObservation: Job? = null

    override val keys: String
        get() =
            when (overlay) {
                is Overlay.Choice -> "↑↓ move · Enter choose · Esc cancel"
                is Overlay.Input -> "Enter save · Esc cancel"
                is Overlay.Confirm -> "y confirm · any other key cancels"
                null -> if (detail != null) DETAIL_KEYS else TREE_KEYS
            }

    /** (Re)starts observing the signed-in user's groups and tasks. */
    override fun onSignedIn() {
        observation?.cancel()
        observation =
            scope.launch {
                getOrCreateDefaultTaskGroup(Interactor.None())
                val tree =
                    getTaskGroups(Interactor.None()).map { it.get() }.flatMapLatest { groups ->
                        if (groups.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            combine(groups.map { g -> getScriptsInGroup(GetScriptsInGroupInteractor.Params(g.id)).map { GroupNode(g, it.get()) } }) {
                                it.toList()
                            }
                        }
                    }
                try {
                    tree.combine(observeTasks(Interactor.None()).map { it.get() }) { g, t -> g to t }.collect { (g, t) ->
                        groups = g
                        tasks = t
                        changed()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    notice = e.message
                    changed()
                }
            }
    }

    override fun body(
        width: Int,
        height: Int,
    ): List<String> {
        val overlay = overlay
        if (overlay is Overlay.Choice) return listOf("  ${overlay.title}") + overlay.list.render(width, height - 1)
        val bottom =
            when (overlay) {
                is Overlay.Input -> "  ${overlay.prompt}: ${overlay.text}_"
                is Overlay.Confirm -> "  ${overlay.question} [y/N]"
                else -> notice?.let { "  ! $it" }
            }
        val mainHeight = if (bottom == null) height else height - 1
        val main = detail?.let { detailBody(it, width, mainHeight) } ?: treeBody(width, mainHeight)
        return main + List(mainHeight - main.size) { "" } + listOfNotNull(bottom)
    }

    override fun onKey(key: Key): Boolean {
        overlay?.let { return onOverlayKey(it, key) }
        notice = null
        val detail = detail
        val char = (key as? CharKey)?.char
        if (detail != null) {
            val task = tasks.find { it.id == detail.taskId }
            when {
                key == Keys.Escape -> closeDetail()
                char == 'f' -> detail.filter = LogFilter.entries[(detail.filter.ordinal + 1) % LogFilter.entries.size]
                char == 't' -> detail.stackExpanded = !detail.stackExpanded
                char == 's' && task != null -> start(task)
                char == 'x' && task != null -> run(stopTask, StopTaskInteractor.Params(task.id))
                else -> return false
            }
            return true
        }
        if (list.onKey(key)) return true
        val node = list.selected
        val pkg = node?.pkg()
        when {
            key == Keys.Enter && node is Node.TaskRow -> openDetail(node.task.id)
            char == 's' && node is Node.TaskRow -> start(node.task)
            char == 'x' && node is Node.TaskRow -> run(stopTask, StopTaskInteractor.Params(node.task.id))
            char == 'S' && pkg != null -> run(startScript, StartAllTasksInScriptPackageInstanceInteractor.Params(pkg))
            char == 'X' && pkg != null -> run(stopScript, StopTasksInScriptPackageInstanceInteractor.Params(pkg))
            char == 'm' -> this.overlay = actionMenu(node)
            else -> return false
        }
        return true
    }

    private fun onOverlayKey(
        overlay: Overlay,
        key: Key,
    ): Boolean {
        when (overlay) {
            is Overlay.Choice -> {
                when {
                    overlay.list.onKey(key) -> {
                        Unit
                    }

                    key == Keys.Enter -> {
                        this.overlay = null
                        overlay.list.selected?.invoke()
                    }

                    key == Keys.Escape -> {
                        this.overlay = null
                    }
                }
            }

            is Overlay.Input -> {
                when (key) {
                    Keys.Escape -> {
                        this.overlay = null
                    }

                    Keys.Backspace -> {
                        overlay.text = overlay.text.dropLast(1)
                    }

                    Keys.Enter -> {
                        this.overlay = null
                        overlay.submit(overlay.text.trim())
                    }

                    is CharKey -> {
                        overlay.text += key.char
                    }
                }
            }

            is Overlay.Confirm -> {
                this.overlay = null
                val char = (key as? CharKey)?.char
                if (char == 'y' || char == 'Y') overlay.yes()
            }
        }
        return true
    }

    /** Restarting a finished (Success) task asks first, as on the desktop. */
    private fun start(task: Task) {
        if (task.status.isRunning()) return
        val go = { run(startTask, StartTaskInteractor.Params(task.id)) }
        if (task.status is TaskStatus.Success) {
            overlay = Overlay.Confirm("Restart finished task #${numberOf(task)}?", go)
        } else {
            go()
        }
    }

    private fun actionMenu(node: Node?): Overlay.Choice {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += "Add group" to { overlay = Overlay.Input("New group name", "") { run(createGroup, CreateScriptInstanceGroupInteractor.Params(it)) } }
        if (node is Node.Group) {
            val group = node.group
            actions += "Rename group" to {
                overlay = Overlay.Input("Rename group", group.name) { run(renameGroup, EditScriptInstanceGroupInteractor.Params(group.id, it)) }
            }
            actions += "Delete group" to {
                overlay = Overlay.Confirm("Delete group ${group.name} and all its scripts?") { run(deleteGroup, DeleteTaskGroupInteractor.Params(group)) }
            }
        }
        node?.pkg()?.let { pkg ->
            actions += "Move to group" to { overlay = moveMenu(pkg) }
            actions += "Delete script" to {
                overlay = Overlay.Confirm("Delete ${pkg.label()} and its tasks?") { run(deleteScript, DeleteScriptInstanceInteractor.Params(pkg)) }
            }
        }
        return Overlay.Choice("Actions", RowList<() -> Unit>().apply { rows = actions.map { (label, action) -> RowList.Row(label, label, action) } })
    }

    private fun moveMenu(pkg: ScriptPackageInstance): Overlay.Choice {
        val current = groups.firstOrNull { g -> g.scripts.any { it.scriptPackageInstance.id == pkg.id } }?.group
        val targets = groups.map { it.group }.filter { it != current }
        val rows =
            targets.map { group ->
                RowList.Row<() -> Unit>(group.id, group.name, { run(moveScript, ChangeScriptPackageInstanceGroupInteractor.Params(pkg, group.id)) })
            }
        return Overlay.Choice("Move ${pkg.label()} to", RowList<() -> Unit>().apply { this.rows = rows })
    }

    private fun openDetail(taskId: String) {
        val detail = Detail(taskId)
        this.detail = detail
        logObservation?.cancel()
        logObservation =
            scope.launch {
                observeTaskLog(ObserveTaskLogInteractor.Params(taskId)).collect { result ->
                    when (result) {
                        is SuspendableResult.Success -> detail.log = result.value
                        is SuspendableResult.Failure -> notice = result.error.message
                    }
                    changed()
                }
            }
    }

    private fun closeDetail() {
        logObservation?.cancel()
        detail = null
    }

    /** Runs [interactor] in the background; a failure shows as the notice line. */
    private fun <T : Any, P> run(
        interactor: Interactor<T, P>,
        params: P,
    ) {
        scope.launch {
            interactor(params) { result ->
                if (result is SuspendableResult.Failure) notice = result.error.message
                changed()
            }
        }
    }

    private fun treeBody(
        width: Int,
        height: Int,
    ): List<String> {
        list.rows = treeRows(width - 2)
        if (list.rows.isEmpty()) return listOf("", "  No groups yet. Press m to add one.")
        return list.render(width, height)
    }

    private fun treeRows(width: Int): List<RowList.Row<Node>> {
        val tasksByPkg = tasks.groupBy { it.scriptInstance.getScriptPackageInstance().id }
        return groups.flatMap { (group, scripts) ->
            val header = "${group.name}  ${scripts.sumOf { it.runningTaskCount }}/${scripts.sumOf { it.taskCount }} running"
            listOf(RowList.Row<Node>("g:${group.id}", header, Node.Group(group))) +
                scripts.flatMap { script ->
                    val pkg = script.scriptPackageInstance
                    val scriptRow = RowList.Row<Node>("s:${pkg.id}", "  ${pkg.label()}  ${script.runningTaskCount}/${script.taskCount} running", Node.Script(pkg))
                    listOf(scriptRow) +
                        numbered(tasksByPkg[pkg.id].orEmpty()).map { (task, number) ->
                            RowList.Row("t:${task.id}", taskLine(task, number, width), Node.TaskRow(task, number))
                        }
                }
        }
    }

    /** `    ● #3  Running  message…  [! INPUT]`, the message cut so the flag stays visible. */
    private fun taskLine(
        task: Task,
        number: Int,
        width: Int,
    ): String {
        val status = task.status
        val prefix = "    ${status.glyph()} #$number  ${status.name()}  "
        val flag = task.userInteraction?.let { "  [! ${it.kind()}]" }.orEmpty()
        val message =
            HeadlessTui.truncate(
                status.message
                    .orEmpty()
                    .lineSequence()
                    .first(),
                (width - prefix.length - flag.length).coerceAtLeast(0),
            )
        return prefix + message + flag
    }

    private fun detailBody(
        detail: Detail,
        width: Int,
        height: Int,
    ): List<String> {
        val task = tasks.find { it.id == detail.taskId } ?: return listOf("", "  This task no longer exists. Esc to go back.")
        val status = task.status
        val head =
            buildList {
                val flag = task.userInteraction?.let { "  [! ${it.kind()}]" }.orEmpty()
                add("  ${task.scriptInstance.getScriptPackageInstance().label()} · task #${numberOf(task)} · ${status.name()}$flag")
                status.message?.let { add("  $it") }
                if (status is TaskStatus.Error && status.stackTrace != null) {
                    if (detail.stackExpanded) status.stackTrace.lines().forEach { add("    $it") } else add("  (t shows the stack trace)")
                }
                add("")
                add("  Logs · ${detail.filter}")
            }
        val log =
            detail.log
                .filter { it.priority.ordinal <= detail.filter.lowest.ordinal }
                .flatMap { event ->
                    val time = SimpleDateFormat("HH:mm:ss").format(event.timestamp)
                    event.message.lines().map { "  $time ${event.priority.label()} $it" }
                }.ifEmpty { listOf("  No log lines.") }
        // Keep the tail of the log in view; an expanded stack trace may push it off the bottom.
        val room = (height - head.size).coerceAtLeast(0)
        return (head + log.takeLast(room)).take(height).map { HeadlessTui.truncate(it, width) }
    }

    private fun numberOf(task: Task): Int = numbered(tasks.filter { it.scriptInstance.getScriptPackageInstance().id == task.scriptInstance.getScriptPackageInstance().id }).first { it.first.id == task.id }.second

    private fun Node.pkg(): ScriptPackageInstance? =
        when (this) {
            is Node.Group -> null
            is Node.Script -> pkg
            is Node.TaskRow -> task.scriptInstance.getScriptPackageInstance()
        }

    companion object {
        private const val TREE_KEYS = "↑↓ move · s/x start/stop · S/X script · Enter detail · m menu"
        private const val DETAIL_KEYS = "f filter · t stack trace · s/x start/stop · Esc back"

        /** Task numbers as on the desktop: a package's tasks by creation order, from 1. */
        fun numbered(tasks: List<Task>): List<Pair<Task, Int>> = tasks.sortedBy { it.createdAt }.mapIndexed { i, task -> task to i + 1 }

        private fun ScriptPackageInstance.label(): String {
            val identifier = definition.mainScript.configuration.getScriptIdentifierValue(mainConfiguration)
            return definition.manifest.name + (identifier?.let { " ($it)" } ?: "")
        }

        private fun TaskStatus.name() =
            when (this) {
                is TaskStatus.Idle -> "Idle"
                is TaskStatus.Running -> "Running"
                is TaskStatus.Success -> "Success"
                is TaskStatus.Error -> "Error"
            }

        private fun TaskStatus.glyph() =
            when (this) {
                is TaskStatus.Idle -> "○"
                is TaskStatus.Running -> "●"
                is TaskStatus.Success -> "✓"
                is TaskStatus.Error -> "✗"
            }

        private fun UserInteraction.kind() =
            when (this) {
                is UserInteraction.Browser -> "BROWSER"
                is UserInteraction.TextInput -> "INPUT"
                is UserInteraction.ContinueButton -> "CONTINUE"
            }

        private fun LoggingPriority.label() =
            when (this) {
                LoggingPriority.ERROR -> "ERR "
                LoggingPriority.WARNING -> "WARN"
                LoggingPriority.INFO -> "INFO"
                LoggingPriority.DEBUG -> "DBG "
            }
    }
}
