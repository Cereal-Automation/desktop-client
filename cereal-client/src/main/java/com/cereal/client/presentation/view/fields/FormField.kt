package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealText

@Composable
fun FormField(
    textError: String? = null,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val baseTextStyle = LocalTextStyle.current
    val errorTextStyle =
        remember(errorColor, baseTextStyle) {
            baseTextStyle.copy(color = errorColor)
        }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            field()
        }
        textError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            CerealText(
                text = textError,
                modifier = Modifier.fillMaxWidth(),
                style = errorTextStyle,
            )
        }
    }
}
