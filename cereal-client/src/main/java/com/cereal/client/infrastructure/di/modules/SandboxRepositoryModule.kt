package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.domain.provider.FailureReportProvider
import com.cereal.client.domain.repository.LlmProviderConfigRepository
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.domain.repository.RunTraceRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptPreferenceRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.datasource.filesystem.SandboxPlainArtifactFileDataSource
import com.cereal.client.infrastructure.data.repository.FileSystemScriptRepository
import com.cereal.client.infrastructure.data.repository.LlmProviderConfigRepositoryImpl
import com.cereal.client.infrastructure.data.repository.RunTraceRepositoryImpl
import com.cereal.client.infrastructure.data.repository.SandboxScriptSeeder
import com.cereal.client.infrastructure.data.repository.SandboxSessionRepository
import com.cereal.client.infrastructure.data.repository.SandboxTextFileLogEventRepository
import com.cereal.client.infrastructure.data.repository.SandboxTextFileNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.ScriptInstanceRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ScriptPreferenceRepositoryImpl
import com.cereal.client.infrastructure.data.repository.TasksRepositoryImpl
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.data.repository.sandboxOutputDirectory
import com.cereal.client.infrastructure.provider.DatasetFileProviderImpl
import com.cereal.client.infrastructure.provider.SandboxAuthProvider
import com.cereal.client.infrastructure.provider.SandboxFailureReportProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Sandbox-only overrides layered on top of [InMemoryRepositoryModule] for the `mock` flavor.
 *
 * Everything stays in-memory except where a *running* sandbox app needs real local behaviour:
 *  - [FileSystemScriptRepository] reads scripts from disk and seeds the bundled sample
 *    (via [SandboxScriptSeeder]) so a runnable script is present out-of-the-box;
 *  - [SandboxUserRepository] drives the real [com.cereal.client.infrastructure.data.datasource.auth.UserSession]
 *    so logging in opens the Koin `UserScope` and populates the session the rest of the graph
 *    (including the script repository) reads from.
 *  - log events and notifications are mirrored to plain-text files under
 *    `~/Cereal-Sandbox/` (see [SandboxTextFileLogEventRepository]) for easy inspection.
 *  - the LLM provider config and everything under Tasks (groups, task setups, their tasks and
 *    per-setup script preferences) use the production Room-backed repositories, so they survive a
 *    restart. Delete `~/Cereal-Sandbox/` (or its `db/` folder) to reset;
 *  - Recent checks (run traces) use the Room-backed [RunTraceRepositoryImpl] with their bytes stored unencrypted
 *    under `~/Cereal-Sandbox/diagnostics/<userId>/<taskId>/<traceId>`, and reported checks are written to
 *    `~/Cereal-Sandbox/failure-reports/` by [SandboxFailureReportProvider] instead of being uploaded;
 *  - [DatasetFileProviderImpl] reads and writes the import files picked in the real native file
 *    dialogs, so downloading a template and importing a file actually work.
 *
 * UI tests do not load this module, so they remain fully hermetic against the in-memory
 * `InMemoryScriptRepository` / `InMemoryUserRepository`.
 *
 * Loaded after [InMemoryRepositoryModule] in
 * [com.cereal.client.infrastructure.di.Injector], so these definitions override the in-memory ones.
 */
object SandboxRepositoryModule {
    fun modules(baseDirectory: File = sandboxOutputDirectory): Module =
        module {
            // Persisted sandbox state (Room DB, scripts, script config) lives under baseDirectory. The dummy
            // encryption keys of InMemoryApplicationConfig and the fixed sandbox user (empty encryption key)
            // keep derived keys stable across launches, so the stored LLM API key is effectively plaintext
            // to anyone who can read the folder. Acceptable for this dev-only flavor.
            single<ApplicationConfig> { InMemoryApplicationConfig(baseDir = baseDirectory) }
            single<LlmProviderConfigRepository> { LlmProviderConfigRepositoryImpl(get(), get()) }
            single<TasksRepository> { TasksRepositoryImpl(get(), get(), get()) }
            single<ScriptInstanceRepository> { ScriptInstanceRepositoryImpl(get(), get(), get(), get()) }
            single<ScriptPreferenceRepository> { ScriptPreferenceRepositoryImpl(get(), get()) }
            single { SandboxScriptSeeder(get()) }
            single<ScriptRepository> { FileSystemScriptRepository(get(), get(), get()) }
            single<SessionRepository> { SandboxSessionRepository(get()) }
            single<AuthProvider> { SandboxAuthProvider(get()) }
            single<LogEventRepository> { SandboxTextFileLogEventRepository(File(baseDirectory, "logs.txt")) }
            single<NotificationHistoryRepository> { SandboxTextFileNotificationHistoryRepository(File(baseDirectory, "notifications.txt")) }
            single<DatasetFileProvider> { DatasetFileProviderImpl(get(), get(), get()) }
            single<RunTraceRepository> {
                RunTraceRepositoryImpl(get(), SandboxPlainArtifactFileDataSource(File(baseDirectory, "diagnostics")), get())
            }
            single<FailureReportProvider> { SandboxFailureReportProvider(File(baseDirectory, "failure-reports")) }
        }
}
