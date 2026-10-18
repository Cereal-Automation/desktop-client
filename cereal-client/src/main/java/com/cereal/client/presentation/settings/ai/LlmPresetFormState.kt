package com.cereal.client.presentation.settings.ai

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.llm.LlmPreset
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.presentation.view.fields.state.StringTextFieldState

/**
 * The preset tiles and connection fields rendered by [LlmPresetForm]. Hosts (Settings → AI, the Home
 * setup card) own one, add their own primary action, and build interactor params from it.
 */
class LlmPresetFormState(
    initial: LlmPreset? = null,
) {
    /** The chosen preset; null means none picked (no saved config). */
    private val _preset = mutableStateOf<LlmPreset?>(null)
    val preset: State<LlmPreset?> = _preset

    val baseUrlState = StringTextFieldState()
    val apiKeyState = StringTextFieldState()
    val modelState = StringTextFieldState()

    /** Model ids from the host's last successful `/models` fetch; empty means the model field is plain text. */
    val models = mutableStateOf<List<String>>(emptyList())

    init {
        select(initial)
    }

    /**
     * Switching preset resets base URL and model to the preset's and clears the key and fetched models,
     * so a key is never sent to another provider's host, even between presets of the same provider type.
     * Returns false (and changes nothing) when [preset] is already selected.
     */
    fun select(preset: LlmPreset?): Boolean {
        if (preset == _preset.value) return false
        _preset.value = preset
        baseUrlState.text = preset?.baseUrl.orEmpty()
        apiKeyState.text = ""
        modelState.text = preset?.model.orEmpty()
        models.value = emptyList()
        clearFieldErrors()
        return true
    }

    fun show(config: LlmProviderConfig) {
        _preset.value = LlmPreset.of(config)
        baseUrlState.text = config.baseUrl.value
        apiKeyState.text = config.apiKey?.reveal().orEmpty()
        modelState.text = config.model
    }

    fun connectionParams(): ListLlmModelsInteractor.Params? = _preset.value?.let { ListLlmModelsInteractor.Params(it.provider, baseUrlState.text, apiKeyState.text) }

    fun saveParams(): SaveLlmProviderConfigInteractor.Params? = _preset.value?.let { SaveLlmProviderConfigInteractor.Params(it.provider, baseUrlState.text, apiKeyState.text, modelState.text) }

    fun showError(
        field: LlmProviderConfig.Field,
        message: String?,
    ) = fieldState(field).showError(message)

    fun clearFieldErrors() {
        LlmProviderConfig.Field.entries.forEach { fieldState(it).resetValidationErrors() }
    }

    private fun fieldState(field: LlmProviderConfig.Field) =
        when (field) {
            LlmProviderConfig.Field.BASE_URL -> baseUrlState
            LlmProviderConfig.Field.API_KEY -> apiKeyState
            LlmProviderConfig.Field.MODEL -> modelState
        }
}
