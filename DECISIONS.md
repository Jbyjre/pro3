# Glint: decisions and why

Glint is a personal fork of [LibrePods](https://github.com/librepods-org/librepods) for a Samsung Galaxy S25 FE with AirPods Pro 3. This page explains the big choices in plain language. "Verified" means I checked it against code, documentation or a build. "Not verified" means it can only be confirmed on your phone.

## 1. Name, look and legal basics

- **New name and identity: "Glint".** LibrePods' name and logo are trademarked, so the app is now called Glint, has its own icon (a glossy earbuds case with its green status light, on graphite; drawn from scratch), and its own app ID (`io.github.jbyjre.glint`). Because the ID is different, Glint installs next to LibrePods instead of replacing it.
  - The first icon (a glass orb with a four-point sparkle on indigo) was replaced: that sparkle-on-indigo look is what a lot of AI apps use, which is the opposite of what you asked for.
- **Open font.** Apple's SF Pro font can't be redistributed, so it's gone. Glint uses **Inter** (SIL Open Font License), which has a similar clean look.
  - LibrePods also used SF Pro's built-in symbols (back arrows, checkmarks, play/pause, speaker, battery bolt, the circled L/R). Without the font those showed up as empty boxes, so Glint now draws its own versions of each one (`GlintSymbols.kt`), in the same rounded style. Buttons that only show a symbol also have a spoken name for screen readers.
- **The AirPods look is LibrePods' own (your call).** You asked to keep LibrePods' AirPods pictures and 3D clips instead of my drawn artwork, so they're back exactly as LibrePods has them:
  - the main screen shows the buds and case pictures with the battery rings under them, as in LibrePods;
  - the connect card plays LibrePods' turning-AirPods clip (a white version in light mode, a dark one in dark mode) inside a rounded "display window" in the glass;
  - the island at the top plays LibrePods' island clip; its black background is removed on the graphics chip so the buds float on the glass.
  - Smoothness: a still frame of each clip sits underneath, so there's never an empty box while the video starts, and the video fades in on its first frame. The connect clip starts with a few blank frames, so when it loops Glint skips them instead of flashing white. With **Reduce motion** on, only the still frames show.
  - Honest caveat: these pictures and clips look like Apple's own product renders (not verified where they came from). LibrePods has shipped them publicly for a long time and this is your personal copy, so I kept them as you asked, but I can't call them licensed.
- **Licence and credit.** Everything stays GPL-3.0 and public. The app credits LibrePods in Settings, and "Report a Glint problem" goes to *this* fork, so the original developer doesn't get bug reports meant for Glint.
- **Unlock flow kept as it was, made reliable.** Tapping unlock still opens the LibrePods developer's GitHub Sponsors page and unlocks on trust 5 seconds later ("Restore" unlocks straight away), so every paid feature is available to you. I improved the wording so it's clear what happens and that the money goes to the original developer. Locked settings say "Locked" instead of just looking greyed out.
  - Fixed: the old unlock timer only lived in memory. If Samsung closed the app while the sponsor page was open (it often does), the unlock never happened. Glint now notes when the page was opened and finishes the unlock when you come back. Covered by tests.
  - This build (the "FOSS" version) has every feature the Google Play version has, plus a Troubleshooting page. Features marked "Needs root" need a rooted phone in both versions.
- **Permission prompts.** The "Grant all" button on the setup screen had bugs: it could forget it was running, and it only opened the "Display over other apps" page if the phone permission was also missing. It now goes through each missing permission in order and ends on the "Display over other apps" page. If Android has stopped showing a permission's pop-up (after you refuse it twice), tapping it opens Glint's settings page instead of doing nothing.

## 2. Building and downloading (GitHub Actions)

- The upstream workflow only ran on branch names without a slash and needed the original developer's private signing keys, so I removed it and wrote **`.github/workflows/glint-apk.yml`**. It runs on **every push to every branch** (slashes included), runs the tests and the code checker, builds a release APK and publishes it as a GitHub Release:
  - Merges to `main` → the **"Glint" release**: https://github.com/Jbyjre/pro3/releases/latest (file: `Glint.apk`).
  - Any other branch → the **"Glint preview" pre-release** (file: `Glint-preview.apk`).
- **Signing key.** Android only installs an update if it's signed with the same key as the installed app. I first tried storing a key in the repository, but a safety check blocked it, correctly: in a public repo anyone could use that key to make a fake "update" of your app. Instead, the workflow creates a private key on its first run and keeps it in GitHub's private build cache (never in the code). A small weekly run keeps that cache alive, because GitHub deletes caches unused for 7 days.
  - Trade-off: if the cache were ever lost, the next build gets a new key and you'd have to uninstall and reinstall once (settings would reset). The weekly run makes that unlikely.
  - Trade-off: GitHub keeps branch caches separate from `main`, so the preview build and the main build have different keys. Install from the main "Glint" release once this is merged; if you tried a preview first, uninstall it before installing the main one.
- **Cloud-session helper.** My cloud machine can't reach Google's download servers (the network policy blocks `dl.google.com`, which also serves every Android library). So a helper workflow (`deps-snapshot.yml`) downloads the libraries on GitHub's machines and parks them on a pre-release called **"Build helper (not an app release)"**. It's only there so future sessions can build and test locally. It is not an app download and can be deleted any time.

## 3. Connecting reliably

Problems I found and fixed (all verified by reading the code; the fixes are covered by unit tests where they're pure logic):

- **Pro 3 "connected but not detected" (upstream issue 595).** The old code only recognised AirPods if Android had already cached their Bluetooth service list. On a fresh connection that list is often missing. Glint now also recognises the AirPods by their saved address and, as a weaker hint, by a name containing "AirPods", and it reacts to every "a device connected" signal (link up, audio up, headset up, service list refreshed).
- **The screen could stay on "not connected"** even after the AirPods connected, because it listened for one signal but reacted to a different one. Fixed; the screen now also follows a single shared status, so it can't miss an update.
- **No retries.** One failed attempt used to mean nothing happened until the next connection. Glint now retries quickly a few times, then slowly for several minutes, while the audio is still connected, and reconnects on its own if the control channel drops.
- **A timeout that never worked.** The old 5-second timeout couldn't interrupt a stuck Bluetooth call; the new one closes the socket after 8 seconds so it really gives up and tries again.
- **Wrong AirPods on startup.** The old start-up check connected to any paired AirPods whenever *any* audio device was connected. It now only picks devices that are actually connected.
- **Crash on Android 16 and older.** One call only exists on Android 17 and would have crashed older phones; it's now guarded.
- **Disconnect button.** Tapping Disconnect now stays disconnected instead of being undone by auto-reconnect.

**Can your phone connect at all?** Samsung phones need One UI 9 (Android 17) for Google's Bluetooth fix that lets apps talk to AirPods without root. One UI 9 started rolling out to the S25 FE on 28 September 2026 (Korea first; other regions following), per Samsung-focused news sites. Whether Samsung's version of Android 17 actually includes Google's fix is **not verified**; nobody has published it. So Glint doesn't guess. It watches what actually happens and tells you in plain words on the setup screen, the main screen and the notification:
- It connected before → "Ready".
- Android 17 or newer, not tried yet → "Should work; checked live".
- Samsung on Android 16 or older → "Waiting for One UI 9", with a button to check for updates.
- The audio works but the control channel keeps failing and never worked on this phone → "Your phone is blocking the connection", explained kindly, and Glint keeps trying in the background.

## 4. Surviving Samsung's background killing

Samsung puts apps to sleep aggressively. Glint uses three layers:
1. **Companion-device link (new).** During setup you link your AirPods to Glint through Android's own "companion device" system. After that, **Android itself wakes Glint when your AirPods connect**, even if the app was killed, and allows it to start its background service from the background. This is the proper, system-supported fix. Not verified on your phone.
2. **Unrestricted battery** via the standard system dialog.
3. **Samsung "Never sleeping apps"**, with step-by-step instructions and a button that tries to open Samsung's battery page (that page's internal name isn't public, so if it doesn't open, the button falls back to the app's info page).

The always-on notification is now **one** notification that updates in place: hidden-style "Glint is ready" while waiting, and battery + listening mode (with a "Listening mode" button) when connected. It also no longer re-posts itself on every data packet, and the service no longer broadcasts every raw packet to nobody, which saves battery.

## 5. Liquid glass: what's real and what isn't

I researched this against Android's SDK and Android's own source code:

- **Android never gives an app the pixels of other apps.** So an overlay can't bend or refract your home screen. (Verified: the public window APIs only offer "blur what's behind" and "blur behind the whole window".)
- **What the overlays do for real:** they ask the system to **blur what's behind them, shaped exactly like the glass and following it frame by frame** as the pill grows into a card. This uses the same system component Android's notification shade uses. It isn't part of the public SDK, so Glint reaches it carefully and only when the phone says window blur is allowed. When it isn't (battery saver, "reduce transparency", or the phone doesn't support it), the glass becomes a clean, nearly solid frosted fill so text stays readable. Whether Samsung's One UI 9 honours this blur is **not verified**; the Glint Lab screen tells you which mode your phone is using.
- **On top of the blur**, every glass surface has: a tint, a thick-glass top sheen, a soft specular highlight and a bright rim that **move when you tilt the phone or touch the glass**, a gentler caustic on the far edge, and a soft two-layer shadow.
- **Real refraction where it's genuinely possible:** inside the app, LibrePods' own glass controls (the Kyant "backdrop" library) already bend what's behind them; Glint keeps them.
- **Motion:** the connection card rises as a small glass pill and springs open into the card; dismissing reverses it. The island at the top grows from a small pill; its battery bubble **buds off like liquid** with a stretching neck and melts back in when you tap to expand. All motion uses springs. Haptics: a soft "rise and click" when an overlay appears, a tick when expanding, a light tap when dismissing (using the system's touch-feedback setting).
- **Comfort settings** (Settings > Stay connected & appearance): Reduce motion (also automatic when the phone's animations are off), Reduce transparency (also automatic with high-contrast text on Android 16+), light-follows-tilt, and the listening-mode island.
- **The island sits just below the status bar**, not around the camera like Apple's Dynamic Island. Android always draws the status bar above app overlays, so a pill around the camera hole would have the clock and icons drawn over it.

**What Android draws itself, so it can't be true glass:**
- **Home-screen widgets:** the launcher draws them and can't blur behind them. They now use a "smoked glass" look (translucent tint, top sheen, light rim, the launcher's own corner radius, Apple-green rings).
- **Notification and quick-settings tile:** drawn by Samsung's system UI. Glint controls only the text, icon and buttons.

## 6. What I kept, changed or dropped

- **Kept:** all non-root features, the Material style (switch in Settings) as a fallback, the root/Xposed features for rooted phones, the demo mode (five taps on the empty main screen), GPL headers.
- **Kept (restored at your request):** LibrePods' AirPods pictures and 3D clips, and its main-screen battery layout.
- **Changed:** the Apple-style look is now the default and uses the Glint glass language; the connection popup and island are rebuilt in Compose around LibrePods' clips; the main screen's "not connected" view explains exactly what's wrong with the one button that helps.
- **Root-only features** (hearing aid, loud sound reduction changes, transparency customisation, battery in system Bluetooth settings) are listed in a clear "Needs root" section instead of silently disappearing.
- **Dropped:** SF Pro (replaced by Inter and Glint's own symbols), the LibrePods icon, my code-drawn AirPods artwork, the per-packet broadcasts, the second duplicate notification.

## 7. How I checked my work

- **Builds, tests and code checker** run locally and on GitHub for every push. Unit tests cover AirPods detection, the retry timing, the "can this phone connect" verdicts, listening-mode cycling and the sponsor unlock (including the app being closed mid-way).
- **Screenshot tests** render the symbols, the island (compact, low battery, listening mode, expanded), the card (light and dark), the main screen with the "not connected" panel, and the icon to images, which I reviewed like a designer. From the latest round: the expanded island's buds overlapped its button and the button's text spilled past its edges; both fixed. The test machine can't play video, so these images show the clips' still frames. GitHub also uploads these images with each build ("ui-screenshots").
- **Not verified (needs your phone):** everything that touches real Bluetooth, video playback smoothness and the island clip's background removal on the S25 FE, the system blur on Samsung, the companion wake-up, Samsung's battery page shortcut, haptic feel, and frame pacing. See TESTING.md.

## 8. Full audit (second pass)

I rendered every screen of the app with LibrePods' demo AirPods (light and dark) and read through the connection code again. What I found and fixed:

**Looks**
- The "not connected" home screen was black in light mode (LibrePods never painted its background there).
- Several screens turned purple or lavender: LibrePods' Apple-style colour set only defines a few colours, so anything else fell back to Google's default purple. The missing colours are now iOS-style greys and blues. Setup (welcome, permissions, "this phone", stay connected) now uses the same Apple style as the rest of the app instead of wallpaper colours (unless you turn on Material 3 Expressive in Settings).
- "Stay connected & appearance" and Glint Lab now use the app's own white cards and green switches; their pale-blue buttons with blue text were hard to read.
- Settings rows with long descriptions no longer squeeze them into a narrow column.
- Long page titles no longer run under the back button.
- The transparency equalizer's "Band 1…8" labels no longer wrap onto two lines.
- The light "Troubleshooting" button on the "not connected" panel was nearly invisible (white on white).
- LibrePods' "What's new" page (about LibrePods' own release) no longer pops up after setup; it's still in Settings.

**Behaviour**
- Every time you came back to the app it attached another copy of its listeners to the background service without removing the old ones, so updates were processed several times and memory slowly leaked. Now it attaches once.
- A settings listener in LibrePods' code was held so loosely that Android could throw it away, so the screen could stop reacting to changes like a rename or an unlock. Fixed.
- The background service could crash when an "AirPods moved to another device" or "taking over" event arrived before any battery report. Fixed in all four places.
- The "couldn't connect" notification now says Glint, not LibrePods, and no longer buzzes again on every retry.
- The privacy page now says up front that it is LibrePods' policy (written by its developer) and that Glint adds no data collection. Note: the app does have internet permission (a bundled library adds it); Glint itself sends nothing.

**Checked, not a bug:** the call-control page title and the licences page (both fine in the real app).

**Still only checkable on your phone (not verified):** real Bluetooth, the head-gesture, rename and equalizer screens (they need live AirPods to open), video smoothness, the system blur, haptics.

A new automated "screen tour" (`AppTourScreenshots`) renders 26 screens on every build so regressions like these show up as images.
