# FlowPlay 1.1.36 — Phone and TV sources

Android IPTV player using Kotlin, Java, Jetpack Compose and Media3. Version **1.1.36**, version code **39**, package **com.zynelabs.flowplay**.

This repository contains both fixed-layout APK variants. It does not contain playlists, provider accounts, portal credentials, access tokens, signing keys or APKs. Users supply their own content sources in the app.

## Source layout

- `app/src/main/`: shared application, data clients, TV player, manifest and resources.
- `app/src/phone/`: Phone-specific player override.
- `prepare_variant.py`: stages Phone or TV sources without modifying the originals; fixes the mode rather than auto-switching by device.
- `build.sh`, `gen_lib_r.py`: the existing manual Kotlin/Java → aapt2 → d8 → zipalign → apksigner pipeline, adapted to repository-relative paths.
- `dependencies.lock.json`, `fetch_dependencies.py`: exact dependency filenames, Maven download URLs and SHA-256 checksums from the 1.1.36 build.
- `libs/`: existing vendored Media3, Guava and ZXing runtime dependencies; retain these files in Git.
- `dl.py`: original POM-walking dependency resolver for maintenance. Use the locked fetch script for this release instead.
- `tests/`: portable regression checks and Java test sources. Old one-off checks requiring external review snapshots and an obsolete pre-TV-panel UI assertion are omitted.

## Public-release sanitization

Developer contact handles, GitHub profile, Telegram invite and website URLs have been replaced with placeholders in `Screens.kt` and `Store.java`. The original sync service URL is also replaced with `https://sync.example.invalid`; QR/cloud sync will not work until you configure your own service. Local/manual playlist input remains available. Package and product branding remain unchanged. Test accounts, tokens and MAC literals are synthetic localhost/example fixtures, not real accounts.

The portable build pipeline successfully produced Phone and TV APKs before these final string-only public-release substitutions. Both source-set checks were rerun afterward.

## Build requirements

Use Linux, or Linux under WSL, with Bash, **JDK 17**, **Python 3**, `zip`, `unzip`, and the Android SDK. Use a checkout path without spaces for the manual shell pipeline.

Install SDK packages with your Android SDK's `sdkmanager`:

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0"
export ANDROID_HOME="$HOME/Android/Sdk" # adjust to your SDK location
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64" # adjust if needed
```

There is no Gradle project or Gradle wrapper in this release. Android Studio/standalone `kotlinc` is not required; the pinned embeddable Kotlin compiler is downloaded by the fetch script.

## Build both APKs

```bash
python3 fetch_dependencies.py
FLOWPLAY_VARIANT=phone bash build.sh
FLOWPLAY_VARIANT=tv bash build.sh
```

Outputs:

```text
artifacts/FlowPlay-1.1.36-Phone.apk
artifacts/FlowPlay-1.1.36-TV.apk
```

The default is `phone`. Phone omits the Leanback launcher category; TV retains it and uses the TV label. Both variants share the same package, so they replace each other rather than install side by side.

## Signing and existing installations

On first build, a fresh local debug keystore is generated in `.local/debug.keystore` using the conventional public Android debug password. That directory is ignored by Git. **This new key will not match previously distributed APKs**, so updating an existing installation requires the original signing key. Do not uninstall casually: that may remove local app data.

To use your existing key, keep it outside the repository and pass its path/password locally:

```bash
export FLOWPLAY_KEYSTORE="/absolute/path/to/your.keystore"
read -r -s -p "Keystore password: " FLOWPLAY_KEYSTORE_PASSWORD; echo
export FLOWPLAY_KEYSTORE_PASSWORD
FLOWPLAY_VARIANT=phone bash build.sh
unset FLOWPLAY_KEYSTORE_PASSWORD
```

The script assumes the key password is the same as the keystore password and the keystore contains one signing key. Adapt signing locally for other configurations. Never upload the keystore or its password.

## Source checks

These checks need Python only:

```bash
python3 tests/build_variants_checks.py
python3 tests/local_poster_checks.py
```

Java test files are included as regression sources; Android-dependent ones require a suitable Android/test harness. Static checks are not a substitute for testing playback or D-pad navigation on real devices.

## Upload to Git

Extract this folder, then run:

```bash
git init
git add .
git status --short
# Review the staged files before committing.
git commit -m "FlowPlay 1.1.36 Phone and TV sources"
git branch -M main
git remote add origin YOUR_REPOSITORY_URL
git push -u origin main
```

Build outputs, fetched dependencies, local settings, logs and signing material are ignored. No old Git history, runtime app data, screenshots or portal probe files are included.

Existing brand artwork attribution remains in `app/src/main/assets/category_brands/sources.json`. Third-party libraries and brand assets retain their respective rights; this source handoff does not assign a new license or grant redistribution rights.
