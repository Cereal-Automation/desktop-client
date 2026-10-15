package com.cereal.client.presentation.error

sealed class ErrorAction {
    data class Message(
        val message: String,
        /** The failure behind [message], when known, so the view can show its localized text instead. */
        val exception: Exception? = null,
        val onDismiss: () -> Unit,
    ) : ErrorAction() {
        fun dismiss() {
            onDismiss()
        }
    }

    object None : ErrorAction()
}
