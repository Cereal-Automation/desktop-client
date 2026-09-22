package com.cereal.client.infrastructure.di.modules

import FileDownloader
import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.OperatingSystemType
import com.cereal.client.domain.service.ProxyHealthChecker
import com.cereal.client.infrastructure.data.datasource.auth.UserTokenDataSource
import com.cereal.client.infrastructure.data.datasource.csv.CsvReader
import com.cereal.client.infrastructure.data.datasource.csv.CsvWriter
import com.cereal.client.infrastructure.data.datasource.database.ArtifactDataSource
import com.cereal.client.infrastructure.data.datasource.database.DatabaseProvider
import com.cereal.client.infrastructure.data.datasource.database.DatasetDataSource
import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.database.LogEventDataSource
import com.cereal.client.infrastructure.data.datasource.database.NotificationHistoryDataSource
import com.cereal.client.infrastructure.data.datasource.database.ProxyDataSource
import com.cereal.client.infrastructure.data.datasource.database.ProxyProviderConnectorDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptInstanceDataSource
import com.cereal.client.infrastructure.data.datasource.database.ScriptPreferenceDataSource
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabaseProvider
import com.cereal.client.infrastructure.data.datasource.database.room.RoomDatabases
import com.cereal.client.infrastructure.data.datasource.database.room.mapper.DatasetMapper
import com.cereal.client.infrastructure.data.datasource.discord.DiscordRpcDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ArtifactFileDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemArtifactDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemScriptsDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.FileSystemTempDataSource
import com.cereal.client.infrastructure.data.datasource.filesystem.ScriptConfigurationDefinitionBuilder
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import com.cereal.client.infrastructure.data.datasource.network.DownloadsApiClient
import com.cereal.client.infrastructure.data.datasource.network.FakeSubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.MarsProxiesProviderDataSource
import com.cereal.client.infrastructure.data.datasource.network.MockObjectStorageDataSource
import com.cereal.client.infrastructure.data.datasource.network.OAuthDataSource
import com.cereal.client.infrastructure.data.datasource.network.ObjectStorageDataSource
import com.cereal.client.infrastructure.data.datasource.network.OkHttpProxyHealthChecker
import com.cereal.client.infrastructure.data.datasource.network.ProxyProviderDataSource
import com.cereal.client.infrastructure.data.datasource.network.RealMarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.RealObjectStorageDataSource
import com.cereal.client.infrastructure.data.datasource.network.RealSubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.SubscriptionDataSource
import com.cereal.client.infrastructure.data.datasource.network.SystemBrowserOAuthDataSource
import com.cereal.client.infrastructure.data.datasource.network.marketplace.MarketplaceApiClient
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.MarsProxiesApiClient
import com.cereal.client.infrastructure.data.datasource.os.AppImageInstaller
import com.cereal.client.infrastructure.data.datasource.os.BrowserDataSource
import com.cereal.client.infrastructure.data.datasource.os.LinuxNotificationDataSource
import com.cereal.client.infrastructure.data.datasource.os.MacAppBundleInstaller
import com.cereal.client.infrastructure.data.datasource.os.MacNotificationDataSource
import com.cereal.client.infrastructure.data.datasource.os.NotificationDataSource
import com.cereal.client.infrastructure.data.datasource.os.WindowsNotificationDataSource
import com.cereal.client.infrastructure.data.datasource.os.WindowsUpdateInstaller
import com.cereal.client.infrastructure.data.notification.discord.DiscordHttpClient
import com.cereal.client.infrastructure.data.notification.telegram.TelegramHttpClient
import com.cereal_automation.cereal_client.BuildConfig
import okhttp3.OkHttpClient
import org.koin.dsl.module

object DataSourceModule {
    val modules =
        module {
            single {
                OkHttpClient
                    .Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            }
            single { Encryption() }
            single<NotificationDataSource> {
                when (get<ApplicationConfig>().operatingSystem) {
                    OperatingSystemType.Windows -> WindowsNotificationDataSource(get())
                    OperatingSystemType.MacOS -> MacNotificationDataSource()
                    OperatingSystemType.Linux -> LinuxNotificationDataSource(get())
                }
            }
            single { DiscordHttpClient() }
            single { TelegramHttpClient() }
            single { DiscordRpcDataSource.create() }
            single { FileSystemScriptsDataSource(get(), get(), get()) }
            // Installer downloads are staged under the per-user app home directory (owner-only),
            // never the shared system temp dir — see FileSystemTempDataSource.
            single { FileSystemTempDataSource(get<ApplicationConfig>().homeDirectory) }
            single<ArtifactFileDataSource> { FileSystemArtifactDataSource(get()) }
            single { FileDownloader(sslPins = get<ApplicationConfig>().downloadsSSLPins) }
            // Database implementations
            single { RoomDatabases(get()) }

            // Database providers
            single<DatabaseProvider> { RoomDatabaseProvider(get()) }

            // Database datasources - provided by DatabaseProvider
            single<KeyValueDataSource> { get<DatabaseProvider>().getKeyValueDataSource() }
            single<ProxyDataSource> { get<DatabaseProvider>().getProxyDataSource() }
            single<ProxyProviderConnectorDataSource> { get<DatabaseProvider>().getProxyProviderConnectorDataSource() }
            single<DatasetDataSource> { get<DatabaseProvider>().getDatasetDataSource() }
            single<ScriptPreferenceDataSource> { get<DatabaseProvider>().getScriptPreferenceDataSource() }
            single<ScriptInstanceDataSource> { get<DatabaseProvider>().getScriptInstanceDataSource() }
            single<LogEventDataSource> { get<DatabaseProvider>().getLogEventDataSource() }
            single<NotificationHistoryDataSource> { get<DatabaseProvider>().getNotificationHistoryDataSource() }
            single<ArtifactDataSource> { get<DatabaseProvider>().getArtifactDataSource() }

            single { BrowserDataSource() }
            single<OAuthDataSource> {
                val browser = get<BrowserDataSource>()
                SystemBrowserOAuthDataSource(
                    config = get<ApplicationConfig>(),
                    openBrowser = { url -> browser.attemptDesktopBrowse(url) || browser.attemptXdgOpen(url) },
                )
            }
            single { AppImageInstaller() }
            single { MacAppBundleInstaller() }
            single { WindowsUpdateInstaller() }
            single { UserTokenDataSource(get()) }

            if (BuildConfig.FLAVOR == "mock") {
                single<SubscriptionDataSource> { FakeSubscriptionDataSource(get()) }
                single<ObjectStorageDataSource> { MockObjectStorageDataSource() }
            } else {
                single<SubscriptionDataSource> { RealSubscriptionDataSource(get(), get()) }
                single {
                    MarketplaceApiClient(
                        url = get<ApplicationConfig>().marketplaceBaseUrl,
                        version = get<ApplicationConfig>().versionName,
                        enableLogging = BuildConfig.IS_DEBUG,
                        tokenDataSource = get<UserTokenDataSource>(),
                        sslPins = get<ApplicationConfig>().marketplaceApiSSLPins,
                        publicKey = get<ApplicationConfig>().marketplacePublicKey,
                    )
                }
                single<MarketplaceDataSource> { RealMarketplaceDataSource(get()) }

                single {
                    MarsProxiesApiClient(
                        url = get<ApplicationConfig>().marsProxiesBaseUrl,
                        sslPins = get<ApplicationConfig>().marsProxiesApiSSLPins,
                        enableLogging = BuildConfig.IS_DEBUG,
                    )
                }
                single<ProxyProviderDataSource> { MarsProxiesProviderDataSource(get()) }

                single {
                    DownloadsApiClient(
                        baseUrl = BuildConfig.DOWNLOADS_BASE_URL,
                        publicKey = get<ApplicationConfig>().releasePublicKey,
                        sslPins = get<ApplicationConfig>().downloadsSSLPins,
                        // A white-label build pulls updates from its Brand feed; stock uses "client".
                        feedPath = get<ApplicationConfig>().updateFeedPath?.takeIf { it.isNotBlank() } ?: "client",
                    )
                }
                single<ObjectStorageDataSource> { RealObjectStorageDataSource(get()) }
            }

            factory { ScriptConfigurationDefinitionBuilder() }
            factory { CsvReader() }
            factory { CsvWriter() }
            factory { DatasetMapper() }
            single<ProxyHealthChecker> { OkHttpProxyHealthChecker() }
        }
}
