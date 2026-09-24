# Headless SSO: device-code, pasted-link, and SSH-forwarded loopback

Research for [#12](https://github.com/Cereal-Automation/desktop-client/issues/12) (map #8).
Question: how can a user on a machine with no browser (a server, over SSH) sign in with
marketplace SSO?

## How it works today (client contract)

- `SystemBrowserOAuthDataSource` binds an HTTP listener on `127.0.0.1:<ephemeral port>`,
  opens `<marketplace>/auth/<slug>/desktop?state=…&redirect=http://127.0.0.1:<port>`, and waits
  up to 5 minutes for `?code&state`. It checks `state` in constant time.
  (`cereal-client/.../network/SystemBrowserOAuthDataSource.kt`)
- `AuthProviderImpl.authenticateWith` posts that code to `POST auth/<slug>/exchange` with
  `{code, device_name}` and gets a `LoginResponse` back.
  (`AuthProviderImpl.kt`, `MarketplaceApiClient.exchangeOAuthCode`, `OAuthExchangeRequestBody.kt`)
- Providers: `GOOGLE("google")` and `DISCORD("discord")`, registered on the backend in
  `config/oauth.php` (`domain/model/auth/OAuthProvider.kt`).
- **The marketplace brokers SSO.** The client never talks to Google or Discord and holds no
  provider client IDs. The provider round-trip is web-to-web: backend to provider and back to
  backend. The backend then hands the desktop a one-time code of its own.
  Every option below follows from this.
- The flow has no PKCE. The one-time code works as a bearer credential until it is exchanged,
  so anyone who sees the code can use it.
- Email/password login (`POST auth/token`) needs no browser. Only accounts that sign in solely
  through SSO are blocked on a headless machine.

## Do the SSO providers matter?

Only if the backend stops acting as a broker. The providers differ a lot:

- **Google** supports RFC 8628 device flow ("OAuth 2.0 for TV and Limited-Input Device
  Applications"). It needs a separate OAuth client of type "TVs and Limited Input devices" and
  allows only a small set of scopes; `openid`, `email` and `profile` are among them.
  Endpoints: `https://oauth2.googleapis.com/device/code`, then poll
  `https://oauth2.googleapis.com/token` with `grant_type=urn:ietf:params:oauth:grant-type:device_code`.
  Source: https://developers.google.com/identity/protocols/oauth2/limited-input-device
- **Discord** has no device authorization grant. Its OAuth2 docs list authorization code,
  implicit, client credentials, bot and webhook flows, and redirect URIs must be registered.
  Source: https://docs.discord.com/developers/topics/oauth2

So a design that proxies each provider's own device flow could never cover Discord. Because the
backend already brokers, it can run the device flow itself and use the existing web SSO for the
provider step. That works for every provider, including ones added later.

## Options

### A. Marketplace-native Device Authorization Grant (RFC 8628)

The backend acts as the device-flow authorization server. The provider step stays the web SSO it
already has.

1. Client: `POST auth/device` returns `device_code`, `user_code`, `verification_uri`,
   `verification_uri_complete`, `expires_in`, `interval` (RFC 8628 §3.2).
2. Client prints "open `<marketplace>/activate` and enter `WDJB-MJHT`", plus the
   `verification_uri_complete` link or a QR code.
3. User opens the page on any phone or laptop, signs in with Google or Discord (existing web SSO),
   and confirms the device.
4. Client polls the token endpoint with `grant_type=urn:ietf:params:oauth:grant-type:device_code`
   and handles `authorization_pending`, `slow_down` (add 5 s), `access_denied` and
   `expired_token` (§3.4–3.5). On success it receives a `LoginResponse`.

**Backend work:** a device-code table (device_code, user_code, expiry, status, user_id), three
endpoints (`auth/device`, token polling, and an `/activate` web page with a confirm step), rate
limiting, and enough user-code entropy (§5.1). The confirm screen must name the device, such as
"Cereal on `<device_name>`", because RFC 8628 §5.4 warns about remote phishing, where an
attacker sends a victim a code to approve. No new provider registrations are needed.
**Client work:** a new `OAuthDataSource` implementation, or a separate method, since it is not
provider-specific. Sign-in becomes one entry point where the user picks a provider on the web page.
**Verdict:** the standard UX and the most secure option (no code in any URL). It is also the
largest backend change.

### B. Pasted code: backend shows the code on a page

Same `/auth/<slug>/desktop` entry point, but with a mode (for example `redirect=oob`, or no
`redirect`) that makes the backend render a page showing the one-time code instead of
redirecting to the loopback. The user copies the code into the headless client, and the client
calls the existing `auth/<slug>/exchange`.

**Backend work:** small. Accept the new mode, render a "copy this code" page, and keep the code
single-use with a short lifetime.
**Client work:** print the URL, read a line from stdin, and exchange it. `state` cannot be checked
by the client here. The backend should bind the code to the `state` the client started with and
require it on exchange, or add PKCE.
**Verdict:** less work than A, but it is the OOB pattern that RFC 8252 moved away from. It is
mostly fine because the page lives on the marketplace's own origin and the code is useless
without the exchange step.

### C. Pasted redirect URL (zero backend change)

Use the flow unchanged. The headless client prints the authorize URL with
`redirect=http://127.0.0.1:<port>`. The user opens it in a browser on another machine. At the end
the browser tries to load `http://127.0.0.1:<port>/?code=…&state=…`, which fails on that machine,
but the full URL with the code is in the address bar. The user pastes that URL into the headless
client. The client parses it, checks `state` as it already does, and exchanges the code.
rclone and several CLIs use this pattern.

**Backend work:** none, if the backend accepts any loopback port, which RFC 8252 §7.3 requires:
"MUST allow any port to be specified at the time of the request for loopback IP redirect URIs".
The client depends on this already, because its port is ephemeral.
**Client work:** a "paste the URL you ended up on" prompt that reuses `parseQuery` and the `state`
check. The loopback listener can keep running in parallel, so whichever path finishes first wins.
**Verdict:** the cheapest option that ships today. The UX is clunky (a "this site can't be
reached" page) and the code ends up in the other browser's history. Short TTL and single use on
the backend limit that risk.

### D. SSH port-forwarding the loopback (zero backend change)

`ssh -L <port>:127.0.0.1:<port> server`, then open the printed URL in the local browser. The
redirect to `127.0.0.1:<port>` reaches the remote listener through the tunnel.

- It works with no backend change, for the same §7.3 reason as C.
- The problem: the port is ephemeral and chosen after the SSH session starts. The user has to add
  the forward mid-session, with the `~C` escape (`-L port:127.0.0.1:port`) or
  `ssh -O forward -L …` on a ControlMaster connection. Alternatively the client could accept a
  fixed port (for example `--oauth-port 53682`) so the forward can be set up beforehand.
- This only helps SSH users with a local browser. It does not help a fully unattended server,
  and a Docker or remote-agent setup needs one more hop.
- **Verdict:** viable as a documented fallback for power users. Adding a fixed-port option costs
  little. C is strictly easier for users, needs about the same client code, and has no networking
  prerequisites.

## Recommendation

1. **Now:** C (pasted redirect URL), with the loopback listener still running. D comes almost free
   with an optional fixed port. Neither needs the backend.
2. **Later, if headless becomes a first-class target:** A (marketplace-native device flow). Skip B:
   it needs backend work anyway, and A is the standard option.
3. Remind headless users that email/password login already works.

## Sources

- RFC 8628, OAuth 2.0 Device Authorization Grant: https://datatracker.ietf.org/doc/html/rfc8628 (§3.2, §3.4, §3.5, §5.1, §5.4)
- RFC 8252, OAuth 2.0 for Native Apps: https://www.rfc-editor.org/rfc/rfc8252 (§7.3 loopback, any port; §8.3 loopback security)
- Google, OAuth 2.0 for TV and Limited-Input Device Applications: https://developers.google.com/identity/protocols/oauth2/limited-input-device
- Discord, OAuth2 topic: https://docs.discord.com/developers/topics/oauth2
- Client code: `SystemBrowserOAuthDataSource.kt`, `AuthProviderImpl.kt`, `MarketplaceApiClient.kt`, `OAuthProvider.kt`

## Open questions (backend not in this repo)

- Does `/auth/<slug>/desktop` validate `redirect` as a loopback URI with any port, and does it
  accept anything else? C and D assume the first. B and A need backend changes either way.
- How long does the one-time code live, and is it single-use and bound to `state`? This decides
  how safe C and B are.
