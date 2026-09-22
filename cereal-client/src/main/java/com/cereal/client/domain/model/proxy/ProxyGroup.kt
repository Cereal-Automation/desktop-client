package com.cereal.client.domain.model.proxy

import com.cereal.client.domain.model.datasets.Group

data class ProxyGroup(
    override val id: String,
    override val name: String,
    override val numberOfItems: Int,
    override val items: Sequence<Proxy>,
    /** The proxy provider this group was synced from, or `null` for manually-managed groups. */
    val provider: ProxyVendor? = null,
    /** Human-readable geo targeting label of the last sync (e.g. "United States · Texas · Dallas"). */
    val geoLabel: String? = null,
) : Group<Proxy> {
    override fun toString(): String = name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProxyGroup) return false

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
