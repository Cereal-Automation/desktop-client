package com.cereal.client.presentation.headless

import com.cereal.client.domain.model.auth.OAuthProvider
import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys

/**
 * Pre-tab login screen: a method list, then either inline email and masked password inputs or the
 * pasted sign-in steps for Google/Discord. Pure UI state; [onSubmit] / [onSso] do the signing in and
 * report back through [showStatus] / [showSignInUrl] / [showError].
 *
 * No guest login, registration or password reset: the hint lines send the user to the marketplace.
 */
class LoginPage(
    private val registerUrl: String,
    private val forgotPasswordUrl: String,
    private val onSubmit: (email: String, password: String) -> Unit,
    private val onSso: (OAuthProvider) -> Unit,
    private val onPaste: (String) -> Unit,
    private val onCancelSso: () -> Unit,
) : TuiPage {
    private enum class Step { METHODS, EMAIL, PASSWORD, PASTE }

    override val title = "Sign in"

    @Volatile
    private var step = Step.METHODS

    /** Index into [methods]. */
    @Volatile
    private var method = 0

    @Volatile
    private var email = ""

    @Volatile
    private var password = ""

    @Volatile
    private var pasted = ""

    /** The SSO authorize URL; null until the sign-in flow hands it over. */
    @Volatile
    private var signInUrl: String? = null

    @Volatile
    private var error: String? = null

    /** Non-null while a sign-in is in flight; input is ignored meanwhile. */
    @Volatile
    private var status: String? = null

    override val keys: String
        get() =
            when {
                status != null -> ""
                step == Step.METHODS -> "↑↓ choose · Enter sign in"
                step == Step.PASTE -> "Enter submit · Esc cancel"
                else -> "Tab next field · Enter sign in · Esc back"
            }

    override val capturesKeys: Boolean get() = step != Step.METHODS || status != null

    fun showStatus(line: String) {
        status = line
    }

    fun showSignInUrl(url: String) {
        signInUrl = url
    }

    fun showError(message: String?) {
        status = null
        error = message
        password = ""
        step = if (step == Step.EMAIL || step == Step.PASSWORD) Step.PASSWORD else Step.METHODS
    }

    override fun body(
        width: Int,
        height: Int,
    ): List<String> =
        buildList {
            add("")
            if (step == Step.PASTE) {
                addAll(pasteSteps(width))
            } else {
                add("  Sign in to Cereal")
                add("")
                methods.forEachIndexed { i, provider ->
                    add("  ${if (step == Step.METHODS && i == method) "›" else " "} ${label(provider)}")
                    if (provider == null && step != Step.METHODS) {
                        add("")
                        add("    Email     $email${cursor(Step.EMAIL)}")
                        add("    Password  ${"*".repeat(password.length)}${cursor(Step.PASSWORD)}")
                        add("")
                    }
                }
            }
            add("")
            add(status?.let { "  $it" } ?: error?.let { "  ! $it" } ?: "")
            if (step != Step.PASTE) {
                add("")
                add("  No account? Register at $registerUrl")
                add("  Forgot your password? Reset it at $forgotPasswordUrl")
            }
        }

    private fun pasteSteps(width: Int): List<String> {
        val url = signInUrl ?: return listOf("  ${label(methods[method])}", "", "  Preparing the sign-in link…")
        // Show the tail of a long paste so the cursor stays in view.
        val field = "    Address  "
        val room = (width - field.length - 2).coerceAtLeast(1)
        val shown = if (pasted.length <= room) pasted else "…" + pasted.takeLast(room - 1)
        return listOf(
            "  ${label(methods[method])}",
            "",
            "  1. Open this link in any browser and sign in:",
            linkLine(url),
            "  2. The browser then fails to load a 127.0.0.1 page. Copy its address.",
            "  3. Paste it below and press Enter within about a minute.",
            "",
            "$field$shown${cursor(Step.PASTE)}",
            "",
            "  If you forwarded the 127.0.0.1 port over SSH, sign-in finishes by itself.",
        )
    }

    override fun onKey(key: Key): Boolean {
        if (status != null) return true
        when (step) {
            Step.METHODS -> onMethodKey(key)
            Step.PASTE -> onPasteKey(key)
            else -> onFormKey(key)
        }
        return step != Step.METHODS || key == Keys.Up || key == Keys.Down || key == Keys.Enter
    }

    private fun onMethodKey(key: Key) {
        when (key) {
            Keys.Up -> method = (method - 1).mod(methods.size)
            Keys.Down -> method = (method + 1).mod(methods.size)
            Keys.Enter -> methods[method]?.let(::startSso) ?: run { step = Step.EMAIL }
        }
    }

    private fun startSso(provider: OAuthProvider) {
        error = null
        pasted = ""
        signInUrl = null
        step = Step.PASTE
        onSso(provider)
    }

    private fun onPasteKey(key: Key) {
        when (key) {
            Keys.Escape -> {
                onCancelSso()
                step = Step.METHODS
            }

            Keys.Backspace -> {
                pasted = pasted.dropLast(1)
            }

            Keys.Enter -> {
                if (pasted.isNotBlank()) {
                    error = null
                    status = "Signing in…"
                    onPaste(pasted)
                }
            }

            is CharKey -> {
                pasted += key.char
            }
        }
    }

    private fun onFormKey(key: Key) {
        when (key) {
            Keys.Escape -> step = Step.METHODS
            Keys.Tab, Keys.Up, Keys.Down -> step = if (step == Step.EMAIL) Step.PASSWORD else Step.EMAIL
            Keys.Backspace -> edit { it.dropLast(1) }
            Keys.Enter -> if (step == Step.EMAIL) step = Step.PASSWORD else submit()
            is CharKey -> edit { it + key.char }
        }
    }

    private fun submit() {
        error = null
        status = "Signing in…"
        onSubmit(email.trim(), password)
    }

    private fun edit(change: (String) -> String) {
        if (step == Step.EMAIL) email = change(email) else password = change(password)
    }

    private fun cursor(field: Step) = if (step == field && status == null) "_" else ""

    private companion object {
        /** Null is email and password. */
        val methods = listOf(null, OAuthProvider.GOOGLE, OAuthProvider.DISCORD)

        fun label(provider: OAuthProvider?) =
            when (provider) {
                null -> "Email and password"
                OAuthProvider.GOOGLE -> "Sign in with Google"
                OAuthProvider.DISCORD -> "Sign in with Discord"
            }
    }
}

/** A pre-tab screen that only shows a status line (booting, retrying). */
class StatusPage(
    private val line: String,
) : TuiPage {
    override val title = "Cereal"

    override fun body(
        width: Int,
        height: Int,
    ) = listOf("", "  $line")
}
