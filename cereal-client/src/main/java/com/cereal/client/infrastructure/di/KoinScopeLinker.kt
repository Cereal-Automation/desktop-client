package com.cereal.client.infrastructure.di

import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.domain.model.task.Task
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named

/**
 * Koin-backed [ScopeLinker] that wires together the dependency-injection scopes
 * of related domain objects, keeping the Koin dependency out of the domain layer.
 */
class KoinScopeLinker :
    ScopeLinker,
    KoinComponent {
    override fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance) {
        val scriptPackageInstanceScope =
            getKoin().getOrCreateScope(
                scriptInstance.packageInstance.id,
                named<ScriptPackageInstance>(),
                source = scriptInstance.packageInstance,
            )

        val scriptInstanceScope =
            getKoin().getOrCreateScope(
                scriptInstance.id,
                named<ScriptInstance>(),
                source = scriptInstance,
            )

        // Link scopes so that the script instance scope also has access to the script package instance scope.
        scriptInstanceScope.linkTo(scriptPackageInstanceScope)
    }

    override fun linkTaskToScriptInstance(task: JobTask) {
        val scriptInstanceScope =
            getKoin().getOrCreateScope(
                task.scriptInstance.id,
                named<ScriptInstance>(),
                source = task.scriptInstance,
            )

        val taskScope =
            getKoin().getOrCreateScope(
                task.id,
                named<Task>(),
                source = task,
            )

        // Link scopes so that the task scope also has access to the script instance scope.
        taskScope.linkTo(scriptInstanceScope)
    }
}
