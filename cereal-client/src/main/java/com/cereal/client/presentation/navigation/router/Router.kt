package com.cereal.client.presentation.navigation.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.cereal.client.presentation.navigation.backpress.BackPressHandler
import com.cereal.client.presentation.navigation.backpress.LocalBackPressHandler

private val backStackMap: MutableMap<Any, BackStack<*>> =
    mutableMapOf()

/**
 * Currently only used for deep link based Routing.
 *
 * Can be set to store a list of Routing elements of different types.
 * The idea is that when we walk through this list in sequence - provided that the sequence
 * is correct - we can set the app into any state that is a combination of Routing on different levels.
 *
 * See [com.example.lifelike.DeepLinkKt.parseProfileDeepLink] in :app-lifelike module for usage
 * example.
 */
val LocalRouting: ProvidableCompositionLocal<List<Any>> =
    compositionLocalOf {
        listOf()
    }

@Composable
fun <T> Router(
    contextId: String,
    defaultRouting: T,
    children: @Composable (BackStack<T>) -> Unit,
) {
    val route = LocalRouting.current

    @Suppress("UNCHECKED_CAST")
    val routingFromAmbient = route.firstOrNull() as? T
    val downStreamRoute = if (route.size > 1) route.takeLast(route.size - 1) else emptyList()

    val upstreamHandler = LocalBackPressHandler.current
    val localHandler = remember { BackPressHandler("${upstreamHandler.id}.$contextId") }
    val backStack = fetchBackStack(localHandler.id, defaultRouting, routingFromAmbient)
    val handleBackPressHere: () -> Boolean = { localHandler.handle() || backStack.pop() }

    DisposableEffect(localHandler) {
        upstreamHandler.children.add(handleBackPressHere)
        onDispose {
            upstreamHandler.children.remove(handleBackPressHere)
            backStackMap.remove(localHandler.id)
        }
    }

    @Composable
    fun Observe(body: @Composable () -> Unit) = body()

    Observe {
        // Not recomposing router on backstack operation
        CompositionLocalProvider(
            LocalBackPressHandler provides localHandler,
            LocalRouting provides downStreamRoute,
        ) {
            children(backStack)
        }
    }
}

@Composable
private fun <T> fetchBackStack(
    key: String,
    defaultElement: T,
    override: T?,
): BackStack<T> {
    val onElementRemoved: (Int) -> Unit = { }

    @Suppress("UNCHECKED_CAST")
    val existing = backStackMap[key] as BackStack<T>?
    @Suppress("UNCHECKED_CAST")
    return when {
        override != null -> BackStack(override as T, onElementRemoved)

        // not stored; caller manages lifecycle
        existing != null -> existing

        else -> BackStack(defaultElement, onElementRemoved).also { backStackMap[key] = it }
    }
}
