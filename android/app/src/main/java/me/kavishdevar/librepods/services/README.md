# services/

The brains of pro. Files are small and named for what they decide. "Pure" means it has no Android in it and is unit-tested without a phone. Why things are this way: `DECISIONS.md` at the repository root. What Jake asked for: `docs/REQUESTS.md`.

## The Dynamic Island and sound

| File | What it does | Pure? |
|---|---|---|
| `MiniIslandRules.kt` | When the camera pill is wanted, what it is about (Music, Sound, AirPods, Rest), its sizes around any camera, camera picking, how long things linger, `PlayTracker` (when music counts as played), `nextCheck` (the safe "look again" timer) | yes |
| `IslandLook.kt` | What goes left and right of the camera in each situation, size, width, glow, colour; stored in the `settings` preferences under `glint_di_*`; `clockText` | storage only |
| `IslandPrefs.kt` | Every on/off switch for the islands and the sound settings (`PREF_*`), the per-moment pop-up switches, the ignored-apps list | storage only |
| `IslandGestures.kt` | Tap counting, hold, swipe, pull: one recogniser; what each gesture does (configurable) | yes |
| `IslandAccess.kt` | The accessibility service "pro Dynamic Island" (puts the pill above the status bar so taps reach it; reports the app on screen and open system panels) and `PanelRules` | rules yes |
| `SoundRules.kt` | Any-sound rules: usage numbers to kinds, which app made it, notification log, app on screen, linger lengths, recent list, wording | yes |
| `SoundSource.kt` | Listens to every sound the phone plays and publishes `heard` and `recent`; app names and icons (cached); test seams (`usagesChanged`, `roleOverride`, `preview*`, `resetForTest`) | no |
| `NowPlaying.kt` | Music: playing or paused, cover and title (with Notification access), play/pause/skip; also hosts `MediaAccessService` (Notification access, notes which app posted a notification) | partly |
| `MusicPulse.kt` | Sound bar levels from the real music (Android Visualizer on the whole output, four bands; needs the microphone permission, nothing is recorded) | no |
| `PhoneStatus.kt` | The phone's own battery (level, charging) for the Phone battery slot and the This phone page | `parse` yes |
| `MiniIslandRules`, `CardGate.kt` | `CardGate` decides when the bottom "case opened" card may appear | yes |
| `GlintStatus.kt` | One shared status for "what is happening with the AirPods link" | |

## AirPods, headphones and the rest

| File | What it does |
|---|---|
| `AirPodsService.kt` | The foreground service: connection, packets, ear detection, listening modes, notification. **Also starts the pill, `NowPlaying`, `SoundSource` and `PhoneStatus` in `onCreate`, with or without headphones.** Large; search before reading. |
| `AirPodsQSService.kt`, `CompanionPresenceService.kt` | Quick Settings tile; Android wakes pro when the linked AirPods connect |
| `DeviceChoice.kt`, `HeadphoneLink.kt` | Which headphones pro follows (default AirPods) and the live state of non-AirPods ones (Beats Solo 4) |
| `HeartRate.kt`, `HeartView.kt`, `HeartHistory.kt`, `HeartBackup.kt`, `HeartLink.kt` | Heart rate: live reading and insights, the honest state to show, one CSV per session, GitHub backup into `pro3` (branch `heart-backup`), live sharing |
| `BatteryEstimator.kt` | Listening time left |
| `CommandFeedback.kt` | Whether a change reached the AirPods |
| `VolumeGuard.kt` | Hearing protection without root: keeps media volume at or below your limit |
| `AppListenerService.kt` | Unused (commented out in the manifest) |

## Where state lives

- Settings: the `settings` SharedPreferences (`IslandPrefs.prefs(context)`). Island looks use `glint_di_*`, sound switches `glint_mini_any_sound`, `glint_sound_icons`, `glint_sound_linger`, `glint_sound_ignored`.
- Live state: `StateFlow`s on `object`s (`NowPlaying.state`, `SoundSource.heard`, `PhoneStatus.state`, `GlintStatus.link`). The pill collects them.
- Never store a new setting without a default that equals the old behaviour, and a test that a bad stored value falls back instead of crashing (see `IslandLookTest`).
