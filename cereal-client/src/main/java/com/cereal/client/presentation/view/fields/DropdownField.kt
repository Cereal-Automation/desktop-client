package com.cereal.client.presentation.view.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.clear
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T : Any> DropdownTextField(
    title: String,
    state: DropDownFieldState<T>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isOpen = remember { mutableStateOf(false) }
    val openCloseOfDropDownList: (Boolean) -> Unit = {
        isOpen.value = it
    }
    val userSelectedString: (T) -> Unit = {
        state.onValueChange(it)
    }
    FormField(state.error, modifier = modifier) {
        Box {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.selectedValue?.toString().orEmpty(),
                    onValueChange = {
                        // No-op, dropdown selection handles this.
                    },
                    enabled = enabled,
                    singleLine = true,
                    // A blank title renders no label at all — see [TextField] for why.
                    label =
                        title.takeIf { it.isNotBlank() }?.let {
                            {
                                CerealText(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        },
                    trailingIcon =
                        if (state.showClearButton()) {
                            {
                                IconButton(onClick = { state.onClear() }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(Res.string.clear),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = state.showErrors(),
                )
                DropdownList(
                    requestToOpen = isOpen.value,
                    list = state.values.value,
                    modifier =
                        Modifier.onFocusChanged { focusState ->
                            state.onFocusChange(focusState.isFocused)
                        },
                    openCloseOfDropDownList,
                    userSelectedString,
                )
            }
            if (enabled) {
                // Transparent overlay to intercept clicks and open the dropdown. The overlay
                // stops before the trailing icon area (48 dp) so the clear button remains
                // clickable on top of the Box z-order.
                Spacer(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .padding(
                                top = 10.dp,
                                end = if (state.showClearButton()) 48.dp else 0.dp,
                            ).clickable(
                                onClick = { isOpen.value = true },
                            ),
                )
            }
        }
    }
}

@Composable
fun <T> DropdownList(
    requestToOpen: Boolean = false,
    list: List<T>,
    modifier: Modifier = Modifier,
    request: (Boolean) -> Unit,
    selectedString: (T) -> Unit,
) {
    DropdownMenu(
        modifier = modifier,
        expanded = requestToOpen,
        onDismissRequest = { request(false) },
    ) {
        list.forEach {
            DropdownMenuItem(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    request(false)
                    selectedString(it)
                },
                text = {
                    CerealText(it.toString(), modifier = Modifier.wrapContentWidth())
                },
            )
        }
    }
}
