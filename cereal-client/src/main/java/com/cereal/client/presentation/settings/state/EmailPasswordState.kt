package com.cereal.client.presentation.settings.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator

class EmailPasswordState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator()))
