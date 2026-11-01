package com.cereal.client.presentation.settings.ai

import com.cereal.client.domain.model.llm.LlmModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A model as [ModelPicker] shows it; [date] is `yyyy-MM-dd`, or null when the list doesn't date it. */
data class LlmModelUiModel(
    val id: String,
    val displayName: String?,
    val free: Boolean,
    val date: String?,
)

fun LlmModel.toUiModel(
    preset: LlmPreset?,
    zone: ZoneId = ZoneId.systemDefault(),
) = LlmModelUiModel(
    id = id,
    displayName = displayName,
    free = preset?.showsFree(this) == true,
    date = created?.let { DateTimeFormatter.ISO_LOCAL_DATE.withZone(zone).format(it) },
)
