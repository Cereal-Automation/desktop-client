package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.domain.provider.LoggerProvider
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.infrastructure.data.repository.SandboxScriptSeeder
import com.cereal.client.infrastructure.data.repository.SandboxSessionRepository
import com.cereal.client.infrastructure.data.repository.ScriptRepositoryImpl
import com.cereal.client.infrastructure.data.repository.SeedingScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.provider.DatasetFileProviderImpl
import com.cereal.client.infrastructure.provider.LoggerProviderImpl
import com.cereal.client.infrastructure.provider.SandboxAuthProvider
import org.koin.dsl.module
import java.io.File

/**
 * Sandbox-only overrides for the `mock` flavor, loaded after the real [RepositoryModule] and the
 * fake [InMemoryProviderModule] in [com.cereal.client.infrastructure.di.Injector].
 *
 * The sandbox runs the real repositories (Room database, key-value store, filesystem) and only
 * fakes what goes over the network. So the overrides here are:
 *  - a Sekret-free [ApplicationConfig] whose data lives under `~/Cereal-Sandbox/`, kept apart from
 *    a real install's data;
 *  - [SandboxSessionRepository] / [SandboxAuthProvider] in place of the marketplace-backed session
 *    and login, driving the real [com.cereal.client.infrastructure.data.datasource.auth.UserSession];
 *  - [SeedingScriptRepository], the real [ScriptRepositoryImpl] plus seeding of the bundled sample
 *    scripts (the marketplace download that normally installs them is unavailable);
 *  - real (non-network) providers that [InMemoryProviderModule] would otherwise no-op: logging to
 *    the real log repository and dataset import/export files.
 */
object SandboxRepositoryModule {
    val modules =
        module {
            single<ApplicationConfig> {
                InMemoryApplicationConfig(baseDir = File(System.getProperty("user.home"), "Cereal-Sandbox"))
            }
            single { SandboxScriptSeeder(get()) }
            single<ScriptRepository> { SeedingScriptRepository(ScriptRepositoryImpl(get(), get(), get()), get(), get()) }
            single<SessionRepository> { SandboxSessionRepository(get()) }
            single<AuthProvider> { SandboxAuthProvider(get()) }
            single<LoggerProvider> { LoggerProviderImpl(get()) }
            single<DatasetFileProvider> { DatasetFileProviderImpl(get(), get(), get()) }
        }
}
