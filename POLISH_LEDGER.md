# Polish ledger

Running record of the "beautiful, smooth, dependable" polish pass. Each area: status, what was
done, proof, what still needs the phone. Read this first when continuing; never redo a finished item.

Build setup used in cloud sessions: see CLAUDE.md (deps snapshot + cimg/android 2026.08.1-ndk
layers 13, 14, 15, 18, 20; NDK 29 / cmake 4.1.2 flags).

## 1. Dynamic Island responds to touch: DONE (session 2026-10-02)
- Root cause: status bar window (layer 15, full width, touchable) sits above app overlays
  (layer 11). Verified in AOSP WindowManagerPolicy + SystemUI StatusBarWindowControllerImpl.
- Fix: `IslandAccessService` (foss manifest only). Pill + pop-ups go in TYPE_ACCESSIBILITY_OVERLAY
  (layer 31) when it runs; fallback to app overlay. Probe window for status-bar visibility
  (windows above the bar get no status-bar insets: WindowState.mAboveInsetsState).
  Panel detection (shade) via getWindows(); edge pull -> GLOBAL_ACTION_NOTIFICATIONS.
- Unified gestures (`IslandGestures`), configurable actions (prefs `glint_di_g_*`), haptic
  touch/confirm, squish + glow, action sign. Setup card in Settings > Islands, TapSetup nudge pop-up
  (max 3), AppLinks deep link.
- Proof: IslandTouchTest (routing model + logic), IslandGestureUiTest (10 real-gesture tests),
  OverlayLayerTest, screenshots mini_island_pressed / mini_island_ack_*.
- Needs phone: One UI layer order, restricted-settings step, lock-screen shade case.

## 2. Heart truthful: DONE (session 2026-10-02)
- `HeartView` (pure): Live / Starting / Resting / NoSignal / Blocked / Off / Away, short words, one
  line, tap action. Chip draws each differently (only Starting/Linking animate). `HeartNote` widens
  the chip with the line + Try again. Glance measuring (`glanceHeartRate`, 90 s on island open,
  120 s on tap; PREF_HR_GLANCE default on), `retryHeartRate`, PREF_HR_CHIP to hide. Pill heart only
  when `worthAPill`. Heart page honest when link refused.
- Proof: HeartViewTest, IslandMomentsTest, screenshots heart_* (8 states/notes).
- Needs phone: whether the sensor link works at all on the S25 FE.
## 3. Dynamic Island customisation: DONE (session 2026-10-02)
- `IslandLook` (situations Music/Paused/Idle/Charging/Talking; slots Cover/Bars/Battery/Buds/Mode/
  Heart/Title/Talk/Same/Nothing; Size/Width/Glow; prefs `glint_di_*`), `MiniIslandRules.size(look)`,
  slot-based drawing with cross-fade + width spring in `MiniIslandHost`, live re-measure in controller.
  `IslandStudio.kt`: live interactive preview, situation/slot chips, segments, gestures list, reset.
- Proof: IslandLookTest, screenshots mini_island_custom_*, island_studio_*, tour 38.
- Needs phone: looks around the real camera.
## 4. Glass: DONE (session 2026-10-02)
- `GlassLight` shared ref-counted tilt source (app resume, island showing, pill touch + 3.5 s);
  overlay rim swings with `rimAxis` (same as Kyant highlight angle); thickness band; `GlassPress`
  swell + finger glow on island buttons/heart chip; pill finger glow; metaball neck on bud-out;
  budget-aware blur; solid border on reduce transparency. No overlay refraction of other apps (stated).
- Proof: GlassLightTest, island_rim_swing_*, island_button_pressed, island_chip_morph_* frames.
## 5. Island bug history: TODO
## 6. Rest of app: TODO
