package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.provider.AppUpdateProvider
import com.cereal.client.domain.provider.AuthProvider
import com.cereal.client.domain.provider.CheckoutProvider
import com.cereal.client.domain.provider.CrashReportingProvider
import com.cereal.client.domain.provider.DatasetFileProvider
import com.cereal.client.domain.provider.DiscordProvider
import com.cereal.client.domain.provider.LoggerProvider
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.domain.provider.NotificationProvider
import com.cereal.client.domain.provider.ProxyConnectionProvider
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.domain.provider.SystemProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAppUpdateProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryAuthProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryCheckoutProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryCrashReportingProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDatasetFileProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDiscordProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryLoggerProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryMarketplaceProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryNotificationProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryProxyConnectionProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryScriptInstallProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemorySystemProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * In-memory replacement for [ProviderModule]. Binds every provider interface to an
 * in-memory/no-op implementation so the app graph runs without touching the network, OS,
 * browser or native libraries.
 *
 * Used by the sandboxed `mock` flavor at runtime (see
 * [com.cereal.client.infrastructure.di.Injector]) and by UI/screen tests, which pass it to
 * [com.cereal.client.infrastructure.di.Injector.appModules] *instead of* [ProviderModule].
 */
object InMemoryProviderModule {
    val modules: Module =
        module {
            single<SystemProvider> { InMemorySystemProvider() }
            single<DiscordProvider> { InMemoryDiscordProvider() }
            single<LoggerProvider> { InMemoryLoggerProvider() }
            single<CrashReportingProvider> { InMemoryCrashReportingProvider() }
            single<AppUpdateProvider> { InMemoryAppUpdateProvider() }
            single<AuthProvider> { InMemoryAuthProvider() }
            single<NotificationProvider> { InMemoryNotificationProvider() }
            single<DatasetFileProvider> { InMemoryDatasetFileProvider() }
            single<MarketplaceProvider> { InMemoryMarketplaceProvider(catalog = SandboxSampleData.marketplaceCatalog) }
            single<ScriptInstallProvider> { InMemoryScriptInstallProvider() }
            single<ProxyConnectionProvider> {
                InMemoryProxyConnectionProvider(connectorRepository = get(), proxyRepository = get())
            }
            single<CheckoutProvider> { InMemoryCheckoutProvider() }
        }
}
