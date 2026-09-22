package com.cereal.client.presentation.view.fields.state

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.domain.model.script.configuration.Secret
import com.cereal.client.presentation.view.fields.validator.StringFieldValidator

/**
 * A text field holding a credential.
 *
 * [text] carries the plaintext, because that is what the user types and what validators inspect; the
 * masking is a *visual* transformation applied at render time. The validated value is wrapped in a
 * [Secret] so that everything downstream of the form — persistence, the task list, the state-modifier
 * view — receives the masking type rather than a bare string.
 *
 * Blank input validates to `null` rather than an empty [Secret], mirroring [StringTextFieldState]: a
 * present-but-empty credential is not valid data.
 */
open class SecretTextFieldState(
    validators: List<StringFieldValidator> = listOf(),
    initialValue: String = "",
    onValueChange: ((TextFieldState<Secret?>) -> Unit)? = null,
) : TextFieldState<Secret?>(validators, initialValue, onValueChange) {
    /**
     * Whether the plaintext is currently shown instead of dots.
     *
     * Starts hidden and is scoped to this one field, so revealing one credential never reveals
     * another. Held only here and never persisted, so it resets whenever the configuration screen is
     * rebuilt — revealing is an explicit act each time, not a remembered preference.
     */
    val isRevealed = mutableStateOf(false)

    /** Flips [isRevealed]. Toggling again re-masks the field. */
    fun toggleReveal() {
        isRevealed.value = !isRevealed.value
    }

    override fun getValidatedValue(): Secret? = if (fieldValue.isBlank()) null else Secret(fieldValue)
}
