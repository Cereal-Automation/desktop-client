package com.cereal.client.infrastructure.di.modules

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
import com.cereal.client.infrastructure.data.repository.ApplicationPreferenceRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ApplicationRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ArtifactRepositoryImpl
import com.cereal.client.infrastructure.data.repository.CustomDatasetRepositoryImpl
import com.cereal.client.infrastructure.data.repository.FeatureFlagRepositoryImpl
import com.cereal.client.infrastructure.data.repository.LogEventRepositoryImpl
import com.cereal.client.infrastructure.data.repository.NotificationHistoryRepositoryImpl
import com.cereal.client.infrastructure.data.repository.NotificationSettingsRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ProxyProviderConnectorRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ProxyProviderCredentialRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ProxyRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ScriptInstanceRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ScriptPreferenceRepositoryImpl
import com.cereal.client.infrastructure.data.repository.ScriptRepositoryImpl
import com.cereal.client.infrastructure.data.repository.SessionRepositoryImpl
import com.cereal.client.infrastructure.data.repository.TasksRepositoryImpl
import com.cereal.client.infrastructure.di.UserScopeProvider
import com.cereal_automation.cereal_client.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Production bindings for domain *data repositories* — entity-owning persistence backed by Room,
 * key-value storage or files-as-data. Providers (adapters to external systems, the OS, the
 * browser, channels) live in [ProviderModule].
 *
 * Also owns a few shared singletons (`UserSession`, `FileSystemProxyTemplateDataSource`, the
 * background schedulers) that the in-memory twin [InMemoryRepositoryModule] mirrors when it
 * replaces this module wholesale.
 */
object RepositoryModule {
    val modules =
        module {
            single<TasksRepository> { TasksRepositoryImpl(get(), get(), get()) }
            single<ScriptRepository> { ScriptRepositoryImpl(get(), get(), get()) }
            single<LogEventRepository> { LogEventRepositoryImpl(get(), get()) }
            single<ArtifactRepository> { ArtifactRepositoryImpl(get(), get(), get()) }
            single<ProxyRepository> { ProxyRepositoryImpl(get(), get(), get()) }
            single<SessionRepository> { SessionRepositoryImpl(get(), get(), get()) }
            single<ProxyProviderConnectorRepository> { ProxyProviderConnectorRepositoryImpl(get(), get()) }
            single<ApplicationPreferenceRepository> { ApplicationPreferenceRepositoryImpl(get(), get()) }
            single<NotificationSettingsRepository> { NotificationSettingsRepositoryImpl(get(), get()) }
            single<ProxyProviderCredentialRepository> { ProxyProviderCredentialRepositoryImpl(get(), get()) }
            single<FeatureFlagRepository> { FeatureFlagRepositoryImpl(isDebugBuild = BuildConfig.IS_DEBUG) }
            single<ScriptPreferenceRepository> { ScriptPreferenceRepositoryImpl(get(), get()) }
            single<ScriptInstanceRepository> { ScriptInstanceRepositoryImpl(get(), get(), get(), get()) }
            single<ApplicationRepository> { ApplicationRepositoryImpl(get()) }
            single<NotificationHistoryRepository> { NotificationHistoryRepositoryImpl(get(), get()) }
            single<CustomDatasetRepository> { CustomDatasetRepositoryImpl(get(), get(), get()) }

            single { PeriodicScriptSyncer(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
            single {
                com.cereal.client.application.proxy.ProxyHealthSweepScheduler(
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
