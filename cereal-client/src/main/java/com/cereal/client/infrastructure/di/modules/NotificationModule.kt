package com.cereal.client.infrastructure.di.modules

import com.cereal.client.infrastructure.data.notification.DiscordNotificationStrategy
import com.cereal.client.infrastructure.data.notification.EmailNotificationStrategy
import com.cereal.client.infrastructure.data.notification.SystemNotificationStrategy
import com.cereal.client.infrastructure.data.notification.TelegramNotificationStrategy
import org.koin.dsl.module

object NotificationModule {
    val modules =
        module {
            // Register notification strategies
            single {
                SystemNotificationStrategy(
                    get(),
                )
            }
            single {
                DiscordNotificationStrategy(
                    get(),
                )
            }
            single {
                TelegramNotificationStrategy(
                    get(),
                )
            }
            single {
                EmailNotificationStrategy()
            }
        }
}
