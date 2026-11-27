# breathing/

The pause that appears the first time a chosen app opens. Behaviour, limits and verification are in
[docs/APP_OPEN_BREATHING.md](../../../../../../../../../docs/APP_OPEN_BREATHING.md). This page is only the code map.

| File | What it is |
|---|---|
| `BreathAccessService.kt` | The accessibility service. Reads window-opened events only (no screen text). Preloads launchable activities so app switches classify synchronously (0 ms), recognises Samsung One UI launcher helpers (Finder, Edge Panels) as Home/Launcher and ignores transient system overlays (share sheet, biometrics, app lock, keyboards), protects the gate state from late launcher events during the first 700 ms of a pause, pre-warms `BreathView`, and writes every decision to the log. |
| `BreathAppGate.kt` | Pure rules, no Android: **new opens pause, then a 15-minute cooldown prevents another pause from opening** (at most 4 times in an hour; switching back through another app stays quiet), the three modes, wake and retry handling. Checked only when a window opens, so no timer and no battery use. Why it cannot know "closed" for certain is in its header. |
| `BreathLog.kt` | The last 60 decisions in plain words ("Recent decisions" on the Breathe page, Copy button). Memory only. |
| `BreathRules.kt` | `AppRule`: one app's own mode and length (up to 30 s), stored as JSON. |
| `BreathGray.kt` | Detects Android's grayscale colour filter so the layer can switch to Mono. |
| `BreathHistory.kt` | Saves the last pause timestamp (both overall and per app) and the last colour in its own small file (`breath_seen`), so a pause starting never reloads the settings the service watches and the 15-minute cooldown survives restarts. |
| `BreathWebApps.kt` | Recognises websites installed from Chrome (`org.chromium.webapk.*`, and Chromium's `...browser.webapps.*` windows) so they never pause. Sources are in the file header. |
| `BreathTimeline.kt` | The whole run as pure math: the rise, then Fade, Lift, Drop, Iris, Slide, Zoom or Instant. Shared by the phone, the preview and the tests. |
| `BreathView.kt` | Draws the pause (also used by the preview and the shortcut route). Opaque from the first frame, pre-warmed shaders and word layout, deep base in the gradient's own hue, gradient rising, light on the leading edge, touch glow, Barlow Condensed type, and frame-clock sleeping whenever a steady phrase is settled. |
| `BreathWords.kt` | What it says: the Psalm 51:10 reference (default, one steady phrase, never the verse text), your own words, or the older sayings. |
| `BreathOptions.kt` | Every user choice (colours and their order, length, exit, words, haptics, app name, mode, cooldown defaulting to 15 min, grayscale) plus the six gradients (Cobalt, Tangerine, Pool, Violet, Crimson, Mono) and their deep bases. |
| `BreathPrefs.kt` | Saved choices and chosen apps. Never returns a web app as a chosen app. |
| `BreathSession.kt` | One monotonic deadline per pause, and the "a pause is running" flag other features respect. |
| `BreathAccess.kt` | Setup status for the page, the replaceable wall clock for tests, and the last pause's measured speed. |
| `BreathActivity.kt`, `BreathShortcuts.kt` | The older pinned-shortcut route. It shares the view, length and words, and hands over without a second layer. |

Rules worth remembering when changing this folder:

- **Never write to the `breathing` preference file while a pause runs.** The service reloads when it changes, which would rebuild the layer mid-animation. History lives in `breath_seen` for that reason.
- **Record "this app has paused" only at the first drawn frame**, never at attach, so a layer that failed to draw cannot use up an app's turn.
- **Nothing is measured, allocated for the first time or rebuilt while it animates.** Prepare it in `refresh()` or `BreathView`'s caches.
- **No timers or polling.** Every rule is a timestamp or a remembered window, compared only when a window opens.
- **Do not turn on screen-content access** (`canRetrieveWindowContent`). Jake chose to keep it off; it is the only thing that would reveal a closed app exactly.
- Fonts are Barlow Condensed (SIL Open Font License, `res/raw/barlow_condensed_license.txt`).
