package com.cereal.client.infrastructure.di

import com.cereal.client.infrastructure.di.modules.ApplicationModule
import com.cereal.client.infrastructure.di.modules.DataSourceModule
import com.cereal.client.infrastructure.di.modules.InMemoryProviderModule
import com.cereal.client.infrastructure.di.modules.InteractorModule
import com.cereal.client.infrastructure.di.modules.NotificationModule
import com.cereal.client.infrastructure.di.modules.ProviderModule
import com.cereal.client.infrastructure.di.modules.RepositoryModule
import com.cereal.client.infrastructure.di.modules.SandboxRepositoryModule
import com.cereal.client.infrastructure.di.modules.ScriptInstanceScopeModule
import com.cereal.client.infrastructure.di.modules.ScriptPackageInstanceScopeModule
import com.cereal.client.infrastructure.di.modules.TaskScopeModule
import com.cereal.client.infrastructure.di.modules.UserScopeModule
import com.cereal.client.infrastructure.di.modules.ViewModelModule
import com.cereal.client.infrastructure.logger.slf4jLogger
import com.cereal_automation.cereal_client.BuildConfig
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.module.Module

object Injector {
    /**
     * The full application module list. The repository module is injectable so tests (and the
     * `mock` flavor) can substitute in-memory implementations while reusing
     * the real ViewModel/interactor wiring.
     */
    fun appModules(
        repositoryModule: Module = RepositoryModule.modules,
        providerModule: Module = ProviderModule.modules,
    ): List<Module> =
        listOf(
            ApplicationModule.modules,
            ViewModelModule.modules,
            DataSourceModule.modules,
            repositoryModule,
            providerModule,
            NotificationModule.modules,
            InteractorModule.modules,
            ScriptPackageInstanceScopeModule.modules,
            ScriptInstanceScopeModule.modules,
            TaskScopeModule.modules,
            UserScopeModule.modules,
        )

    /**
     * The sandboxed `mock` flavor: real repositories, fake network-backed providers, plus the
     * [SandboxRepositoryModule] overrides (Sekret-free config, offline session/login, seeded scripts).
     */
    fun sandboxModules(): List<Module> = appModules(RepositoryModule.modules, InMemoryProviderModule.modules) + SandboxRepositoryModule.modules

    fun initialize(): Koin =
        startKoin {
            slf4jLogger(level = Level.ERROR)

            val koinModules =
                if (BuildConfig.FLAVOR == "mock") sandboxModules() else appModules()

            modules(koinModules)
        }.koin
}
