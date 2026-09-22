package com.cereal.licensechecker

import java.security.SecureRandom

private val secureRandom = SecureRandom()

/**
 * Generates a cryptographically secure random alphanumeric string of the specified length.
 *
 * Uses [SecureRandom] to ensure the output is unpredictable, making it suitable for use
 * as a salt in signature verification schemes.
 *
 * @param length The length of the string to generate.
 * @return A cryptographically secure randomly generated alphanumeric string.
 */
fun getRandomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars[secureRandom.nextInt(allowedChars.size)] }
        .joinToString("")
}
