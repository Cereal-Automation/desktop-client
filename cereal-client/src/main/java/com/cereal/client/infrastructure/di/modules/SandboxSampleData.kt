package com.cereal.client.infrastructure.di.modules

import com.cereal.client.domain.model.datasets.CustomDatasetGroup
import com.cereal.client.domain.model.datasets.CustomDatasetItem
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.ScriptOwner
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.model.script.configuration.toConfigValue
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * Sample data for the sandboxed `mock` flavor (and the in-memory-backed screen tests), injected into
 * the in-memory repositories by [InMemoryRepositoryModule] so the dev UI has something to render.
 *
 * The in-memory repositories themselves carry no built-in data; this is the single place that owns
 * the demo content. Numeric literals here are ad-hoc sample values, not meaningful constants.
 */
@Suppress("MagicNumber")
@OptIn(ExperimentalTime::class)
internal object SandboxSampleData {
    @OptIn(ExperimentalTime::class)
    fun proxyProviderConnector(): com.cereal.client.domain.model.proxy.ProxyProviderConnector {
        val now = Clock.System.now()
        return com.cereal.client.domain.model.proxy.ProxyProviderConnector(
            provider = com.cereal.client.domain.model.proxy.ProxyVendor.MARSPROXIES,
            connectedAt = now - 6.hours,
            lastSyncAt = now - 2.hours,
            subUserHash = "su_hash_7F3K29A",
            availableTrafficGb = 84.2,
            subUserCount = 3,
            credentialKey = com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey.KEY_MARS_PROXIES_API_TOKEN,
        )
    }

    fun proxyGroups(): List<ProxyGroup> {
        val seeds =
            listOf(
                "Residential US" to 240,
                "Residential EU" to 180,
                "Datacenter rotating" to 500,
                "Sneaker ISPs" to 80,
                "Mobile 4G pool" to 24,
                "Sticky sessions" to 36,
                "Free public (do not use)" to 12,
                "Bright Data backup" to 64,
                "Oxylabs primary" to 320,
                "Local dev (127.0.0.1)" to 3,
            )

        return seeds.map { (name, count) ->
            val id = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
            val proxies =
                (1..count).map { i ->
                    val octet = (i % 254) + 1
                    Proxy(
                        id = UUID.randomUUID(),
                        address = "10.${(i / 254) % 256}.${(i / 65) % 256}.$octet",
                        port = 8000 + (i % 1000),
                        username = "user_${id.take(4)}_$i",
                        password = "pw_${UUID.randomUUID().toString().take(8)}",
                    )
                }

            ProxyGroup(
                id = id,
                name = name,
                numberOfItems = count,
                items = proxies.asSequence(),
            )
        }
    }

    fun customDatasetGroups(): List<CustomDatasetGroup> {
        val seeds =
            listOf(
                "EU sneaker SKUs" to 1240,
                "Grocery wishlist" to 84,
                "Burner emails (alias)" to 320,
                "Shipping addresses" to 6,
                "Concert ticket queues" to 12,
                "ASIN watchlist" to 540,
                "Snipes drop targets" to 78,
                "Adidas Confirmed accounts" to 4,
                "Carrefour deal keywords" to 22,
                "Discord webhook map" to 18,
            )

        val now = Clock.System.now()
        return seeds.mapIndexed { index, (name, count) ->
            val id = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
            val datasetItems =
                (1..count).map { i ->
                    CustomDatasetItem(
                        id = UUID.randomUUID(),
                        fields =
                            mapOf(
                                "value" to "$name #$i",
                                "count" to (i * 3),
                                "price" to ((i % 9 + 1) * 12.5),
                                "enabled" to (i % 4 != 0),
                                "url" to "https://example.com/$index/item/$i",
                            ).mapValues { (_, value) -> value.toConfigValue() },
                    )
                }

            CustomDatasetGroup(
                id = id,
                name = name,
                numberOfItems = count,
                itemDefinitions = datasetItemDefinitions,
                items = datasetItems.asSequence(),
                createdAt = now - (index * 17 + 3).hours,
            )
        }
    }

    val marketplaceCatalog: List<MarketplaceScript> =
        listOf(
            // Free script with a high install base.
            MarketplaceScript(
                id = "1",
                publicIdentifier = "com.cereal",
                title = "Instagram Follower Bot",
                shortDescription = "Automate engagement and growth safely using native API calls and realistic delays.",
                price = null,
                formattedPrice = null,
                isFree = true,
                averageRating = 4.8,
                ratingCount = 1200,
                developer = ScriptOwner(name = "Cereal Team"),
                tags = listOf("Social Media"),
            ),
            // Paid script, no trial — vanilla price chip.
            MarketplaceScript(
                id = "2",
                publicIdentifier = "com.cereal.shopify",
                title = "Shopify Checkout Sniper",
                shortDescription = "High-speed checkout logic for limited drops. Supports auto-refresh and quick-pay.",
                price = BigDecimal("29.99"),
                formattedPrice = "\$29.99 / mo",
                isFree = false,
                averageRating = 4.9,
                ratingCount = 840,
                developer = ScriptOwner(name = "Pro Scripts"),
                tags = listOf("E-commerce"),
            ),
            // Paid script with a 7-day free trial — exercises trial badge.
            MarketplaceScript(
                id = "3",
                publicIdentifier = "com.cereal.zalando",
                title = "Zalando Watcher",
                shortDescription = "Track price drops and restocks across the Zalando EU catalog. Webhook alerts on change.",
                price = BigDecimal("4.00"),
                formattedPrice = "€4 / mo",
                isFree = false,
                freeTrialEnabled = true,
                freeTrialDays = 7,
                averageRating = 4.9,
                ratingCount = 12400,
                developer = ScriptOwner(name = "stark", verified = true),
                tags = listOf("Fashion", "EU"),
            ),
            // Paid script with a 14-day free trial.
            MarketplaceScript(
                id = "4",
                publicIdentifier = "com.cereal.bstn",
                title = "BSTN Drop Alerts",
                shortDescription = "Real-time monitor for BSTN sneaker releases. Push notifications when a SKU goes live.",
                price = BigDecimal("7.00"),
                formattedPrice = "€7 / mo",
                isFree = false,
                freeTrialEnabled = true,
                freeTrialDays = 14,
                averageRating = 4.8,
                ratingCount = 8100,
                developer = ScriptOwner(name = "kicks", verified = true),
                tags = listOf("Sneakers", "EU"),
            ),
            // Higher-priced paid, no trial — wider price chip.
            MarketplaceScript(
                id = "5",
                publicIdentifier = "com.cereal.ticketmaster",
                title = "Ticketmaster Alerts",
                shortDescription = "Notify when extra seats or resale tickets appear for an event you're watching.",
                price = BigDecimal("12.00"),
                formattedPrice = "€12 / mo",
                isFree = false,
                averageRating = 4.7,
                ratingCount = 6700,
                developer = ScriptOwner(name = "queue.io"),
                tags = listOf("Tickets"),
            ),
            // Free + community-flagged.
            MarketplaceScript(
                id = "6",
                publicIdentifier = "com.cereal.tgtg",
                title = "Too Good To Go Alerts",
                shortDescription = "Alert when surprise bags appear at your favourite shops. iOS push optional.",
                price = null,
                formattedPrice = null,
                isFree = true,
                isCommunity = true,
                averageRating = 4.9,
                ratingCount = 21000,
                developer = ScriptOwner(name = "aless"),
                tags = listOf("Food"),
            ),
            // Brand-new free script flagged as NEW.
            MarketplaceScript(
                id = "7",
                publicIdentifier = "com.cereal.discord",
                title = "Discord Notifier",
                shortDescription = "First-party Discord bridge — relay any Cereal event to your servers.",
                price = null,
                formattedPrice = null,
                isFree = true,
                isNew = true,
                averageRating = 5.0,
                ratingCount = 44000,
                developer = ScriptOwner(name = "core"),
                tags = listOf("Notifications"),
            ),
            // Paid script currently in maintenance mode.
            MarketplaceScript(
                id = "8",
                publicIdentifier = "com.cereal.snipes",
                title = "Snipes Drop Alerts",
                shortDescription = "Footwear release monitor for Snipes DE/AT/CH. Alerts when sizes restock.",
                price = BigDecimal("5.00"),
                formattedPrice = "€5 / mo",
                isFree = false,
                maintenanceMode = true,
                averageRating = 4.5,
                ratingCount = 3200,
                developer = ScriptOwner(name = "snipes-mod"),
                tags = listOf("Sneakers"),
            ),
            // No rating yet — exercises the em-dash rating fallback.
            MarketplaceScript(
                id = "9",
                publicIdentifier = "com.cereal.adidas",
                title = "Adidas Drop Watch",
                shortDescription = "Monitor the Confirmed app for upcoming releases and raffle openings on your account.",
                price = BigDecimal("9.00"),
                formattedPrice = "€9 / mo",
                isFree = false,
                freeTrialEnabled = true,
                freeTrialDays = 14,
                averageRating = null,
                ratingCount = 0,
                isNew = true,
                developer = ScriptOwner(name = "confirmed"),
                tags = listOf("Sneakers"),
            ),
            // No developer attached — author line is hidden.
            MarketplaceScript(
                id = "10",
                publicIdentifier = "com.cereal.carrefour",
                title = "Carrefour Alerts",
                shortDescription = "Track grocery promos in your local Carrefour.",
                price = null,
                formattedPrice = null,
                isFree = true,
                isCommunity = true,
                averageRating = 4.4,
                ratingCount = 1100,
                developer = null,
                tags = listOf("Grocery", "EU"),
            ),
        )

    private val datasetItemDefinitions =
        listOf(
            ScriptConfigurationItemDefinition(
                name = "Value",
                description = "",
                key = "value",
                position = 0,
                type = ConfigItemType.StringConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = true,
            ),
            ScriptConfigurationItemDefinition(
                name = "Count",
                description = "",
                key = "count",
                position = 1,
                type = ConfigItemType.IntConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
            ScriptConfigurationItemDefinition(
                name = "Price",
                description = "",
                key = "price",
                position = 2,
                type = ConfigItemType.DoubleConfigItem,
                isNullable = true,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
            ScriptConfigurationItemDefinition(
                name = "Enabled",
                description = "",
                key = "enabled",
                position = 3,
                type = ConfigItemType.BooleanConfigItem,
                isNullable = false,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
            ScriptConfigurationItemDefinition(
                name = "URL",
                description = "",
                key = "url",
                position = 4,
                type = ConfigItemType.StringConfigItem,
                isNullable = true,
                stateModifier = null,
                isScriptIdentifier = false,
            ),
        )
}
