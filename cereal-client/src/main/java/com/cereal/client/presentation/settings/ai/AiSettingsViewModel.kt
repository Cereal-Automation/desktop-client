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
import com.cereal.client.presentation.error.ErrorAction
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.feedback.FeedbackAction
import com.cereal.client.presentation.view.UiText
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.ai_connection_bad_key
import com.cereal_automation.cereal_client.generated.resources.ai_connection_unreachable
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for Settings → AI: the preset form ([form]), Test connection, Save and Remove. Validation is
 * the domain's ([com.cereal.client.domain.model.llm.LlmProviderConfig]); this only routes a rejected
 * field's message back onto that field.
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
    val form = LlmPresetFormState()

    private val _feedbackAction = mutableStateOf<FeedbackAction>(FeedbackAction.None)
    val feedbackAction: State<FeedbackAction> = _feedbackAction

    val errorAction: State<ErrorAction> = errorResolver.errorAction

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
                            form.show(it)
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

    /** Picks a preset tile; see [LlmPresetFormState.select] for why that clears the key. */
    fun selectPreset(preset: LlmPreset) {
        if (form.select(preset)) resetConnection()
    }

    /** Deletes the saved config; no preset is selected afterwards. */
    fun remove() {
        form.select(null)
        resetConnection()
        scope.launch(dispatcherProvider.io) {
            clearLlmProviderConfigInteractor(Interactor.None()) { result ->
                withContext(dispatcherProvider.main) { handleSaveResult(result) }
            }
        }
    }

    private fun resetConnection() {
        fetchJob?.cancel()
        _connectionStatus.value = ConnectionStatus.Idle
    }

    /** Checks the form's setup against the provider and refills the model list; saving doesn't need it. */
    fun testConnection() {
        form.clearFieldErrors()
        fetchModels(silent = false)
    }

    /** A [silent] fetch (on open) only fills the list: no status, no field errors. */
    private fun fetchModels(silent: Boolean) {
        val params = form.connectionParams() ?: return
        if (!silent) _connectionStatus.value = ConnectionStatus.Testing
        fetchJob?.cancel()
        fetchJob =
            scope.launch(dispatcherProvider.io) {
                listLlmModelsInteractor(params) { result ->
                    withContext(dispatcherProvider.main) {
                        when (result) {
                            is SuspendableResult.Success -> {
                                form.models.value = result.value.map { it.id }
                                if (!silent) _connectionStatus.value = ConnectionStatus.Connected(result.value.size)
                            }

                            is SuspendableResult.Failure -> {
                                form.models.value = emptyList()
                                if (!silent) _connectionStatus.value = connectionFailure(result.error)
                            }
                        }
                    }
                }
            }
    }

    /** Field errors go onto the form; anything else becomes a [ConnectionStatus.Failed] message. */
    private fun connectionFailure(error: Exception): ConnectionStatus {
        if (error is InvalidLlmProviderConfigException) {
            form.showError(error.field, error.message)
            return ConnectionStatus.Idle
        }
        return ConnectionStatus.Failed(connectionFailureMessage(error))
    }

    fun save() {
        form.clearFieldErrors()
        val params = form.saveParams() ?: return
        scope.launch(dispatcherProvider.io) {
            saveLlmProviderConfigInteractor(params) { result ->
                withContext(dispatcherProvider.main) { handleSaveResult(result) }
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
                    form.showError(error.field, error.message)
                } else {
                    errorResolver.setError(error)
                }
            }
        }
    }

    private companion object {
        const val FEEDBACK_SAVED = "AI settings saved."
    }
}

/** The inline message for a failed `/models` call; shared with the Home setup card's Connect. */
fun connectionFailureMessage(error: Exception): UiText =
    when (error) {
        is LlmCallException.Authentication -> UiText.Resource(Res.string.ai_connection_bad_key)
        is LlmCallException.Unavailable -> UiText.Resource(Res.string.ai_connection_unreachable)
        else -> UiText.Raw(error.message.orEmpty())
    }

/** Where Settings → AI's Test connection stands. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus

    data object Testing : ConnectionStatus

    data class Connected(
        val count: Int,
    ) : ConnectionStatus

    data class Failed(
        val message: UiText,
    ) : ConnectionStatus
}
