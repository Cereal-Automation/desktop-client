# Install Apple Certificate Action

This composite action installs an Apple signing certificate into a temporary keychain for use in macOS builds.

## Description

This action:

1. Creates a temporary keychain with a randomly generated password
2. Imports the provided Apple certificate (P12 format) into the keychain
3. Downloads and installs the Apple WWDR Intermediate Certificate
4. Configures the keychain to allow codesign access without UI prompts

## Inputs

| Input                  | Description                                   | Required |
|------------------------|-----------------------------------------------|----------|
| `certificate-base64`   | Base64-encoded Apple certificate (P12 format) | Yes      |
| `certificate-password` | Password for the Apple certificate            | Yes      |

## Outputs

| Output          | Description                       |
|-----------------|-----------------------------------|
| `keychain-path` | Path to the created keychain file |

## Usage

```yaml
- name: Install Apple certificate
  id: apple-cert
  uses: ./.github/actions/install-apple-certificate
  with:
    certificate-base64: ${{ secrets.APPLE_CERTIFICATE_BASE64 }}
    certificate-password: ${{ secrets.APPLE_CERTIFICATE_PASSWORD }}

- name: Build and sign
  run: |
    # Use the keychain in your build process
    codesign --keychain ${{ steps.apple-cert.outputs.keychain-path }} ...

- name: Clean up keychain
  if: always()
  run: security delete-keychain ${{ steps.apple-cert.outputs.keychain-path }} || true
```

## Important Notes

### Cleanup

Always include a cleanup step to delete the temporary keychain after your build completes:

```yaml
- name: Clean up keychain
  if: always()
  run: security delete-keychain ${{ steps.apple-cert.outputs.keychain-path }} || true
```

The `if: always()` condition ensures the keychain is deleted even if the build fails.

### Certificate Format

The certificate must be:

- In P12 (PKCS#12) format
- Base64-encoded for storage as a GitHub secret
- Protected with a password

To create a base64-encoded certificate:

```bash
base64 -i YourCertificate.p12 | pbcopy
```

### Keychain Settings

The action configures the keychain with:

- **Timeout**: 21600 seconds (6 hours)
- **Auto-lock**: Disabled during the timeout period
- **Partition list**: Configured to allow codesign access without UI prompts

### Apple WWDR Certificate

The action automatically downloads and installs the Apple Worldwide Developer Relations (WWDR) G3 intermediate
certificate, which is required for proper code signing.

## Example: Complete macOS Build Job

```yaml
build-macos:
  name: Build macOS (DMG)
  runs-on: macos-latest
  steps:
    - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803 # v6.1.0

    - name: Install Apple certificate
      id: apple-cert
      uses: ./.github/actions/install-apple-certificate
      with:
        certificate-base64: ${{ secrets.APPLE_CERTIFICATE_BASE64 }}
        certificate-password: ${{ secrets.APPLE_CERTIFICATE_PASSWORD }}

    - name: Build DMG
      run: |
        ./gradlew packageDmg \
          -Pmac.signing.identity="${{ secrets.APPLE_SIGNING_IDENTITY }}" \
          -Pmac.signing.keychain="${{ steps.apple-cert.outputs.keychain-path }}"

    - name: Notarize
      run: |
        xcrun notarytool submit app.dmg \
          --apple-id "${{ secrets.APPLE_ID }}" \
          --password "${{ secrets.APPLE_ID_PASSWORD }}" \
          --team-id "${{ secrets.APPLE_TEAM_ID }}" \
          --wait

    - name: Clean up keychain
      if: always()
      run: security delete-keychain ${{ steps.apple-cert.outputs.keychain-path }} || true
```

## Troubleshooting

### "User interaction is not allowed" error

If you see this error during code signing, ensure:

1. The keychain is unlocked
2. The partition list is correctly set (this action handles this automatically)

### Certificate not found

Verify that:

1. The certificate is correctly base64-encoded
2. The certificate password is correct
3. The signing identity name matches the certificate's common name

### Keychain already exists

If the build is retried, the keychain path might already exist. The action uses `$RUNNER_TEMP` to ensure a unique path
per run.

## Related Actions

- [build-and-upload](../build-and-upload/) - Uses this action's output for signing
- [setup-build-environment](../setup-build-environment/) - Sets up Java and Gradle
