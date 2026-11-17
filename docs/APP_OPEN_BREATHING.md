# App-open breathing — 16 November 2026

The user opens original icons, notifications and links and asked for a 2–3-second pause. Breathe now uses **2.5 seconds**, with no in-app disable, snooze or Quick Settings toggle. This supersedes the November 15 shortcut-only design. The old camera island stays removed.

## Setup

Breathe → Choose apps → Connect Breathe → allow **pro · Breathe** in Android Accessibility. The page distinguishes missing access, enabled-but-not-connected and ready. It refreshes when returning from Settings, offers the app-specific accessibility details route with a general Settings fallback, and provides App info for Android's restricted-settings recovery. No Bluetooth, usage access, Display over other apps or notification permission is needed by this service.

## Trigger and timing

`BreathAccessService` subscribes only to `TYPE_WINDOW_STATE_CHANGED`, with `notificationTimeout=0` and `canRetrieveWindowContent=false`. Selected packages' activity classes, labels, saved appearance and native view are prepared before entry. A known selected activity attaches the layer synchronously in the event callback: no handler debounce, package/icon scan, backend, Bluetooth call, network request or animation wait. Unknown activity classes are validated on IO; dialogs, keyboards and content-change events cannot create a new pause. Out-of-order query results cannot override a newer confirmed transition.

The opaque, touchable `TYPE_ACCESSIBILITY_OVERLAY` covers the app area while system navigation remains available. It never launches/replaces the underlying activity, preserving notification/deep-link destinations. A monotonic first-draw deadline removes it at 2,500 ms; visual animation scale does not control the deadline. The existing native color/type renderer is shared by the preview and compatible legacy shortcuts. Repeated events within a package do not restart the clock. A different selected package replaces the current layer. Home/Back, screen off, system UI, permission loss and service destruction clean up the window and pending deadline. Routine headphone pop-ups are suppressed through the existing shared ownership gate.

The service has no usage polling, resident foreground-service notification, wake lock, screen-tree/text read or automatic app relaunch. Android manages its binding. Preferences survive process replacement; revoked access, force-stop, uninstall and system-managed shutdown remain under Android's control. Setup reports the enabled flag separately from the live connection. No app can truthfully promise that this permission is irrevocable.

## Coverage and limits

Normal icons, notification destinations, links and Recents use the same event-driven transition path when entering a chosen package. A second activity within an already foreground package does not add another pause. Original launch intents are untouched. Keyboard/dialog/system windows are not considered app entries. Existing pinned shortcuts still pause before launching and exempt that immediate handoff from a duplicate service layer.

Accessibility events describe window changes after Android has begun opening a destination. This is the earliest event-driven ordinary-icon route used here, not a pre-launch veto. An initial destination frame may still be visible. Neither subsecond Samsung latency, absolutely zero target exposure, nor background survival on every OEM is certified by cloud tests. `Breathe` logs attach-to-first-draw milliseconds for device investigation; this excludes OS event-delivery latency. Device acceptance checks are in [TESTING](../TESTING.md).

The app-specific Settings intent/flattened component format was checked against [AOSP AccessibilityDetailsSettingsFragment](https://github.com/aosp-mirror/platform_packages_apps_settings/blob/master/src/com/android/settings/accessibility/AccessibilityDetailsSettingsFragment.java). The action is not a public SDK constant, so the code uses its platform action string and falls back to public accessibility settings if unavailable.

## Verification

- Full `testFossDebugUnitTest`: **378 tests in 53 classes, zero failures, errors or skips**. This includes 33 breathing policy/activity/service/setup/render tests and the retained headphone, timer, update and navigation regressions.
- After strengthening cancellation/unselected-app assertions to run beyond 2.5 seconds, reran `BreathLaunchTest` and `BreathSessionTest`: **10 tests passed**. Production code was unchanged by that final test refinement.
- `lintFossRelease`: **zero errors**, with 243 non-blocking warnings in the repository report. `assembleFossRelease` succeeded, including native builds and R8/resource shrinking.
- Actual native renders and Compose setup captures inspected: normal/large text, production transition states, existing palettes, and the corrected high-contrast controls. Screenshots are under `android/app/build/screenshots/breathe`; they do not measure physical frame rate.
- Environment: JDK 21, compile/target API 37, Robolectric API 36, NDK 29.0.14206865 / CMake 4.1.2 via the repository cloud setup. No physical phone was attached.
- Local APK package remains `io.github.jbyjre.glint`, min API 33 / target 37. It is **debug-signed build 100 for validation only**, not an upgrade artifact. Installable updates must come from the existing GitHub release workflow, which uses the retained signing identity and monotonic CI versionCode. No production signing key was generated or changed.

Historical November 15 counts and APK hashes do not apply to this change. Physical original-icon/notification/link latency, no-first-frame exposure, OEM background survival, gesture navigation and upgrade-in-place remain unverified device checks.
