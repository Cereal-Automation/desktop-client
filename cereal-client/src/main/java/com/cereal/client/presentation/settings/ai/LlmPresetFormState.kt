package com.cereal.client.presentation.settings.ai

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.llm.LlmConnection
import com.cereal.client.domain.model.llm.LlmModel
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.presentation.view.UiText
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The preset tiles and connection fields rendered by [LlmPresetForm]. Hosts (Settings → AI, the Home
 * setup card) own one, add their own primary action, and build interactor params from it.
 *
 * The form also loads the provider's model list ([modelList]) by itself: [DEBOUNCE_MILLIS] after an edit
 * to the base URL or key leaves a valid [LlmConnection], and at once on [show] and [retry]. Another edit
 * cancels a pending or running load.
 */
class LlmPresetFormState(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val listLlmModelsInteractor: ListLlmModelsInteractor,
    initial: LlmPreset? = null,
) {
    /** The chosen preset; null means none picked (no saved config). */
    private val _preset = mutableStateOf<LlmPreset?>(null)
    val preset: State<LlmPreset?> = _preset

    private val _customBaseUrl = mutableStateOf(false)

    /**
     * Presets with a fixed host hide the base URL; a saved config whose base URL differs from its preset's
     * (e.g. Anthropic behind a proxy) shows it, so it can be seen and edited.
     */
    val customBaseUrl: State<Boolean> = _customBaseUrl

    val baseUrlState = StringTextFieldState(onValueChange = { loadModels(debounce = true) })
    val apiKeyState = StringTextFieldState(onValueChange = { loadModels(debounce = true) })
    val modelState = StringTextFieldState()

    private val _modelList = mutableStateOf<ModelListState>(ModelListState.Idle)
    val modelList: State<ModelListState> = _modelList

    /** The loaded models; empty unless [modelList] is [ModelListState.Loaded]. */
    val models: List<LlmModel> get() = (_modelList.value as? ModelListState.Loaded)?.models.orEmpty()

    private var loadJob: Job? = null

    init {
        select(initial)
    }

    /**
     * Switching preset resets base URL and model to the preset's and clears the key and the model list,
     * so a key is never sent to another provider's host, even between presets of the same provider type.
     * Returns false (and changes nothing) when [preset] is already selected.
     */
    fun select(preset: LlmPreset?): Boolean {
        if (preset == _preset.value) return false
        _preset.value = preset
        _customBaseUrl.value = false
        baseUrlState.text = preset?.baseUrl.orEmpty()
        apiKeyState.text = ""
        modelState.text = preset?.model.orEmpty()
        clearFieldErrors()
        _modelList.value = ModelListState.Idle
        loadModels(debounce = true)
        return true
    }

    fun show(config: LlmProviderConfig) {
        val preset = LlmPreset.of(config)
        _preset.value = preset
        _customBaseUrl.value = config.baseUrl.value != preset.baseUrl
        baseUrlState.text = config.baseUrl.value
        apiKeyState.text = config.apiKey?.reveal().orEmpty()
        modelState.text = config.model
        loadModels(debounce = false)
    }

    /** Loads the list again now, e.g. after a failed load. */
    fun retry() = loadModels(debounce = false)

    fun connectionParams(): ListLlmModelsInteractor.Params? = _preset.value?.let { ListLlmModelsInteractor.Params(it.provider, baseUrlState.text, apiKeyState.text) }

    fun saveParams(): SaveLlmProviderConfigInteractor.Params? = _preset.value?.let { SaveLlmProviderConfigInteractor.Params(it.provider, baseUrlState.text, apiKeyState.text, modelState.text) }

    fun showError(
        field: LlmProviderConfig.Field,
        message: String?,
    ) = fieldState(field).showError(message)

    fun clearFieldErrors() {
        LlmProviderConfig.Field.entries.forEach { fieldState(it).resetValidationErrors() }
    }

    /** Cancels any load, then starts one when the fields make a valid connection; otherwise the list is [ModelListState.Idle]. */
    private fun loadModels(debounce: Boolean) {
        loadJob?.cancel()
        val params = usableConnectionParams()
        if (params == null) {
            _modelList.value = ModelListState.Idle
            return
        }
        loadJob =
            scope.launch(dispatcherProvider.io) {
                if (debounce) delay(DEBOUNCE_MILLIS)
                withContext(dispatcherProvider.main) { _modelList.value = ModelListState.Loading }
                listLlmModelsInteractor(params) { result ->
                    withContext(dispatcherProvider.main) {
                        _modelList.value =
                            when (result) {
                                is SuspendableResult.Success -> ModelListState.Loaded(result.value)
                                is SuspendableResult.Failure -> ModelListState.Failed(connectionFailureMessage(result.error))
                            }
                    }
                }
            }
    }

    /** The connection params when they make a valid [LlmConnection] and, for a keyed preset, include a key. */
    private fun usableConnectionParams(): ListLlmModelsInteractor.Params? {
        val preset = _preset.value ?: return null
        if (preset.needsKey && apiKeyState.text.isBlank()) return null
        val params = ListLlmModelsInteractor.Params(preset.provider, baseUrlState.text, apiKeyState.text)
        return params.takeIf { runCatching { LlmConnection.of(it.provider, it.baseUrl, it.apiKey) }.isSuccess }
    }

    private fun fieldState(field: LlmProviderConfig.Field) =
        when (field) {
            LlmProviderConfig.Field.BASE_URL -> baseUrlState
            LlmProviderConfig.Field.API_KEY -> apiKeyState
            LlmProviderConfig.Field.MODEL -> modelState
        }
}

/** Where the form's model list stands. */
sealed interface ModelListState {
    /** No valid connection yet, or nothing loaded since the preset changed. */
    data object Idle : ModelListState

    data object Loading : ModelListState

    data class Loaded(
        val models: List<LlmModel>,
    ) : ModelListState

    /** The load failed; [message] is the reason, as Test connection would word it. */
    data class Failed(
        val message: UiText,
    ) : ModelListState
}

private const val DEBOUNCE_MILLIS = 500L
