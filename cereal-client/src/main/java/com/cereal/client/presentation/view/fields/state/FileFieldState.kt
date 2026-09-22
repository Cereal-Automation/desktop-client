package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cereal.client.presentation.view.fields.validator.FileFieldValidator
import java.io.File

open class FileFieldState(
    validators: List<FileFieldValidator> = listOf(),
    private val onValueChange: ((FileFieldState) -> Unit)? = null,
) : FormFieldState<File, File?>(validators) {
    override val fieldValue: File?
        get() = selectedFile

    var selectedFile: File? by mutableStateOf(null)

    fun onValueChange(value: File) {
        this.selectedFile = value
        onValueChange?.invoke(this)
    }

    override fun getValidatedValue(): File? = fieldValue
}
