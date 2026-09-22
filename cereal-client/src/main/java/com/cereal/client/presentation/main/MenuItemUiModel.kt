package com.cereal.client.presentation.main

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.cereal.client.presentation.navigation.Root
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

@Immutable
sealed interface IconSource {
    data class Vector(
        val imageVector: ImageVector,
    ) : IconSource

    data class Drawable(
        val drawableResource: DrawableResource,
    ) : IconSource
}

@Immutable
data class MenuItemUiModel(
    val route: Root.Routing? = null,
    val externalUrl: String? = null,
    val resourcePath: IconSource,
    val titleResource: StringResource,
    val titleOverride: String? = null,
    val selected: Boolean,
    val mainItem: Boolean,
    val hasNotificationDot: Boolean = false,
    val badge: Int? = null,
)
