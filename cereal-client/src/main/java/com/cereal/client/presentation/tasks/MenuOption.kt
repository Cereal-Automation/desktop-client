package com.cereal.client.presentation.tasks

import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.delete
import com.cereal_automation.cereal_client.generated.resources.duplicate
import com.cereal_automation.cereal_client.generated.resources.edit
import com.cereal_automation.cereal_client.generated.resources.move_to_group
import com.cereal_automation.cereal_client.generated.resources.view_full_configuration
import org.jetbrains.compose.resources.StringResource

enum class MenuOption(
    val stringResource: StringResource,
) {
    DELETE(Res.string.delete),
    VIEW_CONFIGURATION(Res.string.view_full_configuration),
    EDIT(Res.string.edit),
    DUPLICATE(Res.string.duplicate),
    MOVE_TO_GROUP(Res.string.move_to_group),
}
