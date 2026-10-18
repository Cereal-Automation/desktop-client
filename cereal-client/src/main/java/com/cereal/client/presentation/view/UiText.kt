package com.cereal.client.presentation.view

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A message a ViewModel hands the UI: our own copy as a string resource, or text that already comes
 * worded from elsewhere (an exception, a script's validator).
 */
sealed interface UiText {
    data class Resource(
        val resource: StringResource,
    ) : UiText

    data class Raw(
        val text: String,
    ) : UiText
}

@Composable
fun UiText.text(): String =
    when (this) {
        is UiText.Resource -> stringResource(resource)
        is UiText.Raw -> text
    }
