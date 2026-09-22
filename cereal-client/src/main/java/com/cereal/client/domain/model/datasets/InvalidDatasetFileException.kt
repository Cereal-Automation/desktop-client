package com.cereal.client.domain.model.datasets

/**
 * Thrown by the CSV mappers ([toDatasetItems] and
 * [com.cereal.client.domain.model.script.configuration.toListRows]) when raw rows violate the
 * configuration rules: a required column is missing, a cell cannot be converted to its configured
 * type, or a non-nullable field is empty.
 *
 * The message may span several lines when more than one problem is reported.
 *
 * This is a domain-level signal; outer layers translate it into a user-facing error
 * (e.g. `InvalidFileException`).
 */
class InvalidDatasetFileException(
    message: String,
) : Exception(message)
