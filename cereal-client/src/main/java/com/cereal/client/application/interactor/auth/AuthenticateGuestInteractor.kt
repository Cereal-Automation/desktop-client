package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager

class AuthenticateGuestInteractor(
    private val userAuthManager: UserAuthManager,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: None) {
        userAuthManager.authenticateGuest().collect { _ ->
            // Ignore values
        }
    }
}
