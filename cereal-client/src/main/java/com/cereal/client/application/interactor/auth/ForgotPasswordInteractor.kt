package com.cereal.client.application.interactor.auth

import com.cereal.client.application.Interactor
import com.cereal.client.domain.provider.AuthProvider

class ForgotPasswordInteractor(
    private val authProvider: AuthProvider,
) : Interactor<Unit, ForgotPasswordInteractor.Params>() {
    override suspend fun run(params: Params) {
        authProvider.forgotPassword(params.email)
    }

    data class Params(
        val email: String,
    )
}
