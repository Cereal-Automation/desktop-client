package com.cereal.client.domain.model.marketplace

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaginatedResultTest {
    private fun result(
        currentPage: Int,
        lastPage: Int,
    ) = PaginatedResult(
        items = listOf("a"),
        currentPage = currentPage,
        lastPage = lastPage,
        total = 1,
        perPage = 10,
    )

    @Test
    fun `hasNextPage is true only when not on the last page`() {
        assertTrue(result(currentPage = 1, lastPage = 3).hasNextPage)
        assertFalse(result(currentPage = 3, lastPage = 3).hasNextPage)
    }

    @Test
    fun `hasPreviousPage is true only when past the first page`() {
        assertFalse(result(currentPage = 1, lastPage = 3).hasPreviousPage)
        assertTrue(result(currentPage = 2, lastPage = 3).hasPreviousPage)
    }
}
