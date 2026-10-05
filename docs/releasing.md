# Releasing

This repo publishes installable APKs through GitHub Releases. Built APKs and mapping files are release assets, not committed source files.

## Version the App

Update `versionCode` and `versionName` in `app/build.gradle.kts`.

```kotlin
versionCode = 7
versionName = "0.5.0"
```

## Configure Signing

Release signing is read from:

```text
~/.android/reels-limiter/keystore.properties
```

For local builds, create that file with:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

For GitHub Actions, add these repository secrets:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Create the base64 secret from the keystore:

```sh
base64 -i release.jks | pbcopy
```

## Create a Release

Tag the commit with the same version name and push the tag:

```sh
git tag v0.5.0
git push origin v0.5.0
```

The workflow runs tests, builds `assembleRelease`, uploads the APK as a workflow artifact, and creates a GitHub Release with the APK attached.

You can also run the workflow manually from the GitHub Actions tab. Manual runs upload the APK as a workflow artifact but do not create a GitHub Release unless the run is for a tag.
