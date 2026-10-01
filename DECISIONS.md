# pro (formerly Glint): decisions and why

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
- **Licence and credit.** Everything stays GPL-3.0 and public (this repository, with `LICENSE` and `THIRD_PARTY_NOTICES.md`). The in-app LibrePods row was removed at your request (section 10); the GPL licence headers in the source files stay, as the licence requires. "Report a Glint problem" goes to *this* fork.
- **Everything included, no paywall (replaces the earlier sponsor unlock).** The Glint build (the "foss" build you install) treats every feature as included from the first launch: there is no unlock button, sponsor page, timer or "Locked" label, and nothing in the app can open the old purchase page. Covered by tests (`FossUnlockTest`).
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

- **Builds, tests and code checker** run locally and on GitHub for every push. Unit tests cover AirPods detection, the retry timing, the "can this phone connect" verdicts, listening-mode cycling the battery time-left estimate and the case-open card rules.
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
- LibrePods' "What's new" page (about LibrePods' own releases) was removed entirely (section 10).

**Behaviour**
- Every time you came back to the app it attached another copy of its listeners to the background service without removing the old ones, so updates were processed several times and memory slowly leaked. Now it attaches once.
- A settings listener in LibrePods' code was held so loosely that Android could throw it away, so the screen could stop reacting to changes like a rename. Fixed.
- The background service could crash when an "AirPods moved to another device" or "taking over" event arrived before any battery report. Fixed in all four places.
- The "couldn't connect" notification now says Glint, not LibrePods, and no longer buzzes again on every retry.
- The app has internet permission (a bundled library adds it); Glint itself sends nothing. (The old privacy page was removed with the agreement step, section 10.)

**Checked, not a bug:** the call-control page title and the licences page (both fine in the real app).

**Still only checkable on your phone (not verified):** real Bluetooth, the head-gesture, rename and equalizer screens (they need live AirPods to open), video smoothness, the system blur, haptics.

A new automated "screen tour" (`AppTourScreenshots`) renders 26 screens on every build so regressions like these show up as images.

## 9. Functionality audit for AirPods Pro 3

- **Model recognised.** Apple lists AirPods Pro 3 as A3063 (left), A3064 (right), A3065 (case); Glint's model table matches (checked against a mirror of Apple's "Identify your AirPods" page and iFixit). A test now guards this.
- **Battery bug fixed.** The AirPods can report 1, 2 or 3 battery entries (the protocol notes in `docs/` describe this). LibrePods only accepted exactly 3, so a report with just the buds (case out of range) or one bud was thrown away and the battery went stale. Glint now reads any number; a part that isn't reported keeps its last level and shows as disconnected, and an invalid level (0xFF) keeps the last good one.
- **Tested with real message formats.** New `AirPodsProtocolTest` feeds the documented AirPods messages through the app's own decoders: battery (full, partial, single bud, invalid, truncated), listening mode, conversation awareness, ear detection, and device information (name, model, serial).
- **Start-up crash guard.** If the phone restarts before set-up is finished (no Bluetooth permission yet), the background service now stops cleanly instead of making Android close the app with an error a few seconds later.
- **Checked and fine:** auto-pause/resume only resumes music Glint paused itself; the notification's "Listening mode" and "Try again" buttons reach working handlers; pop-ups check the "Display over other apps" permission and can't crash; restart after reboot/update is wired.
- **Not added (couldn't verify):** the AirPods Pro 3's Bluetooth product ID for the advert reader's model-name list. Sources disagree, and the name is only used in logs.
- **Open question (needs your call):** Glint also reads the AirPods' short radio "adverts" (battery, lid open). It only trusts adverts it can verify with a key the AirPods hand over the first time the full control connection works. If your phone blocks that connection, adverts are ignored too, so there is no lid-open pop-up and no battery from adverts. A fallback could use unverified adverts from very close AirPods while yours are playing audio, but it could occasionally show someone else's AirPods. Not built without your go-ahead.

## 10. Your own app: no branding, no agreement, no paywall, glass throughout

- **Branding.** Removed from what you can see: the LibrePods row, unlock button and billing banner in Settings; LibrePods' "What's new" screen and its changelog; a hidden contact form and a billing banner that emailed the original developer; the sponsor wording in the app's text. Kept, because the GPL requires it or it's invisible: licence headers in the code, `LICENSE`, `THIRD_PARTY_NOTICES.md`, and the internal package name `me.kavishdevar.librepods` (you never see it; renaming it would be a large, risky change).
  - Open question for you: an optional one-line "Based on LibrePods (GPLv3)" in About. Whether the licence requires an in-app credit is **not verified** (and this isn't legal advice); the source and licence being public in this repository are required, and they are.
- **No agreement step.** Setup is Welcome, This phone, Permissions, Stay connected. The privacy/agreement page is gone.
- **Liquid Glass throughout.** One shared glass material (`presentation/glint/GlassSurface.kt`): backdrop blur, vibrancy, real edge refraction with a slight colour fringe (Kyant backdrop library, on the phone's graphics chip), a rim highlight and a two-layer shadow whose strength depends on how high the surface floats. It's used by setup and the floating notices. On every screen, the top edge is now a soft glass fade instead of a flat tinted bar, and the floating back/settings buttons bend the page scrolling under them. Following Apple's rule, glass is for controls; lists and text stay solid so they're easy to read.
  - Accessibility: "Reduce transparency" (Glint's switch, or Android's high-contrast text) makes glass solid with a visible 1dp border; "Reduce motion" removes the spinning, springs and scaling.
  - Robolectric screenshots can't draw the blur or refraction; they only show the tint and shape. The real glass needs the phone (Android 13+). **Not verified on the phone.**
- **Overlays.** The island (top orb) is unchanged. The bottom "case opened" card is now **off by default** (Settings > "Card when the case opens"). Why it looked random: the lid state comes from Bluetooth adverts, Samsung batches those, and a 2.5-second gap counted as "lid closed", so the next advert re-showed the card. Now the card only re-arms after the lid has stayed closed for 10 seconds, is ignored while both buds are in your ears, and shows at most once a minute. The cause was found by reading the code; **not verified on your phone**.
- **3D viewer (removed at your request).** The separate 3D screen is gone; the turning earbuds now live in the island instead (section 11).
- **Battery time left.** Shown under the battery rings: "About 3 h 20 min of listening left", or "Full in about 40 min" while charging, plus a rough case figure ("Case: about 1.5 more full charges").
  - Accuracy: it only uses the AirPods' own 1% battery reports (the radio adverts are 10% steps, too coarse). It starts from Apple's rating (AirPods Pro 3: up to 8 h with noise cancellation, 24 h with the case, from apple.com), then times each 1% drop while you're wearing them and moves to your real rate within about 30 minutes. Each bud is measured separately; the one that runs out first decides. Changing listening mode, taking the buds out or charging starts a fresh measurement, and finished sessions teach it your pair's own rate per listening mode, so it gets closer over days.
  - It rounds honestly (10-minute steps above an hour, 5 below) and says what it's based on. With no connection it shows nothing rather than guess.
  - Apple's figures for other modes (e.g. 10 h in Transparency with the Hearing Aid feature) aren't used as starting points: they apply to specific features, and the learned rate takes over anyway. **Real-world accuracy not verified**: it needs a few listening sessions on your AirPods.

## 11. Straight glass, a better island, heart rate, recorder, clear feedback

- **Slanted glass, found and fixed.** Two causes, both verified in the code:
  1. The glass library (Kyant backdrop) lights every glass edge from a 45-degree diagonal by default: bright top-left, dark bottom-right. Buttons, sheets and switches used it, so they looked tilted. All of them now use one rim light from straight above (`GlintLight` in `GlassSurface.kt`).
  2. The island's and card's own rim ran corner to corner and slid sideways with phone tilt. It's now vertical, and tilt only nudges it slightly.
- **Island.**
  - It now appears once each time the AirPods actually connect. Before, nothing showed it on connection, only when both buds went into your ears, on a mode change away from the app, or on low battery; that's why it felt random.
  - Putting the buds in shows "In your ears".
  - If an island ever gets stuck (for example the screen went off mid-animation), the next event clears it instead of being swallowed.
  - The earbuds turn continuously from pre-cut frames of the island clip. That's smoother and more dependable in a pop-up than a video player. In the expanded island you can flick them with a finger.
  - The expanded island shows the connection state with a coloured dot, left/right/case batteries, the listening mode, time left, and an **Open Glint** button (plus "Use here" / "Dismiss" when relevant).
  - It morphs with width and height on separate springs, so it grows like a drop, not a box.
  - Fixed: a button inside the expanded island also counted as a tap on the island itself.
  - Tapping the optional bottom card opens Glint.
- **Heart rate (AirPods Pro 3).**
  - Ported from LibrePods' newer "rewrite" version, where it is marked experimental. The message format was checked byte for byte against the head-tracking message the app has always sent.
  - The Heart rate screen shows a live BPM with a heart that beats at that rate, the session as a line (touch to read any point), lowest / average / highest, and an optional high heart-rate alert.
  - Measuring continues in the background and resumes after a reconnect. If no reading arrives, it says so.
  - Not saved to Health Connect: that needs a library the offline cloud build can't fetch. **Not verified on your AirPods.**
- **Recorder (experimental).**
  - Records from the AirPods' own microphones over their control link. Ported from the same LibrePods branch, where it is work in progress.
  - Saves WAV files you can play, share or delete. If no audio arrives, it says so. **Not verified on your AirPods.**
- **Head gestures.**
  - The screen now shows where your head is pointing, live, with a Recenter button, plus a clear "tracking / waiting" line.
  - The two motion lines are blue and orange; one used to be white on a white card.
  - The main-screen row now shows the real on/off state (it used to say Off while on).
  - Nod to answer and shake to decline calls, as before.
- **Knowing whether a change worked.** The AirPods answer each listening-mode change with the mode they switched to (documented in `docs/AAP Definitions.md`). A small glass notice at the bottom of the main screen says "Switching to Adaptive…", then "Adaptive is on". If it can't be sent, isn't confirmed within 3 seconds, or the AirPods stay in another mode, the notice says so and offers Reconnect. A status line under the battery says whether the controls are connected.
- **Notification.** Android requires a notification to keep a background connection alive. Glint now keeps it on a hidden channel at all times, so nothing stays in your notification shade. Android's documentation says the service keeps running and only shows in the Task Manager in that case. To bring back battery in the shade, turn on Settings > Stay connected & appearance > **Status in notifications**. **Not verified on your phone.**
- **Everything from LibrePods' feature list is present.**
  - Listening modes, ear detection, battery, rename, head gestures, conversation awareness, auto-connect, call controls, microphone side, sleep detection, the "Off" mode option, press speed and hold duration, noise cancellation with one bud, swipe for volume, heart rate and the recorder.
  - Hearing aid, transparency customisation, loud sound reduction and multi-device need a changed Bluetooth "vendor ID", which needs a rooted phone. They're listed under "Needs root" instead of silently missing.
  - Find My and head-tracked spatial audio aren't in LibrePods either.
- **Remaining name traces** are internal only: the code package name, style names and the licence headers in source files. Translated text that mentioned LibrePods now says Glint.

## 12. Hold to open, smooth spin again, light/dark, heart-rate insights, less text

- **Island.** The big "Open Glint" button is gone. **Hold the island** (half a second) to open Glint; a tap still expands or collapses it. A small "Use here" / "Dismiss" pill shows only when it's needed.
- **Spinning AirPods glitch.** The previous round drew 48 still frames and blended each into the next. That spaced the poses unevenly and briefly showed two poses at once, which looked like glitching. The island is back to playing the original clip as a video (the smooth version you liked). The still frames were removed.
- **Light and dark mode.** Settings > Appearance: Automatic, Light or Dark, for the whole app and the island/card. The island now has a light version (frosted white with dark text) as well as the dark one. The status-bar icons follow the choice.
- **Heart rate.**
  - The number and "BPM" now line up, and Reset sits in the session header.
  - With your age set, the screen shows your **effort zone** (Light / Moderate / Vigorous / Peak) and time spent in each zone. The zones follow American Heart Association guidance as reported by secondary sources: maximum about 220 minus age; moderate 50–70%, vigorous 70–85%; resting 60–100 BPM for most adults. AHA's own page was blocked from the cloud session.
  - It also shows a **trend** (rising / steady / coming down, over the last 2 minutes compared with the 3 before) and your **earlier sessions** (the last 20 sessions that lasted a minute or more are kept).
- **Less text.** Descriptions were trimmed across the app. Longer explanations moved behind small "i" buttons that open a bubble (time left, effort zones, the recorder, reading notes).
- **Idea for later (not started):** share heart-rate readings with Jake's other personal Claude apps. A simple route is a local export (a file, or Android's share sheet) or Health Connect, which needs a library the offline cloud build can't fetch yet. Decide the target apps first.

## 13. Touch-responsive glass, crowded rows fixed, shorter setup text

- **Crowded rows (your screenshot).** List rows showed the name and description side by side with nothing limiting either, so long pairs wrapped into each other ("Transparency customization" + "Amplification, tone and balance"). The shared row component now stacks the description under the name whenever the pair is long, and keeps short ones on one line, so this can't recur anywhere in the app.
- **Glass that reacts like Apple's.** Apple's interactive Liquid Glass shrinks slightly when pressed, bounces back on release, lights up under your finger and follows drags. Glint now does the same:
  - **Island:** squishes when pressed and bounces back when you let go. Pulling it down stretches it like rubber and opens it; swiping up still dismisses it.
  - **Listening-mode selector:** the thumb stretches like a drop while it travels between modes and settles with a little give. It lifts slightly while you drag it, and has a glass edge and top shine.
  - **Light/Dark selector:** the same liquid thumb.
  - **"i" bubbles:** they grow out of the button you tapped.
  - All of this is off with Reduce motion.
- **Shorter text:** setup (This phone, Permissions, Stay connected), the Accessibility screen descriptions, and the "connect once" note in Settings.
- **Audit:** every screen was rendered in the test tour and checked by eye, including the whole main screen top to bottom. Real touch feel (springs, haptics) **needs the phone; not verified**.


## 14. Live heart-rate sharing and more useful heart info

- **Share live heart rate** (Heart rate > Share live heart rate). Three options, each off until switched on, and all quiet unless you're measuring:
  - **Bluetooth sensor:** the phone shows up as a standard Bluetooth heart-rate sensor (Heart Rate Service 0x180D, the same kind a chest strap is), so watches, bike computers, gym machines and fitness apps can pair to it. Like a chest strap, any nearby device can connect while it's on. Needs the "Nearby devices" permission, which is asked for when you turn it on.
  - **Send to a web address:** posts `{"bpm":72,"time":<ms>,"status":"live"}` to an https address you choose (Home Assistant, Zapier/Make/IFTTT, your own app), every 10 seconds by default (5 s minimum), plus a final `"stopped"` message. https only so the reading is encrypted. Has a "Send a test" button.
  - **Automation apps:** Android broadcast `io.github.jbyjre.glint.HEART_RATE` with extras `bpm`, `time`, `status` for Tasker ("Intent Received" event) or MacroDroid ("Intent Received" trigger).
  - **Not added:** a Wi-Fi live page/stream (a small web server in the app that other devices on your Wi-Fi could open). The cloud session's safety setting blocked writing it ("exposing a local service"); it needs Jake's explicit go-ahead.
- **More useful heart info:** a resting estimate (lowest 3-minute average), 1-minute recovery after the session's peak (shown when the peak is 100 BPM or more; the 12 BPM reference is from Cole et al., NEJM 1999), and "Export readings" (CSV through the share sheet). The expanded island shows the live BPM while you measure.
- Code: `services/HeartLink.kt` (outlets, wired from the service), `presentation/screens/HeartShareScreen.kt`, `HeartInsights.restingEstimate/recovery/csv`.
- **Not verified on the phone:** pairing a watch or app to the Bluetooth sensor, a real web address receiving posts, Tasker receiving the broadcast.

## 15. Background heart rate, glass that catches the light, sweep fixes

- **Measure whenever worn** (Heart rate screen, off by default). When a bud goes in, Glint starts measuring by itself; a minute after both come out it stops and saves the stretch under Earlier. It runs in Glint's existing hidden background service, so there's no notification and it works with the app closed.
  - **Lower cost:** it asks the AirPods for a reading about every 5 seconds instead of every second. If they don't send at that rate, it falls back to every second. The request type that worked is remembered, so later starts are quicker.
  - Pressing Stop pauses it until the buds next go in.
  - Sessions are also saved every 10 minutes, and a break of over 30 minutes starts a new session.
  - **Not verified on the phone:** whether the AirPods honour the 5-second rate, and the actual battery cost.
- **Glass catches the light.** Apple's own description says Liquid Glass "dynamically reacts to movement with specular highlights". Glint's rim light now swings gently as you tilt the phone sideways, at most 14 degrees, using the gravity sensor only while the app is on screen. Held level, it's exactly the straight-overhead light from before. It's off with Reduce motion. **Not verified on the phone.**
- **Heart glow:** a soft red glow behind the heart swells with each beat.
- **Sweep fixes:**
  - Heart rate didn't resume after a quick reconnect (within 30 s), because the resume sat behind the island's once-per-30-seconds check. It now resumes every time.
  - After a disconnect, a running measurement could block restarting.
  - Heart-rate requests now wait until the connection's set-up burst is over.
  - A busy island (several events in a row) could be mistaken for a stuck one and flicker.
  - "Report a problem" no longer says "fork".
  - Lint shows no errors. Its remaining warnings are harmless: unused upstream resources, and false "typos" inside certificate data.

## 16. Heart history, GitHub backup, "what it means", battery pace, more motion

- **First 3 seconds dropped.** You saw readings start high and then jump down. Each time the sensor starts (including each battery-saving burst), readings in the first 3 seconds after the first one arrives are now thrown away. Found and fixed in the code; **not verified on your AirPods**.
- **History of every session.** Sessions are no longer limited to the last 40 summaries: every session of a minute or more is kept with **all its readings** (one small spreadsheet file per session on the phone). Heart rate > **History** shows the last 30 days as daily range bars (tap a day to filter), totals, your **usual resting rate** and whether it's drifting, and every session grouped by day. Tap one to see **its graph**, numbers, zones, export it or delete it. Old summaries were moved in automatically. Sessions longer than the in-memory limit are saved and continued instead of losing their start.
- **Backup to GitHub.** History > **Back up to GitHub**: tap Open GitHub (the token page opens with the "repo" box ticked), generate a token, paste it, Connect. Glint creates a **private** repository (`glint-heart-backup` by default) and uploads each session **as soon as it ends**, and a session that's still going every 10 minutes. On a new phone, connect the same repository and tap **Restore**.
  - Checked against GitHub's own API description: creating a private repository and uploading files need a classic token with the `repo` scope. Public repositories are refused, because heart data is health data.
  - The token is encrypted with a key held in the phone's secure hardware and only sent to api.github.com.
  - Trade-off: anyone holding the token can read and change your repositories, so keep it private (you can revoke it on GitHub any time). **Not verified on your phone.**
- **What your heart rate means.** Under the live number: a plain-language line (for example "Normal resting range: within the typical adult resting range of 60–100 BPM. About 6 above your usual resting rate (64)") and a coloured scale from 40 to 200 BPM with a marker that glides to your reading and a tick at your usual rate. With your age set, effort zones take over above the light range. Sources: American Heart Association (60–100 resting; max about 220 minus age; moderate 50–70%, vigorous 70–85%), as before. For fitness, not medical use.
- **Battery: measuring pace.** Measure whenever worn now has a pace: **Every 5 s** (what you had, still the default), **Balanced** (1 minute of readings every 5 minutes: the sensor is on about 80% less) and **Saver** (1 minute every 15 minutes: about 93% less). Also, background checks now wake every 10 seconds instead of every 3. The real battery saving hasn't been measured.
- **Island.** When background measuring starts, the island shows "Heart rate · 72 BPM · Normal resting range" with a beating heart, once per session; high-rate alerts show on it too. Switch: Heart rate > Show on the island.
- **Conversation Awareness and Adaptive Audio (your question).** They're separate. *Adaptive* is a listening mode that blends noise cancellation and transparency by itself. *Conversation Awareness* is a switch that works in any listening mode: when you start talking, the AirPods turn down your media and bring voices forward, and put things back when you stop. On an iPhone, the phone lowers the volume; on Android, Glint does that part (the AirPods tell Glint when you start and stop talking). Source: Apple's AirPods guide as quoted in search results (Apple's site was blocked from the cloud session, so I couldn't open it directly). **New option: Only in Adaptive** (main screen > Audio): Glint turns Conversation Awareness on when you switch to Adaptive and off for the other modes. Switching Conversation Awareness by hand turns the option off.
- **Fixed along the way:** the app could keep reading an old value for an AirPods setting after it changed (it stored the new value next to the old one instead of replacing it). Pressing a switch row in dark mode flashed a bright grey box.
- **Hearing Protection (your request).** Its two switches send exactly the same commands as LibrePods' newest version (checked line by line). What changed: the screen now shows a notice at the bottom if a change can't reach your AirPods, the switches are locked with "Connect your AirPods to change this" while they're disconnected (before, they flipped with nothing happening), an "i" explains both parts, and when Loud Sound Reduction can't be changed (it needs a rooted phone) the screen says so instead of hiding it silently. The stored-value fix above also makes the Workspace Use switch show its real state after it changes. The 82 dBA / EN 352 figures are LibrePods' own description, **not verified** against Apple. **Not verified on your AirPods.**
- **LibrePods features:** I compared Glint with LibrePods' newest code (main and the "rewrite" branch). Main only changed its README since Glint was made; every AirPods control command the rewrite uses is also used in Glint (24 of 24). Nothing was missing.
- **More motion and glass.** The live heart card is now a field of colour that drifts and swells with each beat, with the heart in a glass orb and a glass Start/Stop button that both bend the colour behind them; the number rolls like an odometer; an ECG-style sweep runs at your rate; graphs draw themselves in; History bars grow in. Across the app: cards rise in when a screen opens, list rows light up under your finger, buttons squish when pressed, battery rings fill when they appear and a soft light runs round them while charging, and the segmented controls have a glass sheen. All of it stops with Reduce motion.
- Code: `services/HeartHistory.kt`, `services/HeartBackup.kt`, `presentation/screens/HeartHistoryScreen.kt` (and the session screen), `HeartParts.kt`, `glint/GlintMotion.kt`, `glint/HeartGlyph.kt`.

## 17. A happier icon, smooth light/dark, deeper heart rate, Hearing Protection that works everywhere

- **New icon (your request: no AirPods, no case, something that makes you happy).** A glossy drop of liquid glass with a bright smile inside, floating on a warm sunset sweep (violet to pink to peach), with a soft shadow, a top sheen and light gathered at its bottom edge like real glass. The themed (one-colour) version is the drop's outline and the smile. Drawn from scratch as vector art, so it's sharp at every size. I tried a wavy "sound wave" smile first; it read as a squiggle, so the final one is a clean round smile.
- **Light and dark.**
  - **Bug fixed:** switching light/dark inside the app left most list rows in the old colours (white rows with white text in dark mode). Each row remembered its colour from when it first appeared. Rows now follow the theme and fade smoothly to the new colours.
  - **Reveal:** picking Light or Dark spreads the new look out in a circle from the button you tapped, with a soft glowing edge. With Reduce motion it switches instantly. The test renderer can't take the screen picture this needs, so the circle is **not verified on your phone**; if it can't run, the app just switches (that part is tested).
  - **Picker:** Automatic, Light and Dark now have small animated symbols: the half-lit disc turns over, the sun's rays stretch and turn, the moon rocks into place with a twinkling star.
- **More depth in heart rate.**
  - **Today** card: time measured today, lowest to highest on a bar, the day's average, and today's resting rate against your usual.
  - Session graphs take each **effort zone's colour** when your age is set (green light, amber moderate, orange vigorous, red peak), with a dashed line at the session's average.
  - Session details add **hardest 5 minutes** (highest 5-minute average, a steadier effort measure than the single peak) and **time above 100 BPM**.
  - History adds a **resting heart rate over 30 days** chart (one dot per day, with your usual rate dashed) and **personal bests** (lowest resting, highest reading, longest session; tap one to open it).
- **Hearing Protection.**
  - The Hearing Protection row could disappear from the main screen: it checked the AirPods model separately from the rest of the screen, and the model can be missing while everything else is known. It now uses the same check, and it always shows.
  - **New: Volume limit** (works without root, any AirPods). While music plays through your AirPods, Glint turns the volume back down whenever it goes above the level you pick (50, 60, 70 or 85% of the phone's volume). The screen says when it last did. The level is a share of the phone's volume steps, not decibels: Glint can't measure how loud it is in your ears. It listens for Android's volume-changed signal, which Android sends but doesn't officially document, and also checks when the AirPods connect. **Not verified on your phone.**
  - Workspace Use only shows for AirPods that support it.
- **Glass everywhere.** I rendered every screen and checked them side by side. The Troubleshooting screen still used flat Material buttons; they're Glint's glass buttons now. Chart labels ("avg", "usual") were moved so they don't sit on the line.
- Code: `res/drawable-v24/ic_launcher_*.xml`, `presentation/theme/ThemeReveal.kt`, `services/VolumeGuard.kt`, `HeartParts.kt` (`RestingTrendChart`, zone-coloured `HeartChart`), `HeartInsights.peakAverage/secondsAbove/records/restingSeries`.


## 18. New icon: battery ring, no pink

- **Your request:** the pink sunset icon wasn't smooth, then a full redesign. The new icon is Glint's own battery ring (a white ring about three-quarters filled, with a glow where it ends) around a glass orb, on a smooth violet-blue-aqua sweep. No AirPods, no case, no pink, no smile or sparkle. Chosen because the ring is what the app itself shows.
- The one-colour (themed icons) version is the ring and orb. Android only uses it if "Themed icons" is on in the phone's settings; it can't be forced off from the app.
- Drawn as a picture first and checked in circle, rounded and square shapes and at small size; the app build itself is **not verified on a phone**.
- Code: `res/drawable-v24/ic_launcher_*.xml`.

## 19. Black/white/graphite icon, an island that shows up more, music controls, the heart inside the island

- **New icon (your request: black, white or gray; smooth; abstract; no AirPods case, no pink glass).** Glint's ring (the same battery ring the app shows) around a glass pearl, in monochrome. Three versions you can pick in **Settings > App icon**: **Black** (default), **White** (dark pearl on white) and **Graphite** (gray). I sketched three ideas side by side first: sound waves around the pearl looked like a generic Wi-Fi/radio icon and two overlapping circles disappeared at small size, so the ring and pearl won. The one-colour "themed icons" version is the ring and pearl.
  - How switching works: each choice is a separate home-screen entry for the same app, and Glint turns on the one you pick and turns off the others (a standard Android technique). On some launchers the home-screen shortcut disappears when you switch; the app stays in the app list and you can drag it back. **Not verified on your phone.**
  - If you still saw a pink icon before this update, that was an older build; the last one was already blue, and this one has no colour at all.
- **The island shows up for more moments, and you choose which (Settings > Island).** Each has its own switch:
  - AirPods connect; AirPods go in (and "Both AirPods in" when the second one goes back in).
  - **An AirPod comes out (new):** "One AirPod out · Music paused" (or "AirPods out"), with a **play button right in the pill** to start the music again.
  - **Something starts playing (new):** the song (or "Now playing") with a **pause button**. It waits 1.5 seconds and only announces audio that keeps playing, so silent autoplay videos while scrolling don't pop it up. Not while Glint is open, and at most every 20 seconds.
  - **Each new song (new, off by default):** it would otherwise appear every few minutes.
  - Listening mode changes, low battery, **charging starts (new)**, moved to another device, high heart rate.
  - Also: **how long it stays** (Short / Normal / Long; touching it restarts the timer, so it never leaves while you use it), **haptics** on/off, and **Try it** buttons that show each look.
  - Honest limit: the AirPods report the "primary" and "secondary" bud, not left and right (checked in the protocol notes in `docs/`), so the island says "One AirPod out" instead of guessing a side.
- **Music controls (your request).** The opened island has a music row: cover, song and artist, **previous / play-pause / next**. Play and pause morph into each other.
  - Play/pause works with any music app, Spotify included, with no setup: Glint presses the same "media buttons" a headset does, and Android sends them to the app that last played.
  - **Song names and covers** need one permission: Android only shares "what's playing" with apps given **Notification access** (checked in Android's documentation). Settings > Island explains it with an Allow button. Glint only uses it to read the song and use the music app's own controls. Because Glint isn't installed from the Play Store, Android may call this a "Restricted setting"; the page tells you how to allow it (App info > ⋮ > Allow restricted settings, a step Android added for apps installed from files).
  - A pause you press on the island is never undone when a bud goes back in.
  - **Not verified on your phone:** Spotify's response to the buttons, and the song names.
- **Heart rate on the island (your request).** It no longer pops the island when measuring starts. Instead, when you **tap the island open** while measuring, a **red heart with the number inside it** beats at your rate. **Tap the heart** and the island turns into a bigger explanation: a large heart, what the number means in plain words (e.g. "Normal resting range"), this session's lowest / average / highest, and the coloured 40–200 scale with your reading on it. Tap the back arrow (or the heart) to return. The island still pops up for **high heart-rate alerts** if you set one (switch: High heart rate).
- **Easier to read.** Bigger text in the pill (14/12 instead of 13/11) and in the opened island (18-point title, larger battery numbers). The opened island is taller to fit the music row without crowding.
- **Glass.** The new buttons are drawn into the island's existing liquid glass (same blur, rim light from straight above, tilt highlights), squish when pressed and spring back; the heart page grows in with a spring. With Reduce motion everything switches instantly.
- **Checked:** every new look rendered as an image in light and dark (pill: AirPod out, music, music without song names, charging; opened: music + heart, heart explanation) and reviewed; the Island settings page and the icon chooser too. Found and fixed during review: the pill's play bubble would have stayed tappable (invisibly) over the listening-mode symbol after the island opened, and the opened island's buttons could have been touchable while it was still a pill. New unit tests (`IslandMomentsTest`) cover the wording, when the heart shows, and the settings defaults.
- Code: `services/IslandPrefs.kt`, `services/NowPlaying.kt` (with `MediaAccessService`), `presentation/overlays/Island.kt`, `presentation/screens/IslandSettingsScreen.kt`, `presentation/theme/AppIcon.kt`, `presentation/components/AppIconPicker.kt`, `res/drawable-v24/ic_launcher*`.

## 20. A smaller opened island, just play/pause, a nicer heart, smoother motion

- **Smaller opened island (your request).** The song bar (cover, song name, skip buttons) is gone. The opened island is now about 30% shorter (212 instead of 300 points tall) and a little narrower, and music has one round glass **play/pause** button in its bottom-right corner. The small pill still shows the song name when something starts playing, with its own play/pause bubble.
- **The heart (your request: less weird, smoother).**
  - **Shape:** rounder, fuller lobes and a softly rounded tip instead of a sharp point. The Heart rate screen uses the same heart, so it changed there too.
  - **Look:** a smooth red gradient with a soft light in the upper left and gentle depth toward the tip, instead of the hard-edged white band across the top. A soft glow behind it swells a little with each beat. The number has a faint shadow so it stays crisp on the red, and it rolls to each new reading instead of jumping.
  - **Beat:** this was the real cause of the jerkiness. Every new reading (about once a second) restarted the heartbeat animation part-way through a beat, so the rhythm kept getting cut off. Now one steady rhythm runs and a new reading only changes the speed of the next beat. Each beat is also softer: a "lub", a smaller "dub" and a long easy release that takes about 60% of the time between beats, so even a fast heart rate gets a pause between beats.
  - Under the hood, the heart's shape and shading are prepared once and the beat only scales the finished picture, so beating no longer redraws or re-measures the number on every frame (that work could cause stutter).
- **Heart page.** Tapping the heart now **grows** the island smoothly to make room for the explanation and shrinks it back when you return, so the everyday view stays small.
- **Smoother motion overall.**
  - The turning-AirPods video used to start at the exact moment the island began to move, so starting it competed with the animation. Now the still picture (the video's own first frame) shows while the island moves, and the video starts once it settles (about a third of a second later). It looks the same and moves more smoothly.
  - The opened island's contents fade in on a smooth S-curve while settling slightly into place, instead of appearing at the end.
  - Buttons squish more gently (10% instead of 16%), and their press highlight fades in and out instead of flashing.
- **Not verified on your phone:** how smooth it feels at your screen's refresh rate. The pictures were checked in the screenshot tests.

## 21. Even smaller island: no time-left line, L/R/case rings, a quiet heart chip

- **Time left removed from the island (your request).** The "About 3 h 20 min of listening left" line is gone from the opened island. The main screen still shows it under the battery pictures; say if you want that gone too.
- **Batteries without words.** "Left / Right / Case" rows are now three rings side by side, marked **L**, **R** and a small **case** symbol inside, with the % under each. Charging turns a ring green with light running round it, and puts a small green bolt before its %.
- **Heart: smaller, and not red (your request).** In the opened island it's now a small glass chip at the bottom left: a little heart beating at your rate and the number (e.g. "72 BPM"), in the island's own colour (white on the dark island, graphite on the light one, like the app icon). Tapping it still grows the island into the explanation page, where the big heart uses the same white/graphite. The only colour left there is a small dot by the headline (green for normal resting, and so on, matching the scale), so the meaning is still clear at a glance. The high-heart-rate pop-up's heart uses the island's colour too.
- **Smaller still:** without the time-left line the opened island is 196 points tall (it was 300 two rounds ago).
- **Smoother:** when the island changes its message while it's showing (for example "One AirPod out" turning into "Both AirPods in"), the words now glide into each other instead of jumping. The heart chip fades and grows in when a reading starts, and its number rolls to each new reading. The play button and heart chip share one glass style.
- **Not verified on your phone:** the feel of the motion.

## 22. New name "pro", heart backup into pro3, icons, more feel

- **Name (your choice: "pro", lowercase).** Everything you see now says pro: the home-screen label, setup, settings, notifications, and the download file (**pro.apk**, test builds **pro-preview.apk**). Two things deliberately did *not* change: the app's internal ID (`io.github.jbyjre.glint`), because changing it would make Android treat it as a different app (it wouldn't update over your current one and your settings would be lost), and internal code names, which you never see.
- **Heart backup goes into pro3 (your request: no second repository).**
  - pro3 is currently **public** (checked on GitHub), and heart data is health data, so you chose to make pro3 private. Until it is, the app refuses to upload and tells you how: pro3 > Settings > Danger Zone > Change visibility > Private (steps checked against GitHub's documentation).
  - The files go on a separate **branch** called `heart-backup` (think of it as a separate shelf in the same cupboard). It holds only heart files, never mixes with the app's code, and the cloud build is told to skip it, so backups don't trigger app builds. To see it on github.com, switch the branch menu from "main" to "heart-backup".
  - **Safer key.** The "Open GitHub" button now opens a *fine-grained* token page already filled in (name, "Contents: write", never expires). You only pick **pro3** under Repository access. This key can only touch pro3, unlike the old kind that could touch every repository you own. Checked: GitHub documents these pre-fill options and lists "Contents: write" as enough for every call the backup makes. Old-style tokens still work.
  - **Smarter:** if you're offline when a session ends, it now backs up by itself as soon as the internet is back (it used to wait for the next session). The repository box also accepts `pro3`, `Jbyjre/pro3` or a pasted github.com link.
  - Note on making pro3 private: the app's download page stays reachable for you while signed in to GitHub. **Not verified:** how many free cloud-build minutes your account gets for private repositories.
- **More icons, less text (your request).**
  - Every setting row whose meaning has a clear picture now shows one on a small glass tile: heart, island, bell, shield, ear, mic, phone, link, L/R and so on (41 new drawn icons in the app's own rounded style). Rows with no clear picture show none rather than a random one.
  - Heart rate: the button under the line is a **play/stop symbol** that morphs between the two; the round glass heart itself is now a start/stop button; the session card has **share** and **reset** symbols instead of the "Export readings" and "Reset" words. History: backup is **cloud-up / cloud-down / power** symbols; a saved session has **share** and **trash** symbols. **Press and hold** any round symbol to see its name in a small bubble; screen readers always hear the name.
- **More tactile, smoother (your request).**
  - Moving between screens now runs on spring physics instead of a fixed timer: it starts quickly and settles softly, and the screen underneath dims and sinks back a little, like a card being covered.
  - Light vibration ticks: tapping cards, listening-mode buttons and symbol buttons (they also squish and spring back with a small overshoot). The row pictures bounce when you press their row. The glass heart swells and brightens under your finger, like Apple's interactive glass.
- **Smarter glass.** On Battery Saver, or when the phone reports it is getting hot, the glass switches to a lighter recipe (less blur, no rainbow edge, a touch more tint so text stays clear) and switches back by itself. Full glass is GPU work; this keeps scrolling smooth exactly when the phone is struggling.
- **Fixed on the way:** switch rows picked their text colour from the phone's light/dark setting instead of the app's own Appearance choice, which could give dark text on a dark row. They now follow the app.
- **Checked:** all 152 unit tests pass (including new ones for reading the repository name), every screen re-rendered in light and dark and reviewed, lint and the release build pass. **Not verified on your phone:** how the vibrations and springs feel, and the backup against your real GitHub account.
- Code: `services/HeartBackup.kt`, `presentation/glint/RowIcons.kt`, `IconAction.kt`, `GlassBudget.kt`, `GlintMotion.kt`, `components/StyledListItem.kt`, `StyledToggle.kt`, `NoiseControlButton.kt`, `navigation/AppNavGraph.kt`, `.github/workflows/glint-apk.yml`.

## 23. Name moved to Settings, the mini island around the camera

- **Name moved (your request).** The AirPods' name row is gone from the main page. It's now the first row in **Settings** (under "pro"), with the current name shown next to it; tapping it opens the same rename page as before.
- **Mini island (your request: one really small thing around the top camera that always shows music is playing).** While anything plays, a small black pill wraps around the front camera, like the iPhone's Dynamic Island in its music state:
  - **Left of the camera:** the song's cover, with a thin ring around it showing how far through the song you are. **Right:** four moving sound bars in the cover's own colour (white for grey covers).
  - **New song or skip:** it widens for about 3 seconds with the song and artist under the camera line, then tucks back. Long names scroll once.
  - **Touch:** tap it to open the full island (opened straight to the big view when your AirPods are connected); **swipe left for the next song, right for the previous one** (it follows your finger a little and ticks); hold it to open pro.
  - **Paused:** the cover greys out and the bars flatten into dots; after 30 seconds it shrinks back into the camera. It also hides by itself while the big island is up, in landscape, in full-screen apps (videos, games), and on the lock screen.
  - It grows out of the camera when it appears and shrinks back into it when it leaves. Black on purpose, in light and dark mode, so the camera looks like part of it.
  - **Settings > Island > Mini island:** turn it off; turn off the song-name moments; or "Only with AirPods connected" (off by default, so it shows for music on any speaker or headphones, as you asked). **Try it** has a "Mini island" button.
  - Song names, covers and the progress ring need the same Notification access as the island's song names. Without it the pill still shows (a music note instead of the cover), and skipping still works using the same media buttons a headset sends.
  - **Smarter, less noise:** while the mini island is on, the big island no longer pops up for "Something starts playing" or "Each new song", since the mini island already shows both. Those two switches say so.
  - Light on battery: the bars update about 30 times a second instead of every frame, stop while paused, and the pill is a tiny window that lets touches through everywhere else.
- **Checked:** new unit tests (`MiniIslandTest`: when it shows, pausing, lock screen, AirPods-only, song changes, sizes, colour picking, progress), the three looks rendered as pictures (playing with cover, paused with no cover, widened with the name), every screen re-rendered, all tests, lint and the release build pass.
- **Not verified on your phone:** the exact fit around your S25 FE's camera (it's sized from the camera position Android reports, which I couldn't measure here), whether Samsung reports full-screen apps to it so it hides during videos, and how the swipe feels.
- Code: `presentation/overlays/MiniIsland.kt`, `services/MiniIslandRules.kt`, `services/IslandPrefs.kt`, `services/NowPlaying.kt` (skip, progress), `overlays/OverlayWindow.kt`, `overlays/GlintOverlays.kt`, `screens/IslandSettingsScreen.kt`, `screens/AppSettingsScreen.kt`.

## 24. The heart chip morphs out of the play button

- **Your request:** in the opened island, about a second after it opens, the heart should come out of the play/pause button and move over to its place; with no heart reading it should show two dashes.
- **What happens now:** open the island. One second later a small glass bubble buds out from under the play/pause button, stretches into the heart chip as it glides left to its spot at the bottom left, overshoots a touch and settles, with a light tick when it lands (if island haptics are on). The heart, number and "BPM" fade in as it takes shape.
  - **Measuring:** the chip shows your live reading with the beating heart, as before; tap it for what the number means.
  - **Not measuring:** it shows **-- BPM** with a quieter, still heart, so it's clear nothing is measuring. Tapping it opens pro.
  - The chip is now always in the opened island (before, it only appeared while measuring). Coming back from the heart explanation page doesn't replay the animation. With Reduce motion it's simply there.
- I read "don't show the music" as the opened island staying free of a song bar, which it still is: only the play/pause button. If you meant the mini island's song-name moments, they can be turned off in Settings > Island > Mini island > Show each new song's name.
- **Checked:** rendered the opened island frame by frame (1.0, 1.1, 1.22, 1.33, 1.45, 1.6 and 3 seconds) and reviewed the motion; all tests, lint and the release build pass. **Not verified on your phone:** how it feels at full speed.
- Code: `presentation/overlays/Island.kt` (`HeartChip`, `CHIP_DELAY_MS`).

## 25. The islands on every kind of phone

- **Your request:** make the mini island and the island work smoothly and correctly on all phones, in harmony with the app.
- **Camera shapes.** The mini island now picks where to sit from all the camera cutouts the phone reports:
  - a punch-hole or notch near the middle of the top: it wraps it (checked on pictures of both);
  - a camera in a corner or off to one side: it sits in the middle of the status bar instead of running off the screen;
  - no cutout, or one on the side/bottom (tablets, landscape-first devices): same, middle of the status bar;
  - a cutout touching the very top edge: the pill moves down just enough not to be cut off.
- **Screen changes.** Rotating, folding/unfolding, or changing Display size makes it measure again, so it stays centred on the camera.
- **Small screens and big text.** Both islands and the bottom card never get wider than the screen. Text follows Android's font size up to 115% and then stops growing, because the pills have fixed heights (at the largest font size the song name used to overflow). On narrow phones the battery rings sit a little closer and the percentages can't wrap onto two lines (found while checking: "54" over "%").
- **Smoother.** When the mini island widens for a song name, it now waits one screen frame after resizing before growing, which avoids a flicker some phones show when a pop-up window changes size. A song's name is shown once: the mini island no longer repeats it every time it comes back (for example after you close the big island).
- **In harmony with the app.** Allowing "Display over other apps" (in setup, Android's settings or Settings > Island) makes the mini island appear straight away if music is playing, instead of waiting for the next song. The mini island still steps aside for the big island and never shows both. The big island's look and the heart chip are unchanged.
- **Checked:** new tests for camera picking (centred hole, notch, two cutouts, corner, side, too wide, none) and for never being cut off; pictures of the mini island on a notch phone, a corner-camera phone, a phone with no cutout, and a 320-wide phone at the largest font; the opened island on that small phone; all 171 tests, lint and the release build pass. **Not verified on real devices other than these simulated shapes:** I can't run it on physical phones from here.
- Code: `services/MiniIslandRules.kt` (`pickCamera`, `centerY`, size limits), `overlays/MiniIsland.kt`, `overlays/OverlayWindow.kt` (`CappedFontScale`), `overlays/Island.kt`, `overlays/Card.kt`, `MainActivity.kt`.

## 26. The camera pill stays with your AirPods, and the island grows out of it

- **Always there with AirPods (your request).** The pill around the camera now stays up the whole time your AirPods are connected, not only while music plays (switch: Settings > Island > "Always while AirPods are connected", on by default).
  - With no music it shows the AirPods: a battery ring with the number on the left (green while charging, amber at 20% and below, red at 10%), and on the right your listening mode, or a small beating heart with your BPM while measuring. Music and AirPods views cross-fade.
  - Tapping it opens the island straight into its big view.
- **Notifications come out of it (your request).** When the island has something to show (connected, low battery, mode change, a tap), it now grows out of the pill: the black pill stretches down from the camera, turning into the glass island, and when it's done it shrinks back up into the pill, which carries on as if it never left. One shape instead of two separate things. Without the pill (turned off), the island appears as before.
  - It always settles below the camera, even on phones that report no status bar height.
- **Checked:** tests for when it stays and what it shows; pictures of the AirPods view (normal, heart, low, charging) and of the island growing out of the pill frame by frame (found and fixed: a one-frame gap at the start, and the island could have settled over the camera); all tests, lint and the release build pass.
