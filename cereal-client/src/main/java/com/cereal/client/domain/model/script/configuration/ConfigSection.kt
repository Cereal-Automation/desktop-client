package com.cereal.client.domain.model.script.configuration

/**
 * A named group of configuration fields declared by the script. [order] is its declaration index, which
 * is the order sections render in. A [collapsed] section is hidden behind a disclosure until opened.
 */
data class ConfigSection(
    val key: String,
    val name: String,
    val collapsed: Boolean,
    val order: Int,
)
