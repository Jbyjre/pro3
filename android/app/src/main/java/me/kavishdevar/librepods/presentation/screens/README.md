# presentation/screens/

One file per page of the app. Routes are in `../navigation/Screen.kt`, the page switcher is `AppNavGraph.kt`, titles and the top-bar buttons are in `NavigationRoot.kt`. A new page needs: a `Screen` object, a `NavEntry` in `AppNavGraph`, a title in `NavigationRoot`, and a way in (a row in `AppSettingsScreen`, a card, or a deep link in `AppLinks`).

## Pages without headphones

| Page | File | Notes |
|---|---|---|
| This phone | `PhoneScreen.kt` | Live Dynamic Island strip (Nothing on / A sound / Music), phone battery, island switches, `SoundsSection`, `RecentSounds`. Opened from Settings > This phone and from `components/ThisPhoneCard.kt` on the main page. |
| Apps | `AppsScreen.kt` | Every app on the phone (read from Android in the background) with a switch for whether its sounds and messages may pop the island; the same switches as "Heard lately". |
| Freezes and crashes | `FreezeCard.kt` | Top of Troubleshooting: the last freeze or crash in Android's own words, with Copy details. `components/FreezeBanner.kt` is the main-page card. |
| Sound settings | `SoundsSection.kt` | `SoundsSection` (pop for any sound, app icons, how long, which clues are on) and `RecentSounds` ("Heard lately" with one switch per app). Used on This phone and in Settings > Islands. `AppBadge` draws an app icon in a circle (a symbol when the app isn't known). |
| Islands | `IslandSettingsScreen.kt` | Settings > Islands: the tap switch card, the Dynamic Island switches, `SoundsSection`, the look editor, gestures, which pop-ups appear, music, duration, haptics, "Try it" buttons. |
| Look editor | `IslandStudio.kt` | `DynamicIslandStudio` (live preview, situation chips, left and right slots, size, width, glow, colour, reset), `IslandGestureSettings`, `LiveIslandStrip` (the preview with your own look, used by This phone). |
| Settings | `AppSettingsScreen.kt` | Appearance, app icon, This phone, Your devices, name, Dynamic Island, Stay connected, Troubleshooting, About. Hidden "pro Lab" (`GlintLabScreen.kt`): Settings > About > tap "Version code" 7 times. |
| Stay connected and appearance | `StayConnectedScreen.kt` | Samsung background setup and the glass and motion comfort switches. |
| Your devices | `DevicesScreen.kt` | Which headphones pro follows (AirPods, Beats Solo 4, others). |
| Troubleshooting, Version, Licences | `TroubleshootingScreen.kt`, `VersionInfoScreen.kt`, `OpenSourceLicensesScreen.kt` | |
| First-run setup | `onboarding/` | Welcome, This phone, Permissions, Stay connected (no agreement step). |

## Pages that need the AirPods or other headphones

| Page | File |
|---|---|
| Main page (AirPods), and the "not connected" panel | `AirPodsSettingsScreen.kt` (large: `AirPodsSettingsRoute` picks AirPods or headphones, then the connected or not-connected layout) |
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
