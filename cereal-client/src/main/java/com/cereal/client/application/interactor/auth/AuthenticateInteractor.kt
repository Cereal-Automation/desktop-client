package com.cereal.client.application.interactor.auth

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.SensitiveParams
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.auth.UserAuthenticatingState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class AuthenticateInteractor(
    private val userAuthManager: UserAuthManager,
) : FlowInteractor<UserAuthenticatingState, AuthenticateInteractor.Params>() {
    override suspend fun run(params: Params): Flow<UserAuthenticatingState> = userAuthManager.authenticate(params.username, params.password)

    data class Params(
        val username: String,
        val password: String,
    ) : SensitiveParams
}
