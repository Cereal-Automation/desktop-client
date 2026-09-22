package com.cereal.client.presentation.view.fields.validator

import com.cereal.client.domain.model.auth.PasswordStrength

class PasswordStrengthValidator : StringFieldValidator {
    override fun validate(value: String?): String? {
        if (value.isNullOrBlank()) {
            return "Password is required"
        }

        val strength = PasswordStrength.evaluate(value)

        return if (strength.isValid) {
            null
        } else {
            val missing = mutableListOf<String>()
            if (!strength.hasMinimumLength) missing.add("at least 8 characters")
            if (!strength.hasUppercase) missing.add("an uppercase letter")
            if (!strength.hasLowercase) missing.add("a lowercase letter")
            if (!strength.hasNumber) missing.add("a number")

            "Password must contain ${missing.joinToString(", ")}"
        }
    }
}
