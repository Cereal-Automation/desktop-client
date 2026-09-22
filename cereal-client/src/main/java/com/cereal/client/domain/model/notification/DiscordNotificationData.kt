package com.cereal.client.domain.model.notification

/**
 * Shared validation for the optional URL fields scattered across the Discord embed models. When [value]
 * is non-null it must be a non-blank HTTP(S) URL; [fieldName] is used verbatim in the failure message so
 * each call site reports the specific field that was invalid.
 */
private fun requireHttpUrl(
    value: String?,
    fieldName: String,
) {
    value?.let {
        require(it.isNotBlank()) { "$fieldName cannot be blank" }
        require(it.startsWith("http://") || it.startsWith("https://")) {
            "$fieldName must be a valid HTTP or HTTPS URL"
        }
    }
}

/**
 * Domain model representing Discord-specific notification data.
 */
data class DiscordNotificationData(
    val username: String? = null,
    val content: String? = null,
    val avatarUrl: String? = null,
    val tts: Boolean? = null,
    val embeds: List<DiscordEmbed> = emptyList(),
    val webhookUrl: String,
) : Notification() {
    init {
        // Validate that either content or embeds is provided
        require(content != null || embeds.isNotEmpty()) {
            "Discord notification must have either content or at least one embed"
        }

        // Validate username length (Discord limit is 80 characters)
        username?.let {
            require(it.isNotBlank()) { "Username cannot be blank" }
            require(it.length <= MAX_USERNAME_LENGTH) { "Username cannot exceed $MAX_USERNAME_LENGTH characters" }
        }

        // Validate content length (Discord limit is 2000 characters)
        content?.let {
            require(it.length <= MAX_CONTENT_LENGTH) { "Content cannot exceed $MAX_CONTENT_LENGTH characters" }
        }

        // Validate avatar URL format
        requireHttpUrl(avatarUrl, "Avatar URL")

        // Validate embeds count (Discord limit is 10 embeds per message)
        require(embeds.size <= MAX_EMBEDS_COUNT) { "Cannot have more than $MAX_EMBEDS_COUNT embeds per Discord notification" }

        // Validate webhook URL
        require(webhookUrl.isNotBlank()) { "Webhook URL cannot be blank" }
        require(
            webhookUrl.startsWith("https://discord.com/api/webhooks/") ||
                webhookUrl.startsWith("https://discordapp.com/api/webhooks/"),
        ) {
            "Webhook URL must be a valid Discord webhook URL"
        }
    }

    private companion object {
        private const val MAX_USERNAME_LENGTH = 80
        private const val MAX_CONTENT_LENGTH = 2000
        private const val MAX_EMBEDS_COUNT = 10
    }
}

/**
 * Domain model representing a Discord embed.
 */
data class DiscordEmbed(
    val title: String? = null,
    val type: String? = null,
    val description: String? = null,
    val url: String? = null,
    val timestamp: String? = null,
    val color: String? = null,
    val footer: FooterEmbed? = null,
    val image: ImageEmbed? = null,
    val thumbnail: ThumbnailEmbed? = null,
    val video: VideoEmbed? = null,
    val provider: ProviderEmbed? = null,
    val author: AuthorEmbed? = null,
    val fields: List<FieldEmbed>? = null,
) {
    init {
        // Validate title length (Discord limit is 256 characters)
        title?.let {
            require(it.isNotBlank()) { "Embed title cannot be blank" }
            require(it.length <= MAX_TITLE_LENGTH) { "Embed title cannot exceed $MAX_TITLE_LENGTH characters" }
        }

        // Validate description length (Discord limit is 4096 characters)
        description?.let {
            require(it.length <= MAX_DESCRIPTION_LENGTH) { "Embed description cannot exceed $MAX_DESCRIPTION_LENGTH characters" }
        }

        // Validate URL format
        requireHttpUrl(url, "Embed URL")

        // Validate timestamp format (ISO 8601)
        timestamp?.let {
            require(it.isNotBlank()) { "Timestamp cannot be blank" }
            require(it.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"))) {
                "Timestamp must be in ISO 8601 format"
            }
        }

        // Validate color (can be hex string or integer)
        color?.let {
            require(it.isNotBlank()) { "Color cannot be blank" }
        }

        // Validate fields count (Discord limit is 25 fields per embed)
        fields?.let {
            require(it.size <= MAX_FIELDS_COUNT) { "Embed cannot have more than $MAX_FIELDS_COUNT fields" }
        }

        // Validate total embed size (Discord limit is 6000 characters total)
        val totalSize =
            (title?.length ?: 0) +
                (description?.length ?: 0) +
                (footer?.text?.length ?: 0) +
                (author?.name?.length ?: 0) +
                (fields?.sumOf { it.name.length + it.value.length } ?: 0)
        require(totalSize <= MAX_TOTAL_SIZE) {
            "Total embed size cannot exceed $MAX_TOTAL_SIZE characters (current: $totalSize)"
        }
    }

    private companion object {
        private const val MAX_TITLE_LENGTH = 256
        private const val MAX_DESCRIPTION_LENGTH = 4096
        private const val MAX_FIELDS_COUNT = 25
        private const val MAX_TOTAL_SIZE = 6000
    }
}

data class FooterEmbed(
    val text: String? = null,
    val iconUrl: String? = null,
    val proxyIconUrl: String? = null,
) {
    init {
        // Validate footer text length (Discord limit is 2048 characters)
        text?.let {
            require(it.isNotBlank()) { "Footer text cannot be blank" }
            require(it.length <= MAX_FOOTER_TEXT_LENGTH) { "Footer text cannot exceed $MAX_FOOTER_TEXT_LENGTH characters" }
        }

        // Validate icon URL format
        requireHttpUrl(iconUrl, "Footer icon URL")
    }

    private companion object {
        private const val MAX_FOOTER_TEXT_LENGTH = 2048
    }
}

data class ImageEmbed(
    val url: String? = null,
    val proxyUrl: String? = null,
    val height: Int? = null,
    val width: Int? = null,
) {
    init {
        // Validate URL format
        requireHttpUrl(url, "Image URL")

        // Validate dimensions
        height?.let {
            require(it > 0) { "Image height must be positive" }
        }

        width?.let {
            require(it > 0) { "Image width must be positive" }
        }
    }
}

data class ThumbnailEmbed(
    val url: String? = null,
    val proxyUrl: String? = null,
    val height: Int? = null,
    val width: Int? = null,
) {
    init {
        // Validate URL format
        requireHttpUrl(url, "Thumbnail URL")

        // Validate dimensions
        height?.let {
            require(it > 0) { "Thumbnail height must be positive" }
        }

        width?.let {
            require(it > 0) { "Thumbnail width must be positive" }
        }
    }
}

data class VideoEmbed(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null,
) {
    init {
        // Validate URL format
        requireHttpUrl(url, "Video URL")

        // Validate dimensions
        height?.let {
            require(it > 0) { "Video height must be positive" }
        }

        width?.let {
            require(it > 0) { "Video width must be positive" }
        }
    }
}

data class ProviderEmbed(
    val name: String? = null,
    val url: String? = null,
) {
    init {
        // Validate name
        name?.let {
            require(it.isNotBlank()) { "Provider name cannot be blank" }
        }

        // Validate URL format
        requireHttpUrl(url, "Provider URL")
    }
}

data class AuthorEmbed(
    val name: String? = null,
    val url: String? = null,
    val iconUrl: String? = null,
    val proxyIconUrl: String? = null,
) {
    init {
        // Validate author name length (Discord limit is 256 characters)
        name?.let {
            require(it.isNotBlank()) { "Author name cannot be blank" }
            require(it.length <= MAX_AUTHOR_NAME_LENGTH) { "Author name cannot exceed $MAX_AUTHOR_NAME_LENGTH characters" }
        }

        // Validate URL format
        requireHttpUrl(url, "Author URL")

        // Validate icon URL format
        requireHttpUrl(iconUrl, "Author icon URL")
    }

    private companion object {
        private const val MAX_AUTHOR_NAME_LENGTH = 256
    }
}

data class FieldEmbed(
    val name: String,
    val value: String,
    val inline: Boolean? = null,
) {
    init {
        // Validate field name (Discord limit is 256 characters, cannot be blank)
        require(name.isNotBlank()) { "Field name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Field name cannot exceed $MAX_NAME_LENGTH characters" }

        // Validate field value (Discord limit is 1024 characters, cannot be blank)
        require(value.isNotBlank()) { "Field value cannot be blank" }
        require(value.length <= MAX_VALUE_LENGTH) { "Field value cannot exceed $MAX_VALUE_LENGTH characters" }
    }

    private companion object {
        private const val MAX_NAME_LENGTH = 256
        private const val MAX_VALUE_LENGTH = 1024
    }
}
