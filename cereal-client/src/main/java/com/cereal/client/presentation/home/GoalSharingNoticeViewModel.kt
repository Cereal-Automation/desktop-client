package com.cereal.client.presentation.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.Interactor
import com.cereal.client.application.interactor.settings.privacy.GetGoalSharingNoticeSeenInteractor
import com.cereal.client.application.interactor.settings.privacy.MarkGoalSharingNoticeSeenInteractor
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.util.InteractorRunner
import kotlinx.coroutines.CoroutineScope

/** The one-time Home notice that goal sharing is on, until the user dismisses it or opens Privacy settings. */
class GoalSharingNoticeViewModel(
    scope: CoroutineScope,
    dispatcherProvider: CoroutinesDispatcherProvider,
    getGoalSharingNoticeSeenInteractor: GetGoalSharingNoticeSeenInteractor,
    private val markGoalSharingNoticeSeenInteractor: MarkGoalSharingNoticeSeenInteractor,
    errorResolver: ErrorResolver,
) {
    private val interactorRunner = InteractorRunner(scope, dispatcherProvider, errorResolver)

    private val _visible = mutableStateOf(false)
    val visible: State<Boolean> = _visible

    init {
        interactorRunner.launch(getGoalSharingNoticeSeenInteractor, Interactor.None()) { seen -> _visible.value = !seen }
    }

    fun dismiss() {
        _visible.value = false
        interactorRunner.launch(markGoalSharingNoticeSeenInteractor, Interactor.None()) {}
    }
}
