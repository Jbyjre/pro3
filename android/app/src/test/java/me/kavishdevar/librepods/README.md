> Current Breathe behavior: [app-open breathing](../../../../../../../../docs/APP_OPEN_BREATHING.md). A dedicated event-driven service covers selected ordinary app opens for 2.5 seconds, without screen-content retrieval. Shortcut-only/one-second statements below are historical.

# Verification

Run the repository's testFossDebugUnitTest, lintFossRelease and signed assembleFossRelease tasks. Cloud setup supplies the dependency cache, Android SDK/NDK and Robolectric image; a full JDK 21 is required. Tests render/run on Robolectric API 36, compiling/targeting API 37. This is neither a physical Samsung nor a claim of emulator radio/compositor testing.

- BreathSessionTest and BreathLaunchTest check actual first-draw/handler launch order at 999/1,000 ms, one completion, cancellation, disable, duplicates, lock and process restore. No observer fallback exists; only pro shortcuts are claimed.
- BreathScreenshots records native production-view transition frames. AppTourScreenshots covers Breathe, Phone and retained headphone screens. Images do not prove smoothness/frame rate.
- UpdatePolicyTest and UpdateFlowTest use controlled newer/invalid/missing/rate-limited/offline/republished releases, verify channel/version/signature policy, interrupted download cleanup, retained retries, pending confirmation and callback session ownership.
- OverlayLayerTest / IslandHandoverTest retain popup permission/removal/touch cleanup and below-status-bar placement. PopupGate checks duplicates without extending display. Camera-only renderer/rule/editor/screen-reader tests are retired with those deleted features; mixed popup, artwork, heart, timer, battery and headphone coverage stays.
- Existing AirPodsProtocolTest, AirPodsUiFixesTest, PhoneTabsUiTest, BatteryAuditTest, PowerPlan tests and headphone/heart tests remain regression evidence. Headphones remain Home and Breathe replaces Island.
- NowPlayingTest checks buffering-session selection, cover-art isolation when two apps publish the same song, and live metadata updates. Retired sound-attribution tests remain removed.

Actual Samsung checks are in the root TESTING.md. Record observed totals/results in docs/BREATHING_AND_UPDATES.md after running, never assume green from compilation or snapshots.
