# Where the client says what Cereal is

Research for [#133](https://github.com/Cereal-Automation/desktop-client/issues/133) (map: [#130](https://github.com/Cereal-Automation/desktop-client/issues/130)).
Snapshot of `origin/master` at `eb5aabc`. Paths are relative to the repo root; `strings.xml` means
`cereal-client/src/main/composeResources/values/strings.xml`.

## Short answer

Cereal describes itself in three places, and they don't agree:

- **The login splash** calls it a monitoring and auto-checkout platform: "Monitoring, notifying and
  auto-checkout in one platform." (`strings.xml:152`)
- **The guest profile** calls it an "automation platform" (`strings.xml:127`).
- **The packaging metadata** (installer description, MSIX, Linux `.desktop`) and the settings footer
  call it a "Desktop Automation Client" / "high performance automation".

The README is the only surface that explains what the product does: it runs marketplace scripts locally,
and those scripts typically watch webshops and notify you.

Only identity values are Brand-aware: the window title, the settings header name, the paywall `%1$s`,
and the installer name, bundle ID and icon. **Brands never override `strings.xml`.** No `values-<brand>`
qualifier or runtime string swap exists. A white-label build therefore shows every stock string it can
reach, including "Welcome to Cereal" and the auto-checkout slogan on its login screen.

## How Brand reaches copy

- `cereal-client/cereal-client.gradle.kts:21-47` reads `-Pbrand=<id>` from `brands/<id>/brand.properties`
  (gitignored private overlay, ADR-0008). `brand(key, default)` falls back to stock values.
- `cereal-client.gradle.kts:56-66` puts `BRAND_NAME`, `BRAND_HOME_DIR`, `BRAND_PAYWALL_URL` and similar into `BuildConfig`.
- `CerealConfiguration.kt:84-86` sets `name` and `title` to `BRAND_NAME` when branded, otherwise
  `"Cereal" + environmentSuffix`.
- Branded builds hide some surfaces instead of rewording them: guest login (`CerealConfiguration.kt:128`,
  `isGuestLoginEnabled = !isBranded`), the Marketplace menu item (`MainViewModel.kt:181-192`) and the
  My Scripts settings row (`ApplicationSettingsView.kt:208-217`). Copy on those hidden surfaces never
  reaches a white-label user.
- The rest of the UI uses `stringResource(Res.string.*)`, which has only one `values/` folder, so the strings are shared.

"Shared" in the tables below means a white-label build shows the same text. "Hidden" means that build
cannot reach the surface. "Brand-aware" means a Brand value replaces the text.

## 1. In-app copy (`strings.xml`)

### Product framing (what Cereal *is*)

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Login/register splash title | `strings.xml:153` → `AuthenticationLayout.kt:147` | Welcome to Cereal | **Shared, leaks.** Branded builds require login, so users see it |
| Login/register splash slogan | `strings.xml:152` → `AuthenticationLayout.kt:154` | Monitoring, notifying and auto-checkout in one platform. | **Shared, leaks** |
| Splash logo | `Res.drawable.application` → `AuthenticationLayout.kt:137` | stock Cereal app icon (compose resource, not `brandAsset`) | **Shared, leaks** |
| Login footer | `strings.xml:242` → `LoginScreen.kt:23`, `AuthenticationDialog.kt:154` | New to Cereal? | **Shared, leaks** |
| Guest profile body | `strings.xml:127` → `GuestProfileScreen.kt:170` | You are currently exploring Cereal as a guest. Create an account to unlock the full potential of our automation platform. | Hidden (no guest login) |
| Guest profile title / hint | `strings.xml:128`, `:126` | Guest Profile / Joining is free and takes less than 60 seconds. | Hidden |
| Guest feature blurb | `strings.xml:113-114` → `GuestProfileScreen.kt:228` | Premium Scripts: Access to premium scripts. | Hidden |
| Guest feature blurb | `strings.xml:109-110` → `GuestProfileScreen.kt:241` | Cloud Sync: Never lose your scripts. Subscriptions automatically restore after any reinstall. | Hidden |
| Guest feature blurb | `strings.xml:111-112` → `GuestProfileScreen.kt:254` | Community: Be part of the Cereal community. | Hidden |
| Settings footer tagline | `strings.xml:521` → `ApplicationSettingsView.kt:426` | BUILT FOR HIGH PERFORMANCE AUTOMATION. | **Shared** (generic, no name) |
| Sidebar app name | `strings.xml:15` (`app_name`) → `MainMenuView.kt:136` | Cereal | **Shared, leaks.** Uses the string resource, not `applicationConfig.name` |

### Name mentions in descriptions, empty states and errors

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Settings › Get in touch › Discord | `strings.xml:57` → `ApplicationSettingsView.kt:343` | Opens the Cereal Discord servers. | **Shared, leaks.** Section is not gated |
| Settings › Get in touch › GitHub | `strings.xml:125` → `:351` | Opens the Cereal's Github repository. | **Shared, leaks** (typo "Cereal's") |
| Settings › Get in touch › Website | `strings.xml:537` → `:359` | Opens Cereal's Official website. | **Shared, leaks** |
| Settings › Privacy › Crash reporting | `strings.xml:32` → `:390` | When Cereal crashes, … the path to your Cereal folder … | **Shared, leaks** |
| Settings › Proxy health check | `strings.xml:438` → `:505` | How often Cereal re-probes saved proxies … | **Shared, leaks** |
| Proxy provider hero | `strings.xml:361` → `ProvidersSection.kt:210` | Connect MarsProxies … manage the connection from Cereal. | **Shared, leaks** |
| Script needs newer client | `strings.xml:306` → `MyScriptsScreen.kt:186` | This script requires a newer version of Cereal. … | Hidden (My Scripts) |
| Notification center empty state | `strings.xml:222` → `NotificationCenterScreen.kt:197` | When your running tasks find items, hit a checkout, or need attention … | **Shared.** Frames tasks as finding items and checking out |
| Marketplace search placeholder | `strings.xml:205` → `MarketplaceFilters.kt:294` | Search for scripts (e.g., 'Too Good To Go')… | Hidden |
| My Scripts empty state | `strings.xml:541-543` | No scripts installed yet / Scripts you install from the marketplace show up here. / Browse marketplace | Hidden |
| Tasks empty state | `strings.xml:544-547` | No script groups yet / Create a group to organize your scripts, or add a script to get started. | Shared (no name) |
| `starting_cereal` | `strings.xml:468` | Starting Cereal | Unused resource. Bootstrap hardcodes it instead (below) |

### Paywall (white-label only)

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Paywall message | `strings.xml:4` → `BrandPaywallScreen.kt:91` | Your %1$s subscription is required to use this app. … | **Brand-aware** (`%1$s` = Brand name) |
| Paywall CTA | `strings.xml:7` → `BrandPaywallScreen.kt:118` | Get %1$s | **Brand-aware** |
| Paywall title / errors | `strings.xml:3,5,6,8,9` | Subscription required / We couldn't verify… / Try again | Shared (no name) |

## 2. Hardcoded Kotlin strings (bypass `strings.xml`)

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Bootstrap progress | `presentation/bootstrap/BootstrapViewModel.kt:225` | Starting Cereal | **Shared, leaks** |
| Test notifications (desktop/Discord/Telegram/email) | `application/interactor/settings/notifications/SendNotificationTestMessageInteractor.kt:24,37,47,56,57` | This is a test notification from Cereal! … / Cereal Email Test / … | **Shared, leaks** |
| Client-update exception | `application/exception/ClientUpdateRequiredException.kt:6` | … requires a newer version of Cereal. Please update your Cereal client. | **Shared, leaks** |
| Script-update message | `ScriptSelectionViewModel.kt:359`, `ScriptDetailViewModel.kt:204` | This script requires a newer version of Cereal. … | Shared (selection) / hidden (detail) |
| WDAC/AppLocker DB error | `infrastructure/.../room/DatabaseConnector.kt:341-343` | … blocks Cereal from running … allow Cereal … install Cereal … | **Shared, leaks** |
| Headless start error | `presentation/main.kt:83-85` | Cereal requires a graphical display to run. … | **Shared, leaks** |
| AI provider error | `infrastructure/.../llm/LlmHttp.kt:186` | … a response Cereal couldn't read. | **Shared, leaks** |

## 3. Window, about and identity

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Window title | `MainWindow.kt:27` ← `CerealConfiguration.kt:86` | `Cereal` + env suffix (e.g. `Cereal-Local`) | **Brand-aware** (`BRAND_NAME`) |
| Settings header ("about box") | `ApplicationSettingsView.kt:182-192` | `applicationConfig.name` + "VERSION: x.y.z" | **Brand-aware** |
| Get-in-touch URLs | `CerealConfiguration.kt:90-93` | cereal-automation.com, github.com/Cereal-Automation, …/contact | **Shared, leaks** (not in the descriptor) |
| Data directory | `ApplicationHome.kt:33` | `~/Cereal` | Brand-aware (`BRAND_HOME_DIR`) |
| `BuildConfig.APP_NAME` | `cereal-client/gradle.properties:1` → `cereal-client.gradle.kts:51` | Cereal Client | Shared. No user-facing reader found |
| Sidebar name | see §1 | Cereal | **Shared, leaks** |

No separate About dialog exists. The settings header is the about box.

## 4. Installer and package metadata

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Package/app name (dmg/exe/msi, `.app` name) | `cereal-client.gradle.kts:234` | `Cereal` | Brand-aware (`brand.packageName`) |
| Installer description | `cereal-client.gradle.kts:236` | `<name> - Desktop Automation Client` | Brand-aware name; **"Desktop Automation Client" suffix shared** |
| Vendor / copyright | `cereal-client.gradle.kts:237-238` | Cereal Automation | **Shared, leaks** |
| macOS bundle ID | `cereal-client.gradle.kts:298` | com.cereal-automation.client | Brand-aware |
| Icons (mac/win/linux) | `cereal-client.gradle.kts:262,301,308` | stock icons | Brand-aware (`brandAsset`) |
| Windows Start-menu group | `cereal-client.gradle.kts:305` | Cereal Automation | **Shared, leaks** |
| Linux `.desktop` | `cereal-client/appimage/cereal.desktop:3-4` | Name=Cereal / Comment=Cereal - Desktop Automation Client | **Shared, leaks.** Static file, copied as-is by `.github/actions/build-appimage/action.yml:70-84` (path also hardcodes `app/Cereal`) |
| MSIX (Microsoft Store) | `.github/actions/create-msix/action.yml:60-62,73-74` | DisplayName Cereal / PublisherDisplayName Cereal Automation / Description "Cereal - Desktop Automation Client" | **Shared, leaks** (CI-only; no branded CI exists per ADR-0008) |

## 5. Auto-update metadata

`latest-<os>.json` (`.github/actions/create-latest-version-json/action.yml:64,98`) holds only `version`,
`min_version`, `download_url`/`store_url`, `download_sha256` and `download_signature`. It contains no
descriptive copy. The feed path is Brand-aware (`BRAND_UPDATE_FEED_PATH`, ADR-0003 §5). The update dialog
strings (`strings.xml:501-504`, "Check if a newer version is available.") don't name the product.

## 6. README

| Surface | Location | Current text | White-label |
|---|---|---|---|
| Title + pitch | `README.md:1-5` | "Cereal": the desktop client for Cereal Automation, an app that "runs automation scripts on your own machine" | n/a (repo only) |
| Purpose | `README.md:7-10` | Scripts come from the Cereal marketplace; the client installs, configures, schedules and reports on them; "Typical scripts watch webshops for stock or price changes and notify you" via Discord/email/Telegram | n/a |

## Observations for the rebrand

1. **There are three framings.** Monitoring + auto-checkout (`strings.xml:152`, `:222`), "automation
   platform" (`:127`, `:521`), and "Desktop Automation Client" (installer, `.desktop`, MSIX). The README
   is the only place that says "runs scripts locally".
2. **Brand coverage stops at identity.** Every place a white-label build still says "Cereal" is marked
   **leaks** above. The worst cases appear first in a branded build: the login splash (title, slogan,
   logo), the "New to Cereal?" footer, the sidebar `app_name`, "Starting Cereal" during bootstrap, and the
   Get-in-touch section with its Cereal URLs.
3. **Fixing it is mostly one mechanism.** Most leaks are the literal "Cereal" in a string. Making those
   strings take `%1$s` = `applicationConfig.name`, as the paywall already does, would cover them. Put
   framing copy (slogan, installer description suffix) in the Brand descriptor using the existing
   `brand(key, default)` pattern. Template the `.desktop` file and MSIX manifest from the same values.
