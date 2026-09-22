package com.cereal.client.infrastructure.di

import org.koin.core.qualifier.named

/**
 * Constants for notification channel qualifiers used in dependency injection.
 */
object NotificationChannelQualifiers {
    val SYSTEM = named("system")
    val DISCORD = named("discord")
    val TELEGRAM = named("telegram")
    val EMAIL = named("email")
}
