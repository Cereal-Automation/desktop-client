package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.interactor.notification.GlobalNotificationConfigReader
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.provider.DiscordProvider
import com.cereal.client.infrastructure.provider.inmemory.InMemoryDiscordProvider
import org.koin.dsl.module

/** Loaded after the app modules in headless mode only; overrides what differs without a display. */
object HeadlessModule {
    val modules =
        module {
            // No tray: sends resolve with the system channel off; the stored preference is untouched.
            single { GlobalNotificationConfigReader(get(), desktopChannelAvailable = false) }
            // Discord RPC talks to a local Discord app that isn't there: never start it (the no-op fake).
            single<DiscordProvider> { InMemoryDiscordProvider() }
            // Tasks left running when the process ended start again after sign-in and script sync.
            single { TaskManager(get(), get(), get(), get(), get(), get(), resumeInterruptedTasks = true) }
        }
}
