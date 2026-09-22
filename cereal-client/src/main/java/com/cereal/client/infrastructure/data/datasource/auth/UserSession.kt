package com.cereal.client.infrastructure.data.datasource.auth

import com.cereal.client.application.exception.UserNotAuthenticatedException
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.di.UserScopeProvider
import io.sentry.Sentry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope

class UserSession :
    KoinComponent,
    UserScopeProvider {
    private val userFlow = MutableStateFlow<User?>(null)
    private var userScope: Scope? = null

    override val currentScope: Scope?
        get() = userScope

    fun getUserFlow(): Flow<User?> = userFlow

    suspend fun requireUser(): User = userFlow.first() ?: throw UserNotAuthenticatedException()

    suspend fun setUser(user: User?) {
        user?.let {
            openUserScope(it)

            val sentryUser =
                io.sentry.protocol.User().apply {
                    id = user.id
                    username = user.name
                    email = user.email
                }
            Sentry.setUser(sentryUser)

            userFlow.emit(user)
        } ?: run {
            userFlow.emit(null)

            Sentry.setUser(null)

            closeUserScope()
        }
    }

    private fun openUserScope(user: User) {
        closeUserScope()
        val scope = getKoin().createScope(user.id, named("UserScope"))
        scope.declare(user)
        userScope = scope
    }

    private fun closeUserScope() {
        userScope?.close()
        userScope = null
    }
}
