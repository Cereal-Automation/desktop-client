package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.app.VersionCheckService
import com.cereal.client.application.interactor.app.CheckForUpdatesInteractor
import com.cereal.client.application.interactor.app.DownloadLatestAppVersionInteractor
import com.cereal.client.application.interactor.app.InstallUpdateInteractor
import com.cereal.client.application.interactor.artifact.ObserveArtifactsInteractor
import com.cereal.client.application.interactor.artifact.SaveArtifactToFileInteractor
import com.cereal.client.application.interactor.auth.AuthenticateGuestInteractor
import com.cereal.client.application.interactor.auth.AuthenticateInteractor
import com.cereal.client.application.interactor.auth.AuthenticateWithOAuthInteractor
import com.cereal.client.application.interactor.auth.ForgotPasswordInteractor
import com.cereal.client.application.interactor.auth.GetAuthenticatedUserInteractor
import com.cereal.client.application.interactor.auth.LogoutInteractor
import com.cereal.client.application.interactor.auth.RegisterInteractor
import com.cereal.client.application.interactor.bootstrap.BootstrapInteractor
import com.cereal.client.application.interactor.brand.GetBrandEntitlementInteractor
import com.cereal.client.application.interactor.brand.GetBrandGateStatusInteractor
import com.cereal.client.application.interactor.brand.SyncBrandScriptsInteractor
import com.cereal.client.application.interactor.customdataset.DeleteCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.customdataset.DeleteDatasetItemInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetGroupsInteractor
import com.cereal.client.application.interactor.customdataset.GetCustomDatasetsInteractor
import com.cereal.client.application.interactor.customdataset.UpdateCustomDatasetGroupInteractor
import com.cereal.client.application.interactor.files.AddCustomDatasetItemsFromFileToGroupInteractor
import com.cereal.client.application.interactor.files.AddProxiesFromFileToGroupInteractor
import com.cereal.client.application.interactor.files.DownloadDatasetTemplateFileInteractor
import com.cereal.client.application.interactor.files.OpenDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadCustomDatasetFileInteractor
import com.cereal.client.application.interactor.files.ReadListFileInteractor
import com.cereal.client.application.interactor.files.ReadProxyFileInteractor
import com.cereal.client.application.interactor.marketplace.GetMarketplaceScriptsInteractor
import com.cereal.client.application.interactor.marketplace.GetPackagesWithRunningTasksInteractor
import com.cereal.client.application.interactor.marketplace.HasRunningTasksForScriptInteractor
import com.cereal.client.application.interactor.marketplace.InstallMarketplaceScriptInteractor
import com.cereal.client.application.interactor.marketplace.IsScriptInstalledInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.notification.GetNotificationCenterLastSeenAtInteractor
import com.cereal.client.application.interactor.notification.HasNotificationChannelsConfiguredInteractor
import com.cereal.client.application.interactor.notification.MarkNotificationsSeenInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationAttemptsInteractor
import com.cereal.client.application.interactor.notification.ObserveNotificationCenterInteractor
import com.cereal.client.application.interactor.notification.ObserveUnseenNotificationCountInteractor
import com.cereal.client.application.interactor.proxy.CheckProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.CheckProxyHealthInteractor
import com.cereal.client.application.interactor.proxy.CreateProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteAllProxiesFromGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteFailedProxiesInGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.DeleteProxyInteractor
import com.cereal.client.application.interactor.proxy.GetProxiesInteractor
import com.cereal.client.application.interactor.proxy.GetProxyGroupsInteractor
import com.cereal.client.application.interactor.proxy.UpdateProxyGroupInteractor
import com.cereal.client.application.interactor.proxy.provider.ConnectProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.DisconnectProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.GetConnectedProxyProviderInteractor
import com.cereal.client.application.interactor.proxy.provider.SyncProxiesInteractor
import com.cereal.client.application.interactor.script.GetScriptCapacityInteractor
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.application.interactor.script.GetScriptsInGroupInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.application.interactor.script.ReportScriptIssueInteractor
import com.cereal.client.application.interactor.script.StartChildScriptInteractor
import com.cereal.client.application.interactor.script.StartScriptInteractor
import com.cereal.client.application.interactor.script.SyncScriptsOnScriptSelectionInteractor
import com.cereal.client.application.interactor.settings.GetApplicationPreferencesSettingsInteractor
import com.cereal.client.application.interactor.settings.OpenFileInteractor
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import com.cereal.client.application.interactor.settings.developers.ObserveShowDebugLogsInteractor
import com.cereal.client.application.interactor.settings.developers.SetDevelopmentScriptsInteractor
import com.cereal.client.application.interactor.settings.developers.SetShowDebugLogsInteractor
import com.cereal.client.application.interactor.settings.discord.SetDiscordActivityStatusEnabledInteractor
import com.cereal.client.application.interactor.settings.notifications.SaveAllNotificationSettingsInteractor
import com.cereal.client.application.interactor.settings.notifications.SendNotificationTestMessageInteractor
import com.cereal.client.application.interactor.settings.privacy.GetCrashReportingEnabledInteractor
import com.cereal.client.application.interactor.settings.privacy.SetCrashReportingEnabledInteractor
import com.cereal.client.application.interactor.settings.proxy.ObserveProxyHealthCheckIntervalInteractor
import com.cereal.client.application.interactor.settings.proxy.SetProxyHealthCheckIntervalInteractor
import com.cereal.client.application.interactor.task.ChangeScriptPackageInstanceGroupInteractor
import com.cereal.client.application.interactor.task.CreateScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.DeleteScriptInstanceInteractor
import com.cereal.client.application.interactor.task.DeleteTaskGroupInteractor
import com.cereal.client.application.interactor.task.EditScriptInstanceGroupInteractor
import com.cereal.client.application.interactor.task.GetOrCreateDefaultTaskGroupInteractor
import com.cereal.client.application.interactor.task.GetTaskGroupsInteractor
import com.cereal.client.application.interactor.task.ObserveTasksInteractor
import com.cereal.client.application.interactor.task.StartAllTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.StartTaskInteractor
import com.cereal.client.application.interactor.task.StopTaskInteractor
import com.cereal.client.application.interactor.task.StopTasksInScriptPackageInstanceInteractor
import com.cereal.client.application.interactor.task.UserInteractionDismissedInteractor
import com.cereal.client.application.script.GitHubIssueUrlBuilder
import org.koin.dsl.module

object InteractorModule {
    val modules =
        module {
            single { VersionCheckService(get(), get()) }
            factory { BootstrapInteractor(get(), get(), get(), get(), get(), get(), get()) }
            factory { OpenUrlInteractor(get()) }
            factory { OpenFileInteractor(get()) }
            factory { InstallUpdateInteractor(get()) }
            factory { DownloadLatestAppVersionInteractor(get()) }
            factory { CheckForUpdatesInteractor(get()) }
            factory { GetBrandEntitlementInteractor(get(), get()) }
            factory { GetBrandGateStatusInteractor(get(), get(), get()) }
            factory { SyncBrandScriptsInteractor(get()) }
            // Scripts
            factory { GetScriptsInteractor(get()) }
            factory { GetSdkVersionInteractor(get()) }
            factory { GetScriptCapacityInteractor(get()) }
            factory { GetScriptConfigDefinitionInteractor(get(), get()) }
            factory { GetScriptPackageInstancesByPackageNameInteractor(get()) }
            factory { StartScriptInteractor(get(), get(), get(), get(), get()) }
            factory { GetScriptsInGroupInteractor(get(), get()) }
            factory { SyncScriptsOnScriptSelectionInteractor(get()) }
            factory { StartChildScriptInteractor(get(), get(), get()) }
            factory { DeleteDatasetItemInteractor(get()) }
            factory { GitHubIssueUrlBuilder() }
            factory { ReportScriptIssueInteractor(get(), get()) }
            // Tasks
            factory { GetTaskGroupsInteractor(get()) }
            factory { GetOrCreateDefaultTaskGroupInteractor(get()) }
            factory { CreateScriptInstanceGroupInteractor(get()) }
            factory { CreateScriptInstanceGroupInteractor(get()) }
            factory { EditScriptInstanceGroupInteractor(get()) }
            factory { DeleteTaskGroupInteractor(get(), get(), get()) }
            factory { DeleteScriptInstanceInteractor(get()) }
            factory { ChangeScriptPackageInstanceGroupInteractor(get()) }
            factory { ObserveTasksInteractor(get()) }
            factory { StartTaskInteractor(get()) }
            factory { StopTaskInteractor(get()) }
            factory { StopTasksInScriptPackageInstanceInteractor(get(), get()) }
            factory { StartAllTasksInScriptPackageInstanceInteractor(get(), get()) }
            factory { UserInteractionDismissedInteractor(get()) }
            // Proxies
            factory { GetProxyGroupsInteractor(get()) }
            factory { CreateProxyGroupInteractor(get()) }
            factory { UpdateProxyGroupInteractor(get()) }
            factory { DeleteProxyGroupInteractor(get()) }
            factory { GetProxiesInteractor(get()) }
            factory { DeleteProxyInteractor(get()) }
            factory { DeleteAllProxiesFromGroupInteractor(get()) }
            factory { DeleteFailedProxiesInGroupInteractor(get()) }
            factory { CheckProxyHealthInteractor(get(), get()) }
            factory { CheckProxiesInGroupInteractor(get(), get()) }
            factory { ObserveProxyHealthCheckIntervalInteractor(get()) }
            factory { SetProxyHealthCheckIntervalInteractor(get()) }
            // Proxy provider connector
            factory { ConnectProxyProviderInteractor(get()) }
            factory { GetConnectedProxyProviderInteractor(get()) }
            factory { DisconnectProxyProviderInteractor(get()) }
            factory { SyncProxiesInteractor(get()) }
            // Settings
            factory { GetApplicationPreferencesSettingsInteractor(get(), get()) }
            factory { SetDevelopmentScriptsInteractor(get(), get()) }
            factory { SetShowDebugLogsInteractor(get()) }
            factory { ObserveShowDebugLogsInteractor(get()) }
            factory { SetDiscordActivityStatusEnabledInteractor(get(), get()) }
            factory { GetCrashReportingEnabledInteractor(get()) }
            factory { SetCrashReportingEnabledInteractor(get()) }
            factory { SendNotificationTestMessageInteractor(get(), get()) }
            factory { SaveAllNotificationSettingsInteractor(get()) }
            factory { HasNotificationChannelsConfiguredInteractor(get()) }
            // Notification center
            factory { ObserveNotificationCenterInteractor(get()) }
            factory { GetNotificationCenterLastSeenAtInteractor(get()) }
            factory { ObserveNotificationAttemptsInteractor(get()) }
            factory { ObserveUnseenNotificationCountInteractor(get(), get()) }
            factory { MarkNotificationsSeenInteractor(get()) }
            // Marketplace
            factory { GetMarketplaceScriptsInteractor(get()) }
            factory { InstallMarketplaceScriptInteractor(get(), get(), get()) }
            factory { IsScriptInstalledInteractor(get()) }
            factory { RemoveMarketplaceScriptInteractor(get(), get(), get(), get()) }
            factory { HasRunningTasksForScriptInteractor(get()) }
            factory { GetPackagesWithRunningTasksInteractor(get()) }
            // Auth
            factory { AuthenticateInteractor(get()) }
            factory { AuthenticateGuestInteractor(get()) }
            factory { AuthenticateWithOAuthInteractor(get()) }
            factory { RegisterInteractor(get()) }
            factory { GetAuthenticatedUserInteractor(get()) }
            factory { LogoutInteractor(get()) }
            factory { ForgotPasswordInteractor(get()) }
            // Files
            factory { ReadCustomDatasetFileInteractor(get()) }
            factory { ReadListFileInteractor(get()) }
            factory { ReadProxyFileInteractor(get()) }
            factory { DownloadDatasetTemplateFileInteractor(get()) }
            factory { ObserveArtifactsInteractor(get()) }
            factory { SaveArtifactToFileInteractor(get()) }
            factory { AddCustomDatasetItemsFromFileToGroupInteractor(get()) }
            factory { AddProxiesFromFileToGroupInteractor(get()) }
            // Custom dataset
            factory { DeleteCustomDatasetGroupInteractor(get()) }
            factory { GetCustomDatasetGroupsInteractor(get()) }
            factory { GetCustomDatasetsInteractor(get()) }
            factory { UpdateCustomDatasetGroupInteractor(get()) }
            factory { OpenDatasetFileInteractor(get()) }
        }
}
