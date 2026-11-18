package me.kavishdevar.librepods.breathing

/**
 * Websites installed from Chrome ("Install app") are not chosen apps, even when Chrome itself is.
 *
 * Checked against Chromium's own sources on 18 November 2026:
 *  - components/webapps/docs/identifiers.md: an installed web app is its own Android package named
 *    `org.chromium.webapk.<hash>`; one installed through Android's web-app manager is
 *    `com.android.webapp.<hash>`.
 *  - chrome/android/java/AndroidManifest.xml: the window that actually shows the web app runs inside
 *    the browser's package, as `org.chromium.chrome.browser.webapps.WebappActivity` or
 *    `SameTaskWebApkActivity`. Android reports that window under the browser's name (for example
 *    Chrome), so without this check choosing Chrome would pause every installed web app too.
 * A plain "Add to Home screen" shortcut opens an ordinary Chrome tab and cannot be told apart.
 */
object BreathWebApps {
    private val packagePrefixes = listOf("org.chromium.webapk.", "com.android.webapp.")
    private const val WEB_APP_WINDOWS = "org.chromium.chrome.browser.webapps."

    fun isWebAppPackage(pkg: String): Boolean = packagePrefixes.any { pkg.startsWith(it) }

    /** True when this window is an installed web app, whichever package Android reports it under. */
    fun isWebAppWindow(pkg: String, className: String): Boolean =
        isWebAppPackage(pkg) || className.startsWith(WEB_APP_WINDOWS)

    /** A stand-in "foreground" name, so coming back to the browser itself counts as a fresh entry. */
    fun marker(pkg: String): String = "$pkg#webapp"
}
