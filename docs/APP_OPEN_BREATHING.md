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


## Recovery and setup refinement

A failed overlay attachment no longer consumes the app entry. The next real window event can retry. A separate presentation-failure state tells setup when Accessibility is connected but Android could not show the layer; it clears after a successful retry or disconnect. If an attached layer never draws, a one-second first-frame watchdog removes its invisible touch target. A successfully drawn layer still receives the full 2.5-second pause; the watchdog is removed on its first frame.

Back, system interruption, screen off and unbind invalidate pending window classifications. Repeated events for one unclassified component share one in-flight lookup; rejected non-activity windows use a bounded cache. This avoids repeatedly querying the package manager for keyboard/dialog events. Touch-mode focus and attach-scoped Back registration support repeated presentations without accumulating deferred attach callbacks.

App selection now uses the entire row as one accessible checkbox target, with room for two-line labels and high-contrast selection marks. All apps / Chosen filters keep the list manageable without reordering rows as they are selected. Existing saved selections and the original-icon/notification/link flow stay intact.

Follow-up verification (17 November): **382 tests across 53 classes passed, zero failures/errors/skips**; the focused recovery/setup/picker run passed all **37 tests**. `lintFossRelease` passed with **0 errors, 243 warnings and 1 hint**. `assembleFossRelease` passed, including native compilation, R8 and resource optimization. Native recovery-screen and 1.8×-text picker captures were inspected. The local APK remains a debug-signed validation artifact; installable updates use the unchanged GitHub signing workflow. The branch build was still running at the one-time CI inspection; physical Samsung latency, gestures and upgrade behavior remain unverified. The 378-test record above describes the preceding delivery.


## Compact setup and immediate choices — 17 November

The picker now scrolls instructions, search, filters and app rows in one lazy list. It reserves keyboard insets and the floating search label's space, and wrapped filters have vertical spacing. A 320×480 dp viewport at 1.8× text can bring a whole app row into view, select it, return to search and select a filtered result. The regression checks at least 64 dp of visible row height; the old fixed-header layout failed that assertion.

Chosen apps are applied synchronously when preferences change, before background package/label/appearance preparation. Selecting an already classified app is effective on its next immediate window event; removing a choice releases its active pause immediately. Newly encountered activity classes still require their normal asynchronous validation. Presentation rechecks display interactivity and keyguard after that validation, preventing late events from showing a layer on a non-interactive display, even without a lock screen.

Focused verification: 19 service/picker tests passed, including five new cases for selection/removal before metadata finishes, a display-off event, display-off during an in-flight lookup, and compact-screen search/selection. The three immediate service cases and the visible-row regression failed before their fixes. Native compact-screen and search captures were inspected. Full verification passed: **387 tests across 53 classes, zero failures/errors/skips**; `lintFossRelease` had **0 errors, 243 warnings and 1 hint**; `assembleFossRelease` succeeded with native compilation, R8 and resource optimization. The local APK is debug-signed for validation only; the existing GitHub workflow supplies the signed update without changing package identity or the production certificate. Real keyboard, gesture and Samsung background behavior still require device verification.


## UI and interaction redesign — 17 November

Breathe now leads with the production renderer's preview, a stronger type hierarchy and warm/light or charcoal/dark neutrals. Setup is compact and actionable from the status row. Palette samples are large, use radio-button semantics and stack at large text/small widths; the full motion row is one switch target. Connection and privacy explanations expand only when requested. Status never labels an unconnected configuration as always on.

Replay is explicit, finite and lifecycle-bound. It starts no automatic loop, stops on background, and does not resume itself when returning. App/system reduced-motion changes are observed and cancel playback; custom selection/layout transitions also honor them. Existing native pause drawing, palette keys, 2.5-second intervention timing and permissions are unchanged.

The picker has a stable search field with accessible clear/reset controls, short selection/list transitions, system-respecting toggle haptics, and a persistent chosen-count/Done footer. Done clears keyboard focus and returns to the previous screen. Count initialization uses saved choices while package loading completes, avoiding a false zero flash.

UI verification: all 10 setup/design/picker tests passed, including in the final-source regression run. Four new design tests cover help and actual Settings navigation, clear-search/Done persistence, playback completion/background/reduced-motion behavior, and dark appearance with saved palette/motion changes. Inspected native captures include light, dark, compact selection/search and 1.8× text with stacked palettes. The full 391-case run passed 390 cases; its only failure was the navigation test expecting the previous subtitle. After updating that assertion, all **5 PhoneTabsUiTest cases passed**, including the corrected tab/Back flow. No production change was needed. `lintFossRelease` passed with **0 errors, 243 warnings and 1 hint**; `assembleFossRelease` passed with native compilation, R8 and resource optimization. The new neutral primary/secondary text pairs were measured at **5.02:1 or higher** contrast. The local APK remains debug-signed for validation only; signed updates use the existing GitHub identity. Physical frame pacing, touch haptics and keyboard transitions remain device checks.
