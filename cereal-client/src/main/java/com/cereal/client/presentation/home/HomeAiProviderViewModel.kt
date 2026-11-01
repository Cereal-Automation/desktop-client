package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.interactor.settings.ai.ConnectLlmProviderInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.settings.ai.LlmPreset
import com.cereal.client.presentation.settings.ai.LlmPresetFormState
import com.cereal.client.presentation.settings.ai.connectionFailureMessage
import com.cereal.client.presentation.view.UiText
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The configured AI provider as Home's "Using …" line shows it. */
data class AiProviderUiModel(
    val preset: LlmPreset,
    val model: String,
)

sealed interface HomeAiProviderState {
    /** Not read yet. */
    data object Loading : HomeAiProviderState

    data object None : HomeAiProviderState

    data class Configured(
        val provider: AiProviderUiModel,
    ) : HomeAiProviderState
}

/**
 * Home's AI provider: read when Home opens (so a removal in Settings shows on return), and the setup
 * card's preset form and Connect when there is none.
 */
@OptIn(FlowPreview::class)
class HomeAiProviderViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    getLlmProviderConfigInteractor: GetLlmProviderConfigInteractor,
    private val connectLlmProviderInteractor: ConnectLlmProviderInteractor,
    listLlmModelsInteractor: ListLlmModelsInteractor,
    private val errorResolver: ErrorResolver,
) {
    private val _state = mutableStateOf<HomeAiProviderState>(HomeAiProviderState.Loading)
    val state: State<HomeAiProviderState> = _state

    /** The setup card's preset form; Gemini's free tier is pre-selected. */
    val setup = LlmPresetFormState(scope, dispatcherProvider, listLlmModelsInteractor, initial = LlmPreset.GEMINI)

    private val _connectError = mutableStateOf<UiText?>(null)
    val connectError: State<UiText?> = _connectError

    private val _isConnecting = mutableStateOf(false)
    val isConnecting: State<Boolean> = _isConnecting

    val errorAction = errorResolver.errorAction

    init {
        scope.launch(dispatcherProvider.io) {
            val result = getLlmProviderConfigInteractor(FlowInteractor.None()).first()
            withContext(dispatcherProvider.main) {
                when (result) {
                    is SuspendableResult.Success -> show(result.value)
                    is SuspendableResult.Failure -> errorResolver.setError(result.error)
                }
            }
        }
    }

    private fun show(config: LlmProviderConfig?) {
        _state.value =
            config?.let { HomeAiProviderState.Configured(AiProviderUiModel(LlmPreset.of(it), it.model)) } ?: HomeAiProviderState.None
    }

    fun onSelectPreset(preset: LlmPreset) {
        if (setup.select(preset)) _connectError.value = null
    }

    /** Tests the connection, then saves; nothing is saved when the test fails. */
    fun onConnect() {
        val form = setup.saveParams() ?: return
        setup.markSaved()
        val params = ConnectLlmProviderInteractor.Params(form.provider, form.baseUrl, form.apiKey, form.model)
        setup.clearFieldErrors()
        _connectError.value = null
        _isConnecting.value = true
        scope.launch(dispatcherProvider.io) {
            connectLlmProviderInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    _isConnecting.value = false
                    when (result) {
                        is SuspendableResult.Success -> {
                            show(result.value)
                        }

                        is SuspendableResult.Failure -> {
                            val error = result.error
                            if (error is InvalidLlmProviderConfigException) {
                                setup.showError(error.field, error.message)
                            } else {
                                _connectError.value = connectionFailureMessage(error)
                            }
                        }
                    }
                }
            }
        }
    }
}
