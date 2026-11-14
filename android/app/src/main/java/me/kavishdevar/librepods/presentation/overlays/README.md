# Temporary pop-ups

`Island.kt` / `IslandController` / `IslandHost` and `MusicIsland.kt` implement retained, short-lived headphone and music pop-ups. They are separate from the removed persistent camera feature (`MiniIsland.kt`). No camera ring, accessibility overlay, invisible status-bar probe, screen reader or continuous sound-attribution listener remains.

`GlintOverlays` posts show requests to main, checks screen/lock/overlay access, suppresses routine events during a breathing interaction and deduplicates repeated events through `PopupGate`. Urgent timer/problem/high-heart events cancel the pending controlled launch. Suppressed routine events are dropped, never queued to extend the pause. Music no longer depends on a camera pill or its song-name suppression.

`OverlayWindow` owns one ordinary TYPE_APPLICATION_OVERLAY. Dismissal removes the actual window and composition. The test suite preserves permission loss/system-removal recovery and hidden-window transparency/touch checks. IslandController registers screen-off/configuration listeners only while showing. Popups rest below the reported status-bar/cutout; orientation or lock clears them. One UI shade ordering still needs the phone because normal overlays sit below system UI.

`PopupArt.kt` contains artwork/crop/accent helpers shared by headphone/music pop-ups and navigation, extracted from the retired camera file. `IslandGeometry`, `IslandSpec`, `IslandMotion`, `IslandEdge` and shared glass code still size/style these pop-ups. `Card.kt` remains the optional case card. The timer's Stop/+1 controls are in `GlancePanel.kt` as TimerPanel; old glance/screen-reading contents are removed.

Keep original AirPods renders/video. Visualizer capture is acquired only by visible music content and released on disposal. `dismissAll` removes windows synchronously on main, including when the breathing activity begins. Do not reintroduce a permanent controller, preview or Off-mode ring.
