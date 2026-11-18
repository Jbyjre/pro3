# breathing/

The pause that appears the first time a chosen app opens. Behaviour, limits and verification are in
[docs/APP_OPEN_BREATHING.md](../../../../../../../../../docs/APP_OPEN_BREATHING.md). This page is only the code map.

| File | What it is |
|---|---|
| `BreathAccessService.kt` | The accessibility service. Reads window-opened events only (no screen text). Decides when to show, builds the full-bleed overlay window, hides it. System windows never cut a running pause short. |
| `BreathAppGate.kt` | Pure rules, no Android: front app, and **when each app last paused** (the 10-minute memory). Checked only when a window opens, so no timer and no battery use. |
| `BreathHistory.kt` | Saves those pause times and the last saying in its own small file (`breath_seen`), so a pause starting never reloads the settings the service watches. |
| `BreathWebApps.kt` | Recognises websites installed from Chrome (`org.chromium.webapk.*`, and Chromium's `...browser.webapps.*` windows) so they never pause. Sources are in the file header. |
| `BreathTimeline.kt` | The whole run as pure math: the rise, the words changing, the fade or lift. Shared by the phone, the preview and the tests. |
| `BreathView.kt` | Draws the pause (also used by the preview and the shortcut route). Opaque from the first frame, deep base in the gradient's own hue, gradient rising, light on the leading edge, touch glow, Barlow Condensed type. |
| `BreathWords.kt` | The three sayings (Breathe in, Slow down, Reset), each a pair: breathe in, then breathe out. |
| `BreathOptions.kt` | Every user choice (colour, length, transition, words, haptics, app name, grace time) plus the three gradients and their deep bases. |
| `BreathPrefs.kt` | Saved choices and chosen apps. Never returns a web app as a chosen app. |
| `BreathSession.kt` | One monotonic deadline per pause, and the "a pause is running" flag other features respect. |
| `BreathAccess.kt` | Setup status for the page, the replaceable wall clock for tests, and the last pause's measured speed. |
| `BreathActivity.kt`, `BreathShortcuts.kt` | The older pinned-shortcut route. It shares the view, length and words, and hands over without a second layer. |

Rules worth remembering when changing this folder:

- **Never write to the `breathing` preference file while a pause runs.** The service reloads when it changes, which would rebuild the layer mid-animation. History lives in `breath_seen` for that reason.
- **Record "this app has paused" only at the first drawn frame**, never at attach, so a layer that failed to draw cannot use up an app's turn.
- **Nothing is measured, allocated for the first time or rebuilt while it animates.** Prepare it in `refresh()` or `BreathView`'s caches.
- **No timers or polling.** The 10-minute rule is a timestamp compared when a window opens.
- Fonts are Barlow Condensed (SIL Open Font License, `res/raw/barlow_condensed_license.txt`).
