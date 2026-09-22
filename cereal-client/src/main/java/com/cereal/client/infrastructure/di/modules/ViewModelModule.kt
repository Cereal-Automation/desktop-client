package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.model.datasets.DatasetType
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.authenticate.components.AuthenticationViewModel
import com.cereal.client.presentation.authenticate.forgotpassword.ForgotPasswordViewModel
import com.cereal.client.presentation.authenticate.login.LoginViewModel
import com.cereal.client.presentation.authenticate.registration.RegistrationViewModel
import com.cereal.client.presentation.bootstrap.BootstrapViewModel
import com.cereal.client.presentation.brand.BrandPaywallViewModel
import com.cereal.client.presentation.customdataset.CustomDatasetViewModel
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.main.MainViewModel
import com.cereal.client.presentation.marketplace.MarketplaceViewModel
import com.cereal.client.presentation.marketplace.ScriptDetailViewModel
import com.cereal.client.presentation.myscripts.MyScriptsViewModel
import com.cereal.client.presentation.navigation.MenuReselectionCoordinator
import com.cereal.client.presentation.notification.NotificationCenterViewModel
import com.cereal.client.presentation.profile.ProfileViewModel
import com.cereal.client.presentation.proxy.ProxyViewModel
import com.cereal.client.presentation.proxy.provider.ProxyProviderViewModel
import com.cereal.client.presentation.proxy.provider.SyncProxiesViewModel
import com.cereal.client.presentation.settings.ApplicationSettingsViewModel
import com.cereal.client.presentation.tasks.TasksActionHandler
import com.cereal.client.presentation.tasks.TasksListObserver
import com.cereal.client.presentation.tasks.TasksViewModel
import com.cereal.client.presentation.tasks.dialog.ImportFromFileViewModel
import com.cereal.client.presentation.tasks.dialog.ViewConfigurationViewModel
import com.cereal.client.presentation.tasks.script.overview.ScriptSelectionViewModel
import com.cereal.client.presentation.tasks.script.overview.configuration.ScriptConfigurationViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

object ViewModelModule {
    val modules =
        module {
            single { MenuReselectionCoordinator() }
            factory { MainViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
            factory { (scope: CoroutineScope, onStartNewInstance: Function1<String, Unit>) ->
                MarketplaceViewModel(scope, get(), get(), get(), onStartNewInstance)
            }
            factory { (scope: CoroutineScope, onStartNewInstance: Function1<String, Unit>) ->
                ScriptDetailViewModel(scope, get(), get(), get(), get(), get(), get(), get(), get(), get(), onStartNewInstance)
            }
            factory { (scope: CoroutineScope) ->
                MyScriptsViewModel(
                    scope = scope,
                    dispatcherProvider = get(),
                    getScriptsInteractor = get(),
                    getSdkVersionInteractor = get(),
                    getPackagesWithRunningTasksInteractor = get(),
                    removeMarketplaceScriptInteractor = get(),
                    errorResolver = get(),
                )
            }
            factory { (scope: CoroutineScope) -> LoginViewModel(scope, get(), get(), get(), get(), get()) }
            factory { RegistrationViewModel(get(), get(), get()) }
            factory { ForgotPasswordViewModel(get(), get()) }
            factory { (scope: CoroutineScope) -> AuthenticationViewModel(scope, get(), get(), get()) }
            factory { (scope: CoroutineScope) ->
                BootstrapViewModel(
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory {
                TasksActionHandler(
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory {
                TasksListObserver(
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory {
                TasksViewModel(
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope) ->
                ApplicationSettingsViewModel(
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope) ->
                ProxyViewModel(
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope) ->
                ProxyProviderViewModel(
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope) ->
                SyncProxiesViewModel(
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope) ->
                CustomDatasetViewModel(
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            // Koin parameter holder unpacks four injected values for this factory.
            @Suppress("DestructuringDeclarationWithTooManyEntries")
            factory { (scope: CoroutineScope, scriptPackageGroup: ScriptPackageGroup, initialScriptPackageInstance: ScriptPackageInstance?, initialPublicIdentifier: String?) ->
                ScriptSelectionViewModel(
                    scope,
                    scriptPackageGroup,
                    initialScriptPackageInstance,
                    initialPublicIdentifier,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope, scriptPackageInstance: ScriptPackageInstance) ->
                ViewConfigurationViewModel(
                    scope,
                    scriptPackageInstance,
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope) -> ProfileViewModel(scope, get(), get(), get()) }
            factory { (scope: CoroutineScope) ->
                NotificationCenterViewModel(
                    scope = scope,
                    dispatcherProvider = get(),
                    observeNotificationCenterInteractor = get(),
                    observeNotificationAttemptsInteractor = get(),
                    getNotificationCenterLastSeenAtInteractor = get(),
                    markNotificationsSeenInteractor = get(),
                    observeTasksInteractor = get(),
                    errorResolver = get(),
                )
            }
            factory { (scope: CoroutineScope) -> BrandPaywallViewModel(scope, get(), get(), get(), get(), get()) }
            factory { ErrorResolver() }
            factory { (scope: CoroutineScope, scriptPackage: ScriptPackage, initialScriptPackageInstance: ScriptPackageInstance?) ->
                ScriptConfigurationViewModel(
                    scriptPackage,
                    initialScriptPackageInstance,
                    scope,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
            factory { (scope: CoroutineScope, datasetType: DatasetType) ->
                ImportFromFileViewModel(
                    scope,
                    datasetType,
                    get(),
                    get(),
                    get(),
                    get(),
                )
            }
        }
}
