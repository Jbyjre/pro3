# presentation/overlays/

Everything pro draws **over other apps**. Android lets an app do this with the "Display over other apps" permission (and, for the Dynamic Island, above the status bar through an accessibility service). Overlays are Compose content in a transparent system window.

| File | What it is |
|---|---|
| `GlintOverlays.kt` | The front door: `GlintOverlays.showIsland(...)`, `startMiniIsland`, `refreshMiniIsland`, `previewMiniIsland(sound = ...)`, `showCard`, `capturing` (true for the instant of a screenshot from the glance, so the pill isn't in the picture). Also `PodsSnapshot` (everything the overlays show about the AirPods or headphones), `IslandEvent` (what a pop-up announces), and the phone-geometry helpers (status bar height, camera cutouts, screen size). Safe to call from any thread: work is posted to the main thread. |
| `OverlayWindow.kt` | One transparent window hosting Compose: which layer it goes in (above the status bar when the accessibility service runs), showing and removing it, becoming fully transparent and untouchable while hidden, noticing when Android removes it, and the status-bar probe that tells when a full-screen app hides the bar. `CappedFontScale` stops big system fonts from overflowing fixed-height pills. |
| `MiniIsland.kt` | **The Dynamic Island** (the pill around the camera). Three parts: `MiniIslandController` (decides when it exists and what it is about; reads the rules in `services/MiniIslandRules.kt`, the look in `IslandLook`, the sound in `SoundSource`; owns the window), `MiniGeometry` (where the camera is and the pill's pixel sizes), and `MiniIslandHost` (the composable that draws it: the grow-out-of-the-camera animation, the width spring, slot cross-fades, touch feedback, gestures). `drawSlot` draws one thing beside the camera; `drawKindGlyph` draws the sound symbols; `batteryRing` is shared by the AirPods and phone battery slots. |
| `GlancePanel.kt` | **The opened Dynamic Island** when nothing plays (`IslandEvent.Glance`, also `TimerDone`): the app you're in and the date, up to two live rows from `GlanceRules` (timer with pause/+1/cancel, charging with time to full, headphones with Open, low battery with Saver, next alarm; one row alone gets a big "hero" layout), and five controls (Torch, Timer presets, Sound/Vibrate, Capture, Lock; Capture and Lock need the accessibility switch and say so). Also the small glyphs (`drawTorch`, `drawStopwatch`). |
| `Island.kt` | **The pop-ups** ("mini island"): connected, low battery, music, mode change, heart, the glance and the ringing timer, and so on. The glance and the ringing timer use a taller window (`IslandGeometry(tall = true)`, `GLANCE_H_DP`); a pop-up already up at the other size is replaced at once. `IslandController` shows them, `IslandGeometry` places them so they never cover the camera, the clock or the pill, `IslandHost` draws them (compact, then expanded on tap, then the heart detail page). They grow out of the Dynamic Island and shrink back into it. |
| `Card.kt` | The bottom "case opened" card (off by default; `services/CardGate.kt` decides when) and `GlassPillButton`, the glass button the settings pages also use. |

## What the Dynamic Island shows

`MiniIslandRules.Content` says what it is about, `IslandLook.Situation` which left/right slots apply:

| Content | Situation(s) | When | Default slots (left, right) |
|---|---|---|---|
| Music | Playing, Paused | Music plays; for 30 s after it pauses (only if it played 1.5 s or more) | Cover, Sound bars |
| Sound | Sounds | Any other sound plays, and for 2.2 / 3.8 / 6.5 s after it ends; a short musical blip counts too | App icon, Sound bars |
| Screen | In an app, Home screen, Lock screen | Nothing playing and pro knows where you are (`services/ScreenApp.kt`: the app needs the accessibility switch; locked needs nothing) | App on screen + Smart glance; Home tiles + Date; Padlock + Smart glance |
| AirPods | AirPods, Charging | AirPods or the chosen headphones are connected, nothing playing, and pro can't tell the app | Battery, Heart (else the listening mode) |
| Rest | Nothing on | Nothing playing or connected and the app unknown (stays because "Always on" is on by default) | Phone battery, Date |
| (any) | Talking | Conversation Awareness has the music down | Keep the left, talking dots on the right |

While music plays, an alert (a message ding) swaps the right-hand slot for the app's icon for a moment (`blipping` in `MiniIslandHost`). A running or ringing timer always takes the right-hand slot as the Smart glance (`timerHere`), except during a sound or talking. The time is never a default: the status bar already shows it (Jake).

A tap opens, by content (`MiniIslandRules.tapOpens`): Music the music island, AirPods the AirPods island, Sound the app that made it, Screen and Rest the glance (`GlancePanel`). A ringing timer is silenced by the tap instead. Changing apps springs the new icon in (`swap`, Settings > Island > Moments > "App icon springs in"); unlocking springs the padlock open (`ScreenApp.Place.Locked(opening)`); plugging the phone in widens the pill with "Charging · N%" (`MiniMoment`).

## Rules for changes here

- The pill is on screen for hours: nothing may animate, tick or read sensors when it doesn't have to.
- A pop-up's window starts **below** the pill so the pill stays tappable (taps in the camera strip otherwise go to the status bar).
- Hidden windows must be fully transparent and untouchable, or they eat taps meant for the app underneath.
- Anything that schedules a recheck with a computed wait must make sure the wait is above zero (`MiniIslandRules.nextCheck`).
- Draw with the shared light: `GlintLight.rim()`, never a hand-made diagonal.
- Changing the default look changes `IslandLookTest` and the screenshots; the default pill window size must stay the original (`defaultSizeIsUnchanged`).
- Screenshots: `GlintScreenshots` has a `mini(...)` helper that draws the real `MiniIslandHost` on a wallpaper with a drawn camera, in any look and any content.
