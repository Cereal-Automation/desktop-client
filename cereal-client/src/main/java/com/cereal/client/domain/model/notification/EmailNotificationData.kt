package com.cereal.client.domain.model.notification

/**
 * Domain model representing Email-specific notification data.
 */
data class EmailNotificationData(
    val to: String,
    val from: String,
    val subject: String,
    val body: String,
    val smtpHost: String,
    val smtpPort: Int,
    val username: String? = null,
    val password: String? = null,
    val useTls: Boolean? = true,
) : Notification() {
    init {
        validateEmailAddress("to", to)
        validateEmailAddress("from", from)
        validateSubject(subject)
        validateSmtpConfiguration(smtpHost)
        validateSmtpPort(smtpPort)
    }

    private fun validateEmailAddress(
        fieldName: String,
        email: String,
    ) {
        require(email.isNotBlank()) { "$fieldName email address cannot be blank" }
        require(EMAIL_REGEX.matches(email)) {
            "$fieldName email address '$email' is not valid"
        }
    }

    private fun validateSubject(subject: String) {
        require(subject.isNotBlank()) { "Email subject cannot be blank" }
        require(subject.length <= MAX_SUBJECT_LENGTH) {
            "Email subject exceeds maximum length of $MAX_SUBJECT_LENGTH characters"
        }
    }

    private fun validateSmtpConfiguration(
        host: String,
    ) {
        require(host.isNotBlank()) { "SMTP host cannot be blank" }
    }

    private fun validateSmtpPort(port: Int) {
        require(port in MIN_PORT..MAX_PORT) {
            "SMTP port must be between $MIN_PORT and $MAX_PORT, but was $port"
        }
    }

    companion object {
        private const val MAX_SUBJECT_LENGTH = 998 // RFC 2822 recommendation
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535

        // Basic email validation regex
        private val EMAIL_REGEX =
            Regex(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            )
    }
}
