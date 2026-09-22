package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.task.ProxiesRandomizerProvider
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.infrastructure.sdkcomponent.FakeLicenseComponent
import com.cereal.client.infrastructure.sdkcomponent.LicenseComponentImpl
import com.cereal.client.infrastructure.sdkcomponent.PreferenceComponentImpl
import com.cereal.sdk.component.license.LicenseComponent
import com.cereal.sdk.component.preference.PreferenceComponent
import com.cereal_automation.cereal_client.BuildConfig
import org.koin.dsl.module

object ScriptPackageInstanceScopeModule {
    val modules =
        module {
            scope<ScriptPackageInstance> {
                scoped { ProxiesRandomizerProvider(get()) }
                scoped<LicenseComponent> {
                    if (BuildConfig.FLAVOR == "mock") {
                        FakeLicenseComponent()
                    } else {
                        LicenseComponentImpl(get())
                    }
                }
                scoped<PreferenceComponent> {
                    val scriptPackageInstance = getSource<ScriptPackageInstance>()

                    PreferenceComponentImpl(
                        scriptPackageInstance?.id
                            ?: throw RuntimeException("ScriptPackageInstance is required"),
                        get(),
                    )
                }
            }
        }
}
