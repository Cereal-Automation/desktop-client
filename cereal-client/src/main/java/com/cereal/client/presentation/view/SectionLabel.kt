package com.cereal.client.presentation.view

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.theme.CerealTypography

/**
 * Section heading rendered in [CerealTypography.sectionLabel]. Used as the little
 * caption above grouped lists, sidebar sections, and filter menu groups. Caller is
 * responsible for casing — pass `.uppercase()` for the standard uppercase variant or
 * raw text for mixed-case group titles.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CerealTheme.colorScheme.contentSubtle,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
) {
    CerealText(
        text = text,
        modifier = modifier.padding(contentPadding),
        style = CerealTypography.sectionLabel,
        color = color,
    )
}
