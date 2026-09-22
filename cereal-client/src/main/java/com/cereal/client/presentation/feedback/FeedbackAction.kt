package com.cereal.client.presentation.feedback

sealed class FeedbackAction {
    data class Success(
        val message: String,
        val onDismiss: () -> Unit,
    ) : FeedbackAction() {
        fun dismiss() {
            onDismiss()
        }
    }

    data class Error(
        val message: String,
        val onDismiss: () -> Unit,
    ) : FeedbackAction() {
        fun dismiss() {
            onDismiss()
        }
    }

    object None : FeedbackAction()
}
