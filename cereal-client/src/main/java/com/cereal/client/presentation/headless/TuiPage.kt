package com.cereal.client.presentation.headless

import com.varabyte.kotter.foundation.input.Key

/**
 * One screen of the TUI body: a tab (`1`–`5`) or, later, a pre-tab screen such as login.
 *
 * Pages render plain lines; the frame truncates them to the terminal width and clips them to the
 * body height, so a page never has to worry about wrapping.
 */
interface TuiPage {
    /** Label shown in the tab bar (may carry a live count, e.g. "Waiting (2)"). */
    val title: String

    /** This page's keys, shown on the first footer line. Empty when it has none. */
    val keys: String get() = ""

    fun body(
        width: Int,
        height: Int,
    ): List<String>

    /** Returns true when the page consumed [key]; unconsumed keys fall through to the frame. */
    fun onKey(key: Key): Boolean = false

    /** Called each time the tabs come up after a sign-in: (re)start observing user data here. */
    fun onSignedIn() {}
}

/** Stand-in for a tab whose screen is not built yet. */
class PlaceholderPage(
    override val title: String,
) : TuiPage {
    override fun body(
        width: Int,
        height: Int,
    ) = listOf("", "  $title is not available yet.")
}
