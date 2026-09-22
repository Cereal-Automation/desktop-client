package com.cereal.client.application.interactor.brand

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.ScriptRepository
import kotlinx.coroutines.flow.first

/**
 * Resolves what a white-label build should show after login: the app, the subscription paywall, or
 * a "scripts unavailable" retry (see docs/adr/0004). Composes the subscription check
 * ([GetBrandEntitlementInteractor]) with an installed-scripts check, because a user can be entitled
 * yet have no script installed if the post-login download failed. Stock Cereal is always [Ready].
 */
class GetBrandGateStatusInteractor(
    private val applicationConfig: ApplicationConfig,
    private val getBrandEntitlementInteractor: GetBrandEntitlementInteractor,
    private val scriptRepository: ScriptRepository,
) : Interactor<BrandGateStatus, Interactor.None>() {
    override suspend fun run(params: Interactor.None): BrandGateStatus {
        if (!applicationConfig.isBranded) return BrandGateStatus.Ready

        when (getBrandEntitlementInteractor.run(Interactor.None())) {
            is BrandEntitlement.MissingSubscription -> return BrandGateStatus.NeedsSubscription
            BrandEntitlement.Entitled -> Unit // entitled — fall through to the install check
        }

        val installedScriptIds =
            scriptRepository
                .getInstalledScripts()
                .first()
                .map { it.manifest.packageName }
                .toSet()
        val notInstalled = applicationConfig.brandScriptIds.filterNot { it in installedScriptIds }

        return if (notInstalled.isEmpty()) BrandGateStatus.Ready else BrandGateStatus.ScriptsUnavailable
    }
}

/** What the branded gate should render. */
sealed class BrandGateStatus {
    /** Entitled and the Brand scripts are installed (or this is stock Cereal) — show the app. */
    data object Ready : BrandGateStatus()

    /** A required subscription is missing — show the paywall. */
    data object NeedsSubscription : BrandGateStatus()

    /** Entitled, but one or more Brand scripts are not installed — offer a retry/download. */
    data object ScriptsUnavailable : BrandGateStatus()
}
