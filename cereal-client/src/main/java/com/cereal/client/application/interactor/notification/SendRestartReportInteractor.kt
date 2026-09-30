package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.provider.SystemProvider

/**
 * Headless only: once per boot, after sign-in, tells the global channels that headless mode
 * restarted and how many tasks resumed or couldn't resume. Later sign-ins send nothing.
 */
class SendRestartReportInteractor(
    private val taskManager: TaskManager,
    private val systemProvider: SystemProvider,
    private val sendGlobalNotificationInteractor: SendGlobalNotificationInteractor,
) : Interactor<Unit, Interactor.None>() {
    override suspend fun run(params: Interactor.None) {
        val report = taskManager.takeResumeReport() ?: return
        val failures =
            if (report.failures.isEmpty()) {
                ""
            } else {
                " Couldn't resume ${report.failures.size} task(s): ${report.failures.joinToString("; ")}."
            }
        sendGlobalNotificationInteractor.run(
            SendGlobalNotificationInteractor.Params(
                title = TITLE,
                message = "Headless mode on ${systemProvider.hostname()} restarted. Resumed ${report.resumed} task(s).$failures",
            ),
        )
    }

    companion object {
        const val TITLE = "Headless mode restarted"
    }
}
