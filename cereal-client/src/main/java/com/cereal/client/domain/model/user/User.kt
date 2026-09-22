package com.cereal.client.domain.model.user

import com.cereal.client.domain.model.exception.InvalidUserException

data class User(
    val id: String,
    val name: String,
    val email: String,
    val encryptionKey: String,
    val accessToken: String,
    val isGuest: Boolean = false,
) {
    init {
        if (id.isBlank()) throw InvalidUserException("Id cannot be blank")
        if (accessToken.isBlank()) throw InvalidUserException("AccessToken cannot be blank")
    }
}
