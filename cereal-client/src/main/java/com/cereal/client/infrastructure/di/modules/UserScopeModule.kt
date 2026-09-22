package com.cereal.client.infrastructure.di.modules

import com.cereal.client.application.ApplicationConfig
import com.cereal.client.domain.model.user.User
import com.cereal.client.infrastructure.data.datasource.database.room.DatabaseConnector
import com.cereal.client.infrastructure.data.datasource.database.room.UserRoomDatabase
import com.cereal.client.infrastructure.data.datasource.filesystem.security.Encryption
import org.koin.core.qualifier.named
import org.koin.dsl.module

object UserScopeModule {
    // Length in bytes of the derived encryption key for a user database.
    private const val USER_ENCRYPTION_KEY_LENGTH = 32

    val modules =
        module {
            scope(named("UserScope")) {
                scoped<UserRoomDatabase> {
                    val user = get<User>()
                    val config = get<ApplicationConfig>()
                    DatabaseConnector.connectUser(config.databaseDirectory, user.id)
                }

                scoped(named("UserEncryptionKey")) {
                    val user = get<User>()
                    val config = get<ApplicationConfig>()
                    val encryptionKey =
                        Encryption.getEncryptionKey(
                            config.databaseEncryptionKey,
                            user.encryptionKey,
                            USER_ENCRYPTION_KEY_LENGTH,
                        )
                    encryptionKey
                }
            }
        }
}
