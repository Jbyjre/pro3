# pro for Android (the `android/` folder)

pro is Jake's personal Android app, built on a Galaxy S25 FE with AirPods Pro 3 and Beats Solo 4. It began as a fork of LibrePods and is now a liquid-glass app with a **Dynamic Island** around the front camera and a growing set of phone features that need no headphones. It is shown to Jake as "pro" (lowercase, his choice). Code names still say Glint, and the app ID `io.github.jbyjre.glint` must never change, or updates stop installing over the old app.

**Start with [`../docs/REQUESTS.md`](../docs/REQUESTS.md)**: what Jake asked for, where it lives, and how finished it is. Then [`../CLAUDE.md`](../CLAUDE.md) (standing rules), [`../DECISIONS.md`](../DECISIONS.md) (why things are the way they are, plain language) and [`../TESTING.md`](../TESTING.md) (what only Jake's phone can confirm).

## Rules that shape how you work here

- Jake has no coding experience: plain words, no terminal commands for him, do the build and release work yourself.
- Verify before you state. Say "not verified" for anything only the phone can show.
- Finished, checked work goes into `main` by you. Never subscribe to PR or CI updates or poll them.
- Don't use sub-agents unless Jake asks.

## Build and check (cloud session)

Cloud sessions can't reach Google's servers and have no Docker daemon. One script fetches everything the build needs from places that are reachable (about 2 to 5 minutes the first time; safe to rerun):

```
android/tools/cloud-setup.sh              # default folder: ~/pro-build
source ~/pro-build/env.sh
cd android
./gradlew $GRADLE_FLAGS :app:testFossDebugUnitTest     # all unit and screenshot tests
./gradlew $GRADLE_FLAGS lintFossRelease                # CI runs this
./gradlew $GRADLE_FLAGS assembleFossRelease            # and this
```

- `$GRADLE_FLAGS` carries `--offline` and the NDK 29 / CMake 4.1.2 versions that exist offline (NDK 30 does not).
- One test class: `--tests '*SoundRulesTest*'`. One screenshot: `--tests '*GlintScreenshots.miniIslandRest*'`.
- Screenshots land in `app/build/screenshots` (`/tour` for the whole-app tour). They are full-phone PNGs; to look at many pills at once, crop the top and tile them (Pillow is installed: `python3 -I`).
- The Robolectric tests draw with a Compose test rule: no real blur, no video, no sensors. Real feel needs the phone.
- CI (`.github/workflows/glint-apk.yml`) runs on every push to every branch: unit tests, `lintFossRelease`, `assembleFossRelease`, then publishes `pro.apk` (main) or `pro-preview.apk` (other branches) as a GitHub Release. It signs with a key it keeps in the Actions cache, never in the repository.

## The app, by folder

All code is under `app/src/main/java/me/kavishdevar/librepods/` (the old LibrePods package name stays on purpose).

| Folder | What it holds | Read |
|---|---|---|
| `services/` | The brains: AirPods service, the Dynamic Island's rules, sound and music listening, heart rate, battery, gestures | [`services/README.md`](app/src/main/java/me/kavishdevar/librepods/services/README.md) |
| `presentation/overlays/` | Everything drawn over other apps: the Dynamic Island, the pop-up islands, the case card | [`presentation/overlays/README.md`](app/src/main/java/me/kavishdevar/librepods/presentation/overlays/README.md) |
| `presentation/glint/` | The glass material, motion, haptics, symbols, row icons | [`presentation/glint/README.md`](app/src/main/java/me/kavishdevar/librepods/presentation/glint/README.md) |
| `presentation/screens/` | One file per page of the app | [`presentation/screens/README.md`](app/src/main/java/me/kavishdevar/librepods/presentation/screens/README.md) |
| `presentation/components/` | Shared pieces: lists, toggles, buttons, banners, cards | |
| `presentation/navigation/` | Routes (`Screen.kt`), the page switcher (`AppNavGraph.kt`), the top bar and the tabs' state (`NavigationRoot.kt`), the three tabs and their Liquid Glass bar (`Tabs.kt`: Phone first, Island, Headphones), deep links from pop-ups (`AppLinks.kt`, `ISLAND_TAB`) | |
| `presentation/theme/` | Colours, the Inter font, light/dark (`Appearance.kt`), the light/dark reveal, the home-screen icon choice | |
| `bluetooth/` | Talking to the AirPods: the Apple accessory protocol (AACP), detection, reconnect timing, the heart sensor stream, the Beats beacon | |
| `audio/` | The AirPods microphone recorder | |
| `data/` | Protocol data classes and settings storage | |
| `utils/` | Media-key control, Conversation Awareness timing, the companion-device link, "will this phone connect" wording | |
| `billing/` | Stub: the FOSS build reports everything as included (there is no paywall) | |
| `receivers/` | Boot and app-update receiver that starts the service | |
| `src/foss/` | Pieces only in the version Jake installs: the accessibility service entry in the manifest and its config | |
| `src/test/` | Tests and screenshot renderers | [`src/test/.../README.md`](app/src/test/java/me/kavishdevar/librepods/README.md) |

## How the Dynamic Island works

```
inputs                                     rules (pure, tested)              drawing
------                                     --------------------              -------
NowPlaying      music: playing/paused,  -> MiniIslandRules.wanted/content -> MiniIslandController (overlays/MiniIsland.kt)
                cover, title, controls     MiniIslandRules.showAsSound       decides, owns the window
SoundSource     any other sound, and its   SoundRules (kinds, which app,      |
                app (see below)            linger, recent list)               v
GlintStatus /   AirPods or headphones   -> IslandLook (what goes left/right  MiniIslandHost (composable)
HeadphoneLink   connected, battery, mode   per situation, size, colour)       draws the pill; slots drawn by drawSlot
PhoneStatus     the phone's own battery                                       |
ScreenApp       the app in front, home,    GlanceRules (what's worth a glance)
                locked; wallpaper colours
IslandTimer     the island's own timer
HeartRate/View  heart rate (honest state)                                     v
Conversation... talking (music is down)                                       OverlayWindow (layer: above the status bar
                                                                              when the accessibility service is on)
```

- **Content** (what the pill is about): Music, Sound, Screen (where you are on the phone), AirPods, Rest. **Situation** (which left/right slots apply): In an app, Home screen, Lock screen, Playing, Paused, Sounds, AirPods, Charging, Talking, Nothing on. The look maps each situation to two slots (App on screen, Home, Date, Padlock, Smart glance, Cover, App icon, Sound bars, Battery, L/R/case, Mode, Heart, Phone battery, Clock, Title, Talking dots, Keep, Nothing).
- **A tap** opens the music island on music, the AirPods island on the AirPods, the app on a sound, and otherwise the glance (`GlancePanel.kt`): what's live and the phone's own controls.
- Taps, swipes and holds are one system (`services/IslandGestures.kt`). They only reach the pill when the accessibility service "pro Dynamic Island" is on, because Android's status bar otherwise takes every touch in the camera strip.
- The pop-up islands (`Island.kt`) grow out of the pill and shrink back into it. The pill's window always starts above the pop-up's so both stay touchable.

## The sound pipeline (any sound, with the app's icon)

1. `SoundSource` registers an Android playback callback once, in the background service's `onCreate` (`AirPodsService`), so it runs with no headphones connected. Android hands apps only the sounds that are playing right now, each with only its type (verified in Android's source and the SDK; there is no app or uid in the public API).
2. `SoundRules.summarize` turns the types into one kind (Call, Alarm, Alert, Voice, Media, Game, Other). Interface clicks and screen-reader speech are ignored.
3. The app is guessed from three clues in `SoundRules.attribute`: the media session (`NowPlaying.playingSessionPackage`, needs Notification access), an app whose notification was posted in the last 3 s (`MediaAccessService.onNotificationPosted`, same access; only the app and time are kept), and the app on screen (`IslandAccessService`, the accessibility switch). Musical sounds trust the session first; alerts trust the notification first.
4. `SoundSource.heard` publishes the sound (active, or ended with an end time). `MiniIslandController.refresh` shows it while it plays and for the "Short sounds stay for" time after, so even a half-second ding is seen. While music plays, an alert only pops its icon into the right-hand slot for a moment.
5. Every app, name and icon lookup needs the `<queries>` entries in `AndroidManifest.xml` (Android hides other apps otherwise).

Tests: `SoundRulesTest` (pure rules), `SoundSourceTest` (events in, sound out, via `SoundSource.usagesChanged` and the `roleOverride` seam), `MiniIslandTest` and `IslandLookTest` (when the pill shows, defaults, storage).

## Gotchas learned the hard way

- **The main thread must never wait.** Android says "pro isn't responding" after about 5 seconds stuck. Bluetooth writes (`OffMain`), ATT reads (2 s each), root shells, `runBlocking` and audio-system setup (`Visualizer`) all belong off it. Six such causes were found and fixed on 2026-10-06 (`DECISIONS.md` 38). If a freeze is reported, ask for Troubleshooting > Freezes and crashes > Copy details.

- **`delay()` with a computed wait.** `delay(x)` with `x <= 0` returns at once. A recheck that reschedules itself with "time left" must only do so when time is left (`MiniIslandRules.nextCheck` exists for this; the old code looped forever after 30 s of paused music).
- **Don't `remember` theme colours.** Rows went white on white. Read them each composition.
- **A page's content in `AppNavGraph` is kept from when it was first made.** Pass anything that changes (like the selected tab) as a function or state that the content reads, never as a plain value, or the page stays stuck while everything around it changes. Test it by pressing the real buttons (`PhoneTabsUiTest`), not only with screenshots that start on the right page.
- **Glass rim light:** always `GlintLight.rim()`. Kyant's default highlight is a 45 degree diagonal that looks tilted. Level phone means straight overhead.
- **Overlay windows.** Android draws the status bar above app overlays and gives it every touch in its strip. Accessibility-layer windows sit above it. Hidden overlay windows must be fully transparent and untouchable or they eat taps (`OverlayWindow`).
- **Status bar height** is the largest of the window insets, the `status_bar_height` resource and the cutout bottom: insets can be 0 from a service.
- **A pop-up's window must not cover the pill.** It starts just below it.
- **Package visibility.** Without `<queries>`, `getApplicationInfo`, `getLaunchIntentForPackage` and `queryIntentActivities` quietly find nothing for other apps.
- **AirPods ear packets are primary and secondary, not left and right.**
- **The pill is on screen for hours.** Nothing in it may animate or run sensors when it doesn't have to (glass tilt runs only while touched and 3.5 s after; the clock updates once a minute).
- **Robolectric** needs its Android image jar offline (`cloud-setup.sh` fetches it). If a test fails with `MavenArtifactFetcher`, the error names the jar.
- **Never commit signing keys.** This repository is public (the heart backup refuses a public repository, so pro3 must be made private by Jake).

## Names (Jake's)

The camera pill is the **Dynamic Island** (code: `MiniIsland*`). The pop-ups are the **mini island** (code: `Island*`, `IslandController`). Hidden Lab: Settings > About > tap "Version code" 7 times. Jake's app shows no visible LibrePods mentions.

---

# LibrePods original Android notes

Kept as LibrePods wrote them, for reference. They describe LibrePods (the project pro was forked from), not pro's own setup.

## Root Requirement

LibrePods *may* require root depending on your device/OS and what features you want access to:

- Features requiring the VendorID hook ([the features marked with an asterisk here](https://github.com/kavishdevar/librepods#key-features)) will always require root regardless of your device/OS.
- On **ColorOS/OxygenOS 16 and realme UI 7.0** and **Pixel devices on Android 16 QPR3** (with the latest Google Play system update), LibrePods does not need root for most features.
- On other devices, LibrePods needs root because of a bug in the Android Bluetooth stack Fluoride/non-compliance of Apple with Bluetooth standards. You must have Xposed installed for the app to workaround this bug and connect to AirPods. [This issue is being tracked here](https://issuetracker.google.com/issues/371713238). **Please do not comment on the issue thread.** The issue has already been resolved and should be available in **Android 17** for all devices.

> [!IMPORTANT]
> This workaround with Xposed is not guaranteed to work on all devices.


## Installation

### Google Play Store

If you are using a supported device/OS combination, you can install LibrePods from the Google Play Store. You can use the VendorID hook features with root even from the Play Store version.

<a href="https://play.google.com/store/apps/details?id=me.kavishdevar.librepods"><img width="170" alt="GetItOnGooglePlay_Badge_Web_color_English" src="https://github.com/user-attachments/assets/2948308f-af92-443f-94d9-ee381c3a6ccc"/></a>

### GitHub Releases

If you need xposed because of the [root requirement](#root-requirement), you will have to use the apk/zip from the [GitHub releases](https://github.com/kavishdevar/librepods/releases/latest).

### As a system app (root module)

If you want LibrePods to have privileged Bluetooth permissions to 
- show battery status in the system settings and widgets
- show AirPods icon in the system settings (xposed is also currently required for this)
- switch audio to phone speakers when you are not wearing your AirPods

you can install the root module. This is optional and only provides extra features, but it is not required for the app to work.

> [!IMPORTANT]
> When using the root module, do not install the Play Store version. There might be issues because of the signature mismatch between the Play Store version and the root module.

## Nightly/Development Builds

Want to try the latest features before they're officially released? You can grab nightly builds from the [latest nightly release](https://github.com/kavishdevar/librepods/releases?q=nightly).

> [!WARNING]
> These builds are automatically generated from the latest code and may contain new features and bug fixes that haven't been included in a stable release yet. However, please note that they may also be less stable than official releases, so use them at your own risk.

## Screenshots

|                                                                                 |                                            |                                                                      |
| ------------------------------------------------------------------------------- | ------------------------------------------ | -------------------------------------------------------------------- |
| ![Settings 1](./imgs/settings-1.png)                                            | ![Settings 2](./imgs/settings-2.png)       | ![Head Tracking and Gestures](./imgs/head-tracking-and-gestures.png) |
| ![Long Press Configuration](./imgs/long-press.png)                              | ![Customizations 1](./imgs/customizations-1.png)                                | ![accessibility](./imgs/accessibility.png) |
| ![transparency](./imgs/transparency.png)                                        | ![hearing-aid](./imgs/hearing-aid.png)     | ![hearing-test](./imgs/hearing-test.png)   |
| ![hearing-aid-adjustments](./imgs/hearing-aid-adjustments.png)                  | ![Battery Notification and QS Tile for NC Mode](./imgs/notification-and-qs.png) | ![Widget](./imgs/widget.png)               |


here's a very unprofessional demo video

https://github.com/user-attachments/assets/43911243-0576-4093-8c55-89c1db5ea533

### Troubleshooting steps for common errors
- Ensure the correct scope is set in LSPosed/Vector.
- Ensure there is no root-hiding module preventing the hook from loading on the Bluetooth app.
- Restart your phone after confirming the scope.

### A few notes

- Due to recent AirPods' firmware upgrades, you must enable `Off listening mode` to switch to `Off`. This is because in this mode, loud sounds are not reduced.

- When renaming your AirPods through the app, you'll need to re-pair them with your phone for the name change to take effect. This is a limitation of how Bluetooth device naming works on Android.
