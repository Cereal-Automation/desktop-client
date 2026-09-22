package com.cereal.client.application.interactor.script

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ApplicationRepository
import net.swiftzer.semver.SemVer

/**
 * Returns the SDK version this client was built with. Used by the presentation layer to determine whether an
 * installed script requires a newer client (see [com.cereal.client.domain.model.script.isClientUpdateRequired]).
 */
class GetSdkVersionInteractor(
    private val applicationRepository: ApplicationRepository,
) : Interactor<SemVer, Interactor.None>() {
    override suspend fun run(params: Interactor.None): SemVer = applicationRepository.getSdkVersion()
}
