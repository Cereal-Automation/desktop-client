package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.interactor.notification.SendNotificationFromScriptInstanceInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.cereal.sdk.component.notification.Notification as SdkNotification
import com.cereal.sdk.component.notification.discord.model.DiscordEmbed as SdkDiscordEmbed
import com.cereal.sdk.component.notification.discord.model.DiscordMessage as SdkDiscordMessage
import com.cereal.sdk.component.notification.discord.model.embed.FieldEmbed as SdkFieldEmbed

class NotificationComponentImplTest {
    private val interactor = mockk<SendNotificationFromScriptInstanceInteractor>(relaxed = true)
    private val scriptPackageInstance = mockk<ScriptPackageInstance>(relaxed = true)

    private val component =
        NotificationComponentImpl(
            sendNotificationFromScriptInstanceInteractor = interactor,
            scriptPackageInstance = scriptPackageInstance,
            taskId = "task-1",
        )

    private fun discordNotificationWithField(fieldName: String): SdkNotification =
        SdkNotification(
            "Title",
            "Message",
            SdkDiscordMessage(
                username = "bot",
                content = "content",
                avatarUrl = null,
                tts = null,
                embeds =
                    listOf(
                        SdkDiscordEmbed(
                            title = "Embed",
                            type = null,
                            description = null,
                            url = null,
                            timestamp = null,
                            color = null,
                            footer = null,
                            image = null,
                            thumbnail = null,
                            video = null,
                            provider = null,
                            author = null,
                            fields = listOf(SdkFieldEmbed(fieldName, "value", false)),
                        ),
                    ),
                webhookUrl = "https://discord.example.com/webhook",
            ),
        )

    @Test
    fun `sendNotification re-raises a blank embed field-name invariant as a CerealException`() =
        runTest {
            // A script-supplied Discord embed with a blank field name violates the domain FieldEmbed
            // invariant (IllegalArgumentException). It must surface as an expected CerealException so
            // TaskExecutor shows it to the user instead of reporting a crash to Sentry (CEREAL-CLIENT-3H).
            val exception =
                assertFailsWith<CerealException> {
                    component.sendNotification(discordNotificationWithField(fieldName = ""))
                }
            assertEquals("Field name cannot be blank", exception.message)

            // The mapping fails before the protected interactor boundary, so nothing is dispatched.
            coVerify(exactly = 0) { interactor.invoke(any(), any()) }
        }

    @Test
    fun `sendNotification delegates a valid notification to the interactor`() =
        runTest {
            component.sendNotification(discordNotificationWithField(fieldName = "Price"))

            coVerify(exactly = 1) { interactor.invoke(any(), any()) }
        }
}
