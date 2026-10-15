package com.cereal.client.presentation.view

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.url.WebUrl
import com.cereal.client.presentation.theme.CerealTheme
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ClickableUrlText")

// A Markdown link `[text](target)` (groups 1 and 2), or a bare http(s) URL — compiled once and reused.
private val LINK_PATTERN =
    Regex(
        """\[([^\]]+)]\(([^)\s]+)\)|https?://[^\s,;!?(){}<>'"]+""",
        RegexOption.IGNORE_CASE,
    )

/**
 * Text that renders Markdown links `[text](url)` as clickable `text` and auto-links bare URLs.
 * Only http(s) targets become clickable; anything else stays literal text. A bare URL never takes a
 * trailing `.` with it, so a sentence-ending URL still opens.
 */
@Composable
fun ClickableUrlText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CerealTheme.colorScheme.contentTertiary,
    linkColor: Color = CerealTheme.colorScheme.link,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val uriHandler = LocalUriHandler.current
    val annotatedString =
        remember(text, linkColor, uriHandler) {
            val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            buildAnnotatedString {
                var lastIndex = 0
                LINK_PATTERN.findAll(text).forEach { match ->
                    val isMarkdown = match.groups[1] != null
                    val label = if (isMarkdown) match.groupValues[1] else match.value.trimEnd('.')
                    val url = WebUrl.parse(if (isMarkdown) match.groupValues[2] else label)
                    append(text.substring(lastIndex, match.range.first))
                    if (url == null) {
                        append(match.value)
                    } else {
                        val link = LinkAnnotation.Url(url.value, linkStyles) { uriHandler.safeOpen(url.value) }
                        withLink(link) { append(label) }
                        if (!isMarkdown) append(match.value.substring(label.length))
                    }
                    lastIndex = match.range.last + 1
                }
                append(text.substring(lastIndex))
            }
        }

    BasicText(
        text = annotatedString,
        modifier = modifier,
        style = style.copy(color = color),
    )
}

private fun UriHandler.safeOpen(url: String) {
    try {
        openUri(url)
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        logger.error("Failed to open URL: $url", e)
        CrashReporter.report(e)
    }
}
