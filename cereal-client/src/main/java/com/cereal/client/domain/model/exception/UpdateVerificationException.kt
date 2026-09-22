package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Thrown when a downloaded update no longer matches its expected SHA-256 at install time. The
 * download is hash-checked while streaming, but the file sits on disk between that check and the
 * moment it is executed; re-verification failing here means the file was corrupted or tampered
 * with in that window, and the install must be aborted (CWE-367).
 */
class UpdateVerificationException(
    message: String,
    cause: Throwable? = null,
) : CerealException(message, cause)
