# Completing kdriver browser prompts on a displayless server

Research for #10 (map #8). The question: on a Linux server or in Docker, with no display, how can a remote
human finish a page opened by `showUrl` / `showHtml` (a captcha, a login, a checkout)?

## How it works today

- `UserInteractionComponentImpl.showUrl/showHtml` suspends the script and stores a `UserInteraction.Browser`
  on the task (`cereal-client/src/main/java/com/cereal/client/infrastructure/sdkcomponent/UserInteractionComponentImpl.kt`).
- The Compose UI picks that up and `launchBrowserInteraction` in
  `presentation/tasks/UserInteractionWindow.kt` calls `createBrowser(coroutineScope, headless = false)`.
  It opens the URL (or injects HTML with `Page.setDocumentContent`), then watches
  `Network.requestWillBeSent` until the script's `shouldFinish` predicate matches. That request resumes the
  script.
- `CheckoutProviderImpl.awaitCheckout` does the same thing with `Page.frameNavigated`. If kdriver fails, it
  falls back to `browserDataSource.attemptDesktopBrowse`, and that fallback is useless on a server too.
- Completion is decided entirely over CDP, so it doesn't matter where the pixels end up. The only open problem
  is getting the page in front of a human and getting their input back into it. The finish detection needs no
  changes.
- Note: the trigger for both flows is Compose (`UserInteractionWindow` is a `@Composable`). A displayless mode
  therefore also needs a non-Compose owner for `UserInteraction.Browser`. That belongs to the TUI/headless
  work in #8, not to this question.

## Primary-source facts that constrain the options

1. **kdriver 0.5.11 `createBrowser`** takes `headless`, `browserArgs`, `sandbox`, `userAgent`, `host` and
   `port` (`core/.../browser/Extensions.kt`). If both `host` and `port` are set, kdriver **attaches to an
   already-running browser** and does not spawn one (`DefaultBrowser.start`, `connectExisting`). Otherwise it
   binds `127.0.0.1` on a free port.
2. **kdriver always passes `--remote-allow-origins=*`** (`Config.defaultBrowserArgs`), so the CDP websocket
   accepts any Origin. If the debugging port can be reached, anyone who reaches it has full control of the
   browser.
3. **kdriver disables the sandbox when running as root** (`Config.init`: "Detected root usage, auto disabling
   sandbox mode"). That is the default in Docker.
4. **Chrome binds DevTools to loopback only.** `chrome/browser/devtools/remote_debugging_server.cc` listens on
   `127.0.0.1` / `::1` and has no address switch. `--remote-debugging-address` is not honoured by
   `chrome` (headful or `--headless=new`). The CDP port cannot be exposed directly; you need an SSH tunnel or a
   TCP proxy in front of it.
5. **CDP has no authentication.** Chrome 136 made `--remote-debugging-port` require a non-default
   `--user-data-dir` because attackers used it to steal cookies
   ([Chrome blog](https://developer.chrome.com/blog/remote-debugging-port)). kdriver already uses a temp
   profile, so this doesn't affect us, but it shows how Chrome's own team rates the risk.
6. **New headless is the same browser as headful** ("unified Headless and headful modes", Chrome 112+). Old
   headless survives only as `chrome-headless-shell`
   ([Chrome docs](https://developer.chrome.com/docs/chromium/headless)).
7. **CDP screencast + input exist but are experimental.** `Page.startScreencast` / `Page.screencastFrame` /
   `Page.screencastFrameAck` are all flagged `experimental` in the protocol JSON
   ([devtools-protocol](https://github.com/ChromeDevTools/devtools-protocol/blob/master/json/browser_protocol.json)).
   `Input.dispatchMouseEvent` and `Input.dispatchKeyEvent` are stable, and `Input.insertText` is experimental.
   kdriver's generated `cdp` module exposes all of them (`tab.page.startScreencast`, `tab.page.screencastFrame`
   flow, `tab.input.dispatchMouseEvent`, ...).
8. **Xvfb + x11vnc + noVNC.** x11vnc attaches to any X display (including Xvfb), supports `-localhost`,
   `-rfbauth` and `-ssl`, and recommends SSH or VPN for internet use
   ([x11vnc README](https://github.com/LibVNC/x11vnc)). noVNC needs a VNC server behind a WebSocket proxy
   (websockify, started by `novnc_proxy`) ([noVNC README](https://github.com/novnc/noVNC)).

## Options compared

| Option | What the remote human does | Setup cost | UX | Security | Docker fit |
|---|---|---|---|---|---|
| **A. Xvfb + x11vnc + noVNC** | Opens a noVNC URL in any browser and sees the whole virtual desktop | Low code (none in Cereal: set `DISPLAY`, keep `headless = false`). Ops: 3 extra processes and packages | Real Chrome window, everything works (drag, file pickers, popups, extensions). Laggy on slow links; the user sees the whole desktop, not just the prompt | VNC password auth is weak; needs TLS (`-ssl` / websockify cert) or a tunnel. Exposes the whole X session | Well-trodden, but a fat image (Xvfb, x11vnc, websockify, fonts) and a supervisor for multiple processes |
| **B. CDP screencast + input forwarding, served by Cereal** | Opens a Cereal-served web page (or a TUI link) showing a live JPEG stream of just that tab; clicks and keys are forwarded | Highest: embedded HTTP/WebSocket server, frame ack loop, coordinate mapping, key-event translation, IME/paste, handling new tabs and popups. All in-house | Only the relevant tab, integrated with Cereal auth. Keyboard fidelity is the hard part (`dispatchKeyEvent` needs `key`/`code`/`text` for each key). No native dialogs, file pickers or drag. Captcha widgets in cross-origin iframes still work because events go in at page coordinates | Cereal owns authentication; CDP stays on loopback. Best posture, but only if we build it right | Best: Chrome runs `--headless=new`, no X stack. One process |
| **C. SSH tunnel to the CDP port + `chrome://inspect`** | `ssh -L 9222:127.0.0.1:<port>`, adds `localhost:9222` under chrome://inspect "Discover network targets", clicks *inspect* and interacts through DevTools' built-in screencast | Near zero for us. We only have to pin or print the port (`port =` on `createBrowser`) | Developer-grade: needs local Chrome and SSH, and the page is a small, clunky preview inside DevTools. Fine for power users, poor for customers | Good: SSH does the auth, CDP stays loopback. But whoever holds the tunnel has **full** CDP control (cookies, JS eval), not just "click the captcha" | Needs sshd, or `docker exec`/port-forwarding plus a proxy, because Chrome won't bind non-loopback (fact 4) |
| **D. Headless Chrome with "remote DevTools" exposed on the network** | Same as C, but without SSH | Needs a TCP proxy (socat) because of fact 4 | Same as C | **Unacceptable:** unauthenticated full browser control, made worse by kdriver's `--remote-allow-origins=*` (fact 2) | Easy, which is exactly the danger |
| **E. Attach to a Chrome on the human's machine** (kdriver `host`+`port`, fact 1) | Runs Chrome locally with `--remote-debugging-port` and a tunnel back to the server (`ssh -R`) | Low code, awkward setup | Best fidelity: their real browser, their real IP and fingerprint | Server gets full CDP control of the user's browser. Scope it to a throwaway `--user-data-dir` | N/A (the browser runs outside the container). The request still leaves from the user's IP, which may be good or bad for the script |

## Recommendation

- **Ship A first, behind a flag, as the zero-code path.** Document an `Xvfb + x11vnc -localhost + noVNC`
  compose recipe, keep `headless = false`, and expose noVNC only through TLS or a reverse proxy with auth.
  It works with every page today, including the checkout flow.
- **Offer C as the documented power-user and ops fallback.** It costs nothing: just log the CDP port, or make
  it configurable.
- **Treat B as the long-term product feature** if a headless/TUI Cereal is a first-class target. It is the
  only option that gives a slim image, prompt-scoped UX and Cereal-owned auth. Budget for the experimental
  status of screencast and for keyboard fidelity.
- **Never do D.**

## Open questions / not verified

- Does `--headless=new` still leak detectable signals (UA token, `navigator.webdriver`, GPU/codec
  differences) that make captcha providers escalate? Secondary sources disagree on the UA token and I found no
  first-party statement. Relevant to B and to any `headless = true` switch. Test it against the real
  providers the scripts hit.
- Who "owns" a `UserInteraction.Browser` when no Compose window exists (TUI, daemon)? It has to be a
  non-Compose handler, and the choice between A, B and C shapes it.
- How does the remote human learn a prompt is waiting (notification, TUI, link in Discord)? And how long do we
  keep the browser alive before cancelling (checkout already uses 10 min)?
- Multiple concurrent prompts: A needs one display per prompt or window management. B and C are naturally
  per tab.
