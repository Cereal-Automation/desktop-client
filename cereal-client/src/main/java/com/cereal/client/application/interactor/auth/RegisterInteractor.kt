package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.application.auth.UserAuthManager

class RegisterInteractor(
    private val userAuthManager: UserAuthManager,
) : Interactor<Unit, RegisterInteractor.Params>() {
    override suspend fun run(params: Params) {
        userAuthManager.register(params.name, params.email, params.password)
    }

    data class Params(
        val name: String,
        val email: String,
        val password: String,
    ) : SensitiveParams
}
