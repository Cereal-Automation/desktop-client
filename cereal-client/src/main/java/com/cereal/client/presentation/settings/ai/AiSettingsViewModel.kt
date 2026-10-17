package com.cereal.client.presentation.settings.ai

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.FlowInteractor
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.settings.ai.ClearLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.GetLlmProviderConfigInteractor
import com.cereal.client.application.interactor.settings.ai.ListLlmModelsInteractor
import com.cereal.client.application.interactor.settings.ai.SaveLlmProviderConfigInteractor
import com.cereal.client.domain.model.exception.InvalidLlmProviderConfigException
import com.cereal.client.domain.model.exception.LlmCallException
import com.cereal.client.domain.model.llm.LlmProviderConfig
import com.cereal.client.domain.model.llm.LlmProviderType
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.feedback.FeedbackAction
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private val listLlmModelsInteractor: ListLlmModelsInteractor,
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

    /** Model ids from the provider's last successful `/models` fetch; empty means the model field is plain text. */
    private val _models = mutableStateOf<List<String>>(emptyList())
    val models: State<List<String>> = _models

    private val _connectionStatus = mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle)
    val connectionStatus: State<ConnectionStatus> = _connectionStatus

    private var fetchJob: Job? = null

    init {
        scope.launch(dispatcherProvider.io) {
            val result = getLlmProviderConfigInteractor(FlowInteractor.None()).first()
            withContext(dispatcherProvider.main) {
                when (result) {
                    is SuspendableResult.Success -> {
                        result.value?.let {
                            show(it)
                            fetchModels(silent = true)
                        }
                    }

                    is SuspendableResult.Failure -> {
                        errorResolver.setError(result.error)
                    }
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
        fetchJob?.cancel()
        _models.value = emptyList()
        _connectionStatus.value = ConnectionStatus.Idle
    }

    /** Checks the form's setup against the provider and refills the model list; saving doesn't need it. */
    fun testConnection() {
        clearFieldErrors()
        fetchModels(silent = false)
    }

    /** A [silent] fetch (on open) only fills the list: no status, no field errors. */
    private fun fetchModels(silent: Boolean) {
        val provider = _provider.value ?: return
        val params = ListLlmModelsInteractor.Params(provider, baseUrlState.text, apiKeyState.text)
        if (!silent) _connectionStatus.value = ConnectionStatus.Testing
        fetchJob?.cancel()
        fetchJob =
            scope.launch(dispatcherProvider.io) {
                listLlmModelsInteractor(params) { result ->
                    withContext(dispatcherProvider.main) {
                        when (result) {
                            is SuspendableResult.Success -> {
                                _models.value = result.value.map { it.id }
                                if (!silent) _connectionStatus.value = ConnectionStatus.Connected(result.value.size)
                            }

                            is SuspendableResult.Failure -> {
                                _models.value = emptyList()
                                if (!silent) showConnectionFailure(result.error)
                            }
                        }
                    }
                }
            }
    }

    private fun showConnectionFailure(error: Exception) {
        if (error is InvalidLlmProviderConfigException) {
            fieldState(error.field).showError(error.message)
            _connectionStatus.value = ConnectionStatus.Idle
            return
        }
        val message =
            when (error) {
                is LlmCallException.Authentication -> "Bad API key: the provider rejected it."
                is LlmCallException.Unavailable -> "Unreachable: check the base URL and your connection."
                else -> error.message.orEmpty()
            }
        _connectionStatus.value = ConnectionStatus.Failed(message)
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

/** Where Settings → AI's Test connection stands; [message] is what the form shows. */
sealed class ConnectionStatus(
    val message: String,
) {
    data object Idle : ConnectionStatus("")

    data object Testing : ConnectionStatus("Testing…")

    class Connected(
        count: Int,
    ) : ConnectionStatus("Connected · $count ${if (count == 1) "model" else "models"}")

    class Failed(
        message: String,
    ) : ConnectionStatus(message)
}
