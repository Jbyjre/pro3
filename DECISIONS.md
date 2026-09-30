# Glint: decisions and why

Glint is a personal fork of [LibrePods](https://github.com/librepods-org/librepods) for a Samsung Galaxy S25 FE with AirPods Pro 3. This page explains the big choices in plain language. "Verified" means I checked it against code, documentation or a build. "Not verified" means it can only be confirmed on your phone.

## 1. Name, look and legal basics

- **New name and identity: "Glint".** LibrePods' name and logo are trademarked, so the app is now called Glint, has its own icon (a glass orb catching a glint of light, drawn from scratch), and its own app ID (`io.github.jbyjre.glint`). Because the ID is different, Glint installs next to LibrePods instead of replacing it.
- **Open font.** Apple's SF Pro font can't be redistributed, so it's gone. Glint uses **Inter** (SIL Open Font License), which has a similar clean look.
- **Original artwork only.** Apple's AirPods Pro 2 photos and the old `.mp4` popup videos are deleted. The Pro 3 buds and case are **drawn in code** from the published Pro 3 dimensions (narrower buds with the ear tip centred on the body; a slightly larger case with a hidden status light). Because they're drawn live, they stay sharp at any size and react to state: they lift slightly when in your ears, fade when missing, the case light glows amber or green when the lid is open or it's charging, and the highlights move when you tilt the phone.
  - Why not real-time 3D? A good 3D model would have to be built from scratch (no licensed model exists that we can ship), it adds a heavy rendering engine, and a mediocre model is exactly the "meh" look you wanted to avoid. Code-drawn art is crisp, tiny and animates smoothly.
- **Licence and credit.** Everything stays GPL-3.0 and public. The app credits LibrePods in Settings, and "Report a Glint problem" goes to *this* fork, so the original developer doesn't get bug reports meant for Glint.
- **Unlock flow kept exactly as it was.** Tapping unlock still opens the LibrePods developer's GitHub Sponsors page and unlocks on trust a few seconds later. I only improved the wording so it's clear what happens and that the money goes to the original developer. Locked settings now say "Locked" instead of just looking greyed out.

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
- **Real refraction where it's genuinely possible:** inside the app, the battery chips under the AirPods art are real refracting glass (they bend the artwork behind them, with a hint of colour fringing), using the Kyant "backdrop" library that LibrePods already included.
- **Motion:** the connection card rises as a small glass pill and springs open into the card; dismissing reverses it. The island at the top grows from a small pill; its battery bubble **buds off like liquid** with a stretching neck and melts back in when you tap to expand. All motion uses springs. Haptics: a soft "rise and click" when an overlay appears, a tick when expanding, a light tap when dismissing (using the system's touch-feedback setting).
- **Comfort settings** (Settings > Stay connected & appearance): Reduce motion (also automatic when the phone's animations are off), Reduce transparency (also automatic with high-contrast text on Android 16+), light-follows-tilt, and the listening-mode island.
- **The island sits just below the status bar**, not around the camera like Apple's Dynamic Island. Android always draws the status bar above app overlays, so a pill around the camera hole would have the clock and icons drawn over it.

**What Android draws itself, so it can't be true glass:**
- **Home-screen widgets:** the launcher draws them and can't blur behind them. They now use a "smoked glass" look (translucent tint, top sheen, light rim, the launcher's own corner radius, Apple-green rings).
- **Notification and quick-settings tile:** drawn by Samsung's system UI. Glint controls only the text, icon and buttons.

## 6. What I kept, changed or dropped

- **Kept:** all non-root features, the Material style (switch in Settings) as a fallback, the root/Xposed features for rooted phones, the demo mode (five taps on the empty main screen), GPL headers.
- **Changed:** the Apple-style look is now the default and uses the Glint glass language; the connection popup and island are rebuilt in Compose; the main screen's "not connected" view explains exactly what's wrong with the one button that helps; the main screen header shows the new art.
- **Root-only features** (hearing aid, loud sound reduction changes, transparency customisation, battery in system Bluetooth settings) are listed in a clear "Needs root" section instead of silently disappearing.
- **Dropped:** mp4 videos, Apple's images, SF Pro, the LibrePods icon, the per-packet broadcasts, the second duplicate notification.

## 7. How I checked my work

- **Builds, tests and code checker** run locally and on GitHub for every push. Unit tests cover AirPods detection, the retry timing, the "can this phone connect" verdicts and listening-mode cycling.
- **Screenshot tests** render the artwork, the island (compact, low battery, listening mode, expanded), the card (light and dark), the main-screen header and the icon to images, which I reviewed and refined like a designer: I fixed bud proportions, removed a cartoonish outline, removed dark details that made the buds look like faces, fixed a hard edge in the highlight, separated the island's bubble cleanly at rest, added the card's shadow, and brightened the Done button. GitHub also uploads these images with each build ("ui-screenshots").
- **Not verified (needs your phone):** everything that touches real Bluetooth, the system blur on Samsung, the companion wake-up, Samsung's battery page shortcut, haptic feel, and frame pacing on the S25 FE. See TESTING.md.
