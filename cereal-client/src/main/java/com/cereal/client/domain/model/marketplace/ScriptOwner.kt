package com.cereal.client.domain.model.marketplace

/**
 * The author/owner of a marketplace script.
 *
 * @property name The display name of the owner.
 * @property avatarUrl Optional URL to the owner's avatar image.
 * @property verified Whether the owner is a verified marketplace publisher.
 */
data class ScriptOwner(
    val name: String,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
)
