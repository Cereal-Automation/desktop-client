package com.cereal.client.presentation.proxy

sealed class ProxyViewState {
    sealed class DialogState {
        data object Hidden : DialogState()

        data object AddingProxyGroup : DialogState()

        class EditingProxyGroup(
            val initialValue: String,
        ) : DialogState()

        data object ImportFromFile : DialogState()

        /** Confirm bulk-delete of all proxies whose last health check failed. */
        class ConfirmDeleteFailing(
            val failedCount: Int,
        ) : DialogState()
    }
}
