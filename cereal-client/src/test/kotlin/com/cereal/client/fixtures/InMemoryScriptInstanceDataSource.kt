package com.cereal.client.fixtures

import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [ScriptInstanceDataSource], implementing all three composed concerns
 * ([com.cereal.client.infrastructure.data.datasource.database.ScriptPackageGroupDataSource],
 * [com.cereal.client.infrastructure.data.datasource.database.ScriptPackageInstanceDataSource],
 * [com.cereal.client.infrastructure.data.datasource.database.ScriptTaskDataSource]).
 *
 * Everything is stored in [MutableStateFlow] backed maps keyed by user id so flow getters observe
 * writes and suspend getters return what was added. Package instances are stored as the domain
 * [ScriptPackageInstance] objects that were added (which already carry their [ScriptPackage]
 * definition); the `scriptPackageDefinitions` map supplied to getters gates inclusion, mirroring
 * the Room implementation which skips entities whose package name is absent from the map.
 */
class InMemoryScriptInstanceDataSource : ScriptInstanceDataSource {
    /** Stored package instance together with its group and known script instances. */
    private data class StoredPackageInstance(
        val instance: ScriptPackageInstance,
        val groupId: String,
        val main: MainScriptInstance,
        val children: List<ChildScriptInstance> = emptyList(),
    )

    // user id -> (group id -> group). Stored in a plain map + revision token because
    // ScriptPackageGroup.equals is id-only, so a StateFlow<Map<…, group>> would conflate a rename
    // (same id, new name) as "no change" and never emit.
    private var groupsByUser: Map<String, Map<String, ScriptPackageGroup>> = emptyMap()
    private val groupsRevision = MutableStateFlow(0L)

    private fun bumpGroups() {
        groupsRevision.value += 1
    }

    // user id -> (package instance id -> stored package instance)
    private val packageInstances = MutableStateFlow<Map<String, Map<String, StoredPackageInstance>>>(emptyMap())

    // user id -> (script instance id -> job tasks)
    private val tasks = MutableStateFlow<Map<String, Map<String, List<JobTask>>>>(emptyMap())

    private fun packageNamePresent(
        instance: ScriptPackageInstance,
        definitions: Map<String, ScriptPackage>,
    ): Boolean = instance.definition.manifest.packageName in definitions

    // region ScriptPackageGroupDataSource

    override fun getScriptInstanceGroups(user: User): Flow<List<ScriptPackageGroup>> = groupsRevision.map { groupsByUser[user.id].orEmpty().values.toList() }

    override suspend fun createScriptInstanceGroup(
        user: User,
        scriptPackageGroup: ScriptPackageGroup,
    ): ScriptPackageGroup {
        val userGroups = groupsByUser[user.id].orEmpty()
        groupsByUser = groupsByUser + (user.id to (userGroups + (scriptPackageGroup.id to scriptPackageGroup)))
        bumpGroups()
        return scriptPackageGroup
    }

    override suspend fun updateScriptPackageGroup(
        user: User,
        scriptPackageGroup: ScriptPackageGroup,
    ) {
        val userGroups = groupsByUser[user.id].orEmpty()
        if (scriptPackageGroup.id in userGroups) {
            groupsByUser = groupsByUser + (user.id to (userGroups + (scriptPackageGroup.id to scriptPackageGroup)))
            bumpGroups()
        }
    }

    override suspend fun deleteScriptInstanceGroup(
        user: User,
        scriptPackageGroupId: String,
    ) {
        val userGroups = groupsByUser[user.id].orEmpty()
        groupsByUser = groupsByUser + (user.id to (userGroups - scriptPackageGroupId))
        bumpGroups()

        val userInstances = packageInstances.value[user.id].orEmpty()
        val remaining = userInstances.filterValues { it.groupId != scriptPackageGroupId }
        packageInstances.value = packageInstances.value + (user.id to remaining)
    }

    // endregion

    // region ScriptPackageInstanceDataSource

    override suspend fun updateScriptPackageInstanceGroup(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
        newGroupId: String,
    ) {
        val userInstances = packageInstances.value[user.id].orEmpty()
        val existing = userInstances[scriptPackageInstance.id] ?: return
        val updated = existing.copy(groupId = newGroupId)
        packageInstances.value =
            packageInstances.value + (user.id to (userInstances + (scriptPackageInstance.id to updated)))
    }

    override suspend fun addScriptPackageInstance(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
        mainScriptInstance: MainScriptInstance,
        groupId: String,
    ) {
        val userInstances = packageInstances.value[user.id].orEmpty()
        val stored =
            StoredPackageInstance(
                instance = scriptPackageInstance,
                groupId = groupId,
                main = mainScriptInstance,
            )
        packageInstances.value =
            packageInstances.value + (user.id to (userInstances + (scriptPackageInstance.id to stored)))
    }

    override suspend fun addChildScript(
        user: User,
        childScriptIdentifier: String,
        scriptInstance: ChildScriptInstance,
        parentInstanceId: String,
    ) {
        val userInstances = packageInstances.value[user.id].orEmpty()
        val packageId = scriptInstance.packageInstance.id
        val existing = userInstances[packageId] ?: return
        val updated = existing.copy(children = existing.children + scriptInstance)
        packageInstances.value =
            packageInstances.value + (user.id to (userInstances + (packageId to updated)))
    }

    override suspend fun getScriptPackageInstancesInGroup(
        user: User,
        groupId: String,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): List<ScriptPackageInstance> =
        packageInstances.value[user.id]
            .orEmpty()
            .values
            .filter { it.groupId == groupId }
            .map { it.instance }
            .filter { packageNamePresent(it, scriptPackageDefinitions) }

    override fun getScriptPackagesInGroupFlow(
        user: User,
        groupId: String,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): Flow<List<ScriptPackageInstance>> =
        packageInstances.map { all ->
            all[user.id]
                .orEmpty()
                .values
                .filter { it.groupId == groupId }
                .map { it.instance }
                .filter { packageNamePresent(it, scriptPackageDefinitions) }
        }

    override suspend fun getScriptPackageInstances(
        user: User,
        scriptPackageDefinitions: Map<String, ScriptPackage>,
    ): List<ScriptPackageInstance> =
        packageInstances.value[user.id]
            .orEmpty()
            .values
            .map { it.instance }
            .filter { packageNamePresent(it, scriptPackageDefinitions) }

    override suspend fun getScriptPackageInstance(
        user: User,
        id: String,
        scriptPackageDefinition: ScriptPackage,
    ): ScriptPackageInstance? = packageInstances.value[user.id]?.get(id)?.instance

    override suspend fun getScriptPackageNameById(
        user: User,
        id: String,
    ): String? =
        packageInstances.value[user.id]
            ?.get(id)
            ?.instance
            ?.definition
            ?.manifest
            ?.packageName

    override suspend fun deleteScriptPackageInstance(
        user: User,
        scriptPackageInstanceId: String,
    ) {
        val userInstances = packageInstances.value[user.id].orEmpty()
        packageInstances.value =
            packageInstances.value + (user.id to (userInstances - scriptPackageInstanceId))
    }

    override suspend fun getScriptInstances(
        user: User,
        scriptPackageInstance: ScriptPackageInstance,
    ): List<ScriptInstance> {
        val stored = packageInstances.value[user.id]?.get(scriptPackageInstance.id) ?: return emptyList()
        return stored.children + stored.main
    }

    // endregion

    // region ScriptTaskDataSource

    override suspend fun addTask(
        user: User,
        task: Task,
    ) {
        val jobTask = task.toJobTask()
        val scriptInstanceId = task.scriptInstance.id
        val userTasks = tasks.value[user.id].orEmpty()
        val instanceTasks = userTasks[scriptInstanceId].orEmpty()
        tasks.value =
            tasks.value + (user.id to (userTasks + (scriptInstanceId to (instanceTasks + jobTask))))
    }

    override suspend fun removeTask(
        user: User,
        taskId: String,
    ) {
        val userTasks = tasks.value[user.id].orEmpty()
        val updated =
            userTasks.mapValues { (_, instanceTasks) ->
                instanceTasks.filterNot { it.id == taskId }
            }
        tasks.value = tasks.value + (user.id to updated)
    }

    override suspend fun addStatusToTask(
        user: User,
        taskId: String,
        status: TaskStatus,
    ) {
        val userTasks = tasks.value[user.id].orEmpty()
        val updated =
            userTasks.mapValues { (_, instanceTasks) ->
                instanceTasks.map { jobTask ->
                    if (jobTask.id == taskId) jobTask.withStatus(status) else jobTask
                }
            }
        tasks.value = tasks.value + (user.id to updated)
    }

    override suspend fun getJobTasksFromHistory(
        user: User,
        scriptInstance: ScriptInstance,
    ): List<JobTask> = tasks.value[user.id]?.get(scriptInstance.id).orEmpty()

    // endregion

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun Task.toJobTask(): JobTask =
        this as? JobTask
            ?: JobTask(
                id = id,
                scriptInstance = scriptInstance,
                configuration = configuration,
                statusHistory = statusHistory,
                userInteraction = userInteraction,
                createdAt = createdAt,
            )
}
