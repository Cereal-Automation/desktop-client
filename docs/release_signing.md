# Release Metadata Signing

The desktop client auto-updates by reading `client/latest-<os>.json` from object storage, which
tells it where to download the next installer. To prevent a compromised bucket, account, or TLS
path from shipping arbitrary code (see issue #484), the client **verifies a cryptographic signature
on that metadata and a hash on the installer** before it downloads or launches anything.

This is enforced strictly: for direct downloads, metadata with a missing or invalid signature is
rejected, and a downloaded installer whose SHA-256 does not match is deleted. **The release pipeline
must publish signed metadata or clients will refuse to update.**

## How it works

1. The release pipeline computes the lowercase hex SHA-256 of the installer.
2. It builds a canonical message:

   ```
   <version>|<min_version>|<download_url>|<download_sha256>
   ```

3. It signs that message with the release **private** key (RSA, SHA-256 / PKCS#1 v1.5) and writes
   both `download_sha256` and a Base64 `download_signature` into `latest-<os>.json`.
4. The client verifies `download_signature` against the embedded release **public** key
   (`ApplicationConfig.releasePublicKey`), then verifies the downloaded bytes against
   `download_sha256`.

The canonical message format is defined once in
[`ReleaseMetadataVerifier.canonicalMessage`](../cereal-client/src/main/java/com/cereal/client/infrastructure/data/datasource/network/security/ReleaseMetadataVerifier.kt)
and must match the signing step in
[`.github/actions/create-latest-version-json`](../.github/actions/create-latest-version-json/action.yml).

Store builds (Microsoft Store / Apple App Store) carry an empty `download_url` and a `store_url`
instead; they are updated through the OS store, so no installer is downloaded and signing does not
apply to them.

### macOS notarized builds

The hash must be computed over the **final shipped bytes**. macOS `.dmg` builds are notarized and
then **stapled** (`xcrun stapler staple`), and stapling rewrites the `.dmg` in place. The pipeline is
ordered so this happens first:

```
build + sign + notarize + staple   (build-client: rewrites the .dmg in place)
  → copy to dist/                  (build-and-upload)
  → SHA-256 + sign metadata        (create-latest-version-json)
  → upload                         (upload-to-r2)
  → purge edge cache               (purge-cloudflare-cache)
```

Because the file is hashed and uploaded only after stapling — and is not touched in between — the
`download_sha256` matches what the client downloads. This integrity check is independent of, and
complementary to, Gatekeeper's own notarization check when the user opens the `.dmg`.

**Do not** move hashing earlier, or re-sign/re-staple/re-compress a `.dmg` after it has been hashed,
or every macOS auto-update will fail verification.

## CDN edge cache (why the upload is followed by a purge)

The download artifacts use **mutable "latest" filenames** — `cereal-client-latest-arm64.zip`,
`latest-macos-arm64.json`, etc. — so every release overwrites the same object keys. `downloads.cereal-automation.com`
is an R2 bucket fronted by the Cloudflare cache: `upload-to-r2` replaces the object at the **origin**,
but the **previous** copy can stay cached at the **edge**. A client that fetches the fresh, signed
metadata but is served a stale zip from the edge computes a different hash and rejects it with:

```
java.io.IOException: SHA-256 mismatch: expected <new> but was <stale>
```

To close this window, `publish-artifact` (and the generic-macOS and Windows-Store JSON uploads) run
[`purge-cloudflare-cache`](../.github/actions/purge-cloudflare-cache/action.yml) immediately after the
upload, purging the download URL and its `latest-*.json` by URL. A failed purge fails the release.

### Required configuration

The purge needs two values; **until both are set the purge is skipped with a warning** (releases still
succeed, but stale-cache mismatches remain possible):

- `CLOUDFLARE_ZONE_ID` — repository **variable**, the zone id of `cereal-automation.com`.
- `CLOUDFLARE_CACHE_PURGE_TOKEN` — repository **secret**, a Cloudflare API token scoped to only the
  **Zone → Cache Purge** permission on that zone.

```bash
gh variable set CLOUDFLARE_ZONE_ID --repo Cereal-Automation/desktop-client --body '<zone-id>'
gh secret   set CLOUDFLARE_CACHE_PURGE_TOKEN --repo Cereal-Automation/desktop-client --body '<api-token>'
```

### Manually verifying / fixing the served files

Compare the signed hash in the metadata against the bytes the CDN actually serves, and check whether
the edge is stale (`cf-cache-status: HIT`) versus the origin (cache-bust with a `?cb=` query):

```bash
curl -s https://downloads.cereal-automation.com/client/latest-macos-arm64.json \
  | grep -o '"download_sha256": *"[a-f0-9]*"'
curl -s -o /tmp/edge.zip   https://downloads.cereal-automation.com/client/cereal-client-latest-arm64.zip
curl -s -o /tmp/origin.zip 'https://downloads.cereal-automation.com/client/cereal-client-latest-arm64.zip?cb=verify'
shasum -a 256 /tmp/edge.zip /tmp/origin.zip   # both must equal download_sha256
```

If the origin matches but the edge does not, purge the cache (dashboard → the `cereal-automation.com`
zone → Caching → Purge Cache → by URL, or re-run the release). If the **origin** itself is wrong, the
artifact must be re-published — purging alone will not fix it.

## The keypair

The release-signing key is **dedicated** — it is intentionally separate from the marketplace key
(`marketplacePublicKey`) so the two never share key material.

- **Public key** — embedded in the client at `ApplicationConfig.releasePublicKey` (set in
  `CerealConfiguration`). Safe to commit.
- **Private key** — stored **only** as the `RELEASE_PRIVATE_KEY` GitHub Actions secret. Never commit
  it and never log it.

### Generating a new keypair

```bash
# Private key (keep secret) + matching public key
openssl genrsa -out release_private.pem 4096
openssl rsa -in release_private.pem -pubout -out release_public.pem
```

Then:

1. Paste the contents of `release_public.pem` into `releasePublicKey` in
   [`CerealConfiguration.kt`](../cereal-client/src/main/java/com/cereal/client/infrastructure/CerealConfiguration.kt).
2. Add the **full contents** of `release_private.pem` (the PEM, including the
   `-----BEGIN/END PRIVATE KEY-----` lines) as the `RELEASE_PRIVATE_KEY` repository secret:

   ```bash
   gh secret set RELEASE_PRIVATE_KEY --repo Cereal-Automation/desktop-client < release_private.pem
   ```

3. Securely delete the local private key:

   ```bash
   rm -P release_private.pem    # macOS; use shred -u on Linux
   ```

### Verifying a keypair matches

```bash
# The two moduli must be identical
openssl rsa -in release_private.pem -pubout -outform DER | openssl dgst -sha256
openssl pkey -pubin -in release_public.pem -pubout -outform DER | openssl dgst -sha256
```

## Rolling the key

Because verification is strict, a key rotation is a coordinated change:

1. Generate a new keypair (above) and update `releasePublicKey` in the client.
2. Add the new private key as `RELEASE_PRIVATE_KEY`.
3. Ship a client release built with the new public key **before** publishing metadata signed with
   the new private key. Clients on the old public key cannot verify new-key signatures, so they will
   stop auto-updating until users are on a build that carries the new public key.

## Manual verification of published metadata

```bash
JSON=$(curl -fsSL "https://<cdn>/client/latest-linux.json")
VERSION=$(jq -r .version <<<"$JSON")
MIN=$(jq -r .min_version <<<"$JSON")
URL=$(jq -r .download_url <<<"$JSON")
SHA=$(jq -r .download_sha256 <<<"$JSON")
SIG=$(jq -r .download_signature <<<"$JSON")

printf '%s' "${VERSION}|${MIN}|${URL}|${SHA}" \
  | openssl dgst -sha256 -verify release_public.pem \
      -signature <(printf '%s' "$SIG" | openssl base64 -d -A)
# => Verified OK
```
