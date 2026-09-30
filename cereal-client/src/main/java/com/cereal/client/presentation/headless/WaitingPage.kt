package com.cereal.client.presentation.headless

import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.task.AnswerUserInteractionInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.presentation.headless.TasksPage.Companion.kind
import com.cereal.client.presentation.headless.TasksPage.Companion.label
import com.github.kittinunf.result.coroutines.SuspendableResult
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tab `2`: every task waiting for the user (browser, text input, continue), with the count on the
 * label. `Enter` opens the task via [open]; `c` answers a continue here. Browser prompts are only
 * listed: they are finished on the prompt page.
 */
class WaitingPage(
    private val scope: CoroutineScope,
    private val changed: () -> Unit,
    observeTasks: ObserveTasksInteractor,
    private val answerInteraction: AnswerUserInteractionInteractor,
    private val open: (taskId: String) -> Unit,
) : TuiPage {
    private val list = RowList<Task>()

    @Volatile private var tasks: List<Task> = emptyList()

    @Volatile private var notice: String? = null

    override val title get() = tasks.count { it.userInteraction != null }.let { if (it > 0) "Waiting $it!" else "Waiting" }

    override val keys get() = if (tasks.none { it.userInteraction != null }) "" else "↑↓ move · Enter open · c continue"

    init {
        scope.launch {
            observeTasks(Interactor.None()).collect { result ->
                if (result is SuspendableResult.Success) {
                    tasks = result.value
                    changed()
                }
            }
        }
    }

    override fun body(
        width: Int,
        height: Int,
    ): List<String> {
        val all = tasks
        list.rows =
            all
                .groupBy { it.scriptInstance.getScriptPackageInstance().id }
                .values
                .flatMap { TasksPage.numbered(it) }
                .mapNotNull { (task, number) ->
                    val interaction = task.userInteraction ?: return@mapNotNull null
                    val text = "${task.scriptInstance.getScriptPackageInstance().label()} #$number  ${interaction.kind()}  ${interaction.describe()}"
                    RowList.Row(task.id, text, task)
                }
        val bottom = listOfNotNull(notice?.let { "  ! $it" })
        if (list.rows.isEmpty()) return listOf("", "  Nothing is waiting for you.") + bottom
        return list.render(width, height - bottom.size) + bottom
    }

    override fun onKey(key: Key): Boolean {
        notice = null
        if (list.onKey(key)) return true
        val task = list.selected ?: return false
        when {
            key == Keys.Enter -> {
                open(task.id)
            }

            (key as? CharKey)?.char == 'c' && task.userInteraction is UserInteraction.ContinueButton -> {
                scope.launch {
                    answerInteraction(AnswerUserInteractionInteractor.Params.Continue(task.id)) { result ->
                        if (result is SuspendableResult.Failure) notice = result.error.message
                        changed()
                    }
                }
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun UserInteraction.describe() =
        when (this) {
            is UserInteraction.Browser -> "$title (finish it on the prompt page)"
            is UserInteraction.TextInput -> title
            is UserInteraction.ContinueButton -> "press c to continue"
        }
}
