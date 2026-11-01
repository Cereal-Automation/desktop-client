package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.GoalSharingRepository

class MarkGoalSharingNoticeSeenInteractor(
    private val goalSharingRepository: GoalSharingRepository,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: None) {
        goalSharingRepository.setNoticeSeen()
    }
}
