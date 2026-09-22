package com.cereal.client.application.interactor.app

import com.cereal.client.application.Interactor
import com.cereal.client.application.app.UpdateCheckResult
import com.cereal.client.application.app.VersionCheckService

class CheckForUpdatesInteractor(
    private val versionCheckService: VersionCheckService,
) : Interactor<UpdateCheckResult, Interactor.None>() {
    override suspend fun run(params: Interactor.None): UpdateCheckResult = versionCheckService.checkForUpdates()
}
