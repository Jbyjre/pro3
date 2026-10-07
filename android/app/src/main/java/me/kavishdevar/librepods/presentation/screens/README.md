# presentation/screens/

One file per page of the app. Routes are in `../navigation/Screen.kt`, the page switcher is `AppNavGraph.kt`, titles and the top-bar buttons are in `NavigationRoot.kt`. The three main pages are tabs (`../navigation/Tabs.kt`: `AppTab` Phone, Island, Headphones, and the Liquid Glass `GlassTabBar`): they all live in the root entry `Screen.AirPodsSettings` (the old name of the main page, kept so nothing else moves), which shows the selected tab. Pages add `LocalTabBarSpace` under their content so the floating bar never covers it. A new page needs: a `Screen` object, a `NavEntry` in `AppNavGraph`, a title in `NavigationRoot`, and a way in (a row in `AppSettingsScreen`, a card, or a deep link in `AppLinks`).

## Pages without headphones

| Page | File | Notes |
|---|---|---|
| **Phone tab** (the main page, opens first) | `PhoneScreen.kt` | Live Dynamic Island strip (In an app / Home / Locked / Music) with Open it and Customize, "Make it work fully" (only the missing permissions, each with Allow), Torch / Sound / Island tiles, the island timer (presets, or the running one with Pause/Cancel/+1), battery with time to full and the next alarm, and a small card for the headphones that opens their tab. Also Settings > This phone (without that card). |
| **Island tab** | `IslandSettingsScreen.kt` | Below. Also Settings > Islands. `HideInApps` lists apps used lately with a switch each. |
| Apps | `AppsScreen.kt` | Every app on the phone (read from Android in the background) with a switch for whether its sounds and messages may pop the island; the same switches as "Heard lately". |
| Freezes and crashes | `FreezeCard.kt` | Top of Troubleshooting: the last freeze or crash in Android's own words, with Copy details. `components/FreezeBanner.kt` is the main-page card. |
| Sound settings | `SoundsSection.kt` | `SoundsSection` (pop for any sound, app icons, how long, which clues are on) and `RecentSounds` ("Heard lately" with one switch per app). Used on This phone and in Settings > Islands. `AppBadge` draws an app icon in a circle (a symbol when the app isn't known). |
| Islands | `IslandSettingsScreen.kt` | The Island tab: the "See your apps, tap the island" switch card, the look editor first, the Dynamic Island switches, Moments (charging, app icon springs in), Hide in these apps, gestures, `SoundsSection`, which pop-ups appear, music, duration, haptics, "Try it" buttons (including the glance). |
| Look editor | `IslandStudio.kt` | `DynamicIslandStudio` (live preview, situation chips starting with In an app, left and right slots, size, width, glow, colour, reset), `IslandGestureSettings`, `LiveIslandStrip` (the preview with your own look, used by the Phone tab). |
| Settings | `AppSettingsScreen.kt` | Appearance, app icon, This phone, Your devices, name, Dynamic Island, Stay connected, Troubleshooting, About. Hidden "pro Lab" (`GlintLabScreen.kt`): Settings > About > tap "Version code" 7 times. |
| Stay connected and appearance | `StayConnectedScreen.kt` | Samsung background setup and the glass and motion comfort switches. |
| Your devices | `DevicesScreen.kt` | Which headphones pro follows (AirPods, Beats Solo 4, others). |
| Troubleshooting, Version, Licences | `TroubleshootingScreen.kt`, `VersionInfoScreen.kt`, `OpenSourceLicensesScreen.kt` | |
| First-run setup | `onboarding/` | Welcome, This phone, Permissions, Stay connected (no agreement step). |

## Pages that need the AirPods or other headphones

| Page | File |
|---|---|
| Headphones tab (AirPods), and the "not connected" panel | `AirPodsSettingsScreen.kt` (large: `AirPodsSettingsRoute` picks AirPods or headphones, then the connected or not-connected layout) |
| Beats Solo 4 or other headphones | `HeadphonesScreen.kt` |
| Rename | `RenameScreen.kt` |
| Listening and hearing | `AdaptiveStrengthScreen.kt`, `TransparencySettingsScreen.kt`, `EqualizerScreen.kt`, `HearingAidScreen.kt`, `HearingAidAdjustmentsScreen.kt`, `UpdateHearingTestScreen.kt`, `HearingProtectionScreen.kt`, `AccessibilitySettingsScreen.kt` |
| Controls | `PressAndHoldSettingsScreen.kt`, `CallControlScreen.kt`, `MicrophoneSettingsScreen.kt`, `HeadTrackingScreen.kt`, `CameraControlScreen.kt` (route commented out) |
| Heart rate | `HeartRateScreen.kt`, `HeartHistoryScreen.kt`, `HeartShareScreen.kt`, `HeartParts.kt` (colours) |
| Recorder (experimental) | `RecorderScreen.kt` |
| Play Store only | `PurchaseScreen.kt` (the build Jake installs has no paywall) |

## Conventions

- Cards are white on light, `0xFF1C1C1E` on dark; ink is black or white by the app's own light/dark choice, not the phone's. Pass `ink` and `dark` down; never recompute from `isSystemInDarkTheme()` inside rows.
- Use `StyledList` with `StyledToggle` / `StyledListItem` for settings rows; they add the row picture from the name. Keep related rows consistent (all with a picture or none).
- Short text. Longer explanations go behind an "i" (`components/GlintBits.kt`).
- Every setting is live: write the preference and the real thing reads it (see `IslandLook`, `IslandPrefs`). Add a test that the default equals the old behaviour.
- Add the new page to the screenshot tour (`AppTourScreenshots`) and look at it.
