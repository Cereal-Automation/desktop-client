package com.cereal.client.infrastructure.provider.fixtures

import com.cereal.client.infrastructure.data.datasource.network.llm.AnthropicLlmDataSource
import com.cereal.client.infrastructure.data.datasource.network.llm.LlmHttp
import com.cereal.client.infrastructure.data.datasource.network.llm.OpenAiCompatibleLlmDataSource
import com.cereal.client.infrastructure.provider.LlmProviderImpl
import okhttp3.OkHttpClient
import kotlin.time.Duration

/** The real [LlmProviderImpl] over real HTTP, retrying 429/5xx three times without waiting. */
fun llmProviderOverHttp(): LlmProviderImpl {
    val http = LlmHttp(OkHttpClient(), retryDelays = List(3) { Duration.ZERO })
    return LlmProviderImpl(AnthropicLlmDataSource(http), OpenAiCompatibleLlmDataSource(http))
}
