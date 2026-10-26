package com.cereal.client.domain.model.task

import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import fixtures.aScriptInstance
import fixtures.aScriptPackageInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TaskSetupSortTest {
    /** A setup created at second [createdAt], running script [name] identified by [identifier]. */
    private fun setup(
        id: String,
        createdAt: Long,
        name: String = "Test",
        identifier: String? = null,
    ): ScriptPackageInstance {
        val base = aScriptPackageInstance(id, "com.example.$id")
        val identifierItem =
            ScriptConfigurationItemDefinition(
                name = "Id",
                description = "",
                key = "id",
                position = 0,
                type = ConfigItemType.StringConfigItem,
                isNullable = true,
                stateModifier = null,
                isScriptIdentifier = true,
            )
        val definition =
            base.definition.copy(
                manifest = base.definition.manifest.copy(name = name),
                mainScript =
                    base.definition.mainScript.copy(
                        configuration =
                            base.definition.mainScript.configuration
                                .copy(configurationItems = listOf(identifierItem)),
                    ),
            )
        return base.copy(
            definition = definition,
            mainConfiguration = identifier?.let { mapOf("id" to ConfigValue.StringValue(it)) }.orEmpty(),
            createdAt = Instant.fromEpochSeconds(createdAt),
        )
    }

    private fun task(
        setup: ScriptPackageInstance,
        status: TaskStatus,
    ) = JobTask(
        id = "${setup.id}-task",
        scriptInstance = aScriptInstance(setup),
        configuration = emptyMap(),
        statusHistory = listOf(status),
        createdAt = Instant.fromEpochSeconds(0),
    )

    private fun TaskSetupSort.ids(
        setups: List<ScriptPackageInstance>,
        tasks: List<Task> = emptyList(),
    ) = sort(setups, tasks.groupBy { it.scriptInstance.packageInstance.id }).map { it.id }

    @Test
    fun `newest first and oldest first order by creation`() {
        val setups = listOf(setup("b", 2), setup("a", 1), setup("c", 3))

        assertEquals(listOf("c", "b", "a"), TaskSetupSort.NEWEST_FIRST.ids(setups))
        assertEquals(listOf("a", "b", "c"), TaskSetupSort.OLDEST_FIRST.ids(setups))
    }

    @Test
    fun `name sorts by script name ignoring case, then identifier, then newest first`() {
        val setups =
            listOf(
                setup("nike-b", 1, name = "Nike", identifier = "b@x.io"),
                setup("adidas", 2, name = "adidas"),
                setup("nike-a", 3, name = "Nike", identifier = "A@x.io"),
                setup("nike-a-old", 0, name = "Nike", identifier = "A@x.io"),
            )

        assertEquals(listOf("adidas", "nike-a", "nike-a-old", "nike-b"), TaskSetupSort.NAME.ids(setups))
    }

    @Test
    fun `needs attention puts failed setups first, then running ones, newest first within each`() {
        val idleOld = setup("idle-old", 0)
        val failedOld = setup("failed-old", 1)
        val running = setup("running", 2)
        val failedNew = setup("failed-new", 3)
        val idleNew = setup("idle-new", 4)
        val tasks =
            listOf(
                task(idleOld, TaskStatus.Idle(timestamp = Instant.fromEpochSeconds(0))),
                task(failedOld, TaskStatus.Error("Failed", timestamp = Instant.fromEpochSeconds(0))),
                task(running, TaskStatus.Running(timestamp = Instant.fromEpochSeconds(0))),
                task(failedNew, TaskStatus.Error("Failed", timestamp = Instant.fromEpochSeconds(0))),
            )

        assertEquals(
            listOf("failed-new", "failed-old", "running", "idle-new", "idle-old"),
            TaskSetupSort.NEEDS_ATTENTION.ids(listOf(idleOld, failedOld, running, failedNew, idleNew), tasks),
        )
    }

    @Test
    fun `an unknown or missing stored value falls back to newest first`() {
        assertEquals(TaskSetupSort.NAME, TaskSetupSort.fromStorageValue("name"))
        assertEquals(TaskSetupSort.NEWEST_FIRST, TaskSetupSort.fromStorageValue("removed_sort"))
        assertEquals(TaskSetupSort.NEWEST_FIRST, TaskSetupSort.fromStorageValue(null))
    }
}
