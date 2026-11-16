> Current Breathe behavior: [app-open breathing](../../../../../../../../../../docs/APP_OPEN_BREATHING.md). A dedicated event-driven service covers selected ordinary app opens for 2.5 seconds, without screen-content retrieval. Shortcut-only/one-second statements below are historical.

# Product screens

Headphones (`AirPodsSettingsScreen`, `HeadphonesScreen`) remain the first/home tab. Current listening controls, Off option, disconnect confirmation, rename, Beats choice and original AirPods artwork are preserved.

`BreatheScreen` replaces Island. It has a clear switch, the real production animation preview, selected-app setup, a temporary pause and accurate shortcut-only coverage. `AppsScreen` queries launchable installed apps off main, searches their actual names, loads bounded icons off main, stores selections and offers Add icon for each. It never guesses packages or assumes social-media use. Android/launcher confirmation for adding an icon is explicit.

`PhoneScreen` retains battery, torch, sound/vibrate and the timer, plus temporary popup preferences and `UpdatePanel`. Battery and torch callbacks stop when the page is paused/disposed. UpdatePanel offers Check now, Download, Update, deferred confirmation/retry, stable/preview channel and optional idle automation. No GitHub/file-manager detour is needed after the bootstrap install.

`AppSettingsScreen` links to Breathe and phone/update tools. `PhoneProfileScreen` still offers Automatic / Galaxy S26 FE / Pixel 6 Pro, thermal tuning and background settings; its preview shows only the physical camera. The old camera editor, sound/message rules, tap-access setup and previews are removed. Screenshot/lock-through-accessibility controls were camera-glance features and are removed with their privilege.

Other headphone, heart, recorder, health and troubleshooting screens keep their existing navigation. ScreenHelpers contains the shared label and safe system-settings opener formerly declared in IslandSettingsScreen. Never leak internal interception/service terminology into everyday controls.
