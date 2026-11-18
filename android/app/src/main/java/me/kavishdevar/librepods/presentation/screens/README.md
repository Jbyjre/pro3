> Current Breathe behavior: [app-open breathing](../../../../../../../../../../docs/APP_OPEN_BREATHING.md). A dedicated event-driven service covers selected ordinary app opens for 2.5 seconds, without screen-content retrieval.

# Product screens

Headphones (`AirPodsSettingsScreen`, `HeadphonesScreen`) remain the first/home tab. Current listening controls, Off option, disconnect confirmation, rename, Beats choice and original AirPods artwork are preserved.

`BreatheScreen` replaces Island. Its preview-led layout uses `BreatheStyle` neutrals that follow the actual app theme, actionable setup status, compact setup rows, large responsive palette samples and a full-row Still switch. Connection/privacy help expands on demand. Saved palette keys and selected apps survive the redesign. The native preview starts at its finished frame; Replay runs once for 2.5 seconds, stops when the lifecycle leaves RESUMED, and does not restart on resume. System and app reduced-motion changes are observed without polling and stop preview/custom transition motion. There is no off/snooze control.

`AppsScreen` queries launchable apps and loads icons off main. Search has accessible clear/reset actions; filters and rows share one lazy list with keyboard insets. Whole rows are checkbox targets with two-line labels, subtle selection fill, bounded list transitions and system-respecting toggle haptics. The persistent chosen count and Done button complete setup; Done clears keyboard focus and returns through AppNavGraph. Large text and short screens retain scrolling access. The chosen count uses saved selections while the app list loads.

Ordinary icons, notifications and links enter through the dedicated accessibility window-event route; no shortcut setup is needed. Settings access retains the app-specific route, fallback and restricted-settings recovery inside Connection help. Permission and connection state refresh on resume; an overlay rendering failure remains distinct from missing access. The native live pause renderer, event handling and 2.5-second deadline are unchanged by this UI pass.

`PhoneScreen` retains battery, torch, sound/vibrate and the timer, plus temporary popup preferences and `UpdatePanel`. Battery and torch callbacks stop when the page is paused/disposed. UpdatePanel offers Check now, Download, Update, deferred confirmation/retry, stable/preview channel and a one-switch setup for automatic checks, downloads and idle installation. No GitHub/file-manager detour is needed after the bootstrap install.

`AppSettingsScreen` links to Breathe and phone/update tools. `PhoneProfileScreen` still offers Automatic / Galaxy S26 FE / Pixel 6 Pro, thermal tuning and background settings; its preview shows only the physical camera. The old camera editor, sound/message rules, tap-access setup and previews are removed. Screenshot/lock-through-accessibility controls were camera-glance features and are removed with their privilege.

Other headphone, heart, recorder, health and troubleshooting screens keep their existing navigation. ScreenHelpers contains the shared label and safe system-settings opener formerly declared in IslandSettingsScreen. Never leak internal interception/service terminology into everyday controls.
