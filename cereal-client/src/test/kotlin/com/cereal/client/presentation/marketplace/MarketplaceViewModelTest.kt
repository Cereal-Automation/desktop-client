package com.cereal.client.presentation.marketplace

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.marketplace.GetMarketplaceScriptsInteractor
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.PaginatedResult
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.client.presentation.model.LoadState
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher)
    private val getMarketplaceScriptsInteractor: GetMarketplaceScriptsInteractor = mockk(relaxed = true)
    private val errorResolver: ErrorResolver = mockk(relaxed = true)

    private val startedInstances = mutableListOf<String>()

    private fun script(id: String) = MarketplaceScript(id = id, publicIdentifier = "pkg.$id", title = "Title $id")

    private fun page(
        items: List<MarketplaceScript>,
        currentPage: Int,
        lastPage: Int,
    ) = PaginatedResult(items = items, currentPage = currentPage, lastPage = lastPage, total = items.size, perPage = 20)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
        // Default: a single page with one item.
        coEvery { getMarketplaceScriptsInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<PaginatedResult<MarketplaceScript>, Exception>) -> Unit>()(
                SuspendableResult.Success(page(listOf(script("a")), currentPage = 1, lastPage = 1)),
            )
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        MarketplaceViewModel(
            scope = CoroutineScope(dispatcher),
            dispatcherProvider = dispatcherProvider,
            getMarketplaceScriptsInteractor = getMarketplaceScriptsInteractor,
            errorResolver = errorResolver,
            onStartNewInstance = { startedInstances.add(it) },
        )

    @Test
    fun `init loads the first page`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.scripts.value.size)
        assertTrue(viewModel.loadState.value is LoadState.NotLoading)
    }

    @Test
    fun `failure surfaces an error load state`() {
        coEvery { getMarketplaceScriptsInteractor(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<PaginatedResult<MarketplaceScript>, Exception>) -> Unit>()(
                SuspendableResult.Failure(RuntimeException("network down")),
            )
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.loadState.value is LoadState.Error)
    }

    @Test
    fun `loadNextPage appends the next page of results`() {
        coEvery { getMarketplaceScriptsInteractor(any(), any()) } coAnswers {
            val params = firstArg<GetMarketplaceScriptsInteractor.Params>()
            val result =
                if (params.page == 1) {
                    page(listOf(script("a")), currentPage = 1, lastPage = 2)
                } else {
                    page(listOf(script("b")), currentPage = 2, lastPage = 2)
                }
            secondArg<suspend (SuspendableResult<PaginatedResult<MarketplaceScript>, Exception>) -> Unit>()(
                SuspendableResult.Success(result),
            )
        }
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.scripts.value.size)
    }

    @Test
    fun `loadNextPage is ignored on the last page`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.loadNextPage()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.scripts.value.size)
    }

    @Test
    fun `onSearchQueryChanged debounces then reloads with the query`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val params = slot<GetMarketplaceScriptsInteractor.Params>()
        coEvery { getMarketplaceScriptsInteractor(capture(params), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<PaginatedResult<MarketplaceScript>, Exception>) -> Unit>()(
                SuspendableResult.Success(page(emptyList(), currentPage = 1, lastPage = 1)),
            )
        }

        viewModel.onSearchQueryChanged("scraper")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("scraper", viewModel.searchQuery.value)
        assertEquals("scraper", params.captured.search)
    }

    @Test
    fun `onPriceFilterChanged updates filter and reloads`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val params = slot<GetMarketplaceScriptsInteractor.Params>()
        coEvery { getMarketplaceScriptsInteractor(capture(params), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<PaginatedResult<MarketplaceScript>, Exception>) -> Unit>()(
                SuspendableResult.Success(page(emptyList(), currentPage = 1, lastPage = 1)),
            )
        }

        viewModel.onPriceFilterChanged(MarketplaceViewModel.PriceFilter.FREE)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MarketplaceViewModel.PriceFilter.FREE, viewModel.priceFilter.value)
        assertEquals(true, params.captured.isFree)
    }

    @Test
    fun `onCommunityFilterChanged updates filter and reloads`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val params = slot<GetMarketplaceScriptsInteractor.Params>()
        coEvery { getMarketplaceScriptsInteractor(capture(params), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<PaginatedResult<MarketplaceScript>, Exception>) -> Unit>()(
                SuspendableResult.Success(page(emptyList(), currentPage = 1, lastPage = 1)),
            )
        }

        viewModel.onCommunityFilterChanged(MarketplaceViewModel.CommunityFilter.COMMUNITY)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(MarketplaceViewModel.CommunityFilter.COMMUNITY, viewModel.communityFilter.value)
        assertEquals(true, params.captured.community)
    }

    @Test
    fun `script selection and dismissal update selectedScript`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val s = script("z")

        viewModel.onScriptSelected(s)
        assertEquals(s, viewModel.selectedScript.value)

        viewModel.onDismissDetail()
        assertNull(viewModel.selectedScript.value)
    }

    @Test
    fun `onStartNewInstance clears selection and forwards the identifier`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onScriptSelected(script("z"))

        viewModel.onStartNewInstance("pkg.z")

        assertNull(viewModel.selectedScript.value)
        assertEquals(listOf("pkg.z"), startedInstances)
    }

    @Test
    fun `refresh reloads the list`() {
        val viewModel = createViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.scripts.value.size)
    }
}
