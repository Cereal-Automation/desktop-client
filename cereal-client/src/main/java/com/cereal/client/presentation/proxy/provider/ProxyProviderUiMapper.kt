package com.cereal.client.presentation.proxy.provider

import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.presentation.notification.formatRelativeTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime

/**
 * Maps proxy-provider domain types to presentation UI models. [nowMillis] is injectable so relative
 * time formatting is deterministic in tests.
 */
@OptIn(ExperimentalTime::class)
class ProxyProviderUiMapper(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun toConnectedCard(connector: ProxyProviderConnector): ConnectedProviderUiModel =
        ConnectedProviderUiModel(
            provider = connector.provider,
            providerName = connector.provider.display.displayName,
            brandMark = connector.provider.display.brandMark,
            brandColorArgb = parseHexColor(connector.provider.display.brandColorHex),
            availableTrafficGb = formatTraffic(connector.availableTrafficGb),
            subUserCount = connector.subUserCount,
            lastSync = formatRelativeTime(connector.lastSyncAt.toEpochMilliseconds(), nowMillis()),
            connectedAt = ABSOLUTE_DATE_FORMAT.format(Date(connector.connectedAt.toEpochMilliseconds())),
        )

    /** The full provider catalogue as picker options (MarsProxies connectable, the rest "coming soon"). */
    fun providerOptions(): List<ProviderOptionUiModel> =
        ProxyVendor.entries.map { provider ->
            ProviderOptionUiModel(
                provider = provider,
                name = provider.display.displayName,
                brandMark = provider.display.brandMark,
                brandColorArgb = parseHexColor(provider.display.brandColorHex),
                tagline = provider.display.tagline,
                available = provider.available,
            )
        }

    private fun formatTraffic(gb: Double): String = String.format(Locale.US, "%.1f", gb)

    private fun parseHexColor(hex: String): Long {
        val cleaned = hex.removePrefix("#")
        return OPAQUE_ALPHA_MASK or cleaned.toLong(HEX_RADIX)
    }

    private companion object {
        const val HEX_RADIX = 16

        // Force full opacity on a 6-digit "#RRGGBB" hex by OR-ing in the alpha byte.
        const val OPAQUE_ALPHA_MASK = 0xFF000000L
        val ABSOLUTE_DATE_FORMAT = SimpleDateFormat("MMM d, yyyy", Locale.US)
    }
}
