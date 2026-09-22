package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cereal.client.presentation.model.LoadState
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.EmptyListView
import com.cereal.client.presentation.view.LoadingView
import com.cereal.client.presentation.view.table.PaneScreen
import com.cereal.client.presentation.view.table.PaneTablePadding
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.marketplace
import com.cereal_automation.cereal_client.generated.resources.marketplace_no_scripts_found
import com.cereal_automation.cereal_client.generated.resources.marketplace_retry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent

@Composable
fun MarketplaceScreen(onStartNewInstance: (publicIdentifier: String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember {
            KoinJavaComponent.get<MarketplaceViewModel>(
                MarketplaceViewModel::class.java,
                parameters = { parametersOf(scope, onStartNewInstance) },
            )
        }

    MarketplaceContent(viewModel)
}

@Composable
private fun MarketplaceContent(viewModel: MarketplaceViewModel) {
    val scripts = viewModel.scripts.value
    val loadState = viewModel.loadState.value
    val searchQuery = viewModel.searchQuery.value
    val priceFilter = viewModel.priceFilter.value
    val communityFilter = viewModel.communityFilter.value
    val selectedScript = viewModel.selectedScript.value
    val isLoadingMore = viewModel.isLoadingMore.value

    selectedScript?.let { script ->
        ScriptDetailDialog(
            script = script,
            onDismiss = viewModel::onDismissDetail,
            onStartNewInstance = viewModel::onStartNewInstance,
        )
    }

    PaneScreen(
        title = stringResource(Res.string.marketplace),
        toolbarActions = {
            FilterMenuButton(
                priceFilter = priceFilter,
                onPriceFilterChanged = viewModel::onPriceFilterChanged,
                communityFilter = communityFilter,
                onCommunityFilterChanged = viewModel::onCommunityFilterChanged,
            )
        },
    ) {
        MarketplaceSearchBar(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
        )
        ActiveFilterChips(
            priceFilter = priceFilter,
            communityFilter = communityFilter,
            onClearPriceFilter = { viewModel.onPriceFilterChanged(MarketplaceViewModel.PriceFilter.ALL) },
            onClearCommunityFilter = { viewModel.onCommunityFilterChanged(MarketplaceViewModel.CommunityFilter.ALL) },
        )
        HorizontalDivider(thickness = 1.dp, color = CerealTheme.colorScheme.border)

        when (loadState) {
            is LoadState.Loading -> {
                LoadingView()
            }

            is LoadState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CerealText(
                        modifier = Modifier.fillMaxWidth(0.5f),
                        text = loadState.message,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CerealButton(
                        onClick = viewModel::refresh,
                        text = stringResource(Res.string.marketplace_retry),
                    )
                }
            }

            is LoadState.NotLoading -> {
                if (scripts.isEmpty() && loadState.success) {
                    EmptyListView(stringResource(Res.string.marketplace_no_scripts_found))
                } else {
                    val gridState = rememberLazyGridState()

                    LaunchedEffect(gridState) {
                        snapshotFlow {
                            val layoutInfo = gridState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisible >= totalItems - 4
                        }.distinctUntilChanged()
                            .filter { it }
                            .collect { viewModel.loadNextPage() }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 400.dp),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = PaneTablePadding, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(scripts, key = { it.id }) { script ->
                            ScriptCard(
                                script = script,
                                onClick = { viewModel.onScriptSelected(script) },
                            )
                        }

                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = CerealTheme.spacing.xl),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CerealCircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
