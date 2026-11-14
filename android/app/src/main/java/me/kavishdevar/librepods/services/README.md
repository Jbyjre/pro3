# Services and active rules

Current behavior supersedes the historical camera-island notes: [breathing and updates](../../../../../../../../../docs/BREATHING_AND_UPDATES.md).

| Area | Source and lifetime |
|---|---|
| Headphone control | AirPodsService, AACPManager, HeadphoneLink, DeviceChoice; existing connectedDevice foreground service and bounded reconnect work. AirPods/Beats controls and normal Bluetooth-on use are preserved. |
| Temporary events | IslandPrefs retains master/duration/haptics/song names and headphone triggers. AirPodsService and HeadphoneLink supply connection/music/battery events to GlintOverlays; no permanent camera window. |
| Music | NowPlaying uses media-session callbacks with optional Notification access and media-key fallback. MediaAccessService does not read or attribute other notifications. SoundSource is now only a bounded real-app name/icon cache, with no playback or screen listeners. MusicPulse is acquired/released by visible music pop-ups. |
| Heart | HeartRate, HeartView, HeartPacing, HeartHistory/Backup/Link, CommandFeedback; existing Smart pacing and screen-off/battery behavior remain. |
| Phone | PhoneStatus/PhoneControls battery and torch observation is owned by the visible Phone page and detached when paused/disposed. IslandTimer/TimerReceiver preserves the timer across restarts and its notification Stop action. |
| Breathing | ../breathing: activity + first-frame deadline + shortcuts, independent of Bluetooth, foreground services and app observation. No accessibility service. |
| Updates | ../updates: sparse persisted JobScheduler work, bounded HTTP/downloads, APK signature validation, PackageInstaller sessions/results. Optional idle install uses platform constraints on API 34+. |
| Migration | FeatureMigration deletes camera-only keys once, preserving unrelated preferences. No whole-settings reset. |
| Efficiency | PowerPlan, GlassBudget, WhileShown, FrameRate, BLEManager scanning policies, VolumeGuard, BatteryEstimator and existing recovery work remain. No measured battery-saving percentages are claimed. |

Removed: MiniIslandRules, IslandLook, camera gestures/editor, IslandAccessService, screen observation/reading/voice, live-notification glance, audio/source attribution and related popup suppression. Their dedicated tests were retired with the feature; mixed popup/phone/heart/battery coverage was retained and updated. Never reintroduce these through a hidden setting.

No sockets, package scans, icon decoding, files, network or root waits on main. Keep background work owned and cancellable. Test service/model behavior and true failure paths rather than merely mirroring implementation.
