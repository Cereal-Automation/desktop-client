package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.infrastructure.data.repository.FileSystemScriptRepository
import com.cereal.client.infrastructure.data.repository.SandboxScriptSeeder
import com.cereal.client.infrastructure.data.repository.SandboxSessionRepository
import com.cereal.client.infrastructure.provider.SandboxAuthProvider
import org.koin.dsl.module

/**
 * Sandbox-only overrides layered on top of [InMemoryRepositoryModule] for the `mock` flavor.
 *
 * Everything stays in-memory except where a *running* sandbox app needs real local behaviour:
 *  - [FileSystemScriptRepository] reads scripts from disk and seeds the bundled sample
 *    (via [SandboxScriptSeeder]) so a runnable script is present out-of-the-box;
 *  - [SandboxUserRepository] drives the real [com.cereal.client.infrastructure.data.datasource.auth.UserSession]
 *    so logging in opens the Koin `UserScope` and populates the session the rest of the graph
 *    (including the script repository) reads from.
 *
 * UI tests do not load this module, so they remain fully hermetic against the in-memory
 * `InMemoryScriptRepository` / `InMemoryUserRepository`.
 *
 * Loaded after [InMemoryRepositoryModule] in
 * [com.cereal.client.infrastructure.di.Injector], so these definitions override the in-memory ones.
 */
object SandboxRepositoryModule {
    val modules =
        module {
            single { SandboxScriptSeeder(get()) }
            single<ScriptRepository> { FileSystemScriptRepository(get(), get(), get()) }
            single<SessionRepository> { SandboxSessionRepository(get()) }
            single<AuthProvider> { SandboxAuthProvider(get()) }
        }
}
