package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.GoalSharingRepository

/** Takes effect straight away, also for runs already going: the reporter checks the choice again at their finish. */
class SetGoalSharingEnabledInteractor(
    private val goalSharingRepository: GoalSharingRepository,
) : Interactor<Unit, SetGoalSharingEnabledInteractor.Params>() {
    override suspend fun run(params: Params) {
        goalSharingRepository.setEnabled(params.enabled)
    }

    data class Params(
        val enabled: Boolean,
    )
}
