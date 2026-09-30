# Handoff: finish Jake's latest Glint request

This is for the next Claude session on `Jbyjre/pro3`. Read `CLAUDE.md`, `DECISIONS.md` and `TESTING.md` first.
This file covers what is done, what is only half-checked, and exactly what is left.
Delete this file in the final commit once the work is finished.

## 1. Who Jake is and the rules (do not break these)

- **Never auto-subscribe to PR or merge updates.**
  - No `subscribe_pr_activity`, no auto-merge, no `send_later` check-ins or polling of CI.
  - Exception: Jake explicitly asks for it in that session.
  - If a tool subscribes you automatically (creating a PR can), unsubscribe right away.
- **Jake has no coding experience.**
  - Explain everything in plain language and define technical words.
  - Never give him terminal commands; you do the build, repo and release work.
- **Verify before you state anything.** Say "not verified" for anything you did not check.
- **Ask before spending money or doing anything hard to undo.**
- **No subagents.** Jake said "don't use agents"; do the work yourself.
- **Signing keys:** never commit them, and never use upstream's.
- Jake chose LibrePods' own AirPods pictures and 3D clips. Do not replace them with drawn artwork.
- **Tone:** be supportive and never condescending.

## 2. Repo, branch, PR

- Repo: `Jbyjre/pro3`. The Android app is in `android/`.
- Work branch: `claude/pensive-franklin-lvce7c`. Open PR: https://github.com/Jbyjre/pro3/pull/1 (into `main`).
- CI: `.github/workflows/glint-apk.yml` runs on every push.
  - It publishes `Glint-preview.apk` from branches and `Glint.apk` from main.
  - Jake installs from the Releases page.
  - Check CI **once** after pushing. Do not watch it.

## 3. Jake's latest request (the one to finish)

In his words, summarised:

1. **Remove the Kavishdevar/LibrePods remnants.** It should feel like *his own* app.
2. **No "Agree and continue" agreement step** in setup.
3. **No features locked behind sponsoring/paying.** Every feature is available and works smoothly.
4. **Overlays** (the pop-up card and the island) look good but should look better. He does not like that they feel "modular", i.e. like separate boxes stuck together.
5. **Liquid Glass throughout the app, especially setup.**
6. **Every button and feature works smoothly.** Be accurate and thorough, use the Liquid Glass skill, and be context-efficient.

## 3b. Jake's NEWEST request (added after the pause; do this too)

Jake's words, summarised:

- The **top orb (island) "looks incredible"**. Keep it.
- The **bottom card** ("your AirPods are connected") "appears kind of randomly" and isn't in sync. He is fine **without it**.
- Instead:
  - Let him **view the 3D AirPods inside the app**.
  - Let him **switch between the 3D views**.
  - Let him **drag to spin** and look around the AirPods.
- **Estimate time left on battery**, and be very accurate about it.

What the previous session found (read in the code, not tested on a phone):

### A. Bottom card: turn it off and stop the random pop-ups

- **Trigger:** `AirPodsService.showPopup()` (~line 1636) is called from `onLidStateChanged(true)` (~line 303), which the BLE advert reader (`bluetooth/BLEManager.kt`) fires.
- **Likely cause of "random"** (not verified on the device):
  1. `BLEManager.checkLidStateTimeout()` forces the lid to "closed" whenever no advert arrives for `LID_CLOSE_TIMEOUT_MS = 2500` ms.
  2. Samsung throttles and batches BLE scans, especially with the screen off, so gaps longer than 2.5 s are normal.
  3. After a gap, the lid goes "closed", which resets `popupShown = false` (~line 331). The next advert says "open" again, so the card pops up again.
  4. The lid bit in adverts also isn't meaningful while the buds are out of the case.
- **Do:**
  - Make the card **off by default**: `show_bottom_sheet_popup` default `false` in `AirPodsService.showPopup`, `AppSettingsViewModel` (~line 153) and `GlintLabScreen`.
  - Keep the Settings toggle, but rename it in plain words, e.g. "Pop-up card when case opens (experimental)".
  - Also make it sane for anyone who turns it back on:
    - Only show it when the lid goes closed → open **and** at least one bud is in the case.
    - Debounce: ignore "closed" unless it stays closed for 10+ s. Don't reset `popupShown` on a mere advert gap.
    - At most one card per 60 s.
  - Keep the island (top orb) exactly as it looks now. Don't restyle it.
  - Check that "Connected" still shows the island once per real connection, not on every advert.

### B. In-app 3D viewer you can spin and switch

- **No real 3D model exists in the repo.** The only "3D" are two LibrePods video clips. Jake chose these (see `CLAUDE.md`), so keep using them:
  - `res/raw/island.mp4`: 418×418, 25 fps, 6 s, **black** background. The buds turn through several angles.
  - `res/raw/connected.mp4` (and `res/raw-night/connected.mp4`): 1050×354, 30 fps, 6 s. The buds and case turn through front/side/back views; white background, dark in the night version.
  - Contact sheets checked: both are rotation clips, but **not a clean continuous 360°**. They jump between poses. Check frame by frame.
- **Recommended approach (no new library, works offline): "scrub to spin".**
  1. Pre-extract frames from each clip into a small drawable sequence (e.g. 48–72 `.webp` frames per view, ~300–400 px) in `res/drawable-nodpi/`. The session had `imageio-ffmpeg` via pip for this. Keep the total small (< 3 MB).
     - Alternative: decode at runtime with `MediaMetadataRetriever.getFrameAtIndex` (API 28+), downscaled, on a background thread, cached. Watch memory.
  2. New composable `presentation/glint/PodsSpinner.kt`:
     - Horizontal drag maps to the frame index, wrapping around.
     - A fling keeps it spinning with decay (`Animatable` + `splineBasedDecay`), easing to a stop.
     - A slow idle auto-rotate, off when reduce motion is on.
     - Light haptic ticks while dragging.
     - Content description "AirPods 3D view, drag to rotate".
  3. **Switch views:** a Liquid Glass segmented control (Kyant `drawBackdrop`, same style as `GlassSheet` in `OnboardingScreen.kt`) with "Earbuds", "Earbuds + case" and "Case", backed by the matching frame ranges of the two clips.
  4. Use the island clip's black-key shader (`PodsVideo` `keyBlack`) or pre-key the frames so the buds float on the glass without a black box. Use the night clip in dark mode.
  5. **Placement:**
     - On the AirPods screen (`AirPodsSettingsScreen.kt` ~line 315, the "battery" item), put the spinner above the battery, on a glass stage.
     - Make it tappable to open a full-screen viewer with the switcher.
  6. Optional, **ask Jake first:** a true 3D model (glTF + SceneView/Filament, Apache-2.0) would allow free rotation in every direction. It needs a new dependency, which must be added to the `ci-deps-snapshot` so offline builds keep working. It also needs a 3D AirPods model whose licence allows redistribution in a public GPL repo, which is **not found or verified**. It would also replace the LibrePods clips he chose.

### C. Battery time-left estimate ("be very accurate")

- **Data available:**
  - **AACP battery packets** (`data/Packets.kt` `BatteryNotification.setBattery`) give **1% steps** for left, right and case, with a charging/discharging status.
  - **BLE adverts** only give **10% steps**. Never base a rate on BLE levels.
- **Apple's official AirPods Pro 3 figures** (from apple.com/airpods-pro/specs, fetched 2026-09-30):
  - Up to **8 h** listening with ANC on one charge; **7.5 h** with Spatial Audio + head tracking.
  - **6.5 h** with heart-rate sensing in workouts.
  - Up to **10 h** in Transparency using the Hearing Aid feature.
  - Up to **24 h** total with the case (ANC).
  - 5 min in the case gives about 1 h.
- **Estimator design** (new file `services/BatteryEstimator.kt`, pure Kotlin, unit-tested):
  1. Record timestamped samples `(time, left%, right%, inEar L/R, listening mode, playing audio?)` only while discharging over AACP. Keep ~2 h in memory and persist a compact rolling history in prefs.
  2. Take the **lower** of the two buds (the one that dies first).
  3. Get the rate from a linear regression over the last 20–30 min of samples. Require at least 3 distinct 1% drops before trusting it. Reset on charging, removal, or a mode change larger than a threshold.
  4. Until there is enough data, use a **prior**: level% × rated hours for the current mode (ANC 8 h, Adaptive and Transparency ~8 h, which is **not verified**, so treat it as ANC, Off ~8 h). Blend smoothly: `weight = min(1, observedMinutes/30)`.
  5. **Remember each user's real rate:** keep a per-mode average of past sessions in prefs. After a few days it beats Apple's numbers.
  6. **Case:**
     - Estimate case charges from case% (the case holds roughly 2 extra full charges, since 24 h ÷ 8 h = 3 × 8 h total).
     - Show "Case: about N more full charges" as a rough figure, marked approximate.
  7. **Charging estimate:** show "Full in ~X min" from the observed charge rate. Early on, fall back to Apple's "5 min ≈ 1 h".
  8. **Display:**
     - Round honestly: "about 3 h 20 m left" (nearest 10 min above 1 h, 5 min below).
     - Show "Estimating…" until the prior is used, and never a false precision.
     - Show it under the battery on the AirPods screen, in the island's battery state (compact text), and in the notification if space allows.
     - When BLE is the only source (no AACP), show "Connect to see time left" instead of guessing.
  9. **Tests:** synthetic sample series cover a steady drain, the mode switch, charging reset, unequal buds, no data, and BLE-only.

## 4. What is done

### Earlier commits (already pushed; CI was green on `c442cbe`)

- `f617554`: restored the LibrePods pictures and 3D clips (PodsVideo).
- `22f8a43`: GlintSymbols.
- `8afdd08`: unlock reliability and "Grant all".
- `c442cbe`: icon and docs.
- `3cdc4a0`: audit fixes (light-mode black page, purple fallback colours, leaks, service crashes, AppTourScreenshots).
- `71ef502`: battery parser for a variable number of parts, `AirPodsProtocolTest`, and service `stopSelf()` when `startForeground` fails.

### This request (committed together with this file as a work-in-progress commit)

All paths are under `android/app/src/`.

**All features included**
- `main/.../billing/FOSSBillingProvider.kt`:
  - `isPremium` is always `true`.
  - purchase/restore do nothing.
  - No sponsor page or timer is left.
- `main/.../presentation/viewmodel/AirPodsViewModel.kt` and `AppSettingsViewModel.kt`:
  - `isPremium` defaults to `!BuildConfig.PLAY_BUILD`.
  - As a result every `enabled = state.isPremium` gate and every "unlock" row unlocks or hides in the `foss` build.

**Branding removed from Settings** (`main/.../presentation/screens/AppSettingsScreen.kt`)
- Removed:
  - the "LibrePods (original project)" row
  - the unlock button
  - the Play billing banner (the one that emailed billing@kavish.xyz)
  - the premium gate on the Material 3 Expressive toggle
- "Version" no longer opens LibrePods' release notes. Tapping "Version code" 7 times still opens Glint Lab.
- "Report a Glint problem" is kept. It opens `github.com/Jbyjre/pro3/issues/new`.

**Agreement step removed**
- `PrivacyPolicyPage.kt` is deleted.

**Setup rebuilt as Liquid Glass** (`main/.../presentation/screens/onboarding/OnboardingScreen.kt`, full rewrite)
- Steps: Welcome, This phone, Permissions, Stay connected.
- `OnboardingScreen(startStep, onOnboardingComplete)`. Android's Back gesture goes back one step.
- `StudioBackdrop`:
  - a studio gradient with a key light and a faint green glow
  - LibrePods' `airpods_pro_2_buds` and `airpods_pro_2_case` pictures
  - drawn into a Kyant `layerBackdrop`
- `GlassSheet`: a 40dp-corner sheet built with `drawBackdrop`.
  - Effects: vibrancy, 22dp blur, and a lens that gives real refraction and chromatic edge.
  - Also an ambient highlight and a two-layer shadow (`glassShadow`).
  - When reduce transparency is on, it falls back to solid.
- Sheet height: 300dp on Welcome, otherwise 79% of the screen (at most 760dp).
- Step dots at the top.
- Slide+fade between steps. Only a fade when reduce motion is on.
- `NotSupportedPage.kt`, `PermissionsPage.kt` and `StayConnectedPage.kt` lost their own grey rounded background so they sit on the glass.
- NotSupportedPage also dropped `DeviceInfoCard` and `AppInfoCard`.

**Island less "modular"** (`main/.../presentation/overlays/Island.kt`)
- The battery ring now rests inside the pill's right end (new `restSplit`, `compactMainW = 252dp`). It reads as one capsule instead of a pill plus a separate bubble.
- Satellite fade and scale timings were adjusted to match.

**Card less "modular"** (`main/.../presentation/overlays/Card.kt`)
- The inner rounded "stage" box is gone.
- The 3D clip now feathers straight into the glass (`featherEdges`, 8% by 10%).
- `Glass.kt` `GlassLooks.card` colours were retuned to match the clips' white / `#1B1B1B` backgrounds.

**Tests**
- `test/.../FossUnlockTest.kt` was rewritten: "everything is included from the start" and "screen state starts unlocked".
- `test/.../AppTourScreenshots.kt` gained setup-step shots, light and dark: `23_setup_welcome`, `24_setup_this_phone`, `25_setup_permissions`, `26_setup_stay_connected`.
  - The old onboarding and privacy shots were removed.

## 5. What is NOT verified yet (start here)

The working tree compiled. The setup-step screenshot tests and `GlintScreenshots` passed. **Nothing else has been run since these edits:**

- [ ] Full `testFossDebugUnitTest`: the rewritten `FossUnlockTest` has never run, and neither has the whole `AppTourScreenshots`.
- [ ] `lintFossRelease`. Check for unused imports:
  - `Card.kt`: background, border, RoundedCornerShape, clip
  - `NotSupportedPage.kt`: DeviceInfoCard, AppInfoCard
  - `AppTourScreenshots.kt`: the unused `page()` helper
- [ ] `assembleFossRelease`.
- [ ] Look at **every** image in `android/app/build/screenshots/tour/` (light and dark). Home and settings now render with everything unlocked, so there should be no "Locked" labels and no unlock rows.
- [ ] CI on the pushed commit (check once).

## 6. Remaining work, in order

1. **Verify** (section 5). Fix anything red.
1b. **Do section 3b** (card off plus debounce, 3D spinner with switcher, battery estimate). Include tests and tour screenshots of the new viewer and the time-left text.

2. **Finish the branding sweep.** Several leftovers can't be reached in the foss build today, but remove or neutralise them so nothing shows up by accident:
   - `strings.xml`:
     - `support_development_description` mentions kavishdevar.
     - `play_foss_premium_banner` mentions billing@kavish.xyz. It is Play-only, but still delete or rewrite it.
   - The dead `contactBottomSheet` in `AppSettingsScreen.kt` still has `contact@kavish.xyz`.
   - `AirPodsSettingsScreen`: the Play banner (billing@kavish.xyz). In foss, `timeUntilFOSSPremiumExpiry` is 0, so it never shows.
   - `PurchaseScreen` and `ReleaseNotesScreen` are still in the nav graph but have no entry point. Remove the routes, or at least make sure nothing links to them.
   - Grep the whole `res/` and `presentation/` tree for `kavish`, `LibrePods`, `librepods.org`, `sponsor` and `Sponsor` in **user-visible** text.
   - **Keep:**
     - GPL licence headers in source files. They are required.
     - `THIRD_PARTY_NOTICES.md`.
     - The internal package name `me.kavishdevar.librepods`. It is invisible to Jake, and renaming it is a huge, risky change; do not do it unless he asks.
   - Credit: you may keep a single quiet line such as "Based on LibrePods (GPLv3)" in *About*, or leave it out.
     - Tell Jake plainly that the licence requires the source code and licence to stay available.
     - Whether an in-app credit is legally required is **not verified, and not legal advice**.

3. **Liquid Glass on the rest of the app.** Setup is done; the main screens still use upstream's `StyledScaffold` (Haze) and flat list cards.
   - Following the skill, glass belongs on the controls layer: top bars, bottom/floating buttons, sheets and dialogs. Keep plain, legible list rows for content.
   - Use Kyant `drawBackdrop` the same way `GlassSheet` does, so the look is consistent.
   - Handle all three accessibility settings:
     - **reduce transparency:** solid fill
     - **reduce motion:** no morphing or scaling
     - **increased contrast:** a visible 1dp border. This one is not implemented anywhere yet.

4. **Overlay polish.** Dark-mode card: a faint soft patch shows where the clip meets the glass's top sheen. Tone down the sheen behind the video, or feather more. Re-shoot `GlintScreenshots` and compare.

5. **Buttons:** walk through every button in the tour screenshots and in the code (onClick handlers that do nothing, or dead links). Make sure Stay connected's "Link", "Allow" and "Open settings" still work from the glass sheet.

6. **Docs are stale.** Update them:
   - `DECISIONS.md`:
     - Line ~18–19 and ~86: still describe the sponsor unlock.
     - Line ~109: still describes the privacy page.
     - Add a new section: "Your own app: branding removed, everything included, glass setup, one-piece overlays".
   - `TESTING.md`:
     - Line 16: "Welcome and Privacy pages".
     - Line 35: the Unlock row. Delete it.
     - Line 39: "needs the unlock".
     - Line 65: "Some features need the unlock". Delete it.
   - `CLAUDE.md`: mention that the foss build has no paywall.
   - PR #1 description: add a plain-language summary of this round.

7. **Commit and push** to `claude/pensive-franklin-lvce7c`:
   - End commit messages with the attribution lines the system gives you.
   - Check CI once.
   - **Do not subscribe.**
   - Delete this `HANDOFF.md` in that final commit.

8. **Report to Jake in plain language:**
   - what changed
   - what was verified, and how
   - what is not verified (anything needing his real phone and AirPods)
   - how to install: Releases > `Glint-preview.apk`, or `Glint.apk` after he merges PR #1 with the green "Merge" button

## 7. Open questions waiting on Jake (don't decide for him)

- **BLE advert fallback.** Glint only trusts Bluetooth adverts after it gets the AirPods' identity key (IRK) over a successful L2CAP connection. The proposal: use nearby unverified adverts for battery until then. That is a trade-off (someone else's AirPods nearby could be shown), so ask Jake before building it.
- **AirPods Pro 3 Bluetooth product ID.** Sources conflict: appledb lists "Device1,8231/8232", and 8239 is only a rumour. It is not added, and it is only used for log names. Leave it until it is verified.
- Whether AAP over L2CAP works on One UI 9 is **not verified** (it needs his real phone).

## 8. Building in a cloud session

Full details are in `CLAUDE.md`. Short version:

1. Download the `gradle-deps.tgz` asset from the `ci-deps-snapshot` release and extract it into a `GRADLE_USER_HOME`.
2. Take the Android SDK from the `cimg/android` Docker image layers.
3. Run gradle with these flags:

   ```
   ./gradlew --offline -PndkVersion=29.0.14206865 -PcmakeVersion=4.1.2 <tasks>
   ```

4. For Robolectric, point `ROBOLECTRIC_DEPS_DIR` at a folder containing the android-all jar (Maven Central rate-limits it).

The previous session used `ANDROID_HOME=/opt/android-sdk-real`, `GRADLE_USER_HOME=/opt/gh` and `ROBOLECTRIC_DEPS_DIR=/opt/robolectric`. **A fresh container will not have these**, so rebuild them as above.

Tasks:
- `testFossDebugUnitTest` (all tests)
- `testFossDebugUnitTest --tests '*AppTourScreenshots*'` writes to `build/screenshots/tour`
- `lintFossRelease`
- `assembleFossRelease`

Robolectric dark qualifier order matters: `w412dp-h915dp-night-xxhdpi`, not `...-xxhdpi-night`, which crashes.

Robolectric does **not** draw the Kyant blur or lens. Screenshots show only the tint and shape. The real glass appears on the phone (API 33+).

## 9. Doing it better than last time

- Run the full test+lint+build **before** declaring anything done. Last session ran out of room before that.
- Commit in small, verified steps and push often. The container is temporary, and unpushed work is lost.
- Look at the screenshots yourself before telling Jake something "looks good".
- Keep messages to Jake short, plain and honest about what still needs his phone to confirm.
