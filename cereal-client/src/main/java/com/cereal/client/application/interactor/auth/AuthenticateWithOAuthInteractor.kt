package com.cereal.client.application.interactor.auth

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.application.auth.UserAuthenticatingState
import com.cereal.client.domain.model.auth.OAuthProvider
import com.cereal.client.domain.model.auth.PastedSignIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class AuthenticateWithOAuthInteractor(
    private val userAuthManager: UserAuthManager,
) : FlowInteractor<UserAuthenticatingState, AuthenticateWithOAuthInteractor.Params>() {
    override suspend fun run(params: Params): Flow<UserAuthenticatingState> = userAuthManager.authenticateWith(params.provider, params.pastedSignIn)

    data class Params(
        val provider: OAuthProvider,
        val pastedSignIn: PastedSignIn? = null,
    )
}
