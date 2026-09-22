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
import com.cereal.client.infrastructure.bootstrap.BootstrapPreferences
import com.cereal.client.infrastructure.provider.AppUpdateProviderImpl
import com.cereal.client.infrastructure.provider.AuthProviderImpl
import com.cereal.client.infrastructure.provider.CheckoutProviderImpl
import com.cereal.client.infrastructure.provider.CrashReportingProviderImpl
import com.cereal.client.infrastructure.provider.DatasetFileProviderImpl
import com.cereal.client.infrastructure.provider.DiscordProviderImpl
import com.cereal.client.infrastructure.provider.LoggerProviderImpl
import com.cereal.client.infrastructure.provider.MarketplaceProviderImpl
import com.cereal.client.infrastructure.provider.NotificationProviderImpl
import com.cereal.client.infrastructure.provider.ProxyConnectionProviderImpl
import com.cereal.client.infrastructure.provider.ScriptInstallProviderImpl
import com.cereal.client.infrastructure.provider.SystemProviderImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Production bindings for domain *providers* — adapters to capabilities the app does not own
 * (remote APIs, the OS, the browser, device bridges, third-party SDKs, outbound channels).
 *
 * Distinct from [RepositoryModule], which binds entity-owning data repositories. See
 * `domain/provider/` for the interfaces and the repository-vs-provider distinction in AGENTS.md.
 *
 * The in-memory twin used by the `mock` flavor and UI tests is [InMemoryProviderModule].
 */
object ProviderModule {
    val modules: Module =
        module {
            single<SystemProvider> { SystemProviderImpl(get(), get(), get(), get(), get(), get()) }
            single<DiscordProvider> { DiscordProviderImpl(get(), get()) }
            single<LoggerProvider> { LoggerProviderImpl(get()) }
            single<AppUpdateProvider> { AppUpdateProviderImpl(get(), get(), get(), get()) }
            single<AuthProvider> { AuthProviderImpl(get(), get(), get(), get()) }
            single<NotificationProvider> {
                NotificationProviderImpl(
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            single<DatasetFileProvider> { DatasetFileProviderImpl(get(), get(), get()) }
            single<MarketplaceProvider> { MarketplaceProviderImpl(get()) }
            single<ScriptInstallProvider> { ScriptInstallProviderImpl(get(), get(), get(), get(), get()) }
            single<ProxyConnectionProvider> { ProxyConnectionProviderImpl(get(), get(), get(), get()) }
            single<CheckoutProvider> { CheckoutProviderImpl(get(), get()) }
            // BootstrapPreferences.default is resolved here rather than bound in Koin: it is a
            // path holder that deliberately re-resolves the application home on every access.
            single<CrashReportingProvider> { CrashReportingProviderImpl(BootstrapPreferences.default, get()) }
        }
}
