package com.cereal.client.presentation.marketplace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.marketplace.ScriptOwner
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.repository.ScriptRepository
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import fixtures.aScriptPackage
import org.koin.java.KoinJavaComponent
import testutil.runScreenTest
import testutil.setScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Renders [ScriptDetailDialog] for a fully-populated [MarketplaceScript] and verifies the metadata
 * sections render, plus that the dismiss and start-new-instance callbacks fire on interaction.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class, ExperimentalTime::class)
class ScriptDetailDialogTest {
    @Test
    fun rendersScriptDetailSections() =
        runScreenTest {
            setScreenContent {
                ScriptDetailDialog(
                    script = aMarketplaceScript(),
                    onDismiss = {},
                )
            }

            // The detail body lives inside a vertical scroll container, so sections below the fold are
            // laid out and present in the semantics tree but may be clipped from view; assert presence.
            // Metadata-row labels render verbatim; DetailSection titles are uppercased by the screen.
            onNodeWithText("Version").assertExists()
            onNodeWithText("Developer").assertExists()
            onNodeWithText("Rating").assertExists()
            onNodeWithText("Updated").assertExists()
            onNodeWithText("Release Notes".uppercase()).assertExists()
            onNodeWithText("Tags".uppercase()).assertExists()
        }

    @Test
    fun clickingCloseInvokesOnDismiss() =
        runScreenTest {
            var dismissed = false
            setScreenContent {
                ScriptDetailDialog(
                    script = aMarketplaceScript(),
                    onDismiss = { dismissed = true },
                )
            }

            // The close action carries the "Close" content description (from Res.string.close).
            onNodeWithContentDescription("Close").performClick()
            waitForIdle()

            assertTrue(dismissed, "onDismiss should fire when the close button is clicked")
        }

    @Test
    fun clickingStartNewInstanceInvokesCallbackWithPublicIdentifier() =
        runScreenTest {
            // Seed the in-memory script repository so the script reads as already installed, which is
            // the only install state that surfaces the "Start new instance" button. The interactor
            // matches an installed package by its manifest packageName, so seed it with the script's
            // public identifier.
            val scriptRepository =
                KoinJavaComponent.get<ScriptRepository>(ScriptRepository::class.java) as InMemoryScriptRepository
            scriptRepository.seed(listOf(aScriptPackage(SCRIPT_PUBLIC_ID)))

            val started = mutableListOf<String>()
            setScreenContent {
                ScriptDetailDialog(
                    script = aMarketplaceScript(),
                    onDismiss = {},
                    onStartNewInstance = { started.add(it) },
                )
            }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Start new instance")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Start new instance").performClick()
            waitForIdle()

            assertEquals(listOf(SCRIPT_PUBLIC_ID), started)
        }

    private companion object {
        private const val SCRIPT_PUBLIC_ID = "com.cereal.test.detail"

        private fun aMarketplaceScript(): MarketplaceScript =
            MarketplaceScript(
                id = "detail-1",
                publicIdentifier = SCRIPT_PUBLIC_ID,
                title = "Detail Test Script",
                description = "A script used to exercise the detail dialog.",
                isFree = true,
                averageRating = 4.5,
                ratingCount = 10,
                latestRelease =
                    Release(
                        versionName = "1.2.3",
                        versionCode = 3L,
                        releaseNotes = "Fixed a bunch of bugs.",
                    ),
                developer = ScriptOwner(name = "Detail Dev"),
                tags = listOf("Testing"),
                updatedAt = Instant.parse("2026-01-15T00:00:00Z"),
            )
    }
}
