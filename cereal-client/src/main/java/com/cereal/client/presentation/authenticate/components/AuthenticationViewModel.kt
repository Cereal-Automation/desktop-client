package com.cereal.client.presentation.authenticate.components

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.settings.OpenUrlInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AuthenticationViewModel(
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val openUrlInteractor: OpenUrlInteractor,
    private val applicationConfig: ApplicationConfig,
) {
    fun marketplace() {
        coroutineScope.launch(dispatcherProvider.io) {
            val params = OpenUrlInteractor.Params(applicationConfig.marketplaceSearchScriptsUrl)
            openUrlInteractor(params) { _ -> }
        }
    }

    fun privacyPolicy() {
        coroutineScope.launch(dispatcherProvider.io) {
            val params = OpenUrlInteractor.Params(applicationConfig.privacyPolicyUrl)
            openUrlInteractor(params) { _ -> }
        }
    }

    fun support() {
        coroutineScope.launch(dispatcherProvider.io) {
            val params = OpenUrlInteractor.Params(applicationConfig.discordUrl)
            openUrlInteractor(params) { _ -> }
        }
    }

    fun status() {
        coroutineScope.launch(dispatcherProvider.io) {
            val params = OpenUrlInteractor.Params(applicationConfig.statusUrl)
            openUrlInteractor(params) { _ -> }
        }
    }
}
