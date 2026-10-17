package com.cereal.client.domain.model.script

/** A platform capability a script declares in its manifest's `features` list. */
enum class ScriptFeature(
    val manifestValue: String,
) {
    LLM("llm"),
    ;

    companion object {
        /** Returns null for a value this client doesn't know. */
        fun fromManifestValue(value: String): ScriptFeature? = entries.firstOrNull { it.manifestValue == value }
    }
}
