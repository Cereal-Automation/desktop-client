# Research: does kdriver 0.5.11 expose the AX-tree and element APIs the agent loop needs?

Resolves #49 (map #47). Follows up the **[UNVERIFIED]** note in
`docs/research/ai-automation-transformation.md` §2.3 and §5 (branch `research/ai-automation-transformation`).
That doc read kdriver's API on `main`. This one checks the `0.5.11` tag that Cereal pins
(`gradle/libs.versions.toml:36`, `kdriver = "0.5.11"`).

## Answer

Yes. Every API the agent loop needs is in 0.5.11, with the same signatures as on 0.6.1. 0.5.11 → 0.6.1
changes **nothing** in the `dom/` package or in the `Accessibility`, `DOM` and `Input` CDP bindings. The
release adds features and reliability fixes. One CDP typing bug in `DOMSnapshot` is fixed in 0.6.x, and it
is harmless for our use.

## Sources

- kdriver git tags `0.5.11` and `0.6.1` (https://github.com/cdpdriver/kdriver). `0.6.1` is the tip of `main`
  (`1bce6ca`). Compared with `git diff 0.5.11 0.6.1`.
- The published jars Cereal resolves, checked with `javap`:
  `~/.gradle/caches/modules-2/files-2.1/dev.kdriver/core-jvm/0.5.11/.../core-jvm-0.5.11.jar` and
  `.../cdp-jvm/0.5.11/.../cdp-jvm-0.5.11.jar`. The jars match the tag source for every symbol below.

## API presence in 0.5.11

| Need | 0.5.11 API | Where (tag `0.5.11`) | In jar |
|---|---|---|---|
| Full AX tree | `tab.accessibility.getFullAXTree(depth, frameId)` | `cdp/.../domain/Accessibility.kt:98,115`; accessor `CDP.accessibility` at `:16` | yes |
| AX query by name/role | `tab.accessibility.queryAXTree(nodeId, backendNodeId, objectId, accessibleName, role)` | `Accessibility.kt:209,231` | yes |
| Other AX calls | `getPartialAXTree`, `getRootAXNode`, `getChildAXNodes`, `getAXNodeAndAncestors`, `enable` | `Accessibility.kt:55-197` | yes |
| AX node → DOM link | `AXNode.backendDOMNodeId: Int?` | `Accessibility.kt:697` | yes |
| DOM snapshot | `tab.dOMSnapshot.captureSnapshot(...)` (note the generated accessor name `dOMSnapshot`) | `cdp/.../domain/DOMSnapshot.kt:13,87,112` | yes |
| Find by text / CSS | `Tab.find(text, bestMatch, returnEnclosingElement, timeout)`, `select`, `findAll`, `selectAll`, `querySelector(All)`, `xpath`, `findElementByText` | `core/.../tab/Tab.kt:254-395` | yes |
| Click | `Element.click()` (JS `this.click()`), `Element.mouseClick(button, modifiers, clickCount)` (real `Input.dispatchMouseEvent` at the element centre, with scroll-into-view and jitter), `Tab.mouseClick(x, y, ...)` | `core/.../dom/Element.kt:114,169`; `Tab.kt:427` | yes |
| Type | `Element.sendKeys(text)` (focus, then one `Input.dispatchKeyEvent type=char` per char), `Element.insertText(text)` (focus, then `Input.insertText`), `clearInput`, `clearInputByDeleting` | `Element.kt:193-238`; `DefaultElement.kt` | yes |
| Screenshot | `Tab.screenshotB64(format = JPEG, fullPage = false): String` (wraps `Page.captureScreenshot`) | `Tab.kt:495`; `DefaultTab.kt:639` | yes |
| backend-node-id → Element | No helper. Build it: `DefaultElement(tab, tab.dom.describeNode(backendNodeId = id).node)`. `DefaultElement` is a public `open class` with a public constructor `(Tab, DOM.Node, DOM.Node? = null)`. Its JS handle is resolved through `dom.resolveNode(backendNodeId = ...)` (`DefaultElement.kt:87,100`), so a backend id is enough. No frontend `nodeId`/`getDocument` is needed. | `core/.../dom/DefaultElement.kt:17`; `cdp/.../domain/DOM.kt:287,1041`; `Element.backendNodeId` at `Element.kt:50` | yes |
| Raw CDP fallback | `Tab : Connection : CDP` exposes every generated domain (`dom`, `input`, `page`, …) | `core/.../connection/Connection.kt:12`; `Tab.kt:34` | yes |

Caveat, from the CDP spec rather than kdriver: `queryAXTree` needs a root (`nodeId`, `backendNodeId` or
`objectId`). With none, Chrome returns an error. Use `getFullAXTree` for the whole page.

## What changes 0.5.11 → 0.6.1

19 commits (`git log 0.5.11..0.6.1`). There is no CHANGELOG file. The commit subjects are the changelog.

**Not touched:** `core/.../dom/*` (Element, DefaultElement) and the CDP bindings `Accessibility`, `DOM`,
`Input` and `Page`. The agent-loop surface above is identical.

**Added (source-compatible):**
- `Tab.getLocalStorage()`, `setLocalStorage(map)`, `clearLocalStorage()` (`5116295`).
- `Browser.cookies: CookieJar` (`714d0c6`).
- `Connection.registerReconnectRestore` / `unregisterReconnectRestore`, plus automatic websocket reconnect
  that restores state (`e0c7196`, `be37a42`, `36bfb66`).
- `Config.timeBeforeConsideredIdle` and `Config.copy()`. `createBrowser` now copies the config instead of
  mutating the caller's (`8f062ea`).
- Better diagnostics when the browser fails to start or dies, from captured stderr and exit reason
  (`f514e08`, `2b2169b`, `d33fadb`). Stop now waits for the process to exit (`a0d443c`).

**Signature changes (binary-incompatible, source-compatible):**
- `Connection.wait(t)` becomes `wait(t, idleTimeout = 10_000)`. The idle wait is now bounded, so a busy page
  cannot hang it (`3e78da3`). Code compiled against 0.5.11 that calls `wait` must be recompiled. Cereal
  does not call it.
- New abstract members on the `Browser`, `Tab` and `Connection` interfaces. They only break hand-written
  implementations. Cereal only uses `mockk<Browser/Tab>(relaxed = true)` in
  `CheckoutProviderImplTest.kt:59-60`, which is unaffected.

**CDP typing fixes (generator, `Poetize.kt`):**
- `DOMSnapshot.NodeTreeSnapshot.attributes` and `LayoutTreeSnapshot.styles` change from
  `List<List<Double>>` to `List<List<Int>>`. In 0.5.11 they decode fine, because JSON integers parse as
  `Double`, but you must `.toInt()` each string-table index. This matters if the snapshot builder uses
  `captureSnapshot` attributes or styles on 0.5.11.
- `DOMStorage.GetDOMStorageItemsReturn.entries` changes from `List<List<Double>>` to
  `List<List<String>>`. On 0.5.11 this is broken: string entries cannot decode as `Double`. The agent loop
  does not need it, and 0.6.x adds `Tab.getLocalStorage()` anyway.

## Implication for #47

- The agent loop can be built on 0.5.11 today: AX snapshot, `backendDOMNodeId` → `DefaultElement`, then
  `mouseClick` / `insertText` / `screenshotB64`.
- Upgrading to 0.6.1 is low-risk for Cereal's current usage (`createBrowser`, `Browser`, `Tab`, `page`,
  `network` in `CheckoutProviderImpl.kt` and `UserInteractionWindow.kt`). It is worth doing for the
  auto-reconnect and the bounded idle wait, which a long-running agent loop benefits from. It is not a
  prerequisite.
- If scripts compiled against the host's kdriver exist (§5's open `ScriptClassLoader` question), the `wait`
  signature change would break any script binary that calls `tab.wait` after an upgrade.
