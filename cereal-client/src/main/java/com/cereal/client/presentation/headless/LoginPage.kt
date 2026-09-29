package com.cereal.client.presentation.headless

import com.varabyte.kotter.foundation.input.CharKey
import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys

/**
 * Pre-tab login screen: a method list, then inline email and masked password inputs. Pure UI state;
 * [onSubmit] does the signing in and reports back through [showStatus] / [showError].
 *
 * No guest login, registration or password reset: the hint lines send the user to the marketplace.
 */
class LoginPage(
    private val registerUrl: String,
    private val forgotPasswordUrl: String,
    private val onSubmit: (email: String, password: String) -> Unit,
) : TuiPage {
    private enum class Step { METHODS, EMAIL, PASSWORD }

    override val title = "Sign in"

    @Volatile
    private var step = Step.METHODS

    @Volatile
    private var email = ""

    @Volatile
    private var password = ""

    @Volatile
    private var error: String? = null

    /** Non-null while a sign-in is in flight; input is ignored meanwhile. */
    @Volatile
    private var status: String? = null

    override val keys: String
        get() =
            when {
                status != null -> ""
                step == Step.METHODS -> "Enter choose"
                else -> "Tab next field · Enter sign in · Esc back"
            }

    fun showStatus(line: String) {
        status = line
    }

    fun showError(message: String?) {
        status = null
        error = message
        password = ""
        if (step != Step.METHODS) step = Step.PASSWORD
    }

    override fun body(
        width: Int,
        height: Int,
    ): List<String> =
        buildList {
            add("")
            add("  Sign in to Cereal")
            add("")
            add("  ${if (step == Step.METHODS) "›" else " "} Email and password")
            if (step != Step.METHODS) {
                add("")
                add("    Email     $email${cursor(Step.EMAIL)}")
                add("    Password  ${"*".repeat(password.length)}${cursor(Step.PASSWORD)}")
            }
            add("")
            add(status?.let { "  $it" } ?: error?.let { "  ! $it" } ?: "")
            add("")
            add("  No account? Register at $registerUrl")
            add("  Forgot your password? Reset it at $forgotPasswordUrl")
        }

    override fun onKey(key: Key): Boolean {
        if (status != null) return true
        if (step == Step.METHODS) {
            if (key != Keys.Enter) return false
            step = Step.EMAIL
            return true
        }
        when (key) {
            Keys.Escape -> step = Step.METHODS
            Keys.Tab, Keys.Up, Keys.Down -> step = if (step == Step.EMAIL) Step.PASSWORD else Step.EMAIL
            Keys.Backspace -> edit { it.dropLast(1) }
            Keys.Enter -> if (step == Step.EMAIL) step = Step.PASSWORD else submit()
            is CharKey -> edit { it + key.char }
        }
        return true
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
