package com.cereal.client.application.interactor.auth

import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.auth.UserAuthManager
import com.cereal.client.domain.model.user.User
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow

@OptIn(FlowPreview::class)
class GetAuthenticatedUserInteractor(
    private val userAuthManager: UserAuthManager,
) : FlowInteractor<User?, Interactor.None>() {
    override suspend fun run(params: Interactor.None): Flow<User?> = userAuthManager.getAuthenticatedUserFlow()
}
