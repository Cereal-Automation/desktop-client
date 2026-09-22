package com.cereal.client.domain.model.proxy

/**
 * Outcome of a successful proxy sync.
 *
 * @property groupName Name of the target group the proxies were written into.
 * @property groupId Id of the target group (existing or newly created).
 * @property syncedCount Number of proxy endpoints appended.
 */
data class ProxySyncResult(
    val groupName: String,
    val groupId: String,
    val syncedCount: Int,
) {
    init {
        require(groupName.isNotBlank()) { "Group name must not be blank" }
        require(groupId.isNotBlank()) { "Group id must not be blank" }
        require(syncedCount >= 0) { "Synced count must not be negative" }
    }
}
