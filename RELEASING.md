# Releasing an update

Both the Android and Windows desktop builds check GitHub Releases directly (repo is public)
for updates. This is the checklist for cutting one.

Desktop self-update is Windows-only for now (MSI in-place upgrade via a fixed
`upgradeUuid`). macOS/Linux users just get pointed at the release page instead of an
auto-install — there's no unattended-install path there without notarization/root handling.

## One-time setup: release keystore

Android refuses to install an update over an existing app unless both are signed with the
same key. Generate a release keystore once, keep it forever, and never commit it:

```
keytool -genkeypair -v -keystore reserveo-release.keystore -alias reserveo \
  -keyalg RSA -keysize 2048 -validity 10000
```

Store the resulting file somewhere safe outside the repo, and back it up durably — if it's
lost, no future build can ever update existing installs again (every user would need to
uninstall and reinstall from scratch).

Create `keystore.properties` at the repo root (gitignored):

```
storeFile=/absolute/path/to/reserveo-release.keystore
storePassword=...
keyAlias=reserveo
keyPassword=...
```

Without this file, `assembleRelease` still builds (signed with the debug keystore, with a
build warning) so local testing keeps working — but that build can't update anyone.

## Cutting a release

1. Bump `RESERVEO_VERSION_CODE` and `RESERVEO_VERSION_NAME` in `gradle.properties`.
   `RESERVEO_VERSION_NAME` now also feeds the desktop MSI's version, which needs a
   `major.minor.build` shape — always use 3 components (e.g. `1.1.0`, not `1.1`).
2. Build both artifacts:
   ```
   ./gradlew :composeApp:assembleRelease   # Android APK
   ./gradlew :composeApp:packageMsi        # Windows installer
   ```
3. Tag and push — **the tag format is load-bearing**, both clients parse `versionCode`
   straight out of it:
   ```
   git tag v<versionCode>-<versionName>   # e.g. v2-1.1.0
   git push origin v<versionCode>-<versionName>
   ```
4. Publish the release with both artifacts attached:
   ```
   gh release create v<versionCode>-<versionName> \
     composeApp/build/outputs/apk/release/composeApp-release.apk \
     composeApp/build/compose/binaries/main/msi/Reserveo-<versionName>.msi \
     --title "<versionName>" --notes "<changelog>"
   ```

Clients with an older `versionCode` will see this release the next time they check for
updates (on launch, or via Settings → About → Check for updates). Windows installs over
the existing one in place (same `upgradeUuid`); if the release has no `.msi` asset attached
(or on macOS/Linux), the desktop app falls back to just linking the release page.
