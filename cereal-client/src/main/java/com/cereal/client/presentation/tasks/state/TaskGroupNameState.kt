package com.cereal.client.presentation.tasks.state

import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal.client.presentation.view.fields.validator.RequiredStringFieldValidator

class TaskGroupNameState : StringTextFieldState(validators = listOf(RequiredStringFieldValidator("Group name can not be empty or blank.")))
