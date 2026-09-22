package com.cereal.client.infrastructure.data.datasource.discord

import com.sun.jna.Structure
import java.util.Objects

/*
typedef struct DiscordUser {
    const char* userId;
    const char* username;
    const char* discriminator;
    const char* avatar;
} DiscordUser;
 */

/**
 * Struct binding for a discord join request.
 */
@Structure.FieldOrder("userId", "username", "discriminator", "avatar")
class DiscordUser(
    encoding: String = "UTF-8",
) : Structure() {
    /**
     * The userId for the user that requests to join
     */
    @JvmField var userId: String? = null

    /**
     * The username of the user that requests to join
     */
    @JvmField var username: String? = null

    /**
     * The discriminator of the user that requests to join
     */
    @JvmField var discriminator: String? = null

    /**
     * The avatar of the user that requests to join
     */
    @JvmField var avatar: String? = null

    init {
        setStringEncoding(encoding)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscordUser) return false

        return userId == other.userId &&
            username == other.username &&
            discriminator == other.discriminator &&
            avatar == other.avatar
    }

    override fun hashCode(): Int = Objects.hash(userId, username, discriminator, avatar)
}
