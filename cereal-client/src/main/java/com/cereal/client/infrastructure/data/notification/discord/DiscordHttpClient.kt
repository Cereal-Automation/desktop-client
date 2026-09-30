package com.cereal.client.infrastructure.data.notification.discord

import com.cereal.client.application.exception.CerealException
import com.cereal.client.infrastructure.data.datasource.discord.await
import com.cereal.client.infrastructure.data.notification.discord.mapper.DiscordModelMapper
import com.cereal.client.infrastructure.provider.DiscordProviderImpl
import com.cereal.sdk.component.notification.discord.model.DiscordMessage
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
                // Parsed here: OkHttp's own parse error quotes the URL, and the webhook URL is a secret.
                .url(url.toHttpUrlOrNull() ?: throw CerealException("The Discord webhook URL is not a valid web address."))
                .build()

        logger.debug("Attempting to message with $discordMessage")

        // Connection failures are retried; any HTTP response is final. A non-2xx one (a deleted
        // webhook's 404, a rate limit's 429) is thrown so the send is recorded as failed.
        var failure: IOException? = null
        repeat(maxAttempts) {
            try {
                httpClient.newCall(request).await().use { response ->
                    if (response.isSuccessful) return
                    throw CerealException("Discord rejected the webhook message (HTTP ${response.code}).")
                }
            } catch (e: IOException) {
                logger.warn("Unable to submit discord post.", e)
                failure = e
            }
        }
        throw failure ?: IOException("Discord could not be reached.")
    }
}
