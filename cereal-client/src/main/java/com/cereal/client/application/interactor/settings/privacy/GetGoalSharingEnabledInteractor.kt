package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.GoalSharingRepository

class GetGoalSharingEnabledInteractor(
    private val goalSharingRepository: GoalSharingRepository,
) : Interactor<Boolean, Interactor.None>() {
    override suspend fun run(params: None): Boolean = goalSharingRepository.isEnabled()
}
