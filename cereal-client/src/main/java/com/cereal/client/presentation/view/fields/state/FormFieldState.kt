package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cereal.client.presentation.view.fields.validator.FieldValidator

abstract class FormFieldState<T, V>(
    private val validators: List<FieldValidator<T>>,
) {
    abstract val fieldValue: T?
    val isVisible = mutableStateOf(true)
    val isRequired = mutableStateOf(false)

    /**
     * Whether the user has actually entered something in this field.
     *
     * Deliberately reads the *raw* [fieldValue] rather than [getValidatedValue]: input that does not
     * parse yet (`abc` in a number field) is still input, and treating it as absent would silently
     * discard what the user typed instead of reporting it.
     */
    open val hasUserInput: Boolean
        get() =
            when (val value = fieldValue) {
                null -> false
                is String -> value.isNotBlank()
                else -> true
            }

    var error: String? by mutableStateOf(null)
        private set

    // was the TextField ever focused
    private var isFocusedDirty: Boolean = false

    abstract fun getValidatedValue(): V?

    fun onFocusChange(focused: Boolean) {
        if (focused) {
            isFocusedDirty = true
        }

        // only validate if the text was at least once focused
        if (!focused && isFocusedDirty) {
            validate()
        }
    }

    // Open so a composite field (see ListFieldState) can also validate the states nested inside it;
    // the form gates starting a script on the top-level states only.
    open fun validate(): Boolean {
        this.error = validators.firstNotNullOfOrNull { it.validate(fieldValue) }
        return this.error == null
    }

    open fun resetValidationErrors() {
        this.error = null
    }

    fun showErrors(): Boolean = this.error != null
}
