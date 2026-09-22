package com.cereal.client.domain.model.user

import com.cereal.client.domain.model.script.ScriptEntitlement

data class Subscription(
    val id: String,
    val entitlement: ScriptEntitlement,
)
