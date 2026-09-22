package com.cereal.client.application.interactor.app

import com.cereal.client.application.Interactor
import com.cereal.client.domain.model.app.UpdateInstallResult
import com.cereal.client.domain.provider.SystemProvider
import java.io.File

/**
 * Installs a downloaded update [Params.installer]. On Linux this self-replaces and relaunches the
 * running AppImage; elsewhere it hands the installer off to the OS. See
 * [SystemProvider.installUpdate].
 */
class InstallUpdateInteractor(
    private val systemRepository: SystemProvider,
) : Interactor<UpdateInstallResult, InstallUpdateInteractor.Params>() {
    override suspend fun run(params: Params): UpdateInstallResult = systemRepository.installUpdate(params.installer, params.expectedSha256)

    data class Params(
        val installer: File,
        /**
         * Expected SHA-256 of [installer] from the release metadata; the provider re-verifies it
         * right before execution and aborts on mismatch. Null skips re-verification (no digest
         * published for this version).
         */
        val expectedSha256: String?,
    )
}
