// PROTOTYPE — throwaway spike (#59). kdriver 0.6.1 adapter: AX snapshot with numbered refs, and the actions.
package spike

import dev.kdriver.cdp.domain.accessibility
import dev.kdriver.cdp.domain.dom
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.dom.DefaultElement
import dev.kdriver.core.tab.ReadyState
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val INTERACTIVE = setOf(
    "button", "link", "textbox", "searchbox", "checkbox", "radio", "combobox", "listbox", "option",
    "menuitem", "tab", "switch", "slider", "spinbutton",
)
private val SKIP = setOf("none", "generic", "InlineTextBox", "LineBreak", "RootWebArea", "paragraph", "group", "list")

class Snapshot(val text: String, val refs: Map<Int, Int>, val names: Map<Int, String>)

class Page(private val browser: Browser) {
    val tab: Tab get() = browser.mainTab!!
    var last: Snapshot = Snapshot("", emptyMap(), emptyMap())

    suspend fun url(): String = (tab.rawEvaluate("location.href") as? JsonPrimitive)?.contentOrNull ?: "?"

    suspend fun navigate(url: String) {
        tab.get(url)
        settle()
    }

    suspend fun settle() {
        delay(600)
        tab.waitForReadyState(ReadyState.COMPLETE, 15_000)
        delay(400)
    }

    suspend fun snapshot(maxChars: Int = 14_000): Snapshot {
        val nodes = tab.accessibility.getFullAXTree().nodes
        val byId = nodes.associateBy { it.nodeId }
        val refs = mutableMapOf<Int, Int>()
        val names = mutableMapOf<Int, String>()
        val sb = StringBuilder()
        fun str(v: dev.kdriver.cdp.domain.Accessibility.AXValue?) =
            (v?.value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        fun walk(id: String, depth: Int, parentName: String) {
            val n = byId[id] ?: return
            val role = str(n.role)
            val name = str(n.name).replace(Regex("\\s+"), " ").take(160)
            var d = depth
            if (!n.ignored && role !in SKIP && !(role == "StaticText" && (name.isEmpty() || name == parentName))) {
                val line = StringBuilder("  ".repeat(depth))
                if (role in INTERACTIVE && n.backendDOMNodeId != null) {
                    val ref = refs.size + 1
                    refs[ref] = n.backendDOMNodeId!!
                    names[ref] = "$role \"$name\""
                    line.append("[$ref] ")
                }
                line.append(role)
                if (name.isNotEmpty()) line.append(" \"").append(name).append('"')
                val value = str(n.value)
                if (value.isNotEmpty() && role in INTERACTIVE) line.append(" value=\"").append(value.take(80)).append('"')
                n.properties?.forEach { p ->
                    val pn = p.name.name.lowercase()
                    if (pn in setOf("disabled", "checked", "expanded", "selected") && str(p.value) == "true") line.append(" ").append(pn)
                }
                sb.append(line).append('\n')
                d = depth + 1
            }
            n.childIds?.forEach { walk(it, d, name.ifEmpty { parentName }) }
        }
        nodes.firstOrNull { it.parentId == null }?.let { walk(it.nodeId, 0, "") }
        val text = if (sb.length > maxChars) sb.substring(0, maxChars) + "\n…(truncated)" else sb.toString()
        return Snapshot(text, refs, names).also { last = it }
    }

    private suspend fun element(ref: Int): DefaultElement {
        val backend = last.refs[ref] ?: error("unknown ref $ref; call read_page for fresh refs")
        tab.dom.scrollIntoViewIfNeeded(backendNodeId = backend)
        return DefaultElement(tab, tab.dom.describeNode(backendNodeId = backend).node)
    }

    suspend fun click(ref: Int) { element(ref).click(); settle() }

    suspend fun type(ref: Int, text: String) {
        val e = element(ref)
        e.clearInput()
        e.sendKeys(text)
    }

    suspend fun scroll(down: Boolean) { if (down) tab.scrollDown(50) else tab.scrollUp(50); delay(300) }

    suspend fun back() { tab.back(); settle() }

    companion object {
        suspend fun launch(scope: CoroutineScope, profileDir: String): Page =
            Page(
                createBrowser(scope) {
                    userDataDir = Path(profileDir)
                    headless = false
                    // Cold headed Chrome with a fresh profile needs a few seconds; kdriver's default gives up sooner.
                    browserConnectionTimeout = 500
                    browserConnectionMaxTries = 60
                },
            )
    }
}
