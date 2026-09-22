package com.cereal.client.application

enum class Environment(
    val value: String,
) {
    LOCAL("local"),
    ACCEPTANCE("acceptance"),
    PRODUCTION("production"),
    ;

    companion object {
        fun fromValue(value: String): Environment =
            entries.firstOrNull { it.value == value }
                ?: throw RuntimeException("Unknown environment provided: $value")
    }
}
