package com.cereal.client.presentation.main

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.cereal.client.presentation.navigation.Root
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.nav_section_agent
import com.cereal_automation.cereal_client.generated.resources.nav_section_ready_made_scripts
import com.cereal_automation.cereal_client.generated.resources.nav_section_resources
import com.cereal_automation.cereal_client.generated.resources.nav_section_system
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

/** Sidebar sections, rendered top to bottom in declaration order. */
enum class MenuSection(
    val titleResource: StringResource,
) {
    AGENT(Res.string.nav_section_agent),
    READY_MADE_SCRIPTS(Res.string.nav_section_ready_made_scripts),
    RESOURCES(Res.string.nav_section_resources),
    SYSTEM(Res.string.nav_section_system),
}

@Immutable
data class MenuItemUiModel(
    val route: Root.Routing? = null,
    val externalUrl: String? = null,
    val resourcePath: IconSource,
    val titleResource: StringResource,
    val titleOverride: String? = null,
    val selected: Boolean,
    /** The sidebar section the item is listed under; null for the user card at the bottom. */
    val section: MenuSection?,
    val hasNotificationDot: Boolean = false,
    val badge: Int? = null,
    /** Red badge when true (something needs the user); neutral otherwise. */
    val isBadgeAttention: Boolean = true,
)
