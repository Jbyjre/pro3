# Prompt for the next Claude session

1. You are taking over work on **Glint**, Jake's personal Android app for using AirPods Pro 3 on a Samsung Galaxy S25 FE.
   - It is a fork of the open-source LibrePods app, licensed GPLv3.
   - Repo: `Jbyjre/pro3`. The Android project is in `android/`.
   - Work branch: `claude/pensive-franklin-lvce7c`. Open pull request: https://github.com/Jbyjre/pro3/pull/1 (into `main`).
   - Before doing anything, read `CLAUDE.md`, `HANDOFF.md`, `DECISIONS.md` and `TESTING.md` at the repo root. `HANDOFF.md` has the file-by-file detail that this prompt summarises.

2. Jake's standing rules. Follow them exactly:
   - **Never auto-subscribe to PR or merge updates.** No `subscribe_pr_activity`, no auto-merge, no scheduled check-ins or CI polling, unless Jake explicitly asks in your session. If creating or editing a PR subscribes you automatically, unsubscribe immediately.
   - **Jake has no coding experience.** Explain everything in plain, friendly language and define technical words. Never give him terminal commands; you do all the repo, build and release work yourself.
   - **Verify facts before stating them**, and label anything unchecked as "not verified".
   - **Ask before spending money or doing anything hard to undo.**
   - **Don't use subagents.**
   - **Never commit signing keys**, and never use upstream's keys.
   - **Be supportive and never condescending.**

3. Current state of the code:
   - Earlier commits (`f617554` through `71ef502`) are pushed, and CI was green on them. They restored LibrePods' AirPods pictures and 3D video clips, redrew the icons, fixed crashes and leaks, and fixed the battery parser.
   - The latest round is pushed as a work-in-progress commit (`d0edea5`) plus handoff commits.
   - That round has **not been fully verified**. It compiles, and the setup screenshot tests pass. The full unit tests, lint and release build have **not** been run since those edits. Your first job is to run them and fix anything that fails.

4. **Request 1: make it Jake's own app.**
   - Already done: in Settings, the "LibrePods (original project)" row, the unlock button, the billing banner and the LibrePods release-notes link are removed.
   - Still to finish:
     - Remove or rewrite leftover user-visible text: `support_development_description` and `play_foss_premium_banner` in `strings.xml`, and the dead contact sheet with `contact@kavish.xyz` in `AppSettingsScreen.kt`.
     - Remove the unreachable `PurchaseScreen` and `ReleaseNotesScreen` routes.
     - Search all user-visible text for "kavish", "LibrePods" and "sponsor".
   - Keep:
     - the GPL headers in source files
     - `THIRD_PARTY_NOTICES.md`
     - the invisible package name `me.kavishdevar.librepods` (renaming it is huge and risky)
   - Tell Jake plainly that the GPL requires the source and licence to stay available. Whether an in-app credit is required is not verified, and not legal advice.

5. **Request 2: no agreement step and no paywall.**
   - Done:
     - The "Agree and continue" privacy page is deleted.
     - `FOSSBillingProvider` always reports premium.
     - Both view models default `isPremium` to `!BuildConfig.PLAY_BUILD`, so every feature is unlocked in the foss build.
   - To do:
     - Verify that no screen still shows "Locked", an unlock row or a greyed-out control.
     - Run the rewritten `FossUnlockTest`.
     - Look at every tour screenshot to confirm.

6. **Request 3: Liquid Glass throughout, especially setup.**
   - Done: setup is rebuilt in `OnboardingScreen.kt` as four steps (Welcome, This phone, Permissions, Stay connected). A real glass sheet (Kyant `drawBackdrop` with blur, lens refraction, highlight and shadow) floats over a studio backdrop with LibrePods' AirPods pictures.
   - Still to do:
     - Bring the same glass language to the main app's controls layer: top bars, floating buttons, sheets and dialogs. Keep list content plain and readable.
     - Handle reduce transparency (solid fill), reduce motion (no morphing) and increased contrast (visible 1dp border; not implemented anywhere yet).

7. **Request 4: overlays should feel less "modular".**
   - Done:
     - The island's battery ring now rests inside the pill, so the island is one capsule.
     - The bottom card lost its inner box, and its video feathers into the glass.
   - Jake has since said the top orb (the island) "looks incredible". Do not restyle it.
   - One known flaw: in dark mode, a faint soft patch shows on the card where the video meets the glass sheen. This matters less now, because of Request 6.

8. **Request 5: every button and feature works smoothly.**
   - Walk through every screen in code and in the screenshots.
   - Find buttons whose click handlers do nothing, links to dead or LibrePods pages, and anything that crashes without a live AirPods connection.
   - Confirm that Stay connected's "Link", "Allow" and "Open settings" buttons still work from the glass sheet.
   - Confirm that "Grant all" permissions and the Back gesture in setup behave correctly.

9. **Request 6 (newest): remove the random bottom card.**
   - Jake says the card at the bottom of the screen ("your AirPods are connected") appears randomly and out of sync, and he is happy without it.
   - Make it **off by default**: the `show_bottom_sheet_popup` default becomes `false` in `AirPodsService.showPopup`, `AppSettingsViewModel` and anywhere else it is read.
   - Keep a Settings toggle with a plain name, such as "Pop-up card when case opens (experimental)".

10. **Why the card is random** (read in the code, not tested on a phone):
    1. `BLEManager.checkLidStateTimeout()` forces the lid to "closed" after only 2.5 seconds without a Bluetooth advert.
    2. Samsung throttles background Bluetooth scanning, so gaps longer than 2.5 seconds are normal.
    3. After a gap, `popupShown` resets, and the next advert re-triggers the card.
    4. The lid bit is also meaningless while the buds are out of the case.

    For anyone who turns the card back on:
    - Require a real closed-then-open change with at least one bud in the case.
    - Treat "closed" as real only after 10+ seconds.
    - Don't reset `popupShown` on an advert gap.
    - Allow at most one card per minute.
    - Make sure the island still shows once per real connection.

11. **Request 7 (newest): a 3D AirPods viewer inside the app that Jake can spin and switch.**
    - There is **no real 3D model in the repo**. The only 3D is LibrePods' two rendered video clips. Jake specifically chose those over drawn art, so build on them.
    - `res/raw/island.mp4`: 418×418, 25 fps, 6 seconds, on black. The earbuds turn through several angles.
    - `res/raw/connected.mp4`: 1050×354, 30 fps, 6 seconds, with a dark version in `res/raw-night/`. The earbuds and case turn through front, side and back views.
    - Neither clip is a perfectly continuous 360° turn, so check them frame by frame. `imageio-ffmpeg` from pip works for extracting frames.

12. **How to build the spinner.**
    1. Pre-extract 48–72 frames per view into small `.webp` files in `res/drawable-nodpi/`. Keep the total under about 3 MB, and pre-key the black background to transparent for the island clip.
    2. Create `presentation/glint/PodsSpinner.kt`:
       - Horizontal drag moves through the frames and wraps around.
       - A fling keeps it spinning and eases to a stop (`Animatable` with decay).
       - Light haptic ticks while dragging.
       - A gentle idle auto-rotate, off when reduce motion is on.
       - Content description "AirPods 3D view, drag to rotate".
    3. Add a Liquid Glass segmented switch, "Earbuds", "Earbuds + case" and "Case", styled like `GlassSheet` in `OnboardingScreen.kt`.
    4. Place it on the AirPods screen (`AirPodsSettingsScreen.kt`, the "battery" item, around line 315) above the battery. Tapping it opens a full-screen viewer.
    5. Use the night clip's frames in dark mode.

13. **Optional true 3D: ask Jake first.** A real 3D model would allow free rotation in every direction. It needs:
    - SceneView/Filament (Apache-2.0)
    - a refreshed `ci-deps-snapshot`, because cloud sessions build offline
    - an AirPods 3D model whose licence allows redistribution in a public GPL repo, which has **not been found or verified**

    It would also replace the LibrePods clips Jake chose, so do not start it without his clear yes.

14. **Request 8 (newest): an accurate battery time-left estimate.**
    - Data sources:
      - AACP battery packets (`data/Packets.kt`, `BatteryNotification.setBattery`) report left, right and case in **1% steps**, with charging status.
      - Bluetooth adverts only give **10% steps**, so never compute a drain rate from them.
    - Apple's official AirPods Pro 3 figures (apple.com/airpods-pro/specs, checked 2026-09-30):
      - up to 8 hours listening with ANC
      - 7.5 hours with Spatial Audio and head tracking
      - 6.5 hours with workout heart-rate sensing
      - up to 10 hours in Transparency with the Hearing Aid feature
      - up to 24 hours total with the case
      - 5 minutes of charging gives about 1 hour of listening

15. **Estimator design.** Create a pure-Kotlin `services/BatteryEstimator.kt` with unit tests.
    - **Samples:**
      - Record timestamped samples (left%, right%, in-ear state, listening mode) only while discharging over AACP.
      - Use the lower bud, since it dies first.
    - **Rate:**
      - Fit a regression over the last 20–30 minutes.
      - Trust it only after at least 3 separate 1% drops.
      - Reset on charging, and on a big listening-mode change.
    - **Before enough data:**
      - Use level% × Apple's rated hours for the current mode.
      - Blend toward the observed rate using `weight = min(1, minutes / 30)`.
    - **Learning:** store a per-mode average of past sessions, so the estimate learns Jake's own AirPods.
    - **Case and charging:**
      - Show the case as "about N more full charges". The case holds roughly two extra full charges (24 h ÷ 8 h), so label it approximate.
      - While charging, show "Full in about X min".

16. **Showing the estimate.**
    - Round honestly: nearest 10 minutes above an hour, nearest 5 below.
    - Show "Estimating…" when there is no basis.
    - Show "Connect to see time left" when only Bluetooth adverts are available.
    - Put it under the battery on the AirPods screen, as compact text in the island's battery state, and in the notification if there is space.
    - Tests: a steady drain, a mode switch, charging reset, uneven buds, no data, and advert-only data.

17. **Building in a cloud session** (details in `CLAUDE.md`):
    1. Download the `gradle-deps.tgz` asset from the `ci-deps-snapshot` release into a `GRADLE_USER_HOME`.
    2. Take the Android SDK from the `cimg/android` Docker image layers.
    3. Run Gradle with:

       ```
       ./gradlew --offline -PndkVersion=29.0.14206865 -PcmakeVersion=4.1.2
       ```

    4. Point `ROBOLECTRIC_DEPS_DIR` at a folder containing the android-all jar.

    Tasks:
    - `testFossDebugUnitTest`
    - `testFossDebugUnitTest --tests '*AppTourScreenshots*'` (images go to `build/screenshots/tour`)
    - `lintFossRelease`
    - `assembleFossRelease`

    Gotchas:
    - The dark Robolectric qualifier must be ordered `w412dp-h915dp-night-xxhdpi`.
    - Robolectric does not draw Kyant blur or lens, so screenshots show only the tint.

18. **Order of work:**
    1. Verify and fix the existing work-in-progress.
    2. Turn off the bottom card and debounce it.
    3. Build the battery estimator with tests.
    4. Build the 3D spinner and switcher.
    5. Finish the branding sweep.
    6. Extend Liquid Glass to the main screens.
    7. Walk through every button.

    Commit in small, verified steps and push often; the cloud container is temporary. End each commit message with the attribution lines the system gives you. Never put a model name in commits or the PR.

19. **Docs to update before finishing:**
    - `DECISIONS.md`: it still describes the sponsor unlock and the privacy page. Add a section on "your own app, everything included, glass setup, one-piece island, card off, 3D viewer, time-left estimate".
    - `TESTING.md`:
      - Line 16 says "Welcome and Privacy pages".
      - Line 35 has an Unlock row to delete.
      - Lines 39 and 65 mention "needs the unlock".
      - Add checks for the 3D viewer and the time-left text.
    - `CLAUDE.md`
    - The PR #1 description, in plain language.
    - Delete `HANDOFF.md` and this file in the final commit.

20. **Finishing.**
    - Push to `claude/pensive-franklin-lvce7c` and check CI **once** (do not subscribe).
    - Report to Jake in short, plain language:
      - what changed
      - what you verified and how
      - what still needs his real phone and AirPods to confirm: Bluetooth connection on One UI 9, the time-left accuracy over a real day, and the lid behaviour
      - how to install: the Releases page, `Glint-preview.apk`, or `Glint.apk` after he taps the green Merge button on PR #1
    - Still waiting on Jake's decision:
      - whether to show battery from nearby AirPods before Glint confirms they are his (the identity-key fallback)
      - whether to keep a small "Based on LibrePods" credit
      - whether he wants true 3D
    - Not verified, so don't add it: the AirPods Pro 3 Bluetooth product ID. Sources conflict.
