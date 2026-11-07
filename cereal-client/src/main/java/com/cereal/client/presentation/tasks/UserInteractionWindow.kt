package com.cereal.client.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.cereal.client.application.exception.ChromeNotInstalledException
import com.cereal.client.domain.model.task.UserInteraction
import com.cereal.client.presentation.theme.CerealTheme
import com.cereal.client.presentation.view.CerealButton
import com.cereal.client.presentation.view.CerealButtonType
import com.cereal.client.presentation.view.CerealOutlinedButton
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextField
import com.cereal.sdk.component.userinteraction.ConfirmationResult
import com.cereal.sdk.component.userinteraction.UserInteractionCanceledException
import com.cereal.sdk.component.userinteraction.WebResourceException
import com.cereal.sdk.component.userinteraction.WebResourceRequest
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.confirmation_expires_in
import com.cereal_automation.cereal_client.generated.resources.submit
import com.cereal_automation.cereal_client.generated.resources.task_title_with_number
import dev.kdriver.cdp.domain.Network
import dev.kdriver.cdp.domain.network
import dev.kdriver.cdp.domain.page
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.exceptions.BrowserExecutableNotFoundException
import dev.kdriver.core.exceptions.NoBrowserExecutablePathException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import java.net.URLDecoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Composable
fun UserInteractionWindow(
    userInteraction: UserInteraction,
    taskNumber: Int? = null,
    reviewRequests: Int = 0,
    closeWindow: () -> Unit,
) {
    when (userInteraction) {
        is UserInteraction.Confirmation -> {
            ConfirmationWindow(userInteraction, taskNumber, reviewRequests, closeWindow)
        }

        is UserInteraction.Browser -> {
            BrowserWindow(userInteraction, taskNumber, closeWindow)
        }

        is UserInteraction.TextInput -> {
            TextInputWindow(userInteraction, taskNumber, closeWindow)
        }

        else -> {}
    }
}

@Composable
private fun BrowserWindow(
    userInteraction: UserInteraction.Browser,
    taskNumber: Int? = null,
    closeWindow: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val browserRef = remember { mutableStateOf<Browser?>(null) }

    DisposableEffect(Unit) {
        val job =
            scope.launch(Dispatchers.IO) {
                launchBrowserInteraction(
                    userInteraction = userInteraction,
                    scope = this,
                    browserRef = browserRef,
                    closeWindow = closeWindow,
                )
            }
        onDispose {
            job.cancel()
            if (!userInteraction.continuation.isCompleted) {
                userInteraction.continuation.resumeWithException(UserInteractionCanceledException())
            }
            browserRef.value?.let { browser ->
                scope.launch(Dispatchers.IO) {
                    if (!browser.stopped) browser.stop()
                }
            }
        }
    }
}

private const val MAX_BROWSER_GET_RETRIES = 20
private const val BROWSER_GET_RETRY_DELAY_MS = 250L

// Single linear browser-setup flow; splitting it would obscure the sequence more than it helps.
@Suppress("LongMethod", "CyclomaticComplexMethod")
private suspend fun launchBrowserInteraction(
    userInteraction: UserInteraction.Browser,
    scope: CoroutineScope,
    browserRef: androidx.compose.runtime.MutableState<Browser?>,
    closeWindow: () -> Unit,
) {
    val continuation = userInteraction.continuation
    var browser: Browser? = null
    try {
        browser = createBrowser(coroutineScope = scope, headless = false)
        browserRef.value = browser

        userInteraction.onStatusUpdate("Waiting for browser interaction...")

        val url = if (userInteraction.url?.isNotEmpty() == true) userInteraction.url else "about:blank"

        var tab: dev.kdriver.core.tab.Tab? = null
        var retries = 0

        // kdriver's browser.get() synchronously expects the browser's target list to be populated.
        // On fresh browser launch, fetching targets via websocket can race with this call, causing a
        // NoSuchElementException. We retry with a delay to allow the browser to initialize its targets.
        while (tab == null) {
            try {
                tab = browser.get(url)
            } catch (e: NoSuchElementException) {
                if (retries >= MAX_BROWSER_GET_RETRIES) throw e
                delay(BROWSER_GET_RETRY_DELAY_MS)
                retries++
            }
        }

        // If HTML content was provided, inject it into the page
        if (userInteraction.html?.isNotEmpty() == true) {
            tab.page.enable()
            val frameTree = tab.page.getFrameTree()
            tab.page.setDocumentContent(frameTree.frameTree.frame.id, userInteraction.html)
        }

        // Enable network monitoring and set extra headers if provided
        tab.network.enable()
        if (!userInteraction.headers.isNullOrEmpty()) {
            tab.network.setExtraHTTPHeaders(
                userInteraction.headers.mapValues { (_, v) -> JsonPrimitive(v) },
            )
        }

        // Monitor loading failures for DOCUMENT resources
        scope.launch {
            tab.network.loadingFailed.collect { event ->
                if (event.type == Network.ResourceType.DOCUMENT && !continuation.isCompleted) {
                    continuation.resumeWithException(
                        WebResourceException(
                            failedUrl = "",
                            errorText = event.errorText,
                            errorCode = -1,
                        ),
                    )
                    closeWindow()
                }
            }
        }

        // Monitor outgoing requests — use first{} to suspend until shouldFinish returns true
        tab.network.requestWillBeSent.first { event ->
            val request = event.request
            val headers =
                request.headers.mapValues { (_, v) ->
                    val str = v.toString()
                    // Strip surrounding quotes from JsonElement string representation
                    if (str.startsWith("\"") && str.endsWith("\"")) str.substring(1, str.length - 1) else str
                }

            val postData = parseFormPostData(request.postData, headers["Content-Type"] ?: headers["content-type"])

            val webResourceRequest =
                WebResourceRequest(
                    method = request.method,
                    requestHeaders = headers,
                    url = request.url,
                    postData = postData,
                )

            if (userInteraction.shouldFinish(webResourceRequest) && !continuation.isCompleted) {
                continuation.resume(webResourceRequest)
                closeWindow()
                true
            } else {
                false
            }
        }
        browser.stop()
    } catch (e: CancellationException) {
        browser?.takeIf { !it.stopped }?.stop()
        throw e
    } catch (e: Exception) {
        val mapped = mapBrowserException(e) ?: e
        if (!continuation.isCompleted) {
            continuation.resumeWithException(mapped)
            closeWindow()
        }
        browser?.stop()
    }
}

internal fun mapBrowserException(e: Exception): ChromeNotInstalledException? =
    when (e) {
        is NoBrowserExecutablePathException,
        is BrowserExecutableNotFoundException,
        -> ChromeNotInstalledException(cause = e)

        else -> null
    }

/**
 * Parses URL-encoded form POST data into a key-value map.
 * Only parses if the content type is `application/x-www-form-urlencoded`.
 */
private fun parseFormPostData(
    rawPostData: String?,
    contentType: String?,
): Map<String, String>? {
    if (rawPostData.isNullOrEmpty()) return null
    if (contentType?.contains("application/x-www-form-urlencoded") != true) return null

    val result = mutableMapOf<String, String>()
    rawPostData.split("&").forEach { param ->
        val keyValue = param.split("=", limit = 2)
        if (keyValue.size == 2) {
            val key = URLDecoder.decode(keyValue[0], "UTF-8")
            val value = URLDecoder.decode(keyValue[1], "UTF-8")
            result[key] = value
        }
    }
    return result
}

@Composable
private fun TextInputWindow(
    userInteraction: UserInteraction.TextInput,
    taskNumber: Int? = null,
    closeWindow: () -> Unit,
) {
    val windowState = rememberWindowState(width = 400.dp, height = 250.dp)
    var text by remember { mutableStateOf("") }

    Window(
        onCloseRequest = {
            if (!userInteraction.continuation.isCompleted) {
                userInteraction.continuation.resumeWithException(UserInteractionCanceledException())
                closeWindow()
            }
        },
        title =
            taskNumber?.let { stringResource(Res.string.task_title_with_number, it, userInteraction.title) }
                ?: userInteraction.title,
        state = windowState,
        alwaysOnTop = true,
    ) {
        CerealTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CerealText(text = userInteraction.description)
                    CerealTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CerealButton(
                        onClick = {
                            if (!userInteraction.continuation.isCompleted) {
                                userInteraction.continuation.resume(text)
                                closeWindow()
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        CerealText(stringResource(Res.string.submit))
                    }
                }
            }
        }
    }
}

/**
 * A script's `requestConfirmation`, always on top. Closing it declines. Expiry and a stopped task need nothing
 * here: the component clears the interaction, which removes the window.
 */
@OptIn(ExperimentalTime::class)
@Composable
private fun ConfirmationWindow(
    confirmation: UserInteraction.Confirmation,
    taskNumber: Int?,
    reviewRequests: Int,
    closeWindow: () -> Unit,
) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(confirmation) {
        while (true) {
            delay(1.seconds)
            now = Clock.System.now()
        }
    }
    Window(
        onCloseRequest = {
            confirmation.answer.complete(ConfirmationResult.Declined)
            closeWindow()
        },
        title =
            taskNumber?.let { stringResource(Res.string.task_title_with_number, it, confirmation.title) }
                ?: confirmation.title,
        state = rememberWindowState(width = 460.dp, height = 400.dp),
        alwaysOnTop = true,
    ) {
        // Review on the task row: bring a buried or minimized window back.
        LaunchedEffect(reviewRequests) {
            if (reviewRequests > 0) {
                window.isMinimized = false
                window.toFront()
                window.requestFocus()
            }
        }
        CerealTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ConfirmationContent(confirmation, now)
            }
        }
    }
}

/** The confirmation's question, details, page link, countdown and buttons; every text is shown as plain text. */
@OptIn(ExperimentalTime::class)
@Composable
internal fun ConfirmationContent(
    confirmation: UserInteraction.Confirmation,
    now: Instant,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CerealText(
                text = confirmation.question,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            confirmation.details.forEachIndexed { index, paragraph ->
                CerealText(
                    text = paragraph,
                    color = if (index == 0) MaterialTheme.colorScheme.onSurface else CerealTheme.colorScheme.contentSecondary,
                )
            }
            confirmation.url?.let { PageLink(it) }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CerealTheme.colorScheme.surfaceRaised)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                confirmation.expiresAt?.let { expiresAt ->
                    CerealText(
                        text =
                            stringResource(
                                Res.string.confirmation_expires_in,
                                (expiresAt - now).toCountdown(),
                                confirmation.declineLabel,
                            ),
                        color = CerealTheme.colorScheme.contentTertiary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            CerealOutlinedButton(
                onClick = { confirmation.answer.complete(ConfirmationResult.Declined) },
                type = CerealButtonType.Neutral,
            ) {
                CerealText(confirmation.declineLabel)
            }
            CerealButton(onClick = { confirmation.answer.complete(ConfirmationResult.Confirmed) }) {
                CerealText(confirmation.confirmLabel)
            }
        }
    }
}

/** The step's page; only a web address opens, anything else stays plain text. */
@Composable
private fun PageLink(url: String) {
    val uriHandler = LocalUriHandler.current
    val isWeb = url.startsWith("https://") || url.startsWith("http://")
    CerealText(
        text = url,
        color = if (isWeb) CerealTheme.colorScheme.link else CerealTheme.colorScheme.contentSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = if (isWeb) Modifier.clickable { uriHandler.openUri(url) } else Modifier,
    )
}

/** mm:ss, never below 0:00. */
internal fun Duration.toCountdown(): String = coerceAtLeast(Duration.ZERO).toComponents { minutes, seconds, _ -> "%d:%02d".format(minutes, seconds) }
