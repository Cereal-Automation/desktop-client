package com.cereal.client.domain.model.proxy

import java.util.UUID

data class Proxy(
    val id: UUID,
    val address: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val health: ProxyHealth = ProxyHealth.Unknown,
) {
    init {
        require(address.isNotBlank()) { "Proxy address must not be blank" }
        require(port in 1..MAX_PORT) { "Port must be between 1 and $MAX_PORT" }
        // If username is provided, password should also be provided and vice versa
        require((username == null && password == null) || (username != null && password != null)) {
            "Username and password must both be provided or both be null"
        }
        username?.let {
            require(it.isNotBlank()) { "Username must not be blank if provided" }
        }
        password?.let {
            require(it.isNotBlank()) { "Password must not be blank if provided" }
        }
    }

    private companion object {
        private const val MAX_PORT = 65535
    }
}
