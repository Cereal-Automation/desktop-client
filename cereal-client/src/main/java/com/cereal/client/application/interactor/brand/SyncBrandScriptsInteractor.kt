package com.cereal.client.application.interactor.brand

import com.cereal.client.application.Interactor
import com.cereal.client.application.script.ScriptSyncManager

/**
 * Re-runs a script sync to (re)install the Brand's scripts at their latest version — used by the
 * branded gate's retry when an entitled user's scripts failed to download (see docs/adr/0004).
 * [ScriptSyncManager.sync] never throws for per-script failures; the gate re-checks installation
 * afterwards to decide whether the retry succeeded.
 */
class SyncBrandScriptsInteractor(
    private val scriptSyncManager: ScriptSyncManager,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None) {
        scriptSyncManager.sync(updateScripts = true)
    }
}
