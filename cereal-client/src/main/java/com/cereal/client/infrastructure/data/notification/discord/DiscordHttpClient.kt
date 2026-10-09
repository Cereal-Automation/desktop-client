package com.cereal.client.infrastructure.data.notification.discord

import com.cereal.client.application.exception.CerealException
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

        var lastFailure: Exception? = null
        repeat(maxAttempts) { attempt ->
            val retryAfterMs =
                try {
                    httpClient.newCall(request).await().use { response ->
                        when {
                            response.isSuccessful -> {
                                logger.debug("Submitted discord log record")
                                return
                            }

                            response.code == HTTP_TOO_MANY_REQUESTS -> {
                                logger.warn("Discord rate limited the webhook, retrying...")
                                lastFailure = CerealException("Discord rate limited the webhook (HTTP 429).")
                                retryAfterMillis(response.header("Retry-After"))
                            }

                            else -> {
                                throw CerealException("Discord webhook rejected the message (HTTP ${response.code}).")
                            }
                        }
                    }
                } catch (e: IOException) {
                    logger.warn("Unable to submit discord post.", e)
                    lastFailure = e
                    0L
                }
            if (attempt < maxAttempts - 1) delay(retryAfterMs)
        }
        throw CerealException("Unable to deliver Discord message after $maxAttempts attempts.", lastFailure)
    }

    private fun retryAfterMillis(header: String?): Long =
        header
            ?.toDoubleOrNull()
            ?.let { (it * 1000).toLong().coerceIn(0L, MAX_RETRY_AFTER_MS) }
            ?: DEFAULT_RETRY_AFTER_MS

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DEFAULT_RETRY_AFTER_MS = 1000L
        const val MAX_RETRY_AFTER_MS = 30_000L
    }
}
