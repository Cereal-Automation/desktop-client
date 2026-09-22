package com.cereal.client.presentation.tasks.mappers

import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.presentation.tasks.formatter.joinToString
import com.cereal.client.presentation.tasks.model.ScriptInstanceUiModel
import com.cereal.client.presentation.tasks.model.TaskUiModel
import com.cereal.client.presentation.view.group.GroupListItemContent
import java.text.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

val taskStatusSortOrder =
    mapOf(
        TaskStatus.Running::class to 1,
        TaskStatus.Idle::class to 2,
        TaskStatus.Error::class to 3,
        TaskStatus.Success::class to 4,
    )

private val dateFormatter =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalTime::class)
fun ScriptInstance.toUiModel(): ScriptInstanceUiModel {
    val name =
        if (definition is ChildScript) {
            "${(definition as ChildScript).name}  [Started on ${dateFormatter.format(this.createdAt.toJavaInstant())}]"
        } else {
            null
        }
    return ScriptInstanceUiModel(this, name)
}

fun Task.toUiModel(taskNumber: Int): TaskUiModel =
    TaskUiModel(
        this,
        taskNumber,
        status.isRunning(),
        isError = status is TaskStatus.Error,
        isSuccess = status is TaskStatus.Success,
        hasSupportUrl = scriptInstance.packageInstance.definition.manifest.supportUrl != null,
        status.toDisplayMessage(),
        status.toDisplayStatus(),
        configuration
            .filter {
                // Only show relevant configuration values, the values that are the same for all the tasks can be
                // viewed at script instance level.
                it.value is ConfigValue.ProxyValue || it.value is ConfigValue.CustomDatasetItemValue
            }.joinToString(scriptInstance.definition.configuration),
        stackTrace = (status as? TaskStatus.Error)?.stackTrace,
    )

@OptIn(ExperimentalTime::class)
fun List<TaskStatus>.toDisplayStatus(): List<String> =
    map {
        val displayStatus = it.toDisplayStatus()
        val displayMessage = it.toDisplayMessage()
        val date = Date(it.timestamp.toEpochMilliseconds())

        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault())
        "${formatter.format(date)} [$displayStatus] $displayMessage"
    }

fun TaskStatus.toDisplayStatus(): String =
    when (this) {
        is TaskStatus.Idle -> "Idle"
        is TaskStatus.Running -> "Running"
        is TaskStatus.Success -> "Success"
        is TaskStatus.Error -> "Error"
    }

fun TaskStatus.toDisplayMessage(): String =
    when (this) {
        is TaskStatus.Running -> this.message.orEmpty()
        is TaskStatus.Success -> this.message
        is TaskStatus.Error -> this.message
        is TaskStatus.Idle -> this.message ?: "Press start"
    }

fun List<ScriptPackageGroup>.toTaskGroupUiModels(): List<GroupListItemContent<ScriptPackageGroup>> = map { it.toUiModel() }

fun ScriptPackageGroup.toUiModel(): GroupListItemContent<ScriptPackageGroup> =
    GroupListItemContent(
        id = this,
        title = this.name,
        badge = this.totalScriptPackages.toString(),
    )
