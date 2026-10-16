# Jake's requests, and how well each one is applied

**Read this first** when you start work on pro (the app is shown to Jake as "pro", lowercase; code names still say Glint). It lists what Jake has asked for, where it lives in the code, and honestly how finished it is.

- Jake has no coding experience. Explain things in plain words and never hand him terminal commands. Do the repo, build and release work yourself.
- Jake's exact words are not stored in the repository. This list is rebuilt from `DECISIONS.md` (each section marks "your request" or "your choice" where Jake asked), `CLAUDE.md`, `POLISH_LEDGER.md`, `TESTING.md` and the git history (30 merged pull requests from 2026-09-30 to 2026-10-02, then this file's own session on 2026-10-06). Section numbers like "D12" mean `DECISIONS.md` section 12.
- **When Jake asks for something new, add a row here. When a status changes, change the row.** Keep it true: if something was not checked on his phone, say so.

## Which phone

Jake has two phones in mind: on 2026-10-15 he asked for pro tuned for the **Samsung Galaxy S26 FE** and the **Google Pixel 6 Pro**, chosen in Settings > Your phone (Automatic by default; section 17). Earlier notes said Galaxy S25 FE, then "Pixel 6" (2026-10-06). **Which one he uses day to day is not verified.** Checked specs (DECISIONS 49): the S26 FE has a flat 6.7-inch FHD+ 120 Hz screen with a centred punch-hole, Exynos 2500, 4,900 mAh, One UI 9 (Android 17); the Pixel 6 Pro a 6.7-inch QHD+ LTPO screen (10 to 120 Hz) curved into its sides, a centred hole-punch, Tensor, 5,003 mAh. pro takes the camera's place and size from what the phone reports, so the island fits either. Whether the AirPods' own controls work depends on the phone's Bluetooth (see `utils/RootlessSupport.kt`), which is separate.

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
| Make it as close as possible to Apple's real Dynamic Island, keep every feature (2026-10-09) | DONE, PHONE CHECK | `services/IslandSpec.kt`, `overlays/Island.kt`, `overlays/MiniIsland.kt`, Settings > Island > Style | D43. iPhone style (default): black in light and dark, Apple's key line in dark mode, pop-ups open out of the pill around the camera (needs the "pro Dynamic Island" switch), Apple's width and 44 dp corner, a detached circle for a timer beside music, blur morph, rounded-square cover. Glass style keeps the old look. Springs and the detached gap are by eye (Apple doesn't publish them). |
| Formatting off everywhere: make everything look and fit well; functionality audit (2026-10-09) | DONE | `theme/Type.kt`, `StyledScaffold.kt`, `StyledList.kt` (`footnote`), `res/values/strings.xml`, `RowIcons.kt`, `MiniIsland.kt` | D44. Text spacing in every row, titles clear of buttons, consistent wording and full stops, missing row pictures, island glow inside the pill; fixed a frozen timer in the separate circle. |

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

## 8. Same day, second round: freezes, swipe, messages, smarter, more yours

Jake's message, as separate asks (every one has a row below):

1. Fix the app randomly saying "pro isn't responding" (he has to close it and reopen it).
2. Make a list of what he has asked for, and do all of it systematically and precisely.
3. Do a full run-through of the app and find several ways to make it smarter (not one), that make his life easier, smooth and interactive with the phone.
4. Let him swipe the opened Dynamic Island (for example the new song's name) back in quickly to its own place.
5. Let him customize things in the app, such as how it looks.
6. Make the Dynamic Island look smooth for all apps: a text message shows in a nice smooth way that doesn't clash with the notifications the phone already shows.
7. It must look smooth and comfortable (not awkward, dry or unfinished) and reflect the apps on his phone (he wrote Pixel 6).
8. It must work reliably with all apps, not only for AirPods.
9. Make the app's behaviour smarter and look better; go the extra mile; finish everything.

| Request | Status | Where | Notes |
|---|---|---|---|
| 1. Fix "pro isn't responding" | DONE, PHONE CHECK | `MiniIslandRules.nextCheck`, `RefreshGuard`, `utils/OffMain.kt`, `bluetooth/AACPManager.kt`, `AirPodsViewModel.loadATT`, `AirPodsService`, `MusicPulse`, `services/FreezeReport.kt` | Android shows that message when the main thread is stuck for about 5 seconds. Causes found and fixed, each by reading the code: (a) the island's recheck timer looped without end 30 s after music paused (reproduced in a test: the old pattern made 200,001 checks without giving the main thread a single turn); (b) opening the main page waited up to 6 s for the AirPods (`loadATT`); (c) a phone call ringing ran Bluetooth writes with `runBlocking` on the main thread; (d) every command to the AirPods was written to the Bluetooth link from the calling thread, often the main one; (e) start-up asked a root shell and waited on the main thread; (f) the music-level listener was created on, and reported to, the main thread. All now run off it. Not reproduced on a phone, so which of these was behind Jake's freezes is **not verified**; (a) matches "random, outside the app, fixed by reopening" best. If it ever happens again, Android's own record of it is saved: the main page shows "pro stopped earlier", Troubleshooting > Freezes and crashes has Copy details to paste to Claude. |
| 2. List of everything asked | DONE | this file, section 8 and the chat reply | |
| 3. Full run-through; several smarter behaviours | DONE | see "Smarter behaviours" below | Eight, each with tests. |
| 4. Swipe the opened island back in | DONE | `IslandGestures.Kind.SwipeUp`, `MiniIslandHost` (`collapseTick`), `MomentsUiTest` | Swipe up while the song's name is out: it tucks back at once. Swipe up on a message, sound or battery moment: it goes away. Also: how long the name stays (Short 1.8 s, Normal 3.2 s, Long 5 s). |
| 5. Customize in the app | DONE | `IslandStudio.kt`, `SoundsSection.kt`, `IslandSettingsScreen.kt`, `AppsScreen.kt` | New: a Messages look you can edit like the others, message options, how long the song name stays, per-app switches for every installed app, phone moments on/off. Earlier options kept. |
| 6. Messages show smoothly, no clash with phone notifications | DONE, PHONE CHECK | `SoundSource.notificationPosted`, `MiniIsland.kt` (Messages look), `MediaAccessService` | A text or chat message shows the app's icon and its name (or the sender, if switched on) beside the camera. It stays inside the status-bar strip: the phone's own heads-up notification drops from below it, so they never overlap. It follows the phone: nothing for silent or muted chats, nothing for what Do Not Disturb holds back, nothing for ongoing notifications, and nothing for the app you're using. |
| 7. Smooth, comfortable, reflects my apps | DONE, PHONE CHECK | `AppsScreen.kt`, `SoundSource.appInfo`, `MiniIsland.kt` | Icons and names come from the apps actually installed (nothing is hard-coded), the ring and glow take each icon's own colour, a little bounce on arrival, names end in an ellipsis instead of being cut, and the window grows before the name slides in so nothing flickers. |
| 8. Works for all apps, not only AirPods | DONE | everything in sections 7 and 8 | None of it needs headphones. |
| 9. Smarter, better looking, extra mile | DONE | below | |

**Smarter behaviours added in this round**

1. Message moments with the app's icon (and the sender, only if you switch that on).
2. The island follows the phone: silent, muted and Do Not Disturb notifications stay quiet.
3. It skips the app you're using (no pop for each message in the chat you're in).
4. One pop per message: duplicates and "alert once" updates are ignored, and a burst doesn't re-announce itself.
5. Phone moments: plugged in, full, 20% and 10%, with the battery ring.
6. Per-app control for every app on the phone (Apps page) and from "Heard lately".
7. Swipe up puts away whatever is out.
8. Self-protection: a limit on how often the island may check itself, blocking work moved off the main thread, and a freeze reporter that records the real cause.

**Bugs found and fixed in this round** (besides the freeze causes above)

- The Messages pill was squeezed to the normal width and the name overlapped the camera: found in a screenshot, fixed (`MiniGeometry.widthFor` now takes the limit).
- A short blip during paused music restarted the 30 second pause (`PlayTracker`, round one).
- Settings said "Samsung background setup" on any phone: now only on Samsung.

## 9. Later the same session (2026-10-07): a Dynamic Island app first, AirPods second

Jake's words, in short: change the purpose of the app. The **Dynamic Island is the main thing**, the AirPods a secondary feature. Tapping the island shouldn't always pull up **music**: make it useful and interesting, **like an iPhone's**. Keep it on permanently, but he already has the **time** in the status bar, so show the **icon of the app he's using** (Spotify, Claude), and show the **home screen** in a way that isn't bland. Give the island **its own tab** (next to the Beats/AirPods one) and make the **main tab about the phone in general**. **Find bugs**, make behaviour **smarter**, go the extra mile. Use the context-efficient, frontend-design and Liquid Glass skills; **no agents**. Details: `DECISIONS.md` section 39.

| Request | Status | Where | Notes |
|---|---|---|---|
| The island shows the app you're using (Spotify shows Spotify) | DONE, PHONE CHECK | `services/ScreenApp.kt`, slot `Screen` in `overlays/MiniIsland.kt` | Needs the "pro Dynamic Island" accessibility switch (the only way Android tells an app which app is open). Only real app screens count; toasts, bubbles, the shade and the keyboard don't. Icon springs in on each app switch. Whether One UI sends these events as standard Android does: not verified. |
| Home screen shown in a non-bland way | DONE, PHONE CHECK | slots `Home`, `Date` | Four tiles in the wallpaper's own colours and a tiny calendar page (red weekday, day number). |
| No time on the island by default (it's in the status bar) | DONE | `IslandLook.DEFAULT_SLOTS` | The Clock slot is still offered in Look, just never a default. |
| Lock screen | DONE, PHONE CHECK | slot `Lock`, `ScreenApp.Place.Locked` | A padlock that springs open as you unlock. Extra, not asked. |
| A tap doesn't always pull up music; make it useful like an iPhone | DONE, PHONE CHECK | `overlays/GlancePanel.kt`, `services/GlanceRules.kt`, `PhoneControls.kt`, `MiniIslandRules.tapOpens` | With nothing playing a tap opens the glance: the app and date, what's live (timer, charging, headphones, low battery, next alarm) and Torch, Timer, Sound/Vibrate, Capture, Lock. Music still opens the music island when music is actually playing or just paused. |
| A timer that lives on the island (iPhone-style Live Activity) | DONE, PHONE CHECK | `services/IslandTimer.kt` | Extra. Rings with the alarm sound until stopped (max 1 min); exact alarm granted at install in the GitHub build (not verified on One UI 9). |
| Battery moments with words, hide in chosen apps, Capture without the island in the picture | DONE, PHONE CHECK | `MiniMoment`, `PREF_HIDE_IN`, `GlintOverlays.capturing` | Extras from `docs/ROADMAP_PHONE.md` and found while building. |
| The island gets its own tab; the main tab is about the phone | DONE | `navigation/Tabs.kt`, `screens/PhoneScreen.kt`, `IslandSettingsScreen.kt` | Liquid Glass tab bar: Phone (opens first), Island, AirPods/Beats (named after the chosen device). Back on Island/AirPods goes to Phone. |
| Find bugs, smarter behaviour | DONE | DECISIONS 39 | Sounds were credited to the wrong app after a toast from another app (fixed); an empty music player on tap (fixed); the island would have appeared in its own screenshots (fixed). |
| Second pass: check every part and make sure it all works | DONE | `PhoneTabsUiTest`, DECISIONS 39 | Found and fixed: tabs that didn't change the page, a timer that could stay silent after Android closed pro, the app icon waiting for the next app switch. Tests now press the tabs, Back, the glance's Timer and Sound, and fire the timer's alarm on a closed pro. |
| The actual app icon, not a fake | DONE | `SoundSource.appInfo` (launcher icon), `ScreenApp.showApp`, `SoundSource.exampleApp`, test `theIconIsTheAppsRealIconNotADrawing` | Real icons on the phone; the lettered squares are only in cloud test pictures. Previews now use a real app from the phone too. Themed icons and icon packs can't be read by other apps (not verified on One UI). |
| Use the three skills; no agents | DONE | | Context-efficient (checklist, verified facts, no agents or polling), frontend-design (one direction: calm black/white/graphite with orange only for timers and green only for charging), Liquid Glass (real refraction where Android allows it, honest about overlays). |

## 10. 2026-10-08: "the app isn't recognizing what app it is on"

| Request | Status | Where | Notes |
|---|---|---|---|
| Recognise the app in front reliably; full audit, fix bugs | DONE, PHONE CHECK | `ScreenApp.look`, `ScreenRules.front`, DECISIONS 40 | Now asks Android which app window is in front after every change (not only the window messages); fixed the stale app after unlocking to the home screen; Island tab shows what pro sees. Needs the "pro Dynamic Island" switch on. Music playing shows the music, not the app. |

## 11. 2026-10-08, later: full audit, UI and UX pass

Jake's words, in short: a full functionality audit of the whole app, find and fix every bug, make sure it works the way he asked in past requests; it must run smoothly, look good and right, with the text format right; lots of UI and UX care; context-efficient and Liquid Glass skills; no agents. Details: `DECISIONS.md` section 41.

| Request | Status | Where | Notes |
|---|---|---|---|
| Full audit, fix bugs | DONE, PHONE CHECK | DECISIONS 41 | Five bugs fixed (skip-the-app used the old signal, repeated "Charging" pops with battery protection, glance Saver button, Phone tab buttons with the island off, Island tab "Apps" row). All 446 tests, lint and the release build pass. |
| Looks right, text format right | DONE | DECISIONS 41 | One permission name everywhere, consistent full stops and sentence case, clearer Beats button rows, "…" instead of "...", setup text matches the island-first app. |
| Lots of UI and UX | DONE | `glint/RowIcons.kt`, `IslandStudio.kt`, `PhoneScreen.kt` | Every "Pops up when" row has a picture; scrolling choice rows fade at the edge; buttons that can't work yet lead to what fixes them. |
| Earlier requests | DONE or PHONE CHECK | sections 1 to 10 | Nothing was left PARTLY. What only a phone can prove is listed in `TESTING.md`. |
| Skills; no agents | DONE | | Context-efficient (checklist, verified facts, no agents or polling), Liquid Glass (glass tab bar and tiles kept on the existing real-refraction path). |

## 12. 2026-10-08: the padlock stays after unlocking

| Request | Status | Where | Notes |
|---|---|---|---|
| After unlocking, show the app, not just a lock | DONE, PHONE CHECK | `ScreenApp.reconcile`, `ScreenRules.lockNow`, DECISIONS 42 | Lock state now follows the phone itself, not the order Android's messages arrive in; a stuck padlock heals on the next screen change. |
| App icon recognition better and more accurate | DONE, PHONE CHECK | `SoundRules.homeApps`, `ScreenApp.unlock` | Settings no longer mistaken for the home screen; the app you unlock into is looked up while the padlock opens. Needs the "pro Dynamic Island" switch on. |
| Cover everything, smooth, looks great | DONE | | All 451 tests, lint and the release build pass. |

## 13. 2026-10-10: smoother, more Apple, covers going over the island

Jake's words, in short: he likes how the app looks, but the Dynamic Island's movement, how it opens and its smart behaviour could all be better; it doesn't reflect the Liquid Glass and Apple look he's after. Song covers sometimes go over the island and look weird, and the formatting with the other things looks weird. The way it opens looks dysfunctional. Fix it ASAP, make it smoother, more accurate and more appealing, more like Apple's, with more function and more customization; research how Apple's real one works; go the extra mile. Skills: frontend-design, context-efficient, Liquid Glass. Details: `DECISIONS.md` section 45.

| Request | Status | Where | Notes |
|---|---|---|---|
| Covers going over the island / weird formatting | DONE, PHONE CHECK | `overlays/MiniIsland.kt` (`drawSlot`, the clip, `grow`, `endInset`), `IslandSpec.roundedSquareHalf`, `insetFromCorner`, `centreSquare` | Four causes found in pictures and fixed: contents drawn full size while the pill was still growing out of the camera (they sat on the camera and stuck out); the square cover and its progress line slightly bigger than the pill's round end; the cover jammed into the corner when the pill widens for a song's name (cut by the corner, before that poking out); wide covers squashed. Also the L/R/case rings and the title in a custom look ran into the curve, and the pop-up's battery ring hung over the edge. Everything inside now stays inside the black shape and off the camera. |
| The way it opens looks dysfunctional | DONE, PHONE CHECK | `overlays/Island.kt` (`IslandLayout`), `overlays/MusicIsland.kt` | Opened content was drawn full size inside a shape still growing, so it was sliced through for a few frames; it now grows with the shape. Tapping the island with music opened the AirPods page (or, without AirPods, a wide in-between pill); in the iPhone style it now opens straight into the music. |
| More like Apple's (researched) | DONE, PHONE CHECK | `MusicIsland.kt`, `IslandSpec.kt` | Apple's guidelines (read 2026-10-10): concentric placement so nothing pokes into the curve; the expanded view is an enlarged compact one, elements moving to their new places rather than vanishing and coming back; content wrapped tightly round the camera; 84 to 160 pt tall; medium-weight text. The opened music island is Apple's Now Playing layout and its cover and bars **grow out of the pill's own** and fly back on close. |
| Smoother | DONE, PHONE CHECK | `services/IslandMotion.kt`, `IslandHost` springs | Closing used a spring that overshot, so the shape dipped smaller than the pill and popped back: closing is now bounce-free. No blink when the pill takes over again. |
| Liquid Glass look | DONE, PHONE CHECK | `overlays/IslandEdge.kt`, `IslandSpec.Edge` | New Edge choice for the iPhone style: Liquid Glass, a rim lit from above with a faint sheen, in light and dark, swinging with tilt. Apple's own island stays opaque black (and Android never shows other apps' pixels to pro), so the island itself is not see-through: not possible over other apps. |
| More function | DONE, PHONE CHECK | `MusicIsland.kt`, `NowPlaying.seekTo` | Back / play-pause / next always on the opened music island; tap the cover or name to open the music app; optional song bar you can drag to move in the song (when the app allows it); the heart and the headphones' battery (tap for the AirPods page) on the music page. |
| More customization | DONE | `IslandSettingsScreen.kt` | Settings > Island: Edge (Key line / Liquid Glass), Motion (Smooth / Snappy / Bouncy), Music > Song bar when opened (off by default: Jake earlier chose no song bar), Try it > Music, opened. |
| Skills; no agents | DONE | | Frontend-design (one direction: Apple's black island, white type at medium weight and up, colour only from the cover), Liquid Glass (rim and sheen, honest about no refraction over other apps), context-efficient (checklist, verified facts, no agents or polling). |

## 14. 2026-10-11: nine fixes, then a second round (buttons, per-app, bulb, Beats model)

Jake's first message, as nine asks (Details: `DECISIONS.md` section 46), then a second message during the work. Skills used: context-efficient, frontend-design, Liquid Glass; no agents.

| Request | Status | Where | Notes |
|---|---|---|---|
| 1. Song covers that fit the island (no square corners poking out) | DONE, PHONE CHECK | `overlays/MiniIsland.kt` (`drawSlot` Cover), `MusicIsland.kt`, `Island.kt` (`artCorner`) | Round everywhere: a circle on the pill's round end (the one shape that fits it exactly), in the pop-up, and in the opened island (concentric with its 44 dp corner, which on a 52 dp cover is a circle). The cover keeps its shape all the way between them. |
| 2. AirPods / Beats connection and ANC that work every time | DONE, PHONE CHECK | `services/CommandFeedback.kt`, `bluetooth/AACPManager.kt` (`sendControl`), `services/HeadphoneLink.kt` (`LinkSettle`) | Every mode sender (island, tile, widget, settings) now confirmed; sent again after 1.2 s; every screen put back on the AirPods' real mode after 3 s or a failed send; quick taps step forward instead of repeating; earlier taps' answers no longer show as failures. Beats: a 2.5 s grace before showing a drop, looks over 8 s while connecting, audio-route changes looked at at once. The Solo 4 has no noise cancelling at all (Beats' support page), so there is no mode to switch on it. Also fixed: the AirPods' settings list could crash when two threads touched it. |
| 3. App intelligence (games, music apps, every app) | DONE, PHONE CHECK (changed by round 2) | `services/AppKinds.kt`, `AppActions.kt`, `AppControls.kt`, `LiveActivities.kt`, `overlays/GlancePanel.kt` | Every app gets a kind (known-apps table, makers' prefixes, the app's own Android category, words in its name). In a full-screen game a small ring stays round the camera (tap or hold: the game's card). Round 2 made it personal per app: see below. |
| 4. 3D AirPods and Beats pictures | DONE, PHONE CHECK (Beats replaced in round 2) | `glint/DeviceStage.kt`, `glint/Headphones3D.kt` | AirPods: LibrePods' own renders and clips kept (Jake's earlier choice), now turning with the tilt, lit, on a soft shadow, springing in. Beats: see round 2. |
| 5. A better unlock animation | DONE, PHONE CHECK | `MiniIsland.kt` (`unlockSweep`, `unlockSwell`), `ScreenRules.UNLOCK_SHOW_MS` | The island's part: fluid shackle, a soft light gliding across, a small breath, then the app's icon (1.1 s). Android doesn't let apps change the system's own lock-to-home transition (IMPOSSIBLE). Toned down in round 2 (calmer). |
| 6. Organic morphing, never bulbous | DONE, PHONE CHECK | `services/SmoothCorner.kt`, `IslandMotion.kt`, `glint/Glass.kt` (`smoothRectPath`) | Apple-style continuous corners; the corner grows from the pill's curve (was half the height: a fat capsule half-way); height never bounces more than width and thins while the width overshoots. |
| 7. Sound bars that follow the vocals; a fallback for instrumentals | DONE, PHONE CHECK | `services/PulseShaper.kt`, `MusicPulse.kt` | Singing found by energy and harmonics in 300 to 3,400 Hz: the middle bars follow the voice and its syllables; no voice: the kick ripples out from the middle; nothing to hear: a steady beat. Lyrics: Android gives no app another app's lyrics (IMPOSSIBLE), so it follows the singing itself. |
| 8. No lag switching apps | DONE, PHONE CHECK | `services/ScreenApp.kt`, `SoundSource.kt` (capped cache), `NowPlaying.kt` | First visits worked out off the main thread; a slow answer can't override a newer app; 40 icons kept at most; recent apps' icons ready after a restart; the icon swap settles in about 250 ms. |
| 9. Everything fits; widgets scale | DONE | `glint/Fit.kt`, `GlancePanel.kt` | Found by rendering long names on a 320 dp phone at 130% text: buttons share the row and shrink together; long names scroll once with soft ends (fade only when too long). |
| **Round 2** (Jake's second message) | | | |
| Quick buttons (torch, timer, sound, capture, lock) only on the home screen, never in an app | DONE | `GlancePanel.kt` (`home`) | Also shown when pro can't tell where you are (the accessibility switch off). Away from the home screen the glance is 28 dp shorter (`GLANCE_AWAY_H_DP`). |
| Personal per app, not one rule per type ("if I have Google Maps going") | DONE, PHONE CHECK | `services/LiveActivities.kt`, `GlancePanel.kt` (`LiveCard`) | The app's own live activity (its ongoing notification: a route, a ride, a download, a call) in its own words with its own buttons (Maps' "Exit navigation"); another app's shows meanwhile (the route while in WhatsApp). Games: heat, time played, Mute / Focus / chat as small pills. Music apps: Play, Search, your recent songs from that app. Needs Notification access; Settings > Island > Live from apps. On the lock screen the card shows only the app and its number, never its words. Which apps post what is up to each app (not verified per app; Google Maps posts Live Updates on Android 16). |
| Show Maps on the island the rest of the time | DONE, PHONE CHECK | `MiniIsland.kt` (`liveHere`, slot `Live`) | The iPhone way: the app's picture left of the camera (Maps' own turn arrow), its one number right ("200 m", "4 min", "45%"). A running timer moves to the detached circle. |
| The right side shows nothing half the time | DONE | `IslandLook.Slot.Glance`, `MiniIsland.kt` | Cause: the "Smart glance" drew nothing when it had nothing to say. Now: an app's live number, else the glance, else the phone's battery ring. |
| The music pop-up looks bulbous (drops down, title, pause): expand sideways, naturally | DONE, PHONE CHECK | `MiniIslandRules.size` (`WIDE_EDGE_DP`), `MiniIsland.kt`, `GlintOverlays.showIsland` | The pill stretches toward the screen's corners at its own height: cover and bars slide to the ends, title left of the camera, artist right. Bug found: with headphones connected a new song showed both the stretch and a pop-up dropping below (notes said it was skipped; it wasn't). Fixed. Other pop-ups (connected, low battery) still open as before. |
| Beats: an actual spinning model, not an ugly drawing | DONE, PHONE CHECK | `glint/Headphones3D.kt`, `HeadphonesScreen.kt` (finish dots) | A real 3D model of the Solo 4's shape built in code (Beats' own 3D files aren't ours to use), lit like a product shot, turning once every 16 s; a finger spins it on the headphones page. Matte Black, Slate Blue, Cloud Pink (colours by eye). |
| Smooth, not flashy, still a little bounce; eyes shouldn't travel up and down | DONE, PHONE CHECK | springs in `MiniIsland.kt` (`STRETCH_*`, `APP_SWAP_*`, `UNLOCK_*`), `DeviceStage` | Sideways moments instead of drops; softer light sweep and bloom; pictures barely rise as they arrive; every spring settles with a hint of give. |
| Research Apple's real one, but don't copy what looks bad | DONE | `DECISIONS.md` 46 | Apple's guidelines (read 2026-10-11): two balanced sides of the camera, animate existing elements to new places. Kept; Apple's drop-down expanded music page is kept only for a tap (Jake's own action), not for a new song. |
| Bug hunt and curation | DONE | DECISIONS 46 | Fixed: the double music pop-up; quick taps repeating a mode; modes shown that the AirPods never took; a crash risk in the AirPods' settings list; app actions that Android 14+ would block from the island; a missing settings switch; first app switches waiting on the main thread; an unbounded icon cache; dead code removed. |

## 15. 2026-10-11, third message: the island reflects the app; battery

Jake: "whenever I open a dynamic Island in an app it just says the app name and nothing ... maybe [have] the app reading my screen ... fun ways to make it apply to the app" and "make sure this app isn't going to destroy my battery ... very battery efficient automatically ... as its main setting ... but don't nerf its capability". Details: `DECISIONS.md` section 47.

| Request | Status | Where | Notes |
|---|---|---|---|
| The opened island reflects the app, not just its name | DONE, PHONE CHECK | `services/ScreenInsights.kt` (`ScreenText` rules), `overlays/GlancePanel.kt` (`ScreenCard`) | When you open the island in an app, pro reads that screen once: its headline (the page, the chat, the video), the website you're on, how long an article takes to read, and up to three one-tap things from it (Call a number, Map an address, Open a link, Add a date, Email, Copy a one-time code). The card's edge takes the app's own colour. Settings > Island > Read the screen (on). Never in the background, nothing stored or sent; passwords, typing, banking, payment, password and authenticator apps are never read. |
| Fun ways it applies to the app | DONE, PHONE CHECK | `services/ScreenVoice.kt` | **Listen**: an article read aloud into your AirPods with the phone's own voice (music pauses, carries on after; tap Stop, or "Stop reading aloud" from anywhere). How long you've been in the app ("Browser · open 12 min"). Codes stay on screen after Copy ("Copied"). |
| Found bug: the island's window checks never worked | DONE | `res/xml/app_listener_service_config.xml`, `IslandAccess.kt` | Android only lists windows to an accessibility service that asks to see window content (its own docs, checked in its source 2026-10-11). pro didn't ask, so the "which app is in front" double-check and the "step aside for the notification shade" check always saw nothing. Now asked; Android keeps the switch on across the update (checked in its source). |
| Battery efficient automatically, without losing anything | DONE, PHONE CHECK | `services/PowerPlan.kt` | No setting: always on. Anything you touch or watch move once (opening, morphing, springs, unlock) stays full speed. Things that redraw for hours on their own are paced: the pill's sound bars at 30 a second (they were 60; the music is only heard 20 times a second), the spinning headphones at 30, both full speed while charging. On Battery Saver, at 20% or less, or when the phone is warm: bars 20, the model rests (still spins with a finger), the music listener hears half as often, the glass lightens in the islands too, and a continuous background heart stream rests between minutes. Already so before (checked): everything stops with the screen off; the tilt sensor runs only for seconds after a touch. |

## 16. 2026-10-11, fourth message: On, Music only, Off

Jake, in short: replace the switch that turns the Dynamic Island off with one choice: normally on; only on while music plays and only about music; and an off that's a very small circle round the camera, still there to tap, showing nothing. Fix any bugs found on the way. Context-efficient skill. Details: `DECISIONS.md` section 48.

| Request | Status | Where | Notes |
|---|---|---|---|
| One choice: On / Music only / Off | DONE, PHONE CHECK | `MiniIslandRules.Mode`, `IslandPrefs.mode`, `IslandSettingsScreen.kt` (top of the Island tab) | Replaces the old "Dynamic Island around the camera" switch (switched off, it now means Off). Settings that don't apply in a mode are hidden. The Phone tab's Island tile shows the mode. |
| Music only: on only while music plays, only music | DONE, PHONE CHECK | `MiniIslandRules.wanted`, controller `refresh()` | Up while music plays and 30 s after a pause; cover, bars, song name. No sounds, messages, timers, moments, apps, live activities, "always on". |
| Off: a very small circle round the camera, still tappable, nothing shown | DONE, PHONE CHECK | `MiniGeometry.ringD` / `ringWindow`, `MiniIslandHost(mode)` | 2 dp round the camera; tap = your controls (the glance), hold = open pro, double tap still plays/pauses. Its window is only as wide as the ring, so the status bar around it stays free. |
| Fix bugs found | DONE | DECISIONS 48 | A new song over a full-screen game widened the island over the game; with the ring, a new song showed nothing at all. |

## 17. 2026-10-15: two phone versions, heart spot checks, smoother and lighter on battery

Jake, in short: two versions of the app to choose in Settings, one for the Samsung S26 FE and one for the Google Pixel 6 Pro, with the Dynamic Island and the rest made better for each; smoother and snappier; better battery use that doesn't drain the phone; go through every part of the app (clearer, smarter, nothing redundant); a new heart-rate mode because one scan every 15 minutes looks terrible on the graph, filling the graph with good information without much battery. Details: `DECISIONS.md` section 49.

| Request | Status | Where | Notes |
|---|---|---|---|
| A Galaxy S26 FE version and a Pixel 6 Pro version, chosen in Settings | DONE, PHONE CHECK | `services/PhoneProfile.kt`, `screens/PhoneProfileScreen.kt` (Settings > Your phone) | Automatic (recognises the phone) / Galaxy S26 FE / Pixel 6 Pro. A drawing of the phone's top edge with the island, what changes, and the battery settings that keep pro running. Other phones keep pro's general settings. |
| Dynamic Island better for each phone | DONE, PHONE CHECK | `MiniIslandRules.size` (edgeDp), `IslandGeometry` | Pixel 6 Pro: the stretched island and pop-ups keep 18 dp off the curved sides. S26 FE: flat, 10 dp (room for longer names). Camera place and size always come from the phone itself. |
| Work better for each phone | DONE, PHONE CHECK | `PhoneProfile.Tuning` | Pixel: saves from the first heat level (Tensor runs warm), Unrestricted battery. S26 FE: Unrestricted plus Samsung's Never sleeping apps (pro can't read that list, so the page asks to check it once). |
| Smoother and snappier | DONE, PHONE CHECK | `glint/FrameRate.kt` (`Booster`), `MainActivity` | 120 Hz while you touch and for 2.5 s after, then the phone's own rate (adaptive on the S26 FE, LTPO down to 10 Hz on the Pixel). Springs and morphs stay full speed. |
| Better battery, without draining the phone | DONE, PHONE CHECK | `glint/WhileShown.kt`, `bluetooth/BLEManager.kt`, `PowerPlan`, `GlassBudget` | Found: five pages kept checking settings every second after leaving pro; the beacon scan ran at full power all day, screen off too (now low power with the screen off); widgets were redrawn on every battery change with no widget placed. Duplicated listeners removed. Real savings not measured (no phone in the cloud). |
| New heart-rate mode that looks good on the graph | DONE, PHONE CHECK | `services/HeartPacing.kt` (Smart, Saver), Heart page > Pace | Smart: a ~20 s spot check every 2 min (4 when steady), nonstop while it changes or is 100+, Saver's rhythm when the AirPods are at 20% or less. Saver: a spot every 5 min, same sensor time as the old 1 min in 15, three times the points. The old Balanced setting becomes Smart. |
| Fill the graph with good information | DONE | `HeartInsights.chartPoints`, `HeartChart` | One smooth line through spot checks, each with a faint lowest-to-highest bar; continuous stretches in full; breaks only at real gaps. Older sessions draw better too. Picture `docs/heart-paces-2026-10-15.png`. |
| Go through the app: clearer, smarter, nothing redundant | DONE | DECISIONS 49 | Heart words say "spot checks"; a dead loop, a duplicate battery listener and repeated questions to Android removed; the scan's cleanup timer no longer doubles. Every file was searched for repeating work, timers, sensors, scans, main-thread file reads and widget refreshes, and each hit read; the heart, battery, scan and island-size code was read in full (not all ~62,000 lines word by word). The Dynamic Island's own loops were already well behaved. |

## Not done or not verifiable from the cloud

- Everything marked PHONE CHECK. `TESTING.md` lists the steps.
- Real refraction of other apps behind the island: IMPOSSIBLE on Android (D5, D33).
- A Wi-Fi heart-rate page, and heart data to Jake's other apps: NOT BUILT (above).
