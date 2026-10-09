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
  NAMES (Jake's): the camera pill is the "Dynamic Island" in the UI (code: MiniIsland*); the pop-ups are the "mini island"
  (code: Island*/IslandController). Conversation Awareness restore timing: `utils/ConversationTiming.kt`.
  Mini island (black pill around the front camera while music plays; cover + progress ring left, bars in cover colour right,
  widens with the song name; tap = big island, swipe = skip, hold = open app; hides in landscape/full-screen/lock screen/30 s
  after pause): `overlays/MiniIsland.kt`, rules `services/MiniIslandRules.kt`, prefs in `IslandPrefs` (`PREF_MINI*`). While it's
  on, the big island skips the music-start/new-song pops. Placement works for punch-hole, notch, corner camera and no
  cutout (`MiniIslandRules.pickCamera`); overlays cap font scale at 1.15 (`CappedFontScale`); island widths clamp to the screen.
  Dynamic Island: always on with AirPods (also landscape/lock screen; hides only in full-screen after 0.7 s; 5 s link grace);
  taps 1 = expand, 2 = play/pause, 3 = next; stays (blurred) under pop-ups. Status bar height = max(insets, status_bar_height
  resource, cutout bottom) because insets can be 0 from the service. Opened island: back/skip bud out of play with the heart chip. The AirPods name row lives in Settings, not the main page (Jake's choice).
  Row pictures: `glint/RowIcons.kt` (picked from the row name; `RowIconTile`); icon buttons with press-and-hold label:
  `glint/IconAction.kt`. Glass lightens on Battery Saver/heat: `glint/GlassBudget.kt`. Screen changes use springs (`AppNavGraph.kt`).
  Light/dark switch reveal: `theme/ThemeReveal.kt` (`ThemeReveal.change`). Don't `remember` theme colours (rows went white-on-white).
  Other headphones (Jake's Beats Solo 4): the chosen device is `services/DeviceChoice.kt` (Settings > Your devices, kept until
  changed; default AirPods). Only the chosen device's controls run: non-AirPods close the AirPods channel and go through
  `services/HeadphoneLink.kt` (Android battery, Solo 4 beacon 0x2520 via `bluetooth/HeadphoneBeacon.kt`, media keys). Page:
  `screens/HeadphonesScreen.kt`; `PodsSnapshot.headphones` = one ring in the islands. Fast refresh: `glint/FrameRate.kt`.
  Sound bars follow the real music: `services/MusicPulse.kt` (Android Visualizer on the whole output, 4 bands; needs
  RECORD_AUDIO, asked once after setup and in Settings > Island; falls back to the old wiggle without it). Dynamic Island
  "Always on" (`PREF_MINI_ANYTIME`, default on): stays even with nothing playing/connected as a plain black pill
  (`Content.Rest` / `Situation.Rest`, tap = the glance, not music controls).
  Volume limit (hearing protection without root): `services/VolumeGuard.kt`. Always-on heart rate: `PREF_HR_ALWAYS`, `autoHeartRate()` in the service. The status notification is hidden unless
  `glint_status_notification` is on. Jake's app is personal: no visible LibrePods mentions.
- Any sound on the Dynamic Island (session 2026-10-06): `services/SoundRules.kt` (pure rules), `SoundSource.kt` (hears every
  sound; app from media session, just-posted notification or app on screen; Android never says which app, only the kind),
  `PhoneStatus.kt` (phone battery), Sounds situation + App icon / Phone battery / Clock slots in `IslandLook`, `SoundsSection.kt`.
  Other apps' names and icons need the `<queries>` in the
  manifest. Never schedule a recheck with a computed wait without making sure it is above zero (`MiniIslandRules.nextCheck`).
- **Never block the main thread** (Android shows "pro isn't responding" after about 5 s): no socket reads or writes (use `OffMain` /
  `serviceScope` on IO), no root shell, no `runBlocking`, no waiting for the AirPods (ATT reads wait up to 2 s) on it. A recheck that
  reschedules itself must have a wait above zero (`MiniIslandRules.nextCheck`); the island's checks are also capped (`RefreshGuard`).
  `FreezeReport` saves Android's own record of a freeze or crash (Troubleshooting > Freezes and crashes, Copy details): ask Jake for it.
- Messages and moments (round 2): `SoundRules.worthAMoment`/`Dedupe`, `SoundSource.message`, `IslandLook.Situation.Message`
  (its window is temporarily wider: `MiniGeometry.messageWindow`), `PhoneStatus.momentBetween`, swipe up (`Kind.SwipeUp`),
  `screens/AppsScreen.kt` (switches are the same set as `IslandPrefs.soundIgnored`). The phone model in use is not verified (S25 FE in older
  notes, Pixel 6 in Jake's message of 2026-10-06).
- Phone-first (session 2026-10-07, Jake: the app is now a Dynamic Island app first, AirPods second): three tabs (`navigation/Tabs.kt`,
  Liquid Glass bar; Phone opens first = `screens/PhoneScreen.kt`, Island = `IslandSettingsScreen.kt`, Headphones = the old main page;
  all inside root `Screen.AirPodsSettings`). The pill shows the app in front (`services/ScreenApp.kt`, via the accessibility service,
  only real activities count), wallpaper-coloured home tiles + date on the home screen, a padlock that springs open on unlock; never
  the time by default (Jake has it in the status bar). A tap with nothing playing opens the glance (`overlays/GlancePanel.kt`,
  `services/GlanceRules.kt`, `PhoneControls.kt`), never an empty music player. Island timer: `services/IslandTimer.kt`
  (`USE_EXACT_ALARM` in the foss manifest only). Hide in apps `PREF_HIDE_IN`; phone moments (`PREF_PHONE_MOMENTS`)
  also widen with a line of words (`MiniMoment.of`).
- iPhone-accurate island (session 2026-10-09, default "iPhone" style, Settings > Island > Style): Apple's numbers live in
  `services/IslandSpec.kt` (quoted from Apple's HIG); black in light and dark, key line only on dark, pop-ups open out of the pill around
  the camera (`IslandGeometry.around`/`band`, needs the accessibility layer), detached timer circle (`MiniIsland` `detached`/`restWindow`).
  The old glass look is the "Glass" style. Jake's gestures stay (Apple's own: tap = open app, hold = expand).
- **`docs/REQUESTS.md` is the ledger of everything Jake has asked for and how finished each thing is: read it first and update it
  whenever he asks for something or a status changes.** Folder READMEs (`android/`, `services/`, `overlays/`, `glint/`, `screens/`,
  tests) map the code: update them with the code. Check a README doesn't already exist before writing one (`git ls-files | grep -i readme`).
- Faster cloud build setup: `android/tools/cloud-setup.sh` (then `source <dir>/env.sh`); it does the steps in the two notes above.
- See `DECISIONS.md` and `TESTING.md` at the repo root.
