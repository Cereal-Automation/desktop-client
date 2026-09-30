package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.interactor.notification.GlobalNotificationConfigReader
import org.koin.dsl.module

/** Loaded after the app modules in headless mode only; overrides what differs without a display. */
object HeadlessModule {
    val modules =
        module {
            // No tray: sends resolve with the system channel off; the stored preference is untouched.
            single { GlobalNotificationConfigReader(get(), desktopChannelAvailable = false) }
        }
}
