# src/test/

Unit tests (plain JUnit and Robolectric) and the screenshot renderers. Run them with the one-command setup in `android/README.md`. CI runs all of them on every push.

## Rules and logic (fast)

| File | Checks |
|---|---|
| `SoundRulesTest` | Any-sound rules: usage numbers to kinds, which app made a sound, notification log, app on screen, recent list, linger, wording |
| `SoundSourceTest` | Playback events in, the sound and its app out (via `SoundSource.usagesChanged` and the `roleOverride` seam); ignored apps; recent list; clicks never pop |
| `MiniIslandTest` | When the Dynamic Island is wanted and what it is about, camera picking, never cut off, bars follow music, `nextCheck` (the zero-wait regression), short blips, phone battery parsing |
| `IslandLookTest` | Look defaults equal the original, storage and reset, situations, slots, sizes, colour, clock text, sound preferences |
| `IslandMomentsTest` | Pop-up wording, when the heart shows, settings defaults |
| `IslandHandoverTest` | Pop-ups and the pill for every camera shape and look: never over the camera or clock, always fits |
| `IslandGestureUiTest` | Real touches on the real pill: taps, double and triple, hold, swipe, pull |
| `IslandTouchTest`, `OverlayLayerTest` | Where touches go with Android's real window layers; which layer each window uses; hidden windows eat nothing |
| `GlassLightTest`, `GlassTiltTest` | One shared tilt light: counted, always released, pointing the right way |
| `HeartViewTest`, `HeartInsightsTest`, `HeartHistoryTest`, `HeartBackupTest`, `HeartLinkTest`, `SensorProtoTest` | Heart rate: honest states, insights, history files, backup, sharing, sensor decoding |
| `AirPodsProtocolTest`, `ConnectionLogicTest`, `CommandFeedbackTest`, `ConversationTimingTest` | Protocol decoding with real message formats, connection and retry rules, change confirmation, Conversation Awareness timing |
| `DeviceChoiceTest` | Which device pro follows; each device's controls stay with it |
| `BatteryTimeTest`, `FossUnlockTest` | Listening time left; nothing is locked in this build |

## Screenshots (to look at, not to assert on)

| File | Renders | Output |
|---|---|---|
| `GlintScreenshots` | Artwork, the pop-ups, and the Dynamic Island in many states (`mini_island_*`: playing, sound, blip, rest, custom looks, camera shapes), the look editor, the sound settings, the This phone page | `app/build/screenshots/` |
| `AppTourScreenshots` | Every page of the app with demo data, light and dark (`01_home`, `38_island_settings`, `46_phone`, ...) | `app/build/screenshots/tour/` |

When you change how something looks, render the screenshots for it and **look at them**. Contact sheets help: crop the top of several `mini_island_*` images and tile them with Pillow (`python3 -I`). The renderer can't show blur, refraction, video or sensors: those need the phone.

## Habits

- A new setting: test its default, that it is saved and read back, and that a bad stored value falls back.
- A bug fix: add the test that fails without it (see `nextLookIsNeverZeroOrNegative`).
- `SoundSource` and the other `object`s hold state for the whole test process: call `resetForTest()` in `@Before` and `@After`.
