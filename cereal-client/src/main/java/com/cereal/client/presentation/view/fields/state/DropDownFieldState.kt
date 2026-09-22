package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cereal.client.presentation.view.fields.validator.AnyFieldValidator

open class DropDownFieldState<T : Any>(
    initialValues: List<T>,
    private val onValueChange: ((DropDownFieldState<T>) -> Unit)? = null,
    private val onImport: ((DropDownFieldState<T>) -> Unit)? = null,
    validators: List<AnyFieldValidator> = listOf(),
    initialSelectedValue: T? = null,
    private val clearable: Boolean = false,
) : FormFieldState<Any, Any?>(validators) {
    val values: MutableState<List<T>> = mutableStateOf(initialValues)

    override val fieldValue: T?
        get() = selectedValue

    var selectedValue: T? by mutableStateOf(initialSelectedValue)

    fun onValueChange(value: T) {
        this.selectedValue = value
        onValueChange?.invoke(this)
    }

    fun onClear() {
        if (!clearable) return
        selectedValue = null
        onValueChange?.invoke(this)
    }

    fun showClearButton(): Boolean = clearable && selectedValue != null

    fun showImportButton(): Boolean = onImport != null

    fun onImport() {
        onImport?.invoke(this)
    }

    override fun getValidatedValue(): Any? = fieldValue
}
