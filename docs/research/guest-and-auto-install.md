# Guests, Brand-script auto-install, and auto-installing one script in stock Cereal

Research for [#132](https://github.com/Cereal-Automation/desktop-client/issues/132) (map: #130).
Traced against `origin/master` at `eb5aabc`. Paths are relative to
`cereal-client/src/main/java/com/cereal/client/` unless they start with `docs/` or `cereal-client/`.

## Short answer

- **A guest can browse the marketplace, install free scripts, and run them.** Paid scripts are blocked in the
  UI with "Login or register to purchase this script". A guest is a real server-side marketplace user (the
  backend issues a guest token), so free installs are ordinary subscriptions and the run-time licence gate
  treats a guest like any other user.
- **There is no Brand-specific auto-install.** "Auto-install on login" is the stock login script sync
  (`ScriptSyncManager`). It installs every script the user is subscribed to and deletes every installed script
  they are not subscribed to. A Brand only *narrows* that list to its Brand scripts. The gate, paywall and
  retry screens are Brand-only.
- **Auto-installing one script in stock Cereal means auto-*subscribing* the user to it.** The existing sync
  then downloads it unchanged. The missing piece is one idempotent "ensure subscribed to X" step before the
  login sync. The catch is uninstall: Remove unsubscribes the user server-side, so an unconditional
  auto-subscribe on every login reinstalls a script the user removed. The step needs a local "already seeded /
  user removed it" flag.

## 1. What a guest can do

| Capability | Today | Evidence |
|---|---|---|
| Log in as guest | Stock only. Disabled when a Brand is set. | `infrastructure/CerealConfiguration.kt:126-128` (`isGuestLoginEnabled = !isBranded`); button at `presentation/authenticate/login/LoginFormContent.kt:262-295` |
| What a guest *is* | A real marketplace account: `POST auth/guest-token` with the device name, returns a `User` with `is_guest = true` | `infrastructure/data/datasource/network/marketplace/MarketplaceApiClient.kt:149-155`, `.../request/GuestLoginRequestBody.kt`, `infrastructure/provider/AuthProviderImpl.kt:61-62`, `domain/model/user/User.kt:11` |
| Post-login setup | Same `onAuthenticated` path as a full user: Discord init, script sync, task restore | `application/auth/UserAuthManager.kt:54-58`, `:78-88` |
| Browse marketplace | Yes. The Marketplace menu item is hidden only for Brands, never for guests. | `presentation/main/MainViewModel.kt:181-192` |
| Install a free script | Yes. Install = `subscribeToScript` (server) followed by a JAR download. Free scripts need no checkout. | `application/interactor/marketplace/InstallMarketplaceScriptInteractor.kt:16-30` |
| Install a paid script | No. The detail view short-circuits to `GuestPurchaseRequired` | `presentation/marketplace/ScriptDetailViewModel.kt:74-79`; copy `marketplace_guest_purchase_required` |
| Run a script | Yes, if subscribed. `StartScriptInteractor` throws `UnlicensedException` without an entitlement. `ScriptLicenseChecker` has no guest branch: it matches the user's subscriptions (+ team scripts) by `publicIdentifier`. | `application/interactor/script/StartScriptInteractor.kt:49`, `application/script/ScriptLicenseChecker.kt:24-37` |
| Profile | Guest-specific "Guest Profile" screen pushing registration | `presentation/profile/ProfileScreen.kt:41-42`, `presentation/profile/GuestProfileScreen.kt` |

The other `isGuest` checks are in login and registration (`LoginViewModel.kt:146`, `RegistrationViewModel.kt:62`),
which let a guest upgrade to a full account.

Unverified: the client never checks whether the backend accepts `subscribe` for a guest token on a free
script. The client lets a guest try (the guest check above only blocks `!isFree`), and the free-install flow
depends on the backend accepting it. Server code was not in scope.

Inconsistency: the comment at `CerealConfiguration.kt:126-127` says a guest "cannot hold" a marketplace
subscription. In fact a guest holds free subscriptions. What a guest cannot do is *purchase*, and Brand
scripts are paid. That is the real reason guests are disabled in Brand builds.

## 2. How Brand scripts get auto-installed on login

### Entry point: the ordinary login sync

1. Any authentication (password, OAuth, guest, or a restored session at bootstrap) goes through
   `UserAuthManager.onAuthenticated`, which calls `syncScripts()` → `ScriptSyncManager.syncWithProgress(updateScripts = true)`.
   `application/auth/UserAuthManager.kt:31-37`, `:78-88`, `:102-117`.
2. `ScriptSyncManager.syncWithProgress` (`application/script/ScriptSyncManager.kt:74-126`):
   - fetches the list to sync: `authProvider.getSubscriptions(true)` (+ team scripts when development scripts are
     on) (`:186-207`);
   - **Brand scoping**: `scopeToBrand` keeps only `applicationConfig.brandScriptIds` when `isBranded`. Stock
     returns the list unchanged (`:180-184`, called at `:81`);
   - **deletes** every installed script not in that list (`removeScripts`, `:131-148`, called at `:84`);
   - **installs** each missing script at its latest release (or draft for team scripts), and updates it when
     `updateScripts` is set (`:90-123`).
3. Brand script IDs come from the descriptor through `BuildConfig.BRAND_SCRIPT_IDS`
   (`infrastructure/CerealConfiguration.kt:73-77`). Stock has an empty list
   (`infrastructure/data/repository/inmemory/InMemoryApplicationConfig.kt:25`).

The same sync also runs every 4 hours (`application/script/PeriodicScriptSyncer.kt:36-52`, `updateScripts = false`),
when the script-selection dialog opens (`application/interactor/script/SyncScriptsOnScriptSelectionInteractor.kt:12`),
and when development scripts are toggled (`application/interactor/settings/developers/SetDevelopmentScriptsInteractor.kt:22`).

### Subscription gating

The sync installs only scripts the user is subscribed to. A Brand user without the purchase therefore gets
nothing installed. ADR-0004 point 3 ("seeding ≠ licensing") still holds: install and run are separate gates
(`ScriptLicenseChecker` at start). On top of the sync, Brand builds add a gate after login:

- `GetBrandEntitlementInteractor` checks for a subscription to *every* Brand script, with `ignoreCache = true`
  so a user who just purchased is not shown the paywall. Stock always returns `Entitled`.
  `application/interactor/brand/GetBrandEntitlementInteractor.kt:17-37`.
- `GetBrandGateStatusInteractor` composes that check with "are all Brand scripts installed?" and returns
  `Ready` / `NeedsSubscription` / `ScriptsUnavailable`.
  `application/interactor/brand/GetBrandGateStatusInteractor.kt:19-36`.

### Failure and retry handling

- **Per-script install failures are swallowed** into `SyncProgress.Finished.exceptions`
  (`ScriptSyncManager.kt:102-118`, `:125`) and do not block login (`UserAuthManager.kt:103-106`). Nothing in
  the login flow surfaces them. In Brand builds the gate catches the result as `ScriptsUnavailable`.
- **Failure to fetch the list** (e.g. a network error on `getSubscriptions`) is *not* caught and fails the
  login on purpose (`UserAuthManager.kt:103-106`).
- **Periodic sync**: after 3 consecutive failures the user is logged out (`PeriodicScriptSyncer.kt:44-50`).
- **Brand retry**: on `ScriptsUnavailable` the gate's Retry calls `SyncBrandScriptsInteractor`, which reruns
  `scriptSyncManager.sync(updateScripts = true)`, then evaluates the gate again
  (`application/interactor/brand/SyncBrandScriptsInteractor.kt:15-17`, `presentation/brand/BrandPaywallViewModel.kt:59-69`).
  If the gate cannot be evaluated at all, the view state is `Error` (retry the check), which is kept separate
  from `Locked` (`BrandPaywallViewModel.kt:94-98`).

### What the UI shows meanwhile

- During login the setup view shows a steady headline plus "Syncing scripts... N of M" once the list size is
  known (`presentation/authenticate/login/SetupProgress.kt:21-32`, consumed at `LoginViewModel.kt:179`;
  string `syncing_scripts_progress`). On app start with a stored session, the bootstrap progress bar shows
  `SynchronizeScripts` at 70% (`application/interactor/bootstrap/BootstrapInteractor.kt:81-86`).
- After login, `MainScreen` wraps the app in `BrandEntitlementGate` (`presentation/main/MainScreen.kt:113-115`).
  In Brand builds it shows a spinner (`Loading`), then the app, or `BrandPaywallScreen` with reason
  `NEEDS_SUBSCRIPTION` / `SCRIPTS_UNAVAILABLE` / `VERIFICATION_ERROR`, each with Retry and Logout
  (`presentation/brand/BrandEntitlementGate.kt:25-75`). In stock builds the gate starts `Entitled` and makes
  no call (`BrandPaywallViewModel.kt:36-46`).
- Brand builds also hide the marketplace menu (`MainViewModel.kt:181-192`), the "search marketplace" link on
  the empty script list (`presentation/tasks/script/overview/ScriptSelectionDialog.kt:307-309`), and the My
  Scripts entry in settings (`presentation/settings/ApplicationSettingsView.kt:210-215`).

## 3. Uninstalling an auto-installed script today

Both Remove buttons call `RemoveMarketplaceScriptInteractor`: marketplace script detail
(`presentation/marketplace/ScriptDetailViewModel.kt:169-198`) and My Scripts
(`presentation/myscripts/MyScriptsViewModel.kt:97`). The interactor refuses while tasks are running, then
**unsubscribes server-side** and deletes the JAR
(`application/interactor/marketplace/RemoveMarketplaceScriptInteractor.kt:17-31`). Paid Stripe subscriptions
cannot be removed that way: the backend returns 403 (`PaidSubscriptionActiveException`, `MarketplaceApiClient.kt:325-353`,
`MarketplaceProviderImpl.kt:73-74`).

- **Stock**: removal sticks. The subscription is gone, so later syncs neither reinstall the script nor
  consider it at all.
- **Brand**: there is no reachable Remove UI (the marketplace and My Scripts entries are hidden), and Brand
  scripts are paid. If the JAR disappears anyway (e.g. deleted on disk), the next sync reinstalls it because
  the subscription is still there. Until then the gate shows `ScriptsUnavailable` with a Retry.

## 4. Reusing this to auto-install one script in stock Cereal

The download, update and periodic-resync machinery needs no change. The sync already installs every
subscribed script in stock builds. What is missing is the subscription itself. A minimal path:

1. **Config**: a single `autoInstallScriptId` (or reuse `brandScriptIds` with a stock default) in
   `ApplicationConfig`/`CerealConfiguration`. Note that `scopeToBrand` and `GetBrandEntitlementInteractor`
   switch on `isBranded`, not on `brandScriptIds` being non-empty. Putting an ID in `brandScriptIds` of a
   stock build therefore does nothing on its own, and setting `isBranded` would turn on the whole lock-down.
   Brand scripts and an "auto-installed script" are separate concepts, so a separate field is the clearer
   choice.
2. **Seed step**: in `UserAuthManager.onAuthenticated`, before `syncScripts()` (`UserAuthManager.kt:84`),
   call `marketplaceProvider.subscribeToScript(id)` if the user is not yet subscribed. `subscribeToScript` is
   already idempotent: 409 counts as success (`MarketplaceApiClient.kt:290-312`). The sync then installs the
   script, and the existing "Syncing scripts... N of M" progress covers the UI. No new screen is needed.
3. **Free only**: if the script is not free, `subscribeToScript` returns `CheckoutRequired`
   (`InstallMarketplaceScriptInteractor.kt:21-25`). That cannot happen silently at login, so the seed step
   should require a free script and skip otherwise.
4. **Failure handling**: wrap the seed step in a try/catch that only logs. Unlike the subscription fetch, a
   failed seed must not fail the login. The next login (or a manual install) can retry it.
5. **Respect uninstall**: Remove unsubscribes (section 3), so a seed step that runs on every login would bring
   the script back after the user removed it. Persist a local "seeded" flag (e.g. in
   `ApplicationPreferenceRepository`) and seed only once per install (or per user), or record "user removed
   it" from `RemoveMarketplaceScriptInteractor`. Server-side, a removal looks exactly like never having
   subscribed, so the flag has to live on the client.
6. **Guests**: the seed works for guests only if the backend accepts free subscriptions on guest tokens
   (section 1, unverified). Each guest login on a device creates or reuses a guest identity by device name. A
   per-install flag (not per-user) avoids re-seeding every new guest identity.

No Brand gate, paywall or entitlement code is reused. Those are Brand-only and transparent in stock builds.
The reusable part is `ScriptSyncManager` together with the existing subscribe call.
