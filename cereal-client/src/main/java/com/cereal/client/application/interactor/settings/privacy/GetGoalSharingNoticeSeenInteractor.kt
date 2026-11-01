package com.cereal.client.application.interactor.settings.privacy

import com.cereal.client.application.Interactor
import com.cereal.client.domain.repository.GoalSharingRepository

class GetGoalSharingNoticeSeenInteractor(
    private val goalSharingRepository: GoalSharingRepository,
) : Interactor<Boolean, Interactor.None>() {
    override suspend fun run(params: None): Boolean = goalSharingRepository.isNoticeSeen()
}
