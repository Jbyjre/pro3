# Rules for Claude sessions on this repo

## Standing rules from Jake (owner)
- NEVER auto-subscribe to PR or merge updates. Do not call `subscribe_pr_activity`,
  do not enable auto-merge, do not schedule check-ins or polling on PRs/CI, unless
  Jake explicitly asks for it in the current session.
- Jake has no coding experience: explain technical things in plain language, never
  hand him terminal commands to run. Do the repo/build/release work yourself.
- Verify facts before stating them. Mark anything unchecked as "not verified".
- Ask before spending money or doing anything hard to undo.
- Always merge finished work into main yourself once you're confident in it (tests, lint and
  release build pass locally; if CI already finished on the latest commit it must be green, but
  don't wait or poll for it). Jake asked for this; no need to ask first.

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
  `android/app/build/screenshots`; `*AppTourScreenshots*` renders every app screen (demo data)
  to `build/screenshots/tour`. Robolectric's Android image may need pre-downloading
  (Maven Central rate-limits it); point `ROBOLECTRIC_DEPS_DIR` at a folder containing the jar.
- Jake chose LibrePods' own AirPods pictures and 3D clips (res/drawable-nodpi/airpods_pro_2*,
  res/raw*/connected.mp4, res/raw/island.mp4) over drawn artwork. Don't replace them with drawn art.
- New Glint code lives in `presentation/glint` (glass, symbols, video, parts), `presentation/overlays`
  (island, card, overlay window), `services/GlintStatus.kt`, `bluetooth/AirPodsDetection.kt`,
  `bluetooth/ReconnectPolicy.kt`, `utils/CompanionLink.kt`. Hidden Glint Lab: Settings >
  About > tap "Version code" 7 times.
- The foss build has no paywall: `FOSSBillingProvider` reports everything included; the purchase page is Play-only.
- The island plays island.mp4 as video (a frame-blending spinner glitched; don't bring it back).
  Light/dark: `presentation/theme/Appearance.kt` (`GlintAppearance`, overlays use its context).
  Battery time left: `services/BatteryEstimator.kt`; bottom-card rules: `services/CardGate.kt`.
- Glass rim light: always `GlintLight.rim()` (Kyant's default highlight is a 45-degree diagonal that looks tilted).
  It swings up to 14 degrees with phone tilt (`glint/GlassTilt.kt`); level = straight overhead.
- Heart rate: `bluetooth/SensorProto.kt` + `services/HeartRate.kt`; recorder: `audio/AirPodsRecorder.kt`;
  change confirmation: `services/CommandFeedback.kt`. Live sharing: `services/HeartLink.kt` (BLE heart-rate sensor,
  https webhook, broadcast; screen `HeartShareScreen.kt`). A Wi-Fi local server was blocked by the session safety
  setting; only add it if Jake explicitly asks. History: `services/HeartHistory.kt` (one CSV per session in
  files/heart); GitHub backup: `services/HeartBackup.kt` (classic token, private repo, Keystore-encrypted token);
  screens `HeartHistoryScreen.kt`. Readings in the first 3 s of each sensor start are dropped (`HR_WARMUP_MS`).
  Background pace `PREF_HR_PACE`. "Only in Adaptive" CA: `PREF_CA_ADAPTIVE_ONLY`. Motion helpers: `glint/GlintMotion.kt`.
  App icon (Jake's choice: no AirPods/case, no pink, black/white/gray, smooth and abstract): Glint's ring around a glass pearl, in
  Black (default), White and Graphite (`drawable-v24/ic_launcher*`); chosen in Settings > App icon via manifest activity-aliases (`theme/AppIcon.kt`).
  Island moments and options: `services/IslandPrefs.kt` (Settings > Island, `IslandSettingsScreen.kt`). Music on the island: `services/NowPlaying.kt`
  (media keys always; song names via optional Notification access, `MediaAccessService`). Jake wants the opened island small: no song bar,
  just a play/pause button. Heart shows only inside the opened island (heart with the number; tapping it grows the island to the
  explanation page); it pops the island only for high-rate alerts. Heart beat: one steady loop (`rememberHeartBeat`), never restarted per reading. Ear packets are primary/secondary, not left/right.
  Light/dark switch reveal: `theme/ThemeReveal.kt` (`ThemeReveal.change`). Don't `remember` theme colours (rows went white-on-white).
  Volume limit (hearing protection without root): `services/VolumeGuard.kt`. Always-on heart rate: `PREF_HR_ALWAYS`, `autoHeartRate()` in the service. The status notification is hidden unless
  `glint_status_notification` is on. Jake's app is personal: no visible LibrePods mentions.
- See `DECISIONS.md` and `TESTING.md` at the repo root.
