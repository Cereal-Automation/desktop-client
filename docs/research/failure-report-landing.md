# Where can failure reports land on the server?

Research for #281 (map #277). Facts only, each cited. Sources read at:

- `Cereal-Automation/Web` `origin/master` @ `e4dbff8` (paths below without a repo prefix are in Web)
- `Cereal-Automation/desktop-client` `origin/master` @ `0cb0ec6` (prefixed `client:`)

## 1. The `telemetry/goal-runs` endpoint

**Routes.** `POST /telemetry/goal-runs` -> `GoalRunReportController@store`, `PATCH /telemetry/goal-runs/{runId}` -> `@update`
(`routes/api.php:45-48`). They sit inside the `sign_response` group (`routes/api.php:24`) and outside the
`auth:sanctum` group (`routes/api.php:50`).

**Authentication: none.** The controller docblock says it is "open to any caller (user token, guest token or none);
nothing is taken from the token" (`app/Http/Controllers/Api/GoalRunReportController.php:12-15`, same in
`routes/api.php:42-44`). The only request precondition is the `sign_response` middleware
(`app/Http/Kernel.php:93`): it rejects a request with 400 unless an `X-Salt` header of at least 32 chars is present,
then signs the response body (`app/Http/Middleware/AddDigitalSignature.php:25-36`). The client always sends a token
when it has one, via `AuthorizationInterceptor`, and its `SignatureInterceptor` handles the salt/signature
(`client:cereal-client/src/main/java/com/cereal/client/infrastructure/data/datasource/network/marketplace/MarketplaceApiClient.kt:96-102`).

**Rate limiting.** The routes drop the group's `throttle:api` (60/min per user or IP, `app/Http/Kernel.php:68-71`,
`app/Providers/RouteServiceProvider.php:58-60`) and use `throttle:goal-run-reports` (`routes/api.php:45`), defined in
`app/Providers/RouteServiceProvider.php:81-94`:

- 120 requests/hour keyed by `install:<installId>`, and 600/hour keyed by `ip:<ip>`.
- On `PATCH`, the body has no `installId`, so the limiter does a DB lookup of `install_id` by `run_id` (`:86-88`).
- `TrustProxies` trusts all proxies (`app/Http/Middleware/TrustProxies.php:15`), so `$request->ip()` comes from forwarded headers.

**Validation and storage.** `store` validates `runId`/`installId` as UUIDs, `goal` max 4000 chars, other strings max
255 (`GoalRunReportController.php:20-32`), runs `goal` and `startUrl` through `GoalTextScrubber::scrub`, and
`createOrFirst` keyed by `run_id`, so a repeat POST is a no-op (`:34-46`). `update` sets outcome
(`succeeded|failed|stopped`), cost, duration and `finished_at`, and 404s on an unknown `runId` via `firstOrFail`
(`:51-66`). Both return 202 with no body.

**Table** `goal_run_reports` (`database/migrations/2026_11_01_000000_create_goal_run_reports_table.php:15-35`):
`run_id` uuid unique, `install_id` uuid indexed, `goal`/`start_url` text, run facts as strings, nullable
outcome/cost/duration/`finished_at`, timestamps, index on `created_at`. No column for blobs or object keys; no
relation to users.

**Scrubber.** `app/Support/GoalTextScrubber.php:24-36`: four regexes over one string (URL query/fragment strip,
emails -> `[email]`, Luhn-valid 13-19 digit runs -> `[card]`, phone shapes -> `[phone]`). The docblock calls it
"defence in depth rather than a guarantee" (`:5-9`). It operates on plain text; nothing in it is DOM- or
snapshot-aware.

**Client side.** `GoalRunReportProviderImpl` is fire-and-forget: a failure is logged and dropped, "no queue, no
retry" (`client:cereal-client/src/main/java/com/cereal/client/infrastructure/provider/GoalRunReportProviderImpl.kt:13,49-57`).
Bodies are JSON posted by `MarketplaceApiClient.reportGoalRunStart` / `reportGoalRunFinish`
(`client:.../marketplace/MarketplaceApiClient.kt:525-549`).

## 2. Request body size limits

| Layer | Limit | Source |
|---|---|---|
| nginx (in container) | `client_max_body_size 30m`, "just above the largest legitimate upload (script JAR, capped at 25 MB)" | `docker/nginx.conf:14-16` |
| nginx -> php-fpm | `fastcgi_read_timeout 120` | `docker/nginx.conf:145` |
| PHP `post_max_size` / `upload_max_filesize` | **Not set anywhere in the repo.** No `php.ini`, `.user.ini` or `ini_set` exists; the Dockerfile only edits `pm.max_children`. The `php:8.5-fpm-bookworm` image ships without an active `php.ini`, so PHP's built-in defaults apply: `post_max_size=8M`, `upload_max_filesize=2M`, `memory_limit=128M` ([php.net ini list](https://www.php.net/manual/en/ini.list.php)). The Dockerfile comment assumes the 128M memory_limit. | `Dockerfile:25,58-67` |
| php-fpm workers | `pm.max_children = 3`, sized for a 512 MB container shared with nginx, Node SSR and the scheduler | `Dockerfile:58-67`, `docker/supervisord.conf:8-44` |
| Laravel | Script JAR max 25600 KB (app-level, Filament upload only) | `config/scripts.php:13`, `app/Filament/Resources/ScriptResource/Pages/ScriptReleases.php:236-243` |
| Edge | Cloudflare fronts the origin (`docker/nginx.conf:126-132`, `app/Http/Controllers/Api/HealthController.php:14-19`). Cloudflare's documented max request body is 100 MB on Free/Pro plans ([Cloudflare docs](https://developers.cloudflare.com/workers/platform/limits/#request-limits)); the plan in use is not in the repo. | |
| Reverse proxy | TLS terminates at a Dokploy reverse proxy (`docker/nginx.conf:50-52`); `CLAUDE.md:64` says production is "deployed on self-hosted Dokploy". Its body limit is not configured in this repo. | |

Note: `.do/app.yaml` (DigitalOcean App Platform spec, `basic-xxs`, 1 instance, `.do/app.yaml:254-255`) is still in
the repo and `app/Console/Kernel.php:29` mentions "the 512MB App Platform box", while `CLAUDE.md:64` and
`docker/nginx.conf:51` say Dokploy. Which one serves production today is not settled by the repo.

The 25 MB JAR upload works under the 8 MB PHP default because Filament's `FileUpload` goes through Livewire temporary
uploads; with no `config/livewire.php` they use the default filesystem disk, which production sets to `s3_cdn`
(`FILESYSTEM_DRIVER`, `.do/app.yaml:184-186`), and Livewire uploads straight to S3 via a presigned URL for an S3
disk. That last step is Livewire's documented behaviour, inferred here, not tested.

## 3. Object storage

Two S3-driver disks, both on DigitalOcean Spaces, both using **one shared key pair**
(`config/filesystems.php:45-65`):

| Disk | Bucket env | Bucket (prod) | Used for |
|---|---|---|---|
| `s3_scripts` | `DO_BUCKET_SCRIPTS` | `cereal-automation-scripts` | Script JARs, private (`ScriptReleases.php:237-240`); handed out as 5-minute presigned GETs (`app/Http/Controllers/Api/ScriptsController.php:45-47`) |
| `s3_cdn` | `DO_BUCKET_CDN` | `cereal-automation-cdn` | Default disk and Filament disk (`FILESYSTEM_DRIVER`/`FILAMENT_FILESYSTEM_DISK` = `s3_cdn`), public CDN at `assets.cereal-automation.com` |

Config keys: `DO_ACCESS_KEY_ID`, `DO_SECRET_ACCESS_KEY`, `DO_DEFAULT_REGION` (`us-east-1`, a placeholder for the
SDK), `DO_ENDPOINT` (`https://ams3.digitaloceanspaces.com`), plus per-bucket URL vars (`.do/app.yaml:161-207`).
`league/flysystem-aws-s3-v3` is installed (`composer.json:21`). There is no bucket, disk or prefix for telemetry or
diagnostics, and no lifecycle (expiry) rule is configured in the repo.

**Client side.** The client holds no Spaces credentials and never writes to object storage. The class named in the
ticket, `DigitalOceanSpacesDataSource`, no longer exists on master; its successor `RealObjectStorageDataSource` only
reads update metadata through `DownloadsApiClient`
(`client:cereal-client/src/main/java/com/cereal/client/infrastructure/data/datasource/network/RealObjectStorageDataSource.kt:8-19`)
from `https://downloads.cereal-automation.com/` (`client:cereal-client/cereal-client.gradle.kts:72`). Release CI
publishes to Cloudflare R2 (`R2_BUCKET_CDN`) and only backs up to a DO Space
(`client:.github/workflows/release-client.yml:34-35,109-113`). `AGENTS.md:201` still names the old
`DigitalOceanSpacesDataSourceTest.kt`.

## 4. The prune job

- `GoalRunReport` uses `MassPrunable`; `prunable()` selects `created_at < now()->subMonths(12)`
  (`app/Models/GoalRunReport.php:20,43-51`).
- Scheduled as a closure, not `model:prune`, to avoid forking a PHP process on the 512 MB box:
  `$schedule->call(fn () => (new GoalRunReport)->pruneAll())->name('prune-goal-run-reports')->dailyAt('04:30')`
  (`app/Console/Kernel.php:45-50`; the fork concern is spelled out at `:27-31`).
- The scheduler runs as `php artisan schedule:work` under supervisord inside the web container
  (`docker/supervisord.conf:35-44`).
- `MassPrunable` deletes with a single query and fires no model events, so nothing hooks per-row deletion today.
- Queue is `sync` (`.do/app.yaml:92-94`): there is no background worker for async processing.

## 5. How an admin reads records today

- The only Filament panel is `developers` (`app/Providers/Filament/AdminPanelProvider.php:33-35`), a seller panel
  with `Team` tenancy (`:51`) that discovers resources from `app/Filament/Resources` (`:58`). That directory holds
  only `ScriptResource`. There is no `GoalRunReport` resource, page or widget, and nothing else in `app/`,
  `resources/` or `routes/` references goal run reports besides the controller, model, limiter and prune.
- `User::canAccessPanel()` returns `true` for every user (`app/Models/User.php:166-169`). There is no admin role or
  separate admin panel; access is scoped only by team tenancy.
- No Nova, Horizon, Telescope or Pulse in `composer.json`.
- So today records are readable only by querying the MySQL database directly (managed cluster
  `db-mysql-ams3-cereal` in `.do/app.yaml:1-8`), e.g. via `php artisan tinker` or the Boost `database-query` tool
  that `CLAUDE.md:133` points to.

## Implications

- A multi-MB trace POSTed to the Laravel app would be refused by PHP above 8 MB, by nginx above 30 MB, and would hold
  one of only three php-fpm workers on a 512 MB box for the upload. A presigned PUT straight to Spaces (the pattern
  Livewire already uses for JARs, and `temporaryUrl` already uses for downloads) avoids all three; the API call then
  only issues the URL and records metadata.
- Storage needs a new private bucket or prefix. The existing key pair is shared and can write the script JARs and
  the public CDN bucket; a separate Spaces key scoped to a diagnostics bucket limits the blast radius. The client
  must never hold Spaces credentials, only short-lived presigned URLs.
- 30-day retention differs from the 12-month goal run report retention, so it needs its own prune: a Spaces
  lifecycle expiration rule on the bucket, or a `Prunable` (not `MassPrunable`) model whose `pruning()` hook
  deletes the object. The current `MassPrunable` pattern alone would leave orphaned objects.
- The endpoint is unauthenticated, so the only abuse guard is the per-install/per-IP limiter. Issuing an upload URL
  should at least require an existing `goal_run_reports` row for the `runId`, the same lookup the PATCH limiter
  already does, and cap the object size in the presigned policy.
- `GoalTextScrubber` is a plain-text regex pass; it cannot mask page snapshots inside an object the server never
  reads. Masking of snapshots has to happen on the client, or in a server-side job, and the queue is `sync` today.
- There is no admin UI. Pulling reports for a `diagnosing-bugs` session needs either a CLI path (artisan command that
  presigns a GET by `runId`) or a new Filament surface, which first needs an admin gate because `canAccessPanel` is
  open to all users.
- Hosting is ambiguous in the repo (App Platform spec vs Dokploy docs); confirm with the operator before relying on
  a proxy body limit or the instance size.
