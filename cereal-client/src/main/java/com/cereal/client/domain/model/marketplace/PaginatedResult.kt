package com.cereal.client.domain.model.marketplace

/**
 * A generic wrapper for paginated results.
 *
 * @param T The type of items in the page.
 * @property items The items on the current page.
 * @property currentPage The current page number (1-based).
 * @property lastPage The last available page number.
 * @property total The total number of items across all pages.
 * @property perPage The number of items per page.
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
    val perPage: Int,
) {
    val hasNextPage: Boolean get() = currentPage < lastPage
    val hasPreviousPage: Boolean get() = currentPage > 1
}
