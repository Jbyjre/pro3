# pro for Android

Personal phone companion for Jake's Galaxy S26 FE (Android 17 / One UI 9), AirPods Pro 3 and Beats Solo 4. Application ID stays **io.github.jbyjre.glint**. Current breathing scope: [App-open breathing](../docs/APP_OPEN_BREATHING.md). Updates/signing: [Breathing and updates](../docs/BREATHING_AND_UPDATES.md). Read [REQUESTS](../docs/REQUESTS.md), [CLAUDE](../CLAUDE.md) and [TESTING](../TESTING.md) first. Do not delegate to sub-agents unless Jake asks.

## Product and source map

- Headphones are the first and default tab (`AppTab.Home`). Preserve the November headphone controls and original AirPods assets.
- Breathe replaces the former Island tab. `breathing/BreathAccessService.kt` reacts to app-window transitions with a prepared native accessibility overlay; no screen text/content is retrieved and no usage polling runs. Choose apps and enable pro · Breathe once in Accessibility. Original icons, notifications, links and Recents can trigger the shared 2.5-second layer when entering a selected package. Existing pinned shortcuts remain compatible; setup no longer creates them. No global disable/snooze/tile bypass remains. Android still controls access and process lifetime.
- `updates/` handles GitHub discovery, verified downloads, PackageInstaller results and an idle periodic Android job. UI is Phone > App updates. Configuration can refresh as data; native behavior still needs a signed APK installation/process replacement.
- `presentation/overlays/Island.kt` and `MusicIsland.kt` are retained temporary pop-ups. **MiniIsland and its camera ring are removed**, including the old camera accessibility component, screen readers and continuous sound attribution. Never restore an old “Off” ring.
- Phone retains battery, torch, sound/vibrate and a timer. Its battery/torch listeners are scoped to visible use. Headphone control remains in AirPodsService, a connectedDevice foreground service; breathing and updater do not depend on it.
- `services/FeatureMigration.kt` retires camera preferences once without clearing headphone, popup, breathing or updater preferences.

## Build and test

The supported `tools/cloud-setup.sh` downloads the dependency snapshot, selected cimg/android SDK/NDK layers and Robolectric Android image. Registry tokens are renewed per layer. Use its environment with NDK 29 / CMake 4.1.2 when NDK 30 is unavailable; a build-tools property supports the installed 37.0.0 tools. A full **JDK 21** is required, not just a JRE. Unit/UI rendering tests run on Robolectric API 36; they do not emulate Samsung's system compositor or Bluetooth radio.

Normal repository tasks remain `testFossDebugUnitTest`, `lintFossRelease`, `assembleFossRelease`. CI uses NDK 30 / CMake 3.22.1. Screenshots are under `app/build/screenshots`, the Breathe frames under `/breathe`. A release built without the real key uses debug signing and must never be presented as an upgrade APK. This delivery uses the recovered original key, whose certificate matches the existing release.

## Signing and releases

The workflow never generates a signing key. It accepts PRO_SIGNING_KEY_B64 / PRO_SIGNING_PASSWORD from repository secrets, then falls back to the original Actions cache. Missing identity or wrong public certificate fails the release. Secret access was denied to the session, so the owner action in the delivery report remains necessary for durable storage.

`.github/workflows/glint-apk.yml` publishes signed immutable `pro-stable-VERSIONCODE` / `pro-preview-VERSIONCODE` assets, then updates the reused `glint-latest` / `glint-preview` metadata. `tools/release-metadata.py` reads actual APK package/version/size/hash. The updater compares numeric versionCode, never `1.0.0` or a release title. Stable and preview share identity and data; an older stable cannot replace a newer preview until stable's code overtakes it. Do not change the app ID or signing identity and never require uninstall as the normal update route.

## Rules that still matter

Never block the main thread with sockets, package/icon scans, network, files, root shells or ATT waits. Keep listeners/compositions and visualizer work scoped to visible/active use. No subscriptions, auto-merge, scheduled CI check-ins or polling. Merge finished work after local required checks pass; inspect already-completed latest CI once, without waiting for it. [Folder documentation](app/src/main/java/me/kavishdevar/librepods/services/README.md) describes current code; older decision entries are historical.

---

# LibrePods original Android notes

Kept as LibrePods wrote them, for reference. They describe LibrePods (the project pro was forked from), not pro's own setup.

## Root Requirement

LibrePods *may* require root depending on your device/OS and what features you want access to:

- Features requiring the VendorID hook ([the features marked with an asterisk here](https://github.com/kavishdevar/librepods#key-features)) will always require root regardless of your device/OS.
- On **ColorOS/OxygenOS 16 and realme UI 7.0** and **Pixel devices on Android 16 QPR3** (with the latest Google Play system update), LibrePods does not need root for most features.
- On other devices, LibrePods needs root because of a bug in the Android Bluetooth stack Fluoride/non-compliance of Apple with Bluetooth standards. You must have Xposed installed for the app to workaround this bug and connect to AirPods. [This issue is being tracked here](https://issuetracker.google.com/issues/371713238). **Please do not comment on the issue thread.** The issue has already been resolved and should be available in **Android 17** for all devices.

> [!IMPORTANT]
> This workaround with Xposed is not guaranteed to work on all devices.


## Installation

### Google Play Store

If you are using a supported device/OS combination, you can install LibrePods from the Google Play Store. You can use the VendorID hook features with root even from the Play Store version.

<a href="https://play.google.com/store/apps/details?id=me.kavishdevar.librepods"><img width="170" alt="GetItOnGooglePlay_Badge_Web_color_English" src="https://github.com/user-attachments/assets/2948308f-af92-443f-94d9-ee381c3a6ccc"/></a>

### GitHub Releases

If you need xposed because of the [root requirement](#root-requirement), you will have to use the apk/zip from the [GitHub releases](https://github.com/kavishdevar/librepods/releases/latest).

### As a system app (root module)

If you want LibrePods to have privileged Bluetooth permissions to 
- show battery status in the system settings and widgets
- show AirPods icon in the system settings (xposed is also currently required for this)
- switch audio to phone speakers when you are not wearing your AirPods

you can install the root module. This is optional and only provides extra features, but it is not required for the app to work.

> [!IMPORTANT]
> When using the root module, do not install the Play Store version. There might be issues because of the signature mismatch between the Play Store version and the root module.

## Nightly/Development Builds

Want to try the latest features before they're officially released? You can grab nightly builds from the [latest nightly release](https://github.com/kavishdevar/librepods/releases?q=nightly).

> [!WARNING]
> These builds are automatically generated from the latest code and may contain new features and bug fixes that haven't been included in a stable release yet. However, please note that they may also be less stable than official releases, so use them at your own risk.

## Screenshots

|                                                                                 |                                            |                                                                      |
| ------------------------------------------------------------------------------- | ------------------------------------------ | -------------------------------------------------------------------- |
| ![Settings 1](./imgs/settings-1.png)                                            | ![Settings 2](./imgs/settings-2.png)       | ![Head Tracking and Gestures](./imgs/head-tracking-and-gestures.png) |
| ![Long Press Configuration](./imgs/long-press.png)                              | ![Customizations 1](./imgs/customizations-1.png)                                | ![accessibility](./imgs/accessibility.png) |
| ![transparency](./imgs/transparency.png)                                        | ![hearing-aid](./imgs/hearing-aid.png)     | ![hearing-test](./imgs/hearing-test.png)   |
| ![hearing-aid-adjustments](./imgs/hearing-aid-adjustments.png)                  | ![Battery Notification and QS Tile for NC Mode](./imgs/notification-and-qs.png) | ![Widget](./imgs/widget.png)               |


here's a very unprofessional demo video

https://github.com/user-attachments/assets/43911243-0576-4093-8c55-89c1db5ea533

### Troubleshooting steps for common errors
- Ensure the correct scope is set in LSPosed/Vector.
- Ensure there is no root-hiding module preventing the hook from loading on the Bluetooth app.
- Restart your phone after confirming the scope.

### A few notes

- Due to recent AirPods' firmware upgrades, you must enable `Off listening mode` to switch to `Off`. This is because in this mode, loud sounds are not reduced.

- When renaming your AirPods through the app, you'll need to re-pair them with your phone for the name change to take effect. This is a limitation of how Bluetooth device naming works on Android.
