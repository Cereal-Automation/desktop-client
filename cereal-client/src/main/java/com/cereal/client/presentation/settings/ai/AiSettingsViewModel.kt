package com.cereal.client.presentation.settings.ai

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.settings.ai.ClearLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.feedback.FeedbackAction
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for Settings → AI: the provider choice and its form fields. Validation is the domain's
 * ([LlmProviderConfig]); this only routes a rejected field's message back onto that field.
 */
@OptIn(FlowPreview::class)
class AiSettingsViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getLlmProviderConfigInteractor: GetLlmProviderConfigInteractor,
    private val saveLlmProviderConfigInteractor: SaveLlmProviderConfigInteractor,
    private val clearLlmProviderConfigInteractor: ClearLlmProviderConfigInteractor,
    private val errorResolver: ErrorResolver,
) {
    /** The chosen provider; null is "None". */
    private val _provider = mutableStateOf<LlmProviderType?>(null)
    val provider: State<LlmProviderType?> = _provider

    val baseUrlState = StringTextFieldState()
    val apiKeyState = StringTextFieldState()
    val modelState = StringTextFieldState()

    private val _feedbackAction = mutableStateOf<FeedbackAction>(FeedbackAction.None)
    val feedbackAction: State<FeedbackAction> = _feedbackAction

    val errorAction: State<ErrorAction> = errorResolver.errorAction

    init {
        scope.launch(dispatcherProvider.io) {
            val result = getLlmProviderConfigInteractor(FlowInteractor.None()).first()
            withContext(dispatcherProvider.main) {
                when (result) {
                    is SuspendableResult.Success -> result.value?.let(::show)
                    is SuspendableResult.Failure -> errorResolver.setError(result.error)
                }
            }
        }
    }

    private fun show(config: LlmProviderConfig) {
        _provider.value = config.provider
        baseUrlState.text = config.baseUrl.value
        apiKeyState.text = config.apiKey?.reveal().orEmpty()
        modelState.text = config.model
    }

    /**
     * Switching provider resets the base URL to its default and clears key and model, so a key is
     * never sent to another provider's host. Re-selecting the current provider changes nothing.
     */
    fun selectProvider(provider: LlmProviderType?) {
        if (provider == _provider.value) return
        _provider.value = provider
        baseUrlState.text = provider?.defaultBaseUrl.orEmpty()
        apiKeyState.text = ""
        modelState.text = provider?.defaultModel.orEmpty()
        clearFieldErrors()
    }

    fun save() {
        clearFieldErrors()
        val provider = _provider.value
        scope.launch(dispatcherProvider.io) {
            val onResult: suspend (SuspendableResult<Unit, Exception>) -> Unit = { result ->
                withContext(dispatcherProvider.main) { handleSaveResult(result) }
            }
            if (provider == null) {
                clearLlmProviderConfigInteractor(Interactor.None(), onResult)
            } else {
                val params =
                    SaveLlmProviderConfigInteractor.Params(
                        provider = provider,
                        baseUrl = baseUrlState.text,
                        apiKey = apiKeyState.text,
                        model = modelState.text,
                    )
                saveLlmProviderConfigInteractor(params, onResult)
            }
        }
    }

    private fun handleSaveResult(result: SuspendableResult<Unit, Exception>) {
        when (result) {
            is SuspendableResult.Success -> {
                _feedbackAction.value = FeedbackAction.Success(FEEDBACK_SAVED) { _feedbackAction.value = FeedbackAction.None }
            }

            is SuspendableResult.Failure -> {
                val error = result.error
                if (error is InvalidLlmProviderConfigException) {
                    fieldState(error.field).showError(error.message)
                } else {
                    errorResolver.setError(error)
                }
            }
        }
    }

    private fun fieldState(field: LlmProviderConfig.Field) =
        when (field) {
            LlmProviderConfig.Field.BASE_URL -> baseUrlState
            LlmProviderConfig.Field.API_KEY -> apiKeyState
            LlmProviderConfig.Field.MODEL -> modelState
        }

    private fun clearFieldErrors() {
        LlmProviderConfig.Field.entries.forEach { fieldState(it).resetValidationErrors() }
    }

    private companion object {
        const val FEEDBACK_SAVED = "AI settings saved."
    }
}
