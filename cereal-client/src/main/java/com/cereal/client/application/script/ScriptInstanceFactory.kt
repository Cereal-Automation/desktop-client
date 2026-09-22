package com.cereal.client.application.script

import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.ChildScript
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.MainScript
import com.cereal.client.domain.model.script.MainScriptInstance
import com.cereal.client.domain.model.script.ScriptConfigurationValues
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.sdk.component.script.ScriptParameters
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ScriptInstanceFactory(
    private val scopeLinker: ScopeLinker,
) {
    /**
     * Creates a new instance of a `MainScriptInstance` using the provided parameters.
     *
     * @param id The unique identifier for the script instance.
     * @param definition The definition of the main script, including its class and configuration.
     * @param configuration The specific configuration values for this script instance.
     * @param createdAt The timestamp indicating when this script instance was created.
     * @param packageInstance The parent `ScriptPackageInstance` to which this script instance belongs.
     * @return A new `MainScriptInstance` object with linked scopes.
     */
    @OptIn(ExperimentalTime::class)
    fun createMainScriptInstance(
        id: String,
        definition: MainScript,
        configuration: ScriptConfigurationValues,
        createdAt: Instant,
        packageInstance: ScriptPackageInstance,
    ): MainScriptInstance {
        val instance =
            MainScriptInstance(
                id = id,
                definition = definition,
                configuration = configuration,
                createdAt = createdAt,
                packageInstance = packageInstance,
            )

        scopeLinker.linkScriptInstanceToPackage(instance)

        return instance
    }

    /**
     * Creates an instance of a child script and links its scopes.
     *
     * @param id The unique identifier for the child script instance.
     * @param definition The definition of the child script to be instantiated.
     * @param configuration The configuration values for the child script instance.
     * @param createdAt The timestamp indicating when the child script instance was created.
     * @param packageInstance The script package instance associated with the child script.
     * @param parent The parent script instance to which this child script instance belongs.
     * @param params Optional parameters for the child script instance.
     * @return A newly created `ChildScriptInstance` with linked scopes.
     */
    @OptIn(ExperimentalTime::class)
    fun createChildScriptInstance(
        id: String,
        definition: ChildScript,
        configuration: ScriptConfigurationValues,
        createdAt: Instant,
        packageInstance: ScriptPackageInstance,
        parent: ScriptInstance,
        params: ScriptParameters?,
    ): ChildScriptInstance {
        val instance =
            ChildScriptInstance(
                id = id,
                definition = definition,
                configuration = configuration,
                createdAt = createdAt,
                packageInstance = packageInstance,
                parent = parent,
                params = params,
            )

        scopeLinker.linkScriptInstanceToPackage(instance)

        return instance
    }
}
