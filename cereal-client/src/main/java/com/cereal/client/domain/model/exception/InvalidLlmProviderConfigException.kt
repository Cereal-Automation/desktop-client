package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.llm.LlmProviderConfig

/** Raised when Settings → AI input can't form a valid [LlmProviderConfig]; [field] is the input to fix. */
class InvalidLlmProviderConfigException(
    val field: LlmProviderConfig.Field,
    message: String,
) : CerealException(message)
