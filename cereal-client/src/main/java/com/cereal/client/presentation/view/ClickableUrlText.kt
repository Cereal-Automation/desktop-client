package com.cereal.client.presentation.view

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.presentation.theme.CerealTheme
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.net.URI

private val logger = LoggerFactory.getLogger("ClickableUrlText")

// URL pattern (http, https, ftp, www.) — compiled once and reused across recompositions.
private val URL_PATTERN =
    Regex(
        """(https?://|ftp://|www\.)[^\s,;!?(){}<>'"]+""",
        RegexOption.IGNORE_CASE,
    )

/**
 * A text component that automatically detects URLs in the text and makes them clickable.
 */
@Composable
fun ClickableUrlText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CerealTheme.colorScheme.contentTertiary,
    linkColor: Color = CerealTheme.colorScheme.link,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val annotatedString =
        remember(text, color, linkColor) {
            buildAnnotatedString {
                var lastIndex = 0

                URL_PATTERN.findAll(text).forEach { matchResult ->
                    val start = matchResult.range.first
                    val end = matchResult.range.last + 1
                    val url = matchResult.value

                    // Add text before the URL
                    if (start > lastIndex) {
                        withStyle(style = SpanStyle(color = color)) {
                            append(text.substring(lastIndex, start))
                        }
                    }

                    // Add the URL with clickable annotation
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(
                        style =
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                    ) {
                        append(url)
                    }
                    pop()

                    lastIndex = end
                }

                // Add remaining text after the last URL
                if (lastIndex < text.length) {
                    withStyle(style = SpanStyle(color = color)) {
                        append(text.substring(lastIndex))
                    }
                }
            }
        }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotatedString,
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    layoutResult.value?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(offset)
                        annotatedString
                            .getStringAnnotations(tag = "URL", start = position, end = position)
                            .firstOrNull()
                            ?.let { annotation ->
                                try {
                                    var url = annotation.item
                                    // Add https:// prefix if URL starts with www.
                                    if (url.startsWith("www.", ignoreCase = true)) {
                                        url = "https://$url"
                                    }

                                    if (Desktop.isDesktopSupported()) {
                                        val desktop = Desktop.getDesktop()
                                        if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                            desktop.browse(URI(url))
                                        }
                                    }
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (e: Exception) {
                                    logger.error("Failed to open URL: ${annotation.item}", e)
                                    CrashReporter.report(e)
                                }
                            }
                    }
                }
            },
        style = style.copy(color = color),
        onTextLayout = { layoutResult.value = it },
    )
}
