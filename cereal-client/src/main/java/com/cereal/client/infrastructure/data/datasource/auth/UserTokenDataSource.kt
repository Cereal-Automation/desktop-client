package com.cereal.client.infrastructure.data.datasource.auth

import com.cereal.client.infrastructure.data.datasource.database.KeyValueDataSource
import com.cereal.client.infrastructure.data.datasource.network.marketplace.interceptor.AuthorizationInterceptor
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.flow.first

class UserTokenDataSource(
    private val keyValueDataSource: KeyValueDataSource,
) : AuthorizationInterceptor.TokenDataSource {
    private var currentToken: String? = null

    override fun getToken(): String? = currentToken

    suspend fun saveToken(token: String?) {
        currentToken = token
        if (token == null) {
            // Explicitly delete the persisted key so the in-memory cache is also cleared
            // by the datasource, preventing heap dumps from retaining the token past sign-out.
            keyValueDataSource.deleteValueByKey(ApplicationPreferenceKey.UserAuthenticationToken.key)
        } else {
            keyValueDataSource.setStringByKey(ApplicationPreferenceKey.UserAuthenticationToken.key, token)
        }
    }

    suspend fun loadToken(): String? {
        val token =
            keyValueDataSource
                .getStringByKey(ApplicationPreferenceKey.UserAuthenticationToken.key)
                .first()
        currentToken = token
        return token
    }
}
