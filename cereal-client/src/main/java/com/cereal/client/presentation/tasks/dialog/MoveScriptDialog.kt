package com.cereal.client.presentation.tasks.dialog

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
import com.cereal.client.domain.model.task.ScriptPackageGroup
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.Dialog
import com.cereal.client.presentation.view.fields.DropdownTextField
import com.cereal.client.presentation.view.fields.state.DropDownFieldState
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.cancel
import com.cereal_automation.cereal_client.generated.resources.move
import com.cereal_automation.cereal_client.generated.resources.move_to_group
import org.jetbrains.compose.resources.stringResource

class ScriptPackageGroupItem(
    val group: ScriptPackageGroup,
) {
    override fun toString(): String = group.name
}

@Composable
fun MoveScriptDialog(
    currentGroup: ScriptPackageGroup,
    availableGroups: List<ScriptPackageGroup>,
    onDismissRequest: () -> Unit,
    onMoveClicked: (ScriptPackageGroup) -> Unit,
) {
    val state =
        remember(availableGroups, currentGroup) {
            val items = availableGroups.map { ScriptPackageGroupItem(it) }
            val initialItem = items.firstOrNull { it.group.id == currentGroup.id } ?: items.firstOrNull()
            DropDownFieldState(items, initialSelectedValue = initialItem)
        }

    Dialog(
        title = stringResource(Res.string.move_to_group),
        modifier =
            Modifier
                .width(400.dp)
                .height(260.dp)
                .padding(horizontal = 6.dp),
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            DropdownTextField(
                modifier = Modifier.padding(16.dp),
                title = stringResource(Res.string.move_to_group),
                state = state,
            )

            Spacer(Modifier.weight(1.0f))

            Row(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1.0f))

                CerealButton(
                    type = CerealButtonType.Surface,
                    content = {
                        CerealText(stringResource(Res.string.cancel), style = MaterialTheme.typography.titleMedium)
                    },
                    onClick = onDismissRequest,
                )

                Spacer(modifier = Modifier.width(8.dp))

                CerealButton(
                    content = {
                        CerealText(stringResource(Res.string.move), style = MaterialTheme.typography.titleMedium)
                    },
                    onClick = {
                        val selectedGroup = state.selectedValue?.group
                        if (selectedGroup != null) {
                            onMoveClicked(selectedGroup)
                        } else {
                            onDismissRequest()
                        }
                    },
                )
            }
        }
    }
}
