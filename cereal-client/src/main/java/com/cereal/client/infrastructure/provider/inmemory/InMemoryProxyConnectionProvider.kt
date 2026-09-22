package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.model.exception.InvalidProxyProviderTokenException
import com.cereal.client.domain.model.exception.NoProxyProviderSubUserException
import com.cereal.client.domain.model.exception.ProxyProviderConnectivityException
import com.cereal.client.domain.model.exception.ProxyProviderNotConnectedException
import com.cereal.client.domain.model.exception.ProxySyncFailedException
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGeoCatalogue
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyProviderAccount
import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxySubUser
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.domain.model.proxy.ProxySyncResult
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.proxy.SyncTarget
import com.cereal.client.domain.provider.ProxyConnectionProvider
import com.cereal.client.domain.repository.ProxyProviderConnectorRepository
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.datasource.preference.ApplicationPreferenceKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * In-memory [ProxyConnectionProvider]. Backs the `mock` flavor and doubles as a refactor-proof test
 * fake: no network, no Room. Connector state lives in the shared [connectorRepository].
 *
 * The connect contract mirrors the real provider — a wrong token, a zero-sub-user account, and a
 * forced connectivity failure each raise the same domain exceptions — so interactor/ViewModel tests
 * exercise the real paths without MockK.
 */
@OptIn(ExperimentalTime::class)
class InMemoryProxyConnectionProvider(
    private val connectorRepository: ProxyProviderConnectorRepository,
    private val validToken: String = DEMO_TOKEN,
    private val account: ProxyProviderAccount = DEMO_ACCOUNT,
    private val subUsers: List<ProxySubUser> = DEMO_SUBUSERS,
    private val connectivityFails: Boolean = false,
    private val syncFails: Boolean = false,
    private val proxyRepository: ProxyRepository? = null,
) : ProxyConnectionProvider {
    private val mutex = Mutex()

    /** Synced proxies kept in-memory when no [proxyRepository] is wired (test introspection helper). */
    private val syncedProxiesByGroup = mutableMapOf<String, MutableList<Proxy>>()

    /** Returns the proxies synced into [groupId] when no external [proxyRepository] backs this fake. */
    suspend fun syncedProxies(groupId: String): List<Proxy> = mutex.withLock { syncedProxiesByGroup[groupId]?.toList() ?: emptyList() }

    override suspend fun connect(
        provider: ProxyVendor,
        token: String,
    ): ProxyProviderConnector {
        if (connectivityFails) throw ProxyProviderConnectivityException(provider.name)
        if (token.trim() != validToken) throw InvalidProxyProviderTokenException(provider.name)

        val resolved =
            subUsers.maxByOrNull { it.trafficAvailable }
                ?: throw NoProxyProviderSubUserException(provider.name)

        val now = Clock.System.now()
        val existing = connectorRepository.getConnectedProvider(provider).first()
        val connector =
            ProxyProviderConnector(
                provider = provider,
                connectedAt = existing?.connectedAt ?: now,
                lastSyncAt = now,
                subUserHash = resolved.hash,
                availableTrafficGb = account.availableTrafficGb,
                subUserCount = account.subUserCount,
                credentialKey = ApplicationPreferenceKey.KEY_MARS_PROXIES_API_TOKEN,
            )
        connectorRepository.upsertConnector(connector)
        return connector
    }

    override suspend fun syncProxies(
        provider: ProxyVendor,
        config: ProxySyncConfig,
    ): ProxySyncResult {
        val connector =
            connectorRepository.getConnectedProvider(provider).first()
                ?: throw ProxyProviderNotConnectedException(provider.name)
        if (syncFails) throw ProxySyncFailedException(provider.name)

        val endpointCount =
            when (config.session) {
                ProxySession.ROTATING -> 1
                ProxySession.STICKY -> config.count
            }
        val proxies =
            (1..endpointCount).map { i ->
                Proxy(
                    id = UUID.randomUUID(),
                    address = GATEWAY_HOST,
                    port = GATEWAY_HTTP_PORT,
                    username = "${connector.subUserHash}-${config.country.lowercase()}-$i",
                    password = "pw_${UUID.randomUUID().toString().take(SESSION_TOKEN_LENGTH)}_session",
                )
            }

        val geoLabel =
            listOfNotNull(ProxyGeoCatalogue.countryName(config.country), config.state, config.city)
                .joinToString(" · ")
        val (groupId, groupName) = resolveTargetGroup(provider, config.target, geoLabel)

        val repo = proxyRepository
        if (repo != null) {
            proxies.forEach { repo.createOrUpdateProxy(it, groupReference(groupId, groupName)) }
            repo.stampProxyGroupProvider(groupId, provider, geoLabel)
        } else {
            mutex.withLock {
                syncedProxiesByGroup.getOrPut(groupId) { mutableListOf() }.addAll(proxies)
            }
        }

        connectorRepository.upsertConnector(connector.copy(lastSyncAt = Clock.System.now()))
        return ProxySyncResult(groupName = groupName, groupId = groupId, syncedCount = proxies.size)
    }

    private suspend fun resolveTargetGroup(
        provider: ProxyVendor,
        target: SyncTarget,
        geoLabel: String,
    ): Pair<String, String> =
        when (target) {
            is SyncTarget.NewGroup -> {
                val id = UUID.randomUUID().toString()
                proxyRepository?.createProxyGroup(
                    ProxyGroup(id = id, name = target.name, numberOfItems = 0, items = emptySequence(), provider = provider, geoLabel = geoLabel),
                )
                id to target.name
            }

            is SyncTarget.ExistingGroup -> {
                val name =
                    proxyRepository
                        ?.getProxyGroups()
                        ?.first()
                        ?.firstOrNull { it.id == target.groupId }
                        ?.name
                        ?: target.groupId
                target.groupId to name
            }
        }

    private fun groupReference(
        groupId: String,
        groupName: String,
    ): ProxyGroup = ProxyGroup(id = groupId, name = groupName, numberOfItems = 0, items = emptySequence())

    override suspend fun disconnect(provider: ProxyVendor) {
        connectorRepository.deleteConnector(provider)
    }

    companion object {
        const val DEMO_TOKEN = "mp_live_7F3KQ29ApLx8sVbN4dWc"
        private const val GATEWAY_HOST = "ultra.marsproxies.com"
        private const val GATEWAY_HTTP_PORT = 44443

        // Length of the synthetic per-endpoint session token in the fake password.
        private const val SESSION_TOKEN_LENGTH = 8

        private val DEMO_ACCOUNT = ProxyProviderAccount(availableTrafficGb = 84.2, subUserCount = 3, accountHash = "acct_mp_7F3K29A")

        private val DEMO_SUBUSERS =
            listOf(
                ProxySubUser("su_1", "hash_low", "mp-us-1", "pw1", trafficAvailable = 12.0, trafficUsed = 4.0),
                ProxySubUser("su_2", "hash_top", "mp-us-2", "pw2", trafficAvailable = 60.0, trafficUsed = 2.0),
                ProxySubUser("su_3", "hash_mid", "mp-us-3", "pw3", trafficAvailable = 30.0, trafficUsed = 8.0),
            )
    }
}
