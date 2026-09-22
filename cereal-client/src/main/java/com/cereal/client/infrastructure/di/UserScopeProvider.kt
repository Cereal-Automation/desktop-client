package com.cereal.client.infrastructure.di

import org.koin.core.scope.Scope

interface UserScopeProvider {
    val currentScope: Scope?
}
