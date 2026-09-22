package com.cereal.client.application.interactor.settings.notifications

import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryNotificationSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the Email-settings branches of [SaveAllNotificationSettingsInteractor] that the original
 * [SaveAllNotificationSettingsInteractorTest] (Discord/Telegram/Desktop only) does not exercise.
 */
class SaveAllNotificationSettingsInteractorAdditionalTest {
    private lateinit var applicationPreferenceRepository: InMemoryNotificationSettingsRepository
    private lateinit var interactor: SaveAllNotificationSettingsInteractor

    @BeforeEach
    fun setUp() {
        applicationPreferenceRepository = InMemoryNotificationSettingsRepository()
        interactor = SaveAllNotificationSettingsInteractor(applicationPreferenceRepository)
    }

    @Test
    fun `run saves all Email settings when provided`() =
        runTest {
            interactor.run(
                SaveAllNotificationSettingsInteractor.Params(
                    emailEnabled = true,
                    emailSmtpHost = "smtp.example.com",
                    emailSmtpPort = 465,
                    emailUsername = "user@example.com",
                    emailPassword = "s3cret",
                    emailFrom = "from@example.com",
                    emailTo = "to@example.com",
                    emailUseTls = false,
                ),
            )

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.emailEnabled)
            assertEquals("smtp.example.com", settings.emailSmtpHost)
            assertEquals(465, settings.emailSmtpPort)
            assertEquals("user@example.com", settings.emailUsername)
            assertEquals("s3cret", settings.emailPassword)
            assertEquals("from@example.com", settings.emailFrom)
            assertEquals("to@example.com", settings.emailTo)
            assertEquals(false, settings.emailUseTls)
        }

    @Test
    fun `run does not touch Email settings when none are provided`() =
        runTest {
            applicationPreferenceRepository.setEmailEnabled(true)
            applicationPreferenceRepository.setEmailSmtpHost("preexisting.example.com")
            applicationPreferenceRepository.setEmailSmtpPort(2525)
            applicationPreferenceRepository.setEmailUsername("preexisting-user")
            applicationPreferenceRepository.setEmailPassword("preexisting-pass")
            applicationPreferenceRepository.setEmailFrom("preexisting-from@example.com")
            applicationPreferenceRepository.setEmailTo("preexisting-to@example.com")
            applicationPreferenceRepository.setEmailUseTls(false)

            interactor.run(SaveAllNotificationSettingsInteractor.Params(discordEnabled = true))

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(true, settings.emailEnabled)
            assertEquals("preexisting.example.com", settings.emailSmtpHost)
            assertEquals(2525, settings.emailSmtpPort)
            assertEquals("preexisting-user", settings.emailUsername)
            assertEquals("preexisting-pass", settings.emailPassword)
            assertEquals("preexisting-from@example.com", settings.emailFrom)
            assertEquals("preexisting-to@example.com", settings.emailTo)
            assertEquals(false, settings.emailUseTls)
        }

    @Test
    fun `run disables Email when set to false`() =
        runTest {
            applicationPreferenceRepository.setEmailEnabled(true)

            interactor.run(SaveAllNotificationSettingsInteractor.Params(emailEnabled = false))

            assertEquals(false, applicationPreferenceRepository.getApplicationPreferenceSettings().emailEnabled)
        }

    @Test
    fun `run saves individual Email fields without altering the others`() =
        runTest {
            applicationPreferenceRepository.setEmailSmtpHost("old.example.com")

            interactor.run(SaveAllNotificationSettingsInteractor.Params(emailSmtpPort = 25))

            val settings = applicationPreferenceRepository.getApplicationPreferenceSettings()
            assertEquals(25, settings.emailSmtpPort)
            assertEquals("old.example.com", settings.emailSmtpHost)
        }
}
