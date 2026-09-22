package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.application.script.GitHubIssueUrlBuilder
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.provider.SystemProvider

class ReportScriptIssueInteractor(
    private val systemRepository: SystemProvider,
    private val gitHubIssueUrlBuilder: GitHubIssueUrlBuilder,
) : Interactor<Unit, ReportScriptIssueInteractor.Params>() {
    override suspend fun run(params: Params) {
        val manifest = params.scriptPackageInstance.definition.manifest
        val supportUrl = manifest.supportUrl ?: return
        val url =
            gitHubIssueUrlBuilder.build(
                supportUrl = supportUrl,
                scriptName = manifest.name,
                versionCode = manifest.versionCode,
                errorMessage = params.errorMessage,
                stackTrace = params.stackTrace,
            )
        systemRepository.browser(url)
    }

    data class Params(
        val scriptPackageInstance: ScriptPackageInstance,
        val errorMessage: String,
        val stackTrace: String?,
    )
}
