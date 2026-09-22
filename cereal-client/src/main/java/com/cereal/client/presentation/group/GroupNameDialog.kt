package com.cereal.client.presentation.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealCard
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.Dialog
import com.cereal.client.presentation.view.fields.TextField
import com.cereal.client.presentation.view.fields.state.StringTextFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.create
import com.cereal_automation.cereal_client.generated.resources.creating_group
import com.cereal_automation.cereal_client.generated.resources.delete
import com.cereal_automation.cereal_client.generated.resources.edit
import com.cereal_automation.cereal_client.generated.resources.editing_group
import com.cereal_automation.cereal_client.generated.resources.name
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog for entering a group name, shared by the proxy, task and custom-dataset features.
 *
 * Renders in two modes driven by [onDeleteClicked]:
 * - **Add** ([onDeleteClicked] == null): titled "Creating group", a bare name field and a single
 *   "Create" button.
 * - **Edit** ([onDeleteClicked] != null): titled "Editing group", the name field wrapped in a
 *   [CerealCard], a surface-styled "Delete" button on the left and an "Edit" confirm button.
 *
 * @param onConfirmClicked invoked with the entered name when the confirm button is clicked.
 * @param initialValue pre-filled name, used in edit mode.
 * @param onDeleteClicked when provided, switches the dialog to edit mode and renders a delete button.
 */
@Composable
fun GroupNameDialog(
    onConfirmClicked: (value: String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    onDeleteClicked: (() -> Unit)? = null,
) {
    val isEditMode = onDeleteClicked != null
    val textFieldState = remember { StringTextFieldState(initialValue = initialValue) }
    Dialog(
        title = stringResource(if (isEditMode) Res.string.editing_group else Res.string.creating_group),
        modifier =
            modifier
                .width(400.dp)
                .height(220.dp)
                .padding(horizontal = 6.dp),
        onDismissRequest = {
            onDismissRequest()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val nameField: @Composable () -> Unit = {
                TextField(
                    modifier = Modifier.padding(8.dp),
                    title = stringResource(Res.string.name),
                    state = textFieldState,
                )
            }

            if (isEditMode) {
                CerealCard(modifier = Modifier.padding(8.dp)) {
                    nameField()
                }
            } else {
                nameField()
            }

            Row(Modifier.padding(horizontal = 8.dp)) {
                if (onDeleteClicked != null) {
                    CerealButton(
                        type = CerealButtonType.Surface,
                        content = {
                            CerealText(stringResource(Res.string.delete), style = MaterialTheme.typography.titleMedium)
                        },
                        onClick = {
                            onDeleteClicked()
                        },
                    )
                }

                Spacer(Modifier.weight(1.0f))

                CerealButton(
                    content = {
                        CerealText(
                            stringResource(if (isEditMode) Res.string.edit else Res.string.create),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    onClick = {
                        onConfirmClicked(textFieldState.text)
                    },
                )
            }
        }
    }
}
