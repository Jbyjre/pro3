# Rules for Claude sessions on this repo

## Standing rules from Jake (owner)
- NEVER auto-subscribe to PR or merge updates. Do not call `subscribe_pr_activity`,
  do not enable auto-merge, do not schedule check-ins or polling on PRs/CI, unless
  Jake explicitly asks for it in the current session.
- Jake has no coding experience: explain technical things in plain language, never
  hand him terminal commands to run. Do the repo/build/release work yourself.
- Verify facts before stating them. Mark anything unchecked as "not verified".
- Ask before spending money or doing anything hard to undo.

## Project notes
- Android app ("Glint", a fork of LibrePods) lives in `android/`. Local build:
  `./gradlew assembleFossDebug`. Cloud build: `.github/workflows/glint-apk.yml` runs on
  every branch and publishes `Glint.apk` (main) or `Glint-preview.apk` (other branches)
  as GitHub Releases. It signs with a key the workflow creates and keeps in the Actions
  cache. Never commit signing keys to this public repo; never use upstream's keys.
- Cloud sessions cannot reach dl.google.com / maven.google.com. `deps-snapshot.yml`
  publishes a Gradle cache snapshot (release tag `ci-deps-snapshot`) that sessions can
  download to build offline.
- Local build in a cloud session: download the `ci-deps-snapshot` release asset
  (`gradle-deps.tgz`), extract into a GRADLE_USER_HOME, then run gradle with `--offline`.
  The Android SDK can be taken from the `cimg/android` Docker image layers (Docker Hub is
  reachable). NDK 30 isn't in that image: pass `-PndkVersion=29.0.14206865 -PcmakeVersion=4.1.2`.
- Screenshot tests: `./gradlew testFossDebugUnitTest --tests '*GlintScreenshots*'` writes PNGs to
  `android/app/build/screenshots`. Robolectric's Android image may need pre-downloading
  (Maven Central rate-limits it); point `ROBOLECTRIC_DEPS_DIR` at a folder containing the jar.
- New Glint code lives in `presentation/glint` (art, glass, parts), `presentation/overlays`
  (island, card, overlay window), `services/GlintStatus.kt`, `bluetooth/AirPodsDetection.kt`,
  `bluetooth/ReconnectPolicy.kt`, `utils/CompanionLink.kt`. Hidden Glint Lab: Settings >
  About > tap "Version code" 7 times.
- See `DECISIONS.md` and `TESTING.md` at the repo root.
