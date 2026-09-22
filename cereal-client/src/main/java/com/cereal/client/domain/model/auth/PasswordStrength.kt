package com.cereal.client.domain.model.auth

/**
 * Represents password strength evaluation result.
 * Domain model - NO framework dependencies.
 */
data class PasswordStrength(
    val hasMinimumLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasNumber: Boolean,
) {
    val isValid: Boolean
        get() = hasMinimumLength && hasUppercase && hasLowercase && hasNumber

    val strength: Strength
        get() {
            val criteriaCount =
                listOf(
                    hasMinimumLength,
                    hasUppercase,
                    hasLowercase,
                    hasNumber,
                ).count { it }

            return when (criteriaCount) {
                4 -> Strength.STRONG
                3 -> Strength.GOOD
                2 -> Strength.FAIR
                else -> Strength.WEAK
            }
        }

    enum class Strength {
        WEAK,
        FAIR,
        GOOD,
        STRONG,
    }

    companion object {
        const val MIN_LENGTH = 8
        private val UPPERCASE_REGEX = Regex("[A-Z]")
        private val LOWERCASE_REGEX = Regex("[a-z]")
        private val NUMBER_REGEX = Regex("[0-9]")

        fun evaluate(password: String): PasswordStrength =
            PasswordStrength(
                hasMinimumLength = password.length >= MIN_LENGTH,
                hasUppercase = UPPERCASE_REGEX.containsMatchIn(password),
                hasLowercase = LOWERCASE_REGEX.containsMatchIn(password),
                hasNumber = NUMBER_REGEX.containsMatchIn(password),
            )
    }
}
