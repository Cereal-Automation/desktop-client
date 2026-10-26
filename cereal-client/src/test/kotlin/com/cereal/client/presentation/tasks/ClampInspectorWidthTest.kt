package com.cereal.client.presentation.tasks

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClampInspectorWidthTest {
    @Test
    fun `a width below the minimum is raised to 400dp`() {
        assertEquals(400.dp, clampInspectorWidth(requested = 250.dp, available = 1200.dp))
    }

    @Test
    fun `a width that would squeeze the list below 400dp is capped`() {
        assertEquals(800.dp, clampInspectorWidth(requested = 1000.dp, available = 1200.dp))
    }

    @Test
    fun `the inspector keeps 400dp when the window is too narrow for both minimums`() {
        assertEquals(400.dp, clampInspectorWidth(requested = 500.dp, available = 600.dp))
    }
}
