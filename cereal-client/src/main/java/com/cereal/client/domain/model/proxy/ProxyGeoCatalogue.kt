package com.cereal.client.domain.model.proxy

/**
 * Bundled geo catalogue for proxy targeting. There is no provider locations endpoint, so Cereal ships
 * the country and US-state options; city is free text.
 *
 * @property code ISO 3166-1 alpha-2 country code.
 * @property displayName Human-readable country name.
 */
data class ProxyCountry(
    val code: String,
    val displayName: String,
)

/**
 * Pure-Kotlin catalogue of the countries (and US states) a user can target when syncing proxies.
 * Mirrors the design handoff catalogue; the US state list is offered only when the country is the US.
 */
object ProxyGeoCatalogue {
    /** ISO 3166-1 alpha-2 code for the United States, the only country with a state list. */
    const val UNITED_STATES_CODE = "US"

    val countries: List<ProxyCountry> =
        listOf(
            ProxyCountry("US", "United States"),
            ProxyCountry("GB", "United Kingdom"),
            ProxyCountry("DE", "Germany"),
            ProxyCountry("CA", "Canada"),
            ProxyCountry("FR", "France"),
            ProxyCountry("JP", "Japan"),
            ProxyCountry("AU", "Australia"),
            ProxyCountry("BR", "Brazil"),
            ProxyCountry("NL", "Netherlands"),
            ProxyCountry("ES", "Spain"),
            ProxyCountry("IT", "Italy"),
            ProxyCountry("IN", "India"),
        )

    /** Full US state names. Offered only when the selected country is [UNITED_STATES_CODE]. */
    val unitedStates: List<String> =
        listOf(
            "Alabama",
            "Alaska",
            "Arizona",
            "Arkansas",
            "California",
            "Colorado",
            "Connecticut",
            "Delaware",
            "Florida",
            "Georgia",
            "Hawaii",
            "Idaho",
            "Illinois",
            "Indiana",
            "Iowa",
            "Kansas",
            "Kentucky",
            "Louisiana",
            "Maine",
            "Maryland",
            "Massachusetts",
            "Michigan",
            "Minnesota",
            "Mississippi",
            "Missouri",
            "Montana",
            "Nebraska",
            "Nevada",
            "New Hampshire",
            "New Jersey",
            "New Mexico",
            "New York",
            "North Carolina",
            "North Dakota",
            "Ohio",
            "Oklahoma",
            "Oregon",
            "Pennsylvania",
            "Rhode Island",
            "South Carolina",
            "South Dakota",
            "Tennessee",
            "Texas",
            "Utah",
            "Vermont",
            "Virginia",
            "Washington",
            "West Virginia",
            "Wisconsin",
            "Wyoming",
        )

    /** The display name for [countryCode], falling back to the raw code when it isn't in the catalogue. */
    fun countryName(countryCode: String): String = countries.firstOrNull { it.code == countryCode }?.displayName ?: countryCode

    /** Whether [countryCode] supports US-state targeting. */
    fun supportsStates(countryCode: String): Boolean = countryCode == UNITED_STATES_CODE

    /** US states when [countryCode] is the US, otherwise an empty list. */
    fun statesFor(countryCode: String): List<String> = if (supportsStates(countryCode)) unitedStates else emptyList()
}
