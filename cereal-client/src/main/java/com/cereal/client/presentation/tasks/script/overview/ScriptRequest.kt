package com.cereal.client.presentation.tasks.script.overview

/** A request to add a specific entry script: a package's default entry ([variantId] null) or one of its variants. */
data class ScriptRequest(
    val packageName: String,
    val variantId: String? = null,
)
