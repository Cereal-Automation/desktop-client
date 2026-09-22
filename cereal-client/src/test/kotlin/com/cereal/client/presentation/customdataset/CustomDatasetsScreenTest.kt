package com.cereal.client.presentation.customdataset

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test

/**
 * Renders [CustomDatasetsScreen] on in-memory repositories. The screen's `CustomDatasetRepository`
 * is backed by [com.cereal.client.infrastructure.data.repository.inmemory.InMemoryCustomDatasetRepository]
 * seeded from
 * [com.cereal.client.infrastructure.di.modules.SandboxSampleData.customDatasetGroups], so the table
 * renders the deterministic sandbox groups (e.g. "EU sneaker SKUs" with 1,240 rows).
 *
 * Covers the table layout (column headers, a seeded group name and its formatted row count) plus the
 * two row-level interactions: opening the edit-group dialog and the import-from-file dialog.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class CustomDatasetsScreenTest {
    @Test
    fun rendersTableHeadersAndSeededGroup() =
        runScreenTest {
            setScreenContent { CustomDatasetsScreen() }

            // Column headers are rendered upper-cased by PaneTableHeader.
            onNodeWithText("NAME").assertIsDisplayed()
            onNodeWithText("ROWS").assertIsDisplayed()
            onNodeWithText("CREATED").assertIsDisplayed()

            // A deterministic seeded group and its thousands-formatted row count. The separator is
            // locale-dependent, so format it the same way the row cell does ("%,d").
            onNodeWithText("EU sneaker SKUs").assertIsDisplayed()
            onNodeWithText("%,d".format(1240)).assertIsDisplayed()
        }

    @Test
    fun clickingEditOpensEditGroupDialog() =
        runScreenTest {
            setScreenContent { CustomDatasetsScreen() }

            // Each row carries an "Edit" icon action button (contentDescription = "Edit").
            onAllNodesWithText("EU sneaker SKUs").onFirst().assertIsDisplayed()
            onAllNodesWithContentDescription("Edit").onFirst().performClick()

            // EditGroupDialog renders with the "Edit group" title and a "Name" field.
            onNodeWithText("Edit group").assertIsDisplayed()
        }

    @Test
    fun clickingImportOpensImportFromFileDialog() =
        runScreenTest {
            setScreenContent { CustomDatasetsScreen() }

            onAllNodesWithText("EU sneaker SKUs").onFirst().assertIsDisplayed()
            // Row import action button (contentDescription = "Import").
            onAllNodesWithContentDescription("Import").onFirst().performClick()

            // ImportFromFileDialog renders its required-fields section and import description.
            onNodeWithText("Required Fields:").assertIsDisplayed()
            onNodeWithText("Upload your data in CSV format. Make sure it matches the required structure.")
                .assertIsDisplayed()
        }
}
