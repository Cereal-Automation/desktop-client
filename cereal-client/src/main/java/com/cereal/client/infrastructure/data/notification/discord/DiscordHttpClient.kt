package com.cereal.client.infrastructure.data.notification.discord

import com.cereal.client.infrastructure.data.datasource.discord.await
import com.cereal.client.infrastructure.data.notification.discord.mapper.DiscordModelMapper
import com.cereal.client.infrastructure.provider.DiscordProviderImpl
import com.cereal.sdk.component.notification.discord.model.DiscordMessage
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException

class DiscordHttpClient {
    private val logger = LoggerFactory.getLogger(DiscordProviderImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient: OkHttpClient = OkHttpClient().newBuilder().build()
    private val jsonMediaType = "application/json".toMediaTypeOrNull()

    suspend fun message(
        url: String,
        discordMessage: DiscordMessage,
        maxAttempts: Int = 3,
    ) {
        var retryAttempt = 0
        val serializableMessage =
            DiscordModelMapper.toSerializable(
                discordMessage,
            )
        val requestBody =
            json
                .encodeToString(serializableMessage)
                .toRequestBody(jsonMediaType)

        val request =
            Request
                .Builder()
                .post(requestBody)
                .url(url)
                .build()

        logger.debug("Attempting to message with $discordMessage")

        while (retryAttempt < maxAttempts) {
            retryAttempt++

            try {
                val response = httpClient.newCall(request).await()
                try {
                    if (response.body
                            .string()
                            .contains("You are being rate limited")
                    ) {
                        logger.debug("You are being rate limited, retrying...")
                    }
                } finally {
                    logger.debug("Submitted discord log record")
                    response.close()
                    break
                }
            } catch (e: IOException) {
                logger.warn("Unable to submit discord post.", e)
            }
        }
    }
}
