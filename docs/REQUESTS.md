# Jake's requests, and how well each one is applied

**Read this first** when you start work on pro (the app is shown to Jake as "pro", lowercase; code names still say Glint). It lists what Jake has asked for, where it lives in the code, and honestly how finished it is.

- Jake has no coding experience. Explain things in plain words and never hand him terminal commands. Do the repo, build and release work yourself.
- Jake's exact words are not stored in the repository. This list is rebuilt from `DECISIONS.md` (each section marks "your request" or "your choice" where Jake asked), `CLAUDE.md`, `POLISH_LEDGER.md`, `TESTING.md` and the git history (30 merged pull requests from 2026-09-30 to 2026-10-02, then this file's own session on 2026-10-06). Section numbers like "D12" mean `DECISIONS.md` section 12.
- **When Jake asks for something new, add a row here. When a status changes, change the row.** Keep it true: if something was not checked on his phone, say so.

## Status words

| Status | Meaning |
|---|---|
| DONE | Built, and tests or rendered screenshots in the cloud session check it. |
| DONE, PHONE CHECK | Built and checked in the cloud, but only Jake's real phone (Galaxy S25 FE) can confirm how it behaves. Almost everything touching Bluetooth, touch feel, system windows or video is here. `TESTING.md` has the steps to check each one. |
| PARTLY | Some of it is built. The notes say what is missing. |
| NOT BUILT | Deliberately not done. The notes say why. |
| IMPOSSIBLE | Android does not allow it. The notes say what was done instead. |

---

## 1. Identity and ground rules

| Request | Status | Where | Notes |
|---|---|---|---|
| Make it Jake's own app, named "pro" (lowercase, his choice; first called Glint) | DONE | strings, launcher label, `.github/workflows/glint-apk.yml` (`pro.apk`, `pro-preview.apk`) | D1, D22. Internal names stay (`Glint*` classes, app ID `io.github.jbyjre.glint`, package `me.kavishdevar.librepods`): changing the ID would stop updates installing over the old app. |
| Keep LibrePods' own AirPods pictures and 3D clips, not drawn artwork | DONE | `res/drawable-nodpi/airpods_pro_2*`, `res/raw*/connected.mp4`, `island.mp4`, `presentation/glint/PodsVideo.kt` | D1. Do not replace with drawn art. Caveat in D1: the pictures look like Apple renders and are not licensed to us. |
| No visible LibrePods mentions, no agreement step, no paywall | DONE | `billing/FOSSBillingProvider.kt` (everything included), onboarding | D1, D10. Open question left for Jake: an optional "Based on LibrePods" line in About (whether the licence needs one is not verified). |
| Liquid Glass look throughout | DONE, PHONE CHECK | `presentation/glint/Glass*.kt`, `GlassTilt.kt`, `overlays/` | D5, D10, D13, D15, D22, D33. The in-app glass really refracts. Over other apps Android never hands over their pixels (IMPOSSIBLE), so overlays use real system blur plus drawn light. |
| Build and download through GitHub (no terminal for Jake) | DONE | `.github/workflows/glint-apk.yml`, `deps-snapshot.yml` | D2. Signing key lives in the Actions cache, never in the repository. |
| Plain language; verify facts; mark anything unchecked | Standing rule | `CLAUDE.md` | |
| Merge finished work into `main` yourself; never subscribe to PR or CI updates | Standing rule | `CLAUDE.md` | |

## 2. Connecting and staying alive

| Request | Status | Where | Notes |
|---|---|---|---|
| Fix "AirPods Pro 3 connected but not detected" on the S25 FE; retries | DONE, PHONE CHECK | `bluetooth/AirPodsDetection.kt`, `ReconnectPolicy.kt`, `services/AirPodsService.kt` | D3. Whether Samsung's One UI 9 includes Google's Bluetooth fix is not verified. The app shows the live truth in words. |
| Survive Samsung killing background apps | DONE, PHONE CHECK | `utils/CompanionLink.kt`, `services/CompanionPresenceService.kt`, `receivers/BootReceiver.kt` | D4. |
| Audit AirPods Pro 3 features and the protocol decoding | DONE | `data/Packets.kt`, `AirPodsProtocolTest` | D8, D9. Left open: Pro 3 Bluetooth product ID, unverified radio adverts. |
| Use Beats Solo 4 (and any other headphones), picked once and kept | DONE, PHONE CHECK | `services/DeviceChoice.kt`, `HeadphoneLink.kt`, `bluetooth/HeadphoneBeacon.kt`, `screens/HeadphonesScreen.kt`, `DevicesScreen.kt` | D36. Whether the Solo 4 reports its battery to Android is not verified. |
| Work without AirPods or Beats connected; become a general phone app | DONE (section 8) | `screens/PhoneScreen.kt`, `components/ThisPhoneCard.kt`, `services/SoundSource.kt`, `PhoneStatus.kt` | D37. The Dynamic Island, sounds, phone battery and clock already needed no headphones; This phone gathers them. Most other pages (listening modes, hearing, heart rate) are still AirPods-only because they need the AirPods' own protocol. |

## 3. AirPods features

| Request | Status | Where | Notes |
|---|---|---|---|
| Heart rate from the AirPods Pro 3 sensor, recorder, head gestures, change confirmation | DONE, PHONE CHECK | `services/HeartRate.kt`, `bluetooth/SensorProto.kt`, `audio/AirPodsRecorder.kt`, `services/CommandFeedback.kt` | D11. Whether the S25 FE lets pro reach the sensor is not verified. |
| Heart: first 3 s dropped, history of every session, GitHub backup into pro3 (private), what the number means, battery pace, background measuring | DONE, PHONE CHECK | `services/HeartHistory.kt`, `HeartBackup.kt`, `HeartInsights` (in `HeartRate.kt`), `screens/HeartHistoryScreen.kt` | D14 to D16, D22. Backup refuses a public repository, so pro3 must stay private. |
| Share live heart rate (Bluetooth sensor, web address, automation broadcast) | DONE, PHONE CHECK | `services/HeartLink.kt`, `screens/HeartShareScreen.kt` | D14. |
| Share heart rate on Wi-Fi (a small web page other devices open) | NOT BUILT | none | D14. The cloud session's safety setting blocked it. Only build it if Jake explicitly asks. |
| Share heart rate with Jake's other Claude apps | NOT BUILT | none | D12. Idea only: needs the target apps decided first. |
| Heart that tells the truth (Live, Starting, Resting, No signal, Blocked, Off) | DONE, PHONE CHECK | `services/HeartView.kt`, `overlays/Island.kt` | D31. |
| Hearing Protection that works and says when it can't | DONE | `screens/HearingProtectionScreen.kt`, `services/VolumeGuard.kt` | D16, D17. |
| Conversation Awareness gives music back at the right moment | DONE, PHONE CHECK | `utils/ConversationTiming.kt` | D27. |
| Battery time left | DONE | `services/BatteryEstimator.kt`, `components/BatteryTimeLeft.kt` | D10. Real-world accuracy not verified. Jake asked it off the opened island (D21); it stays on the main page. |

## 4. App icon

| Request | Status | Where | Notes |
|---|---|---|---|
| Icon with no AirPods or case, no pink, black / white / gray, smooth, abstract | DONE | `res/drawable-v24/ic_launcher*`, `theme/AppIcon.kt`, `components/AppIconPicker.kt` | D17 to D19. Glint's ring around a glass pearl. Black (default), White, Graphite chosen in Settings > App icon. |

## 5. Dynamic Island (the pill around the camera) and mini island (the pop-ups)

Names are Jake's: the camera pill is the **Dynamic Island** (code: `MiniIsland*`); the pop-ups are the **mini island** (code: `Island*`, `IslandController`).

| Request | Status | Where | Notes |
|---|---|---|---|
| One really small thing around the top camera that shows music is playing | DONE, PHONE CHECK | `overlays/MiniIsland.kt`, `services/MiniIslandRules.kt` | D23. |
| Stays up with AirPods connected; pop-ups grow out of it and shrink back | DONE, PHONE CHECK | `overlays/MiniIsland.kt`, `Island.kt`, `GlintOverlays.kt` | D26, D28. |
| Works on every kind of phone (punch-hole, notch, corner camera, none), rotation, folding, big text | DONE, PHONE CHECK | `MiniIslandRules.pickCamera`, `IslandHandoverTest` | D25, D34. |
| Opened island: small, just play/pause, no song bar, no time-left line, L/R/case rings, a quiet heart chip that morphs out of play/pause | DONE, PHONE CHECK | `overlays/Island.kt` | D19 to D24. |
| Taps (1 open, 2 play/pause, 3 next), swipe, hold, pull down | DONE, PHONE CHECK | `services/IslandGestures.kt`, `IslandAccess.kt`, `src/foss/AndroidManifest.xml` | D28, D30. Needs the "pro Dynamic Island" accessibility switch, otherwise Android gives the camera area's taps to the status bar. One UI's layer order is not verified. |
| Subtle look when idle with AirPods | DONE | `MiniIsland.kt` (glow, tone) | D27. |
| Choose what it shows in each situation, what each touch does, size / width / glow, live preview, reset | DONE | `services/IslandLook.kt`, `screens/IslandStudio.kt`, `IslandSettingsScreen.kt` | D32. |
| Sound bars that follow the real music; always-on pill | DONE, PHONE CHECK | `services/MusicPulse.kt`, `PREF_MINI_ANYTIME` | `TESTING.md` check 2: do the bars still follow after leaving pro? |
| Glass that catches the light and answers the finger | DONE, PHONE CHECK | `glint/GlassTilt.kt`, `Glass.kt` | D13, D15, D33. |

## 6. Settings and look

| Request | Status | Where | Notes |
|---|---|---|---|
| More pictures, less text; rows that don't crowd | DONE | `glint/RowIcons.kt`, `components/StyledListItem.kt` | D12, D13, D22. |
| Tactile and springy motion, light haptics, light/dark reveal | DONE, PHONE CHECK | `glint/GlintMotion.kt`, `theme/ThemeReveal.kt`, `navigation/AppNavGraph.kt` | D22. |
| AirPods name row moved into Settings | DONE | `screens/AppSettingsScreen.kt` | D23. |
| Polish pass: visible slider knobs, clean titles, matching icons | DONE | commit 3514df8 | |

## 7. This session (2026-10-06): any sound, less empty, more options, phone-first, READMEs

Jake's words, in short: the Dynamic Island should work for **any noise**, even a single sound, and show **which app** made it (WhatsApp shows WhatsApp). It should look **less empty** when nothing plays, have **more options**, look smoother and cleaner, work **without AirPods or Beats**, and the app should start moving toward being a **multi-purpose phone app**. Write **more READMEs** so AI sessions have better context, including this list. **Find bugs.**

| Request | Status | Where | Notes |
|---|---|---|---|
| Pop for any sound, even one | DONE, PHONE CHECK | `services/SoundSource.kt`, `SoundRules.kt`, `MiniIslandRules.kt`, `overlays/MiniIsland.kt` | Listens to Android's playback events (no permission needed). Every kind pops it except interface clicks and a screen reader's speech. A half-second ding stays up 2.2 / 3.8 / 6.5 s (Short / Normal / Long). Tests: `SoundRulesTest`, `SoundSourceTest`. Which sound type WhatsApp uses for each of its sounds is not verified. |
| Show the app's icon (WhatsApp shows WhatsApp) | DONE, PHONE CHECK | `SoundSource.attribute`, `slot App` in `MiniIsland.kt` | Android only says what *kind* of sound plays, never which app (checked in the SDK and Android's source). So the app is found from clues: the music app's session, an app whose notification arrived a moment ago (both need Notification access), or the app on screen (needs the "pro Dynamic Island" accessibility switch). With none, a symbol for the kind of sound shows (bell, speaker, handset, clock, microphone). Settings > Islands > Sounds shows which clues are on and what was heard lately. |
| Less empty when nothing plays | DONE | `IslandLook.DEFAULT_SLOTS` (Rest) | Nothing on now shows the phone's battery ring and the time. Both are Look options (slots Phone battery, Clock), as are App icon and Title. |
| More ways to change how it looks | DONE | `IslandLook.kt`, `IslandStudio.kt`, `SoundsSection.kt` | New Sounds situation, three new slots, "Colour of bars and rings", sound switches (any sound, app icons, how long), per-app switches in "Heard lately". |
| Smoother and cleaner | DONE, PHONE CHECK | `MiniIsland.kt`, `MiniIslandRules.nextCheck` | Sounds reuse the pill's existing spring morph and cross-fade. Fixed the three problems below. |
| Works without AirPods or Beats | DONE, PHONE CHECK | `PhoneScreen.kt`, `ThisPhoneCard.kt` | The background service already starts at boot without any headphones. New This phone page (Settings > This phone, and a card on the main page) holds the live island, phone battery, sound settings and recent sounds. |
| Start becoming a multi-purpose phone app | DONE (section 8) | as above | Section 8 made the phone the main tab. Remaining ideas: `docs/ROADMAP_PHONE.md`. |
| More READMEs for AI sessions, including this list | DONE | this file, `README.md`, `docs/README.md`, `android/README.md`, folder READMEs | Update them when you change what they describe. |
| Find bugs | DONE | see below | |

**Bugs found and fixed this session**

1. **A timer that could spin without end.** The pill rechecked itself "when the 30 s of paused music run out", with a wait of (30 s minus time paused) plus 0.1 s. Once the 30 s were over that wait was zero or negative, so the recheck ran again at once, scheduled itself again, and so on. With "Always on" (the default) the pill stays wanted, so this began 30 s after any music paused. Found by reading the code, **not reproduced on a phone**. Fixed by `MiniIslandRules.nextCheck` (never zero or negative), with a regression test (`nextLookIsNeverZeroOrNegative`).
2. **A half-second blip left a "Paused" pill for 30 s.** Any short musical sound (a game effect) counted as "music played". Now music must play 1.5 s to count (`MiniIslandRules.PlayTracker`); a shorter one shows as a sound instead, and it no longer restarts the 30 seconds of an earlier real pause.
3. **Other apps' names and icons could not be looked up.** Android hides other apps from an app unless it declares what it needs. Added `<queries>` for launchable apps and the home screen to the manifest (it also makes the song app's name work for apps outside the built-in list).
4. **A screen reader would have popped the island on every spoken word.** TalkBack's speech is now ignored.

## 8. This session (2026-10-07): a Dynamic Island app first, AirPods second

Jake's words, in short: change the purpose of the app. The **Dynamic Island is the main thing**, the AirPods a secondary feature. Tapping the island shouldn't always pull up **music**: make it useful and interesting, **like an iPhone's**. Keep it on permanently, but he already has the **time** in the status bar, so show the **icon of the app he's using** (Spotify, Claude), and show the **home screen** in a way that isn't bland. Give the island **its own tab** (next to the Beats/AirPods one) and make the **main tab about the phone in general**. **Find bugs**, make behaviour **smarter**, go the extra mile. Use the context-efficient, frontend-design and Liquid Glass skills; **no agents**. Details: `DECISIONS.md` section 38.

| Request | Status | Where | Notes |
|---|---|---|---|
| The island shows the app you're using (Spotify shows Spotify) | DONE, PHONE CHECK | `services/ScreenApp.kt`, slot `Screen` in `overlays/MiniIsland.kt` | Needs the "pro Dynamic Island" accessibility switch (the only way Android tells an app which app is open). Only real app screens count; toasts, bubbles, the shade and the keyboard don't. Icon springs in on each app switch. Whether One UI sends these events as standard Android does: not verified. |
| Home screen shown in a non-bland way | DONE, PHONE CHECK | slots `Home`, `Date` | Four tiles in the wallpaper's own colours and a tiny calendar page (red weekday, day number). |
| No time on the island by default (it's in the status bar) | DONE | `IslandLook.DEFAULT_SLOTS` | The Clock slot is still offered in Look, just never a default. |
| Lock screen | DONE, PHONE CHECK | slot `Lock`, `ScreenApp.Place.Locked` | A padlock that springs open as you unlock. Extra, not asked. |
| A tap doesn't always pull up music; make it useful like an iPhone | DONE, PHONE CHECK | `overlays/GlancePanel.kt`, `services/GlanceRules.kt`, `PhoneControls.kt`, `MiniIslandRules.tapOpens` | With nothing playing a tap opens the glance: the app and date, what's live (timer, charging, headphones, low battery, next alarm) and Torch, Timer, Sound/Vibrate, Capture, Lock. Music still opens the music island when music is actually playing or just paused. |
| A timer that lives on the island (iPhone-style Live Activity) | DONE, PHONE CHECK | `services/IslandTimer.kt` | Extra. Rings with the alarm sound until stopped (max 1 min); exact alarm granted at install in the GitHub build (not verified on One UI 9). |
| Charging moment, hide in chosen apps, Capture without the island in the picture | DONE, PHONE CHECK | `MiniMoment`, `PREF_HIDE_IN`, `GlintOverlays.capturing` | Extras from `docs/ROADMAP_PHONE.md` and found while building. |
| The island gets its own tab; the main tab is about the phone | DONE | `navigation/Tabs.kt`, `screens/PhoneScreen.kt`, `IslandSettingsScreen.kt` | Liquid Glass tab bar: Phone (opens first), Island, AirPods/Beats (named after the chosen device). Back on Island/AirPods goes to Phone. |
| Find bugs, smarter behaviour | DONE | DECISIONS 38 | Sounds were credited to the wrong app after a toast from another app (fixed); an empty music player on tap (fixed); the island would have appeared in its own screenshots (fixed). |
| Use the three skills; no agents | DONE | | Context-efficient (checklist, verified facts, no agents or polling), frontend-design (one direction: calm black/white/graphite with orange only for timers and green only for charging), Liquid Glass (real refraction where Android allows it, honest about overlays). |

## Not done or not verifiable from the cloud

- Everything marked PHONE CHECK. `TESTING.md` lists the steps.
- Real refraction of other apps behind the island: IMPOSSIBLE on Android (D5, D33).
- A Wi-Fi heart-rate page, and heart data to Jake's other apps: NOT BUILT (above).
