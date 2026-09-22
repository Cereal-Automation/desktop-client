package com.cereal.client.infrastructure.provider

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.UserNotAuthenticatedException
import com.cereal.client.domain.model.exception.PaidSubscriptionActiveException
import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.domain.model.marketplace.PaginatedResult
import com.cereal.client.domain.model.marketplace.ScriptOwner
import com.cereal.client.domain.model.marketplace.ScriptSubscriptionResult
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.provider.MarketplaceProvider
import com.cereal.client.infrastructure.data.datasource.network.MarketplaceDataSource
import com.cereal.client.infrastructure.data.datasource.network.exception.ApiException
import com.cereal.client.infrastructure.data.datasource.network.exception.AuthenticationException
import com.cereal.client.infrastructure.data.datasource.network.exception.PaidSubscriptionException
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.SubscribeStatus
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.MarketplaceScript as MarketplaceScriptResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.Release as ReleaseResponse
import com.cereal.client.infrastructure.data.datasource.network.marketplace.responses.model.ScriptOwner as ScriptOwnerResponse

class MarketplaceProviderImpl(
    private val marketplaceDataSource: MarketplaceDataSource,
) : MarketplaceProvider {
    override suspend fun getMarketplaceScripts(
        search: String?,
        sort: MarketplaceSort,
        direction: MarketplaceDirection,
        community: Boolean?,
        isFree: Boolean?,
        page: Int,
        perPage: Int,
    ): PaginatedResult<MarketplaceScript> =
        translatingErrors {
            val response =
                marketplaceDataSource.getMarketplaceScripts(
                    search = search,
                    sort = sort.toApiValue(),
                    direction = direction.toApiValue(),
                    community = community,
                    isFree = isFree,
                    page = page,
                    perPage = perPage,
                )
            PaginatedResult(
                items = response.data.map { it.toDomain() },
                currentPage = response.currentPage,
                lastPage = response.lastPage,
                total = response.total,
                perPage = response.perPage,
            )
        }

    override suspend fun subscribeToScript(publicScriptId: String): ScriptSubscriptionResult = translatingErrors { marketplaceDataSource.subscribeToScript(publicScriptId).toDomain() }

    override suspend fun unsubscribeFromScript(publicScriptId: String) = translatingErrors { marketplaceDataSource.unsubscribeFromScript(publicScriptId) }

    /**
     * Translates the transport-level exceptions that can surface from the marketplace data source into
     * domain/application exceptions, so callers above the infrastructure boundary never see network
     * types. Only the known infrastructure exceptions are intercepted; everything else (including
     * [kotlinx.coroutines.CancellationException] and [java.io.IOException]) propagates untouched.
     */
    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
            block()
        } catch (_: AuthenticationException) {
            throw UserNotAuthenticatedException()
        } catch (_: PaidSubscriptionException) {
            throw PaidSubscriptionActiveException()
        } catch (e: ApiException) {
            throw CerealException(e.message, e)
        }

    @OptIn(ExperimentalTime::class)
    private fun MarketplaceScriptResponse.toDomain(): MarketplaceScript =
        MarketplaceScript(
            id = id,
            publicIdentifier = publicIdentifier,
            title = title,
            description = description,
            shortDescription = shortDescription,
            price = price,
            formattedPrice = formattedPrice,
            isFree = isFree,
            freeTrialEnabled = freeTrialEnabled,
            freeTrialDays = freeTrialDays,
            supportUrl = supportUrl,
            averageRating = averageRating,
            ratingCount = ratingCount,
            isCommunity = isCommunity,
            isNew = isNew,
            maintenanceMode = maintenanceMode,
            latestRelease = latestRelease?.toDomain(),
            developer = developer?.toDomain(),
            tags = tags,
            createdAt = createdAt?.toKotlinInstant(),
            updatedAt = updatedAt?.toKotlinInstant(),
        )

    private fun ReleaseResponse.toDomain(): Release =
        Release(
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = releaseNotes,
        )

    private fun ScriptOwnerResponse.toDomain(): ScriptOwner =
        ScriptOwner(
            name = name,
            avatarUrl = avatarUrl,
            verified = verified,
        )

    private fun SubscribeScriptResponse.toDomain(): ScriptSubscriptionResult =
        when (status) {
            SubscribeStatus.SUBSCRIBED -> {
                ScriptSubscriptionResult.Subscribed
            }

            SubscribeStatus.ALREADY_SUBSCRIBED -> {
                ScriptSubscriptionResult.AlreadySubscribed
            }

            SubscribeStatus.CHECKOUT_INITIATED -> {
                ScriptSubscriptionResult.CheckoutRequired(
                    checkoutUrl = checkoutUrl ?: error("checkout_initiated response missing checkout_url."),
                )
            }
        }

    private fun MarketplaceSort.toApiValue(): String =
        when (this) {
            MarketplaceSort.TITLE -> "title"
            MarketplaceSort.PRICE -> "price"
            MarketplaceSort.CREATED_AT -> "created_at"
            MarketplaceSort.UPDATED_AT -> "updated_at"
            MarketplaceSort.RATING -> "rating"
        }

    private fun MarketplaceDirection.toApiValue(): String =
        when (this) {
            MarketplaceDirection.ASC -> "asc"
            MarketplaceDirection.DESC -> "desc"
        }
}
