package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.proxy.ProxyHealthSweepScheduler
import com.cereal.client.application.script.PeriodicScriptSyncer
import com.cereal.client.domain.repository.ApplicationPreferenceRepository
import com.cereal.client.domain.repository.ApplicationRepository
import com.cereal.client.domain.repository.ArtifactRepository
import com.cereal.client.domain.repository.CustomDatasetRepository
import com.cereal.client.domain.repository.FeatureFlagRepository
import com.cereal.client.domain.repository.LogEventRepository
import com.cereal.client.domain.repository.NotificationHistoryRepository
import com.cereal.client.domain.repository.NotificationSettingsRepository
import com.cereal.client.domain.repository.ProxyProviderConnectorRepository
import com.cereal.client.domain.repository.ProxyProviderCredentialRepository
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.domain.repository.ScriptInstanceRepository
import com.cereal.client.domain.repository.ScriptPreferenceRepository
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.domain.repository.SessionRepository
import com.cereal.client.domain.repository.TasksRepository
import com.cereal.client.infrastructure.data.datasource.auth.UserSession
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemProxyTemplateDataSource
import com.cereal.client.infrastructure.data.repository.FeatureFlagRepositoryImpl
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationConfig
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryApplicationRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryArtifactRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryLogEventRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationHistoryRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyProviderConnectorRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyProviderCredentialRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryProxyRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptPreferenceRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemorySessionRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryTasksRepository
import com.cereal.client.infrastructure.di.UserScopeProvider
import com.cereal_automation.cereal_client.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * In-memory replacement for [RepositoryModule]. Binds every data-repository interface to an
 * in-memory implementation so the app graph runs without touching the database, filesystem or
 * native libraries. Providers have their own in-memory twin in [InMemoryProviderModule].
 *
 * Used in two places:
 *  - the sandboxed `mock` flavor at runtime (selected in [com.cereal.client.infrastructure.di.Injector]);
 *  - UI/screen tests, which pass it to [com.cereal.client.infrastructure.di.Injector.appModules]
 *    *instead of* [RepositoryModule].
 *
 * Like [RepositoryModule], it also owns the shared singletons (`UserSession`,
 * `FileSystemProxyTemplateDataSource`, the background schedulers) since it replaces that module
 * wholesale, plus an [ApplicationConfig] override whose production counterpart resolves encryption
 * keys via the Sekret native library.
 */
object InMemoryRepositoryModule {
    val modules =
        module {
            // Overrides ApplicationModule's CerealConfiguration, whose constructor resolves
            // encryption keys via the Sekret native library (not available in the sandbox/test JVM).
            single<ApplicationConfig> { InMemoryApplicationConfig() }

            single<TasksRepository> { InMemoryTasksRepository() }
            single<ScriptRepository> { InMemoryScriptRepository() }
            single<LogEventRepository> { InMemoryLogEventRepository() }
            single<ArtifactRepository> { InMemoryArtifactRepository() }
            single<ProxyRepository> { InMemoryProxyRepository(initialGroups = SandboxSampleData.proxyGroups()) }
            single<SessionRepository> { InMemorySessionRepository() }
            single<ProxyProviderConnectorRepository> {
                InMemoryProxyProviderConnectorRepository(
                    initialConnector = SandboxSampleData.proxyProviderConnector(),
                )
            }
            single<ApplicationPreferenceRepository> { InMemoryApplicationPreferenceRepository() }
            single<NotificationSettingsRepository> { InMemoryNotificationSettingsRepository() }
            single<ProxyProviderCredentialRepository> { InMemoryProxyProviderCredentialRepository() }
            // Feature flags are pure build configuration (no database/network/native deps), so the
            // sandbox graph uses the real impl rather than an in-memory fake. Tests that need to
            // force a specific flag state override this binding with InMemoryFeatureFlagRepository.
            single<FeatureFlagRepository> { FeatureFlagRepositoryImpl(isDebugBuild = BuildConfig.IS_DEBUG) }
            single<ScriptPreferenceRepository> { InMemoryScriptPreferenceRepository() }
            single<ScriptInstanceRepository> { InMemoryScriptInstanceRepository() }
            single<ApplicationRepository> { InMemoryApplicationRepository() }
            single<NotificationHistoryRepository> { InMemoryNotificationHistoryRepository() }
            single<CustomDatasetRepository> { InMemoryCustomDatasetRepository(initialGroups = SandboxSampleData.customDatasetGroups()) }

            single { PeriodicScriptSyncer(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
            single {
                ProxyHealthSweepScheduler(
                    get(),
                    get(),
                    get(),
                    get(),
                    CoroutineScope(SupervisorJob() + Dispatchers.Default),
                )
            }
            single { UserSession() } bind UserScopeProvider::class
            single { FileSystemProxyTemplateDataSource() }
        }
}
