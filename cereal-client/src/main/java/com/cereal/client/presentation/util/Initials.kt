package com.cereal.client.presentation.util

private val INITIAL_SEPARATORS = charArrayOf(' ', '_', '-', '.', '(')

fun initialsOf(
    name: String?,
    fallback: Char = '?',
): String {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return fallback.uppercaseChar().toString()
    val parts = trimmed.split(*INITIAL_SEPARATORS).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> trimmed.first().uppercaseChar().toString()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
    }
}
