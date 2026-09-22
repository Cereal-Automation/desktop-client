package com.cereal.client.domain.model.notification

/**
 * Strategy for sending notifications of a specific type.
 * @param T The type of data this strategy handles.
 */
interface NotificationStrategy<T> {
    /**
     * Sends a notification with the given data.
     * @param data The notification data to send.
     */
    suspend fun send(data: T)
}
