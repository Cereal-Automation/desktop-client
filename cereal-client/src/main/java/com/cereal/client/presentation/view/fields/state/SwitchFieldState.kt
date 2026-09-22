package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cereal.client.presentation.view.fields.validator.BooleanFieldValidator

/**
 * @param initiallySet whether [initialValue] came from somewhere real — a stored configuration, a
 *   copied script instance, an imported CSV cell — rather than being the widget's own default. A
 *   switch cannot be told apart from an untouched one by its value alone, so a seeded `true` would
 *   otherwise read as "never filled in" and be dropped on the way back out.
 */
open class SwitchFieldState(
    validators: List<BooleanFieldValidator> = listOf(),
    initialValue: Boolean = false,
    initiallySet: Boolean = false,
    private val onValueChange: ((SwitchFieldState) -> Unit)? = null,
) : FormFieldState<Boolean, Boolean>(validators) {
    override val fieldValue: Boolean
        get() = isChecked

    var isChecked: Boolean by mutableStateOf(initialValue)

    /**
     * A switch always holds true or false, so its value is no evidence the user filled anything in —
     * only having been toggled is. Consumers that must tell "unset" from "set to false" (a nullable
     * boolean field inside a list) rely on this.
     */
    override val hasUserInput: Boolean
        get() = isSet

    private var isSet: Boolean = initiallySet

    fun onValueChange(isChecked: Boolean) {
        this.isChecked = isChecked
        isSet = true
        onValueChange?.invoke(this)
    }

    override fun getValidatedValue(): Boolean = fieldValue
}
