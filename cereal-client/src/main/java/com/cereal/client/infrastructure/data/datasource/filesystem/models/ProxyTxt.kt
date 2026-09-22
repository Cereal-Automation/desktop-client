package com.cereal.client.infrastructure.data.datasource.filesystem.models

data class ProxyTxt(
    val address: String,
    val port: Int,
    val username: String?,
    val password: String?,
)
