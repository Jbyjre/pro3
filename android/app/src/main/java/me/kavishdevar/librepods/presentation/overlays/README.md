# presentation/overlays/

Everything pro draws **over other apps**. Android lets an app do this with the "Display over other apps" permission (and, for the Dynamic Island, above the status bar through an accessibility service). Overlays are Compose content in a transparent system window.

| File | What it is |
|---|---|
| `GlintOverlays.kt` | The front door: `GlintOverlays.showIsland(...)`, `startMiniIsland`, `refreshMiniIsland`, `previewMiniIsland(sound = ...)`, `showCard`. Also `PodsSnapshot` (everything the overlays show about the AirPods or headphones), `IslandEvent` (what a pop-up announces), and the phone-geometry helpers (status bar height, camera cutouts, screen size). Safe to call from any thread: work is posted to the main thread. |
| `OverlayWindow.kt` | One transparent window hosting Compose: which layer it goes in (above the status bar when the accessibility service runs), showing and removing it, becoming fully transparent and untouchable while hidden, noticing when Android removes it, and the status-bar probe that tells when a full-screen app hides the bar. `CappedFontScale` stops big system fonts from overflowing fixed-height pills. |
| `MiniIsland.kt` | **The Dynamic Island** (the pill around the camera). Three parts: `MiniIslandController` (decides when it exists and what it is about; reads the rules in `services/MiniIslandRules.kt`, the look in `IslandLook`, the sound in `SoundSource`; owns the window), `MiniGeometry` (where the camera is and the pill's pixel sizes), and `MiniIslandHost` (the composable that draws it: the grow-out-of-the-camera animation, the width spring, slot cross-fades, touch feedback, gestures). `drawSlot` draws one thing beside the camera; `drawKindGlyph` draws the sound symbols; `batteryRing` is shared by the AirPods and phone battery slots. |
| `Island.kt` | **The pop-ups** ("mini island"): connected, low battery, music, mode change, heart, and so on. `IslandController` shows them, `IslandGeometry` places them so they never cover the camera, the clock or the pill, `IslandHost` draws them (compact, then expanded on tap, then the heart detail page). They grow out of the Dynamic Island and shrink back into it. |
| `Card.kt` | The bottom "case opened" card (off by default; `services/CardGate.kt` decides when) and `GlassPillButton`, the glass button the settings pages also use. |

## What the Dynamic Island shows

`MiniIslandRules.Content` says what it is about, `IslandLook.Situation` which left/right slots apply:

| Content | Situation(s) | When | Default slots (left, right) |
|---|---|---|---|
| Music | Playing, Paused | Music plays; for 30 s after it pauses (only if it played 1.5 s or more) | Cover, Sound bars |
| Sound | Sounds | Any other sound plays, and for 2.2 / 3.8 / 6.5 s after it ends; a short musical blip counts too | App icon, Sound bars |
| AirPods | AirPods, Charging | AirPods or the chosen headphones are connected, nothing playing | Battery, Heart (else the listening mode) |
| Message | Messages | A text or chat message just arrived (Notification access) and no music plays; the window is wider only for those seconds | App icon, Title (the sender, or the app's name) |
| Rest | Nothing on | Nothing playing or connected (stays because "Always on" is on by default); also for 4 s when the phone starts charging, gets full or runs low | Phone battery, Clock |
| (any) | Talking | Conversation Awareness has the music down | Keep the left, talking dots on the right |

While music plays, an alert, a message or a phone battery moment swaps the right-hand slot for the app's icon (or the phone's ring) for a moment (`blipping` in `MiniIslandHost`).

**Swipe up** (`IslandGestures.Kind.SwipeUp`) tucks the song's name back at once (`collapseTick`), or puts away a message, sound or battery moment (`putAway` in the controller; `dismissedAt` hides moments that began before it).

**The Messages window.** The Messages look needs more width than the usual window, so the window grows first (`onWindowSize(messageWindow)`), then the name slides in, and it shrinks after the pill has narrowed. It is never permanently wider: a wider window swallows taps meant for the status bar. The phone's own heads-up notification drops from below the status bar, so the island never grows downward for a message and never overlaps it.

## Rules for changes here

- The pill is on screen for hours: nothing may animate, tick or read sensors when it doesn't have to.
- A pop-up's window starts **below** the pill so the pill stays tappable (taps in the camera strip otherwise go to the status bar).
- Hidden windows must be fully transparent and untouchable, or they eat taps meant for the app underneath.
- Anything that schedules a recheck with a computed wait must make sure the wait is above zero (`MiniIslandRules.nextCheck`).
- Draw with the shared light: `GlintLight.rim()`, never a hand-made diagonal.
- Changing the default look changes `IslandLookTest` and the screenshots; the default pill window size must stay the original (`defaultSizeIsUnchanged`).
- Screenshots: `GlintScreenshots` has a `mini(...)` helper that draws the real `MiniIslandHost` on a wallpaper with a drawn camera, in any look and any content.
