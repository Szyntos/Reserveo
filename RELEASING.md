# Releasing an Android update

The app checks GitHub Releases directly (repo is public) for updates. This is the checklist
for cutting one.

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
2. `./gradlew :composeApp:assembleRelease`
3. Tag and push — **the tag format is load-bearing**, the app parses `versionCode` straight
   out of it:
   ```
   git tag v<versionCode>-<versionName>   # e.g. v2-1.1.0
   git push origin v<versionCode>-<versionName>
   ```
4. Publish the release with the APK attached:
   ```
   gh release create v<versionCode>-<versionName> \
     composeApp/build/outputs/apk/release/composeApp-release.apk \
     --title "<versionName>" --notes "<changelog>"
   ```

Clients with an older `versionCode` will see this release the next time they check for
updates (on launch, or via Settings → About → Check for updates).
