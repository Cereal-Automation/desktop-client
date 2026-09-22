package com.cereal.client.presentation.view.fields

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Renders every character as a bullet, exactly as `PasswordVisualTransformation` does — but is
 * deliberately **not** that type.
 *
 * Compose gates the clipboard on the transformation's identity, not on any explicit setting:
 * `TextFieldSelectionManager.isCopyAllowed` is `hasSelection && !isPassword`, where `isPassword` is
 * `visualTransformation is PasswordVisualTransformation`. Using the stock transformation therefore
 * silently disables copy on the field.
 *
 * Credential fields need copy to keep working, so the user can reuse a key in another script's
 * configuration — and anyone who can reach the clipboard can reach the reveal toggle anyway. Masking
 * through this type gives the same visual result while leaving copy enabled.
 *
 * The substitution is one character for one character, so [OffsetMapping.Identity] is correct and the
 * caret lands where the user expects.
 */
class MaskedVisualTransformation(
    private val mask: Char = BULLET,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            AnnotatedString(mask.toString().repeat(text.length)),
            OffsetMapping.Identity,
        )

    companion object {
        /** The same glyph `PasswordVisualTransformation` uses, so masked fields look consistent. */
        const val BULLET: Char = '•'
    }
}
