package com.cereal.client.domain.model.notification

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmailNotificationDataTest {
    private val validFrom = "sender@example.com"
    private val validSmtpHost = "smtp.example.com"
    private val validSmtpPort = 587

    private fun createEmail(
        to: String? = "recipient@example.com",
        from: String? = validFrom,
        subject: String = "S",
        body: String = "B",
        smtpHost: String? = validSmtpHost,
        smtpPort: Int? = validSmtpPort,
        username: String? = null,
        password: String? = null,
        useTls: Boolean? = true,
    ) = EmailNotificationData(
        to = to ?: "recipient@example.com", // Dummy default if null wasn't intended to be tested as null
        from = from ?: validFrom,
        subject = subject,
        body = body,
        smtpHost = smtpHost ?: validSmtpHost,
        smtpPort = smtpPort ?: validSmtpPort,
        username = username,
        password = password,
        useTls = useTls,
    )

    // Valid Email Address Tests
    @Test
    fun `should create EmailNotificationData with valid to email address`() {
        assertDoesNotThrow {
            createEmail(to = "user@example.com")
        }
    }

    @Test
    fun `should create EmailNotificationData with valid from email address`() {
        assertDoesNotThrow {
            createEmail(from = "sender@example.com")
        }
    }

    @Test
    fun `should accept email with plus sign`() {
        assertDoesNotThrow {
            createEmail(to = "user+tag@example.com")
        }
    }

    @Test
    fun `should accept email with dots`() {
        assertDoesNotThrow {
            createEmail(to = "user.name@example.co.uk")
        }
    }

    @Test
    fun `should accept email with underscore`() {
        assertDoesNotThrow {
            createEmail(to = "user_name@example.com")
        }
    }

    @Test
    fun `should accept email with hyphen`() {
        assertDoesNotThrow {
            createEmail(to = "user-name@example-domain.com")
        }
    }

    // Invalid Email Address Tests
    @Test
    fun `should throw exception when to email is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                EmailNotificationData(
                    to = "",
                    from = validFrom,
                    subject = "S",
                    body = "B",
                    smtpHost = validSmtpHost,
                    smtpPort = validSmtpPort,
                )
            }
        assertEquals("to email address cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when to email is whitespace only`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                EmailNotificationData(
                    to = "   ",
                    from = validFrom,
                    subject = "S",
                    body = "B",
                    smtpHost = validSmtpHost,
                    smtpPort = validSmtpPort,
                )
            }
        assertEquals("to email address cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when from email is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                EmailNotificationData(
                    to = "recipient@example.com",
                    from = "",
                    subject = "S",
                    body = "B",
                    smtpHost = validSmtpHost,
                    smtpPort = validSmtpPort,
                )
            }
        assertEquals("from email address cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when to email has no at symbol`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(to = "userexample.com")
            }
        assertEquals("to email address 'userexample.com' is not valid", exception.message)
    }

    @Test
    fun `should throw exception when to email has no domain`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(to = "user@")
            }
        assertEquals("to email address 'user@' is not valid", exception.message)
    }

    @Test
    fun `should throw exception when to email has no username`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(to = "@example.com")
            }
        assertEquals("to email address '@example.com' is not valid", exception.message)
    }

    @Test
    fun `should throw exception when to email has no top-level domain`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(to = "user@example")
            }
        assertEquals("to email address 'user@example' is not valid", exception.message)
    }

    @Test
    fun `should throw exception when from email is invalid`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(from = "invalid-email")
            }
        assertEquals("from email address 'invalid-email' is not valid", exception.message)
    }

    @Test
    fun `should throw exception when to email has spaces`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(to = "user name@example.com")
            }
        assertEquals("to email address 'user name@example.com' is not valid", exception.message)
    }

    // Subject Validation Tests
    @Test
    fun `should create EmailNotificationData with valid subject`() {
        assertDoesNotThrow {
            createEmail(subject = "Test Subject")
        }
    }

    @Test
    fun `should accept subject at maximum length`() {
        val maxLengthSubject = "a".repeat(998)
        assertDoesNotThrow {
            createEmail(subject = maxLengthSubject)
        }
    }

    @Test
    fun `should throw exception when subject is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(subject = "")
            }
        assertEquals("Email subject cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when subject is whitespace only`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(subject = "   ")
            }
        assertEquals("Email subject cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when subject exceeds maximum length`() {
        val tooLongSubject = "a".repeat(999)
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(subject = tooLongSubject)
            }
        assertEquals("Email subject exceeds maximum length of 998 characters", exception.message)
    }

    // SMTP Configuration Tests
    @Test
    fun `should create EmailNotificationData with valid SMTP configuration`() {
        assertDoesNotThrow {
            createEmail(
                smtpHost = "smtp.example.com",
                smtpPort = 587,
            )
        }
    }

    @Test
    fun `should throw exception when SMTP host is blank`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(smtpHost = "")
            }
        assertEquals("SMTP host cannot be blank", exception.message)
    }

    @Test
    fun `should throw exception when SMTP host is whitespace only`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(smtpHost = "   ")
            }
        assertEquals("SMTP host cannot be blank", exception.message)
    }

    @Test
    fun `should accept SMTP host without port`() {
        // Wait, port is now required.
        assertDoesNotThrow {
            createEmail(smtpHost = "smtp.example.com", smtpPort = 587)
        }
    }

    // SMTP Port Validation Tests
    @Test
    fun `should accept valid SMTP port 25`() {
        assertDoesNotThrow {
            createEmail(smtpPort = 25)
        }
    }

    @Test
    fun `should accept valid SMTP port 465`() {
        assertDoesNotThrow {
            createEmail(smtpPort = 465)
        }
    }

    @Test
    fun `should accept valid SMTP port 587`() {
        assertDoesNotThrow {
            createEmail(smtpPort = 587)
        }
    }

    @Test
    fun `should accept minimum valid port 1`() {
        assertDoesNotThrow {
            createEmail(smtpPort = 1)
        }
    }

    @Test
    fun `should accept maximum valid port 65535`() {
        assertDoesNotThrow {
            createEmail(smtpPort = 65535)
        }
    }

    @Test
    fun `should throw exception when SMTP port is 0`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(smtpPort = 0)
            }
        assertEquals("SMTP port must be between 1 and 65535, but was 0", exception.message)
    }

    @Test
    fun `should throw exception when SMTP port is negative`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(smtpPort = -1)
            }
        assertEquals("SMTP port must be between 1 and 65535, but was -1", exception.message)
    }

    @Test
    fun `should throw exception when SMTP port exceeds maximum`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                createEmail(smtpPort = 65536)
            }
        assertEquals("SMTP port must be between 1 and 65535, but was 65536", exception.message)
    }

    // Comprehensive Valid Data Tests
    @Test
    fun `should create EmailNotificationData with all valid fields`() {
        val data =
            assertDoesNotThrow {
                createEmail(
                    to = "recipient@example.com",
                    from = "sender@example.com",
                    subject = "Test Email",
                    body = "This is a test email body",
                    smtpHost = "smtp.example.com",
                    smtpPort = 587,
                    username = "user",
                    password = "password",
                    useTls = true,
                )
            }
        assertNotNull(data)
        assertEquals("recipient@example.com", data.to)
        assertEquals("sender@example.com", data.from)
        assertEquals("Test Email", data.subject)
        assertEquals("This is a test email body", data.body)
        assertEquals("smtp.example.com", data.smtpHost)
        assertEquals(587, data.smtpPort)
        assertEquals("user", data.username)
        assertEquals("password", data.password)
        assertEquals(true, data.useTls)
    }

    @Test
    fun `should create EmailNotificationData with default values`() {
        val data =
            assertDoesNotThrow {
                createEmail()
            }
        assertNotNull(data)
        assertEquals(true, data.useTls)
    }

    // Body Validation Tests
    @Test
    fun `should accept empty body`() {
        assertDoesNotThrow {
            createEmail(body = "")
        }
    }

    @Test
    fun `should accept long body`() {
        val longBody = "a".repeat(10000)
        assertDoesNotThrow {
            createEmail(body = longBody)
        }
    }

    // Username and Password Tests
    @Test
    fun `should accept empty username`() {
        assertDoesNotThrow {
            createEmail(username = "")
        }
    }

    @Test
    fun `should accept empty password`() {
        assertDoesNotThrow {
            createEmail(password = "")
        }
    }

    // TLS Flag Tests
    @Test
    fun `should accept useTls as true`() {
        assertDoesNotThrow {
            createEmail(useTls = true)
        }
    }

    @Test
    fun `should accept useTls as false`() {
        assertDoesNotThrow {
            createEmail(useTls = false)
        }
    }

    @Test
    fun `should accept null useTls`() {
        assertDoesNotThrow {
            createEmail(useTls = null)
        }
    }
}
