package com.cereal.client.infrastructure.data.datasource.os

import java.awt.Image

interface NotificationDataSource {
    suspend fun createTrayIcon(
        image: Image,
        title: String,
    )

    suspend fun sendNotification(
        title: String?,
        message: String,
    )
}
