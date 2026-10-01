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
- Android app (shown to Jake as "pro", lowercase, his choice; code names still say Glint; app ID
  `io.github.jbyjre.glint` must not change or updates stop installing over the old app) lives in `android/`. Local build:
  `./gradlew assembleFossDebug`. Cloud build: `.github/workflows/glint-apk.yml` runs on
  every branch and publishes `pro.apk` (main) or `pro-preview.apk` (other branches)
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
  `bluetooth/ReconnectPolicy.kt`, `utils/CompanionLink.kt`. Hidden Lab: Settings >
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
  files/heart); GitHub backup: `services/HeartBackup.kt` (into pro3 itself, orphan branch `heart-backup`
  that the APK workflow skips; fine-grained token for pro3 with Contents: write; refuses public repos, so pro3 must be
  private; Keystore-encrypted token; retries by itself when the internet returns);
  screens `HeartHistoryScreen.kt`. Readings in the first 3 s of each sensor start are dropped (`HR_WARMUP_MS`).
  Background pace `PREF_HR_PACE`. "Only in Adaptive" CA: `PREF_CA_ADAPTIVE_ONLY`. Motion helpers: `glint/GlintMotion.kt`.
  App icon (Jake's choice: no AirPods/case, no pink, black/white/gray, smooth and abstract): Glint's ring around a glass pearl, in
  Black (default), White and Graphite (`drawable-v24/ic_launcher*`); chosen in Settings > App icon via manifest activity-aliases (`theme/AppIcon.kt`).
  Island moments and options: `services/IslandPrefs.kt` (Settings > Island, `IslandSettingsScreen.kt`). Music on the island: `services/NowPlaying.kt`
  (media keys always; song names via optional Notification access, `MediaAccessService`). Jake wants the opened island small: no song bar,
  no time-left line, batteries as L/R/case rings, just a play/pause button. Heart shows only inside the opened island as a small
  chip in the island's own white/graphite (never red); it is always there ("-- BPM" with no reading, tap opens the app),
  morphing out of the play/pause button 1 s after opening (`CHIP_DELAY_MS`); with a reading, tapping it grows the island to the explanation page. It pops the island only for high-rate alerts. Heart beat: one steady loop (`rememberHeartBeat`), never restarted per reading. Ear packets are primary/secondary, not left/right.
  Mini island (black pill around the front camera while music plays; cover + progress ring left, bars in cover colour right,
  widens with the song name; tap = big island, swipe = skip, hold = open app; hides in landscape/full-screen/lock screen/30 s
  after pause): `overlays/MiniIsland.kt`, rules `services/MiniIslandRules.kt`, prefs in `IslandPrefs` (`PREF_MINI*`). While it's
  on, the big island skips the music-start/new-song pops. The AirPods name row lives in Settings, not the main page (Jake's choice).
  Row pictures: `glint/RowIcons.kt` (picked from the row name; `RowIconTile`); icon buttons with press-and-hold label:
  `glint/IconAction.kt`. Glass lightens on Battery Saver/heat: `glint/GlassBudget.kt`. Screen changes use springs (`AppNavGraph.kt`).
  Light/dark switch reveal: `theme/ThemeReveal.kt` (`ThemeReveal.change`). Don't `remember` theme colours (rows went white-on-white).
  Volume limit (hearing protection without root): `services/VolumeGuard.kt`. Always-on heart rate: `PREF_HR_ALWAYS`, `autoHeartRate()` in the service. The status notification is hidden unless
  `glint_status_notification` is on. Jake's app is personal: no visible LibrePods mentions.
- See `DECISIONS.md` and `TESTING.md` at the repo root.
