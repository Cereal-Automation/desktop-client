package com.cereal.client.application.interactor.notification

import com.cereal.client.application.Interactor
import com.cereal.client.application.task.ResumeReport
import com.cereal.client.application.task.TaskManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SendRestartReportInteractorTest {
    @Test
    fun `a long failure list is capped so the message fits a Discord post`() =
        runTest {
            val taskManager = mockk<TaskManager>()
            every { taskManager.takeResumeReport() } returns ResumeReport(0, List(50) { "Monitor #$it: " + "x".repeat(500) })
            val sent = slot<SendGlobalNotificationInteractor.Params>()
            val send = mockk<SendGlobalNotificationInteractor>()
            coEvery { send.run(capture(sent)) } returns Unit

            SendRestartReportInteractor(taskManager, mockk { every { hostname() } returns "host" }, send).run(Interactor.None())

            val message = sent.captured.message
            assertTrue(message.length < 2000, "length ${message.length}")
            assertTrue(message.contains("Couldn't resume 50 task(s)") && message.endsWith(" and 45 more."), message)
        }
}
