package com.cereal.client.infrastructure.provider

import com.cereal.client.domain.model.exception.InvalidProxyProviderTokenException
import com.cereal.client.domain.model.exception.NoProxyProviderSubUserException
import com.cereal.client.domain.model.exception.ProxyProviderConnectivityException
import com.cereal.client.domain.model.exception.ProxyProviderNotConnectedException
import com.cereal.client.domain.model.exception.ProxySyncFailedException
import com.cereal.client.domain.model.proxy.Proxy
import com.cereal.client.domain.model.proxy.ProxyGeoCatalogue
import com.cereal.client.domain.model.proxy.ProxyGroup
import com.cereal.client.domain.model.proxy.ProxyProviderConnector
import com.cereal.client.domain.model.proxy.ProxySession
import com.cereal.client.domain.model.proxy.ProxySyncConfig
import com.cereal.client.domain.model.proxy.ProxySyncResult
import com.cereal.client.domain.model.proxy.ProxyVendor
import com.cereal.client.domain.model.proxy.SyncTarget
import com.cereal.client.domain.provider.ProxyConnectionProvider
import com.cereal.client.domain.repository.ProxyProviderConnectorRepository
import com.cereal.client.domain.repository.ProxyProviderCredentialRepository
import com.cereal.client.domain.repository.ProxyRepository
import com.cereal.client.infrastructure.data.datasource.network.ProxyProviderDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.marsproxies.dto.MarsProxiesGenerateProxyListRequest
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Default [ProxyConnectionProvider]. Validates the token against the proxy vendor, resolves the
 * sub-user with the most available traffic, stores the token as a sensitive credential, and delegates
 * connector persistence to [ProxyProviderConnectorRepository]. Translates transport exceptions to
 * domain exceptions at this boundary.
 *
 * Only [ProxyVendor.MARSPROXIES] is connectable today; other providers are guarded out.
 */
@OptIn(ExperimentalTime::class)
class ProxyConnectionProviderImpl(
    private val proxyProviderDataSource: ProxyProviderDataSource,
    private val connectorRepository: ProxyProviderConnectorRepository,
    private val credentialRepository: ProxyProviderCredentialRepository,
    private val proxyRepository: ProxyRepository,
) : ProxyConnectionProvider {
    override suspend fun connect(
        provider: ProxyVendor,
        token: String,
    ): ProxyProviderConnector {
        requireSupported(provider)

        val account =
            translatingErrors(provider) { proxyProviderDataSource.validateAndFetchAccount(token) }
        val subUsers =
            translatingErrors(provider) { proxyProviderDataSource.fetchSubUsers(token) }

        val resolved =
            subUsers.maxByOrNull { it.trafficAvailable }
                ?: throw NoProxyProviderSubUserException(provider.name)

        // Store the token as a sensitive credential (redacted from logs) before persisting the record,
        // so a connector record never references a credential that isn't stored.
        storeCredential(provider, token)

        val now = Clock.System.now()
        val connector =
            ProxyProviderConnector(
                provider = provider,
                connectedAt = now,
                lastSyncAt = now,
                subUserHash = resolved.hash,
                availableTrafficGb = account.availableTrafficGb,
                subUserCount = account.subUserCount,
                credentialKey = credentialKeyFor(provider),
            )
        connectorRepository.upsertConnector(connector)
        return connector
    }

    override suspend fun syncProxies(
        provider: ProxyVendor,
        config: ProxySyncConfig,
    ): ProxySyncResult {
        requireSupported(provider)

        val connector =
            connectorRepository.getConnectedProvider(provider).first()
                ?: throw ProxyProviderNotConnectedException(provider.name)
        val token = currentToken(provider)
        if (token.isBlank()) throw ProxyProviderNotConnectedException(provider.name)

        val request = buildGenerateRequest(connector.subUserHash, config)

        val rendered =
            try {
                proxyProviderDataSource.generateProxies(token, request)
            } catch (e: IOException) {
                throw ProxySyncFailedException(provider.name, e)
            }

        // Rotating represents the whole pool with a single endpoint; storing N identical rotating
        // strings would add nothing. Sticky keeps every distinct endpoint that was returned.
        val endpoints =
            when (config.session) {
                ProxySession.ROTATING -> rendered.take(1)
                ProxySession.STICKY -> rendered
            }
        val proxies = endpoints.mapNotNull { parseProxy(it) }
        if (proxies.isEmpty()) throw ProxySyncFailedException(provider.name)

        val geoLabel = buildGeoLabel(config)
        val (groupId, groupName) = resolveTargetGroup(config.target)

        proxies.forEach { proxy ->
            proxyRepository.createOrUpdateProxy(proxy, groupReference(groupId, groupName))
        }
        proxyRepository.stampProxyGroupProvider(groupId, provider, geoLabel)

        // Refresh last-sync on the connector record.
        connectorRepository.upsertConnector(connector.copy(lastSyncAt = Clock.System.now()))

        return ProxySyncResult(groupName = groupName, groupId = groupId, syncedCount = proxies.size)
    }

    override suspend fun disconnect(provider: ProxyVendor) {
        requireSupported(provider)
        clearCredential(provider)
        connectorRepository.deleteConnector(provider)
    }

    private inline fun <T> translatingErrors(
        provider: ProxyVendor,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (_: AuthenticationException) {
            throw InvalidProxyProviderTokenException(provider.name)
        } catch (e: IOException) {
            throw ProxyProviderConnectivityException(provider.name, e)
        }

    private suspend fun storeCredential(
        provider: ProxyVendor,
        token: String,
    ) {
        credentialRepository.setToken(provider, token)
    }

    private suspend fun clearCredential(provider: ProxyVendor) {
        credentialRepository.setToken(provider, "")
    }

    private fun credentialKeyFor(provider: ProxyVendor): String = credentialRepository.credentialKey(provider)

    private suspend fun currentToken(provider: ProxyVendor): String = credentialRepository.getToken(provider).first()

    private fun buildGenerateRequest(
        subUserHash: String,
        config: ProxySyncConfig,
    ): MarsProxiesGenerateProxyListRequest {
        val rotation =
            when (config.session) {
                ProxySession.STICKY -> ROTATION_STICKY
                ProxySession.ROTATING -> ROTATION_ROTATING
            }
        return MarsProxiesGenerateProxyListRequest(
            format = CONNECTION_FORMAT,
            hostname = GATEWAY_HOST,
            port = GATEWAY_HTTP_PORT,
            rotation = rotation,
            lifetime = if (config.session == ProxySession.STICKY) STICKY_LIFETIME else null,
            subUserHash = subUserHash,
            location = buildLocation(config),
            // Rotating collapses to a single endpoint at our boundary; don't request N rotating strings.
            proxyCount = if (config.session == ProxySession.STICKY) config.count else null,
        )
    }

    /**
     * Composes the provider `location` query value from the geo selection.
     *
     * Provisional: MarsProxies encodes geo in the password (`_country-us`, `_state-texas`, `_city-dallas`).
     * We send a readable composed value here and rely on the data-source seam + live-token verification to
     * confirm the exact param semantics. Returns `null` when no targeting is requested.
     */
    private fun buildLocation(config: ProxySyncConfig): String? {
        val parts =
            buildList {
                add("country-${config.country.lowercase()}")
                config.state?.let { add("state-${it.lowercase().replace(" ", "")}") }
                config.city?.let { add("city-${it.lowercase().replace(" ", "")}") }
            }
        return parts.joinToString(LOCATION_SEPARATOR).ifBlank { null }
    }

    /** Human-readable geo label, e.g. "United States · Texas · Dallas" (blank parts omitted). */
    private fun buildGeoLabel(config: ProxySyncConfig): String =
        listOfNotNull(
            ProxyGeoCatalogue.countryName(config.country),
            config.state,
            config.city,
        ).joinToString(GEO_LABEL_SEPARATOR)

    /** Creates a new group when needed; returns the resolved (id, name). */
    private suspend fun resolveTargetGroup(target: SyncTarget): Pair<String, String> =
        when (target) {
            is SyncTarget.NewGroup -> {
                val id = UUID.randomUUID().toString()
                proxyRepository.createProxyGroup(
                    ProxyGroup(id = id, name = target.name, numberOfItems = 0, items = emptySequence()),
                )
                id to target.name
            }

            is SyncTarget.ExistingGroup -> {
                val group =
                    proxyRepository.getProxyGroups().first().firstOrNull { it.id == target.groupId }
                        ?: error("Target proxy group ${target.groupId} not found")
                group.id to group.name
            }
        }

    private fun groupReference(
        groupId: String,
        groupName: String,
    ): ProxyGroup = ProxyGroup(id = groupId, name = groupName, numberOfItems = 0, items = emptySequence())

    /**
     * Parses a rendered `host:port:user:pass` connection string into a domain [Proxy].
     *
     * The password is the remainder after the third `:`, so passwords containing `:` (or underscores)
     * survive intact. Returns `null` for malformed lines so a single bad row doesn't fail the batch.
     */
    private fun parseProxy(connectionString: String): Proxy? {
        val parts = connectionString.split(":", limit = MIN_CONNECTION_PARTS)
        if (parts.size < MIN_CONNECTION_PARTS) return null
        val host = parts[0]
        val port = parts[1].toIntOrNull() ?: return null
        val username = parts[2]
        val password = parts[PASSWORD_PART_INDEX]
        if (host.isBlank() || username.isBlank() || password.isBlank()) return null
        return Proxy(
            id = UUID.randomUUID(),
            address = host,
            port = port,
            username = username,
            password = password,
        )
    }

    private fun requireSupported(provider: ProxyVendor) {
        require(provider.available) { "Provider ${provider.name} is not connectable yet." }
    }

    private companion object {
        const val GATEWAY_HOST = "ultra.marsproxies.com"
        const val GATEWAY_HTTP_PORT = 44443
        const val CONNECTION_FORMAT = "{hostname}:{port}:{username}:{password}"
        const val ROTATION_STICKY = "sticky"
        const val ROTATION_ROTATING = "rotating"
        const val STICKY_LIFETIME = "30m"
        const val LOCATION_SEPARATOR = "_"
        const val GEO_LABEL_SEPARATOR = " · "

        // host:port:user:pass — split with this limit keeps any ':' in the password as the remainder.
        const val MIN_CONNECTION_PARTS = 4

        // The password is the 4th field (index 3): the remainder after the third ':'.
        const val PASSWORD_PART_INDEX = 3
    }
}
