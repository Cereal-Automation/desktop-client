package com.cereal.client.presentation.view.fields.validator

interface FieldValidator<T> {
    /**
     * @return an error message when validation failed or else null.
     */
    fun validate(value: T?): String?
}
