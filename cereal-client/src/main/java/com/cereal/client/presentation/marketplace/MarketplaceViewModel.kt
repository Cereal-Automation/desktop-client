package com.cereal.client.presentation.marketplace

import androidx.compose.runtime.mutableStateOf
import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.marketplace.GetMarketplaceScriptsInteractor
import com.cereal.client.domain.model.marketplace.MarketplaceDirection
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.MarketplaceSort
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketplaceViewModel(
    private val scope: CoroutineScope,
    private val dispatcherProvider: CoroutinesDispatcherProvider,
    private val getMarketplaceScriptsInteractor: GetMarketplaceScriptsInteractor,
    private val errorResolver: ErrorResolver,
    private val onStartNewInstance: (publicIdentifier: String) -> Unit = {},
) {
    companion object {
        const val PAGE_SIZE = 20
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
    }

    val scripts = mutableStateOf<List<MarketplaceScript>>(emptyList())
    val loadState = mutableStateOf<LoadState>(LoadState.NotLoading())
    val searchQuery = mutableStateOf("")
    val errorAction = errorResolver.errorAction

    val selectedScript = mutableStateOf<MarketplaceScript?>(null)

    // Infinite scroll state
    val isLoadingMore = mutableStateOf(false)
    private var currentPage = 1
    private var lastPage = 1
    private var isLoading = false

    enum class PriceFilter {
        ALL,
        FREE,
        PAID,
    }

    enum class CommunityFilter {
        ALL,
        COMMUNITY,
    }

    val priceFilter = mutableStateOf(PriceFilter.ALL)
    val communityFilter = mutableStateOf(CommunityFilter.ALL)

    private var searchJob: Job? = null

    init {
        resetAndLoad()
    }

    fun onScriptSelected(script: MarketplaceScript) {
        selectedScript.value = script
    }

    fun onDismissDetail() {
        selectedScript.value = null
    }

    fun onStartNewInstance(publicIdentifier: String) {
        selectedScript.value = null
        this.onStartNewInstance.invoke(publicIdentifier)
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        searchJob =
            scope.launch(dispatcherProvider.io) {
                delay(SEARCH_DEBOUNCE_MILLIS)
                resetAndLoad()
            }
    }

    fun onPriceFilterChanged(filter: PriceFilter) {
        priceFilter.value = filter
        resetAndLoad()
    }

    fun onCommunityFilterChanged(filter: CommunityFilter) {
        communityFilter.value = filter
        resetAndLoad()
    }

    fun refresh() {
        resetAndLoad()
    }

    /** Called by the UI when the last visible item is near the bottom of the list. */
    fun loadNextPage() {
        if (isLoading || currentPage >= lastPage) return
        loadScripts(page = currentPage + 1, append = true)
    }

    private fun resetAndLoad() {
        currentPage = 1
        lastPage = 1
        scripts.value = emptyList()
        loadScripts(page = 1, append = false)
    }

    private fun loadScripts(
        page: Int,
        append: Boolean,
    ) {
        if (isLoading) return
        isLoading = true

        scope.launch(dispatcherProvider.io) {
            withContext(dispatcherProvider.main) {
                if (append) {
                    isLoadingMore.value = true
                } else {
                    loadState.value = LoadState.Loading()
                }
            }

            val isFreeParam =
                when (priceFilter.value) {
                    PriceFilter.ALL -> null
                    PriceFilter.FREE -> true
                    PriceFilter.PAID -> false
                }

            val communityParam =
                when (communityFilter.value) {
                    CommunityFilter.ALL -> null
                    CommunityFilter.COMMUNITY -> true
                }

            val params =
                GetMarketplaceScriptsInteractor.Params(
                    search = searchQuery.value.ifBlank { null },
                    sort = MarketplaceSort.RATING,
                    direction = MarketplaceDirection.DESC,
                    isFree = isFreeParam,
                    community = communityParam,
                    page = page,
                    perPage = PAGE_SIZE,
                )

            getMarketplaceScriptsInteractor(params) { result ->
                withContext(dispatcherProvider.main) {
                    isLoading = false
                    isLoadingMore.value = false
                    when (result) {
                        is SuspendableResult.Failure -> {
                            val message =
                                result.error.localizedMessage
                                    ?: "An unexpected error occurred."
                            loadState.value = LoadState.Error(message)
                        }

                        is SuspendableResult.Success -> {
                            val paginated = result.value
                            currentPage = paginated.currentPage
                            lastPage = paginated.lastPage
                            scripts.value =
                                if (append) {
                                    scripts.value + paginated.items
                                } else {
                                    paginated.items
                                }
                            loadState.value = LoadState.NotLoading(success = true)
                        }
                    }
                }
            }
        }
    }
}
