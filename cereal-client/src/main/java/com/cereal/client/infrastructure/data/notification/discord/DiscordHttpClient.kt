package com.cereal.client.infrastructure.data.notification.discord

import com.cereal.client.infrastructure.data.datasource.discord.await
import com.cereal.client.infrastructure.data.notification.discord.mapper.DiscordModelMapper
import com.cereal.client.infrastructure.provider.DiscordProviderImpl
import com.cereal.sdk.component.notification.discord.model.DiscordMessage
import kotlinx.coroutines.delay
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
                val rateLimited =
                    httpClient.newCall(request).await().use { response ->
                        when {
                            response.code == HTTP_TOO_MANY_REQUESTS -> {
                                true
                            }

                            response.isSuccessful -> {
                                false
                            }

                            else -> {
                                // Permanent failure (bad payload, deleted webhook): retrying won't help.
                                logger.warn("Discord webhook rejected the message: statusCode={}", response.code)
                                false
                            }
                        }
                    }
                if (!rateLimited) {
                    logger.debug("Submitted discord log record")
                    break
                }
                logger.debug("You are being rate limited, retrying...")
                if (retryAttempt < maxAttempts) delay(RATE_LIMIT_RETRY_DELAY_MS)
            } catch (e: IOException) {
                logger.warn("Unable to submit discord post.", e)
            }
        }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429

        // Fixed back-off; switch to the retry_after Discord returns if 1s proves too short.
        const val RATE_LIMIT_RETRY_DELAY_MS = 1000L
    }
}
