package com.cereal.client.application.datasets

import com.cereal.client.application.exception.CerealException
import com.cereal.client.domain.model.datasets.InvalidDatasetFileException

class InvalidFileException(
    message: String,
    cause: Throwable? = null,
) : CerealException("The provided file is invalid: $message", cause)

/**
 * Translates the domain's rejection of a file into the user-facing error the import dialog renders.
 * The message may span several lines when the mapper reported more than one problem.
 */
fun InvalidDatasetFileException.toInvalidFileException(): InvalidFileException = InvalidFileException(message ?: "Unexpected error", this)
