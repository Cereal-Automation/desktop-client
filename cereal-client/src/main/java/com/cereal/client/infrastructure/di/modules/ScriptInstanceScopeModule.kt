package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.infrastructure.sdkcomponent.ScriptLauncherComponentImpl
import com.cereal.sdk.component.script.ScriptLauncherComponent
import org.koin.dsl.module

object ScriptInstanceScopeModule {
    val modules =
        module {
            scope<ScriptInstance> {
                scoped<ScriptLauncherComponent> {
                    val scriptInstance = getSource<ScriptInstance>()

                    ScriptLauncherComponentImpl(
                        get(),
                        scriptInstance ?: throw RuntimeException("ScriptInstance is required"),
                        get(),
                    )
                }
            }
        }
}
