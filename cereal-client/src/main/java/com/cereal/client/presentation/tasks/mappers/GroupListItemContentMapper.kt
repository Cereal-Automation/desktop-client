package com.cereal.client.presentation.tasks.mappers

import com.cereal.client.application.interactor.script.ScriptInstanceInGroup
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.getScriptIdentifierValue
import com.cereal.client.presentation.view.group.GroupListItemContent

object GroupListItemContentMapper {
    fun fromScriptInstanceInGroup(scriptInstanceInGroup: ScriptInstanceInGroup): GroupListItemContent<ScriptPackageInstance> {
        val scriptName = scriptInstanceInGroup.scriptPackageInstance.definition.manifest.name
        val identifierValue =
            scriptInstanceInGroup.scriptPackageInstance.definition.mainScript.configuration
                .getScriptIdentifierValue(scriptInstanceInGroup.scriptPackageInstance.mainConfiguration)

        // Note: Warning message should be filled when there's no valid config: scriptInstanceInGroup.scriptPackageInstance.hasValidConfiguration(). Need to optimize performance first.
        return GroupListItemContent(
            scriptInstanceInGroup.scriptPackageInstance,
            badge = scriptInstanceInGroup.taskCount.toString(),
            title = scriptName,
            subTitle = identifierValue,
            warningMessage = null,
            isRunning = scriptInstanceInGroup.runningTaskCount > 0,
        )
    }
}
