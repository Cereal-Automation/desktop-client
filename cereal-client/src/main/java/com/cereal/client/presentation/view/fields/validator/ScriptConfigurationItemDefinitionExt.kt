package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.Visibility

fun ScriptConfigurationItemDefinition.isRequired(scriptConfig: ScriptConfig): Boolean = !isNullable || stateModifier?.getVisibility(scriptConfig) == Visibility.VisibleRequired
