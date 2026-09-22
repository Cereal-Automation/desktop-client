package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager

class LogoutInteractor(
    private val userAuthManager: UserAuthManager,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: None) {
        userAuthManager.deauthenticate()
    }
}
