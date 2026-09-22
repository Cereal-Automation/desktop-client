package com.cereal.client.domain.model

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask

/**
 * Links the resource scopes of related domain objects so that a child object's scope
 * inherits the resources of its parent's scope.
 *
 * The domain depends only on this abstraction; the concrete dependency-injection
 * wiring lives in the infrastructure layer.
 */
interface ScopeLinker {
    /**
     * Links a script instance's scope to the scope of its script package instance.
     */
    fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance)

    /**
     * Links a task's scope to the scope of its script instance.
     */
    fun linkTaskToScriptInstance(task: JobTask)
}
