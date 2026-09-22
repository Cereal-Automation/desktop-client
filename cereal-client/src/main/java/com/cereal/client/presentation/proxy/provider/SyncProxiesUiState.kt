package com.cereal.client.presentation.proxy.provider

import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxySyncConfig

/** Which step of the sync wizard is showing. */
enum class SyncWizardStep {
    CONFIGURE,
    SYNCING,
    DONE,
}

/** Whether the synced proxies go into a new group or an existing one. */
enum class SyncTargetMode {
    NEW,
    EXISTING,
}

/** One existing proxy group offered as a sync target. */
data class SyncTargetGroupUiModel(
    val id: String,
    val name: String,
    val proxyCount: Long,
)

/** Outcome shown on the Done step. */
data class SyncDoneUiModel(
    val syncedCount: Int,
    val groupName: String,
    val newGroup: Boolean,
    // True once a background health check has been kicked off for the synced group. The Done step
    // surfaces this instead of fabricating healthy/unreachable counts that haven't been computed yet.
    val healthCheckRunning: Boolean = false,
)

/**
 * State of the "Sync new proxies" wizard modal. [Closed] hides it; [Open] carries the full Configure
 * form plus the [step] state machine (configure → syncing → done, with an inline [failed] flag).
 */
sealed interface SyncWizardState {
    data object Closed : SyncWizardState

    /**
     * @param country ISO 3166-1 alpha-2 code of the selected country.
     * @param state Full US state name, or null when none / not applicable.
     * @param city Free-text city, or null when blank.
     * @param session Sticky (primary) or rotating.
     * @param count Sticky endpoint count (10..2000); ignored for rotating.
     * @param targetMode New group vs existing group.
     * @param newGroupName Name typed for a new group.
     * @param selectedGroupId Id of the chosen existing group, or null when none picked yet.
     * @param existingGroups The existing proxy groups the user can sync into.
     * @param zeroTraffic Whether the connected account reports 0 GB of available traffic.
     * @param failed Whether the last sync attempt failed (shows an inline retry message).
     * @param done Set once a sync completes; drives the Done step summary.
     */
    data class Open(
        val step: SyncWizardStep,
        val country: String,
        val state: String?,
        val city: String?,
        val session: ProxySession,
        val count: Int,
        val targetMode: SyncTargetMode,
        val newGroupName: String,
        val selectedGroupId: String?,
        val existingGroups: List<SyncTargetGroupUiModel>,
        val zeroTraffic: Boolean,
        val failed: Boolean,
        val done: SyncDoneUiModel?,
    ) : SyncWizardState {
        /** Whether the Configure form is currently submittable. */
        val canSync: Boolean
            get() =
                count in ProxySyncConfig.MIN_COUNT..ProxySyncConfig.MAX_COUNT &&
                    when (targetMode) {
                        SyncTargetMode.NEW -> newGroupName.isNotBlank()
                        SyncTargetMode.EXISTING -> selectedGroupId != null
                    }
    }
}
