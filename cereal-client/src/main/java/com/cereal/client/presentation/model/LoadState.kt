package com.cereal.client.presentation.model

enum class AuthType {
    REGULAR,
    GUEST,
    GOOGLE,
    DISCORD,
}

sealed class LoadState {
    data class Error(
        val message: String,
    ) : LoadState()

    data class NotLoading(
        val success: Boolean = false,
    ) : LoadState()

    data class Loading(
        val authType: AuthType? = null,
    ) : LoadState()
}
