package com.cereal.client.presentation.error

sealed class ErrorAction {
    data class Message(
        val message: String,
        val onDismiss: () -> Unit,
    ) : ErrorAction() {
        fun dismiss() {
            onDismiss()
        }
    }

    object None : ErrorAction()
}
