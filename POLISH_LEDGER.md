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

## 2. Heart truthful: TODO
## 3. Dynamic Island customisation: TODO (gesture prefs already exist from 1; UI still to build)
## 4. Glass: TODO
## 5. Island bug history: TODO
## 6. Rest of app: TODO
