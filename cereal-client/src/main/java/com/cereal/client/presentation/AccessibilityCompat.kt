package com.cereal.client.presentation

import java.io.File
import java.util.Properties

private const val ASSISTIVE_TECHNOLOGIES_PROPERTY = "javax.accessibility.assistive_technologies"

/**
 * Prevents the fatal `AWTError: Assistive Technology not found: ...` crash without disabling
 * accessibility support for everyone.
 *
 * The JVM's [java.awt.Toolkit] reads the configured assistive-technology class names and calls
 * `Class.forName(...).newInstance()` on each; if a configured class is missing (e.g. a Windows user
 * has Java accessibility enabled but the AccessBridge JAR is absent) it wraps the failure in a fatal
 * [java.awt.AWTError]. See https://bugs.openjdk.org/browse/JDK-8285058.
 *
 * This mirrors the JVM's own resolution (system property first, then the `accessibility.properties`
 * file) and only rewrites [ASSISTIVE_TECHNOLOGIES_PROPERTY] when a configured class can't actually be
 * loaded — dropping the unavailable ones while keeping any that work. When nothing is configured, or
 * everything loads, the property is left untouched so screen readers keep working normally.
 *
 * Must be called before any AWT/Swing init (i.e. before `application()`).
 */
internal fun ensureLoadableAssistiveTechnologies() {
    val configured = resolveConfiguredAssistiveTechnologies()
    val resolved =
        resolveAvailableAssistiveTechnologies(configured) { name ->
            runCatching {
                Class.forName(name, false, ClassLoader.getSystemClassLoader())
            }.isSuccess
        }
    if (resolved != null) {
        System.setProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY, resolved)
    }
}

/**
 * Pure core: given the [configured] comma-separated class names and an [isLoadable] predicate,
 * returns the value to set for [ASSISTIVE_TECHNOLOGIES_PROPERTY], or `null` to mean "leave the JVM
 * default alone".
 */
internal fun resolveAvailableAssistiveTechnologies(
    configured: String?,
    isLoadable: (String) -> Boolean,
): String? {
    val names =
        configured
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    if (names.isEmpty()) return null // nothing configured → don't touch
    val loadable = names.filter(isLoadable)
    if (loadable.size == names.size) return null // all load → leave AT enabled
    return loadable.joinToString(",") // drop unloadable ones ("" if none load)
}

/**
 * Resolves the configured assistive-technology class names the same way [java.awt.Toolkit] does:
 * the system property takes precedence, otherwise the `assistive_technologies` key from the JDK's
 * `accessibility.properties` file (`<java.home>/conf` on JDK 9+, legacy `<java.home>/lib`).
 *
 * Defensive: a missing or unreadable file is treated as no file-level configuration.
 */
private fun resolveConfiguredAssistiveTechnologies(): String? {
    System.getProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY)?.let { return it }

    val javaHome = System.getProperty("java.home") ?: return null
    val candidates =
        listOf(
            File(javaHome, "conf/accessibility.properties"),
            File(javaHome, "lib/accessibility.properties"),
        )
    for (file in candidates) {
        if (!file.isFile) continue
        val value =
            runCatching {
                Properties().apply { file.inputStream().use { load(it) } }
            }.getOrNull()?.getProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY)
        if (value != null) return value
    }
    return null
}
