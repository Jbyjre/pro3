package me.kavishdevar.librepods

import me.kavishdevar.librepods.breathing.BreathWebApps
import org.junit.Assert.*
import org.junit.Test

class BreathWebAppsTest {
    @Test fun installedWebAppPackagesAreRecognised() {
        assertTrue(BreathWebApps.isWebAppPackage("org.chromium.webapk.a1b2c3d4e5"))
        assertTrue(BreathWebApps.isWebAppPackage("com.android.webapp.f00ba4"))
    }
    @Test fun realAppsAndBrowsersAreNotWebAppPackages() {
        listOf("com.android.chrome", "com.google.android.gm", "com.anthropic.claude", "org.chromium.chrome", "com.android.webview", "io.github.jbyjre.glint")
            .forEach { assertFalse(it, BreathWebApps.isWebAppPackage(it)) }
    }
    @Test fun aWebAppWindowInsideChromeIsRecognisedButChromesOwnWindowsAreNot() {
        assertTrue(BreathWebApps.isWebAppWindow("com.android.chrome", "org.chromium.chrome.browser.webapps.SameTaskWebApkActivity"))
        assertTrue(BreathWebApps.isWebAppWindow("com.android.chrome", "org.chromium.chrome.browser.webapps.WebappActivity"))
        assertTrue("any Chromium-based browser", BreathWebApps.isWebAppWindow("com.microsoft.emmx", "org.chromium.chrome.browser.webapps.WebappActivity"))
        assertFalse(BreathWebApps.isWebAppWindow("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity"))
        assertFalse(BreathWebApps.isWebAppWindow("com.android.chrome", "org.chromium.chrome.browser.customtabs.CustomTabActivity"))
        assertFalse(BreathWebApps.isWebAppWindow("com.google.android.gm", "com.google.android.gm.ConversationListActivity"))
    }
    @Test fun aWebApkPackageWindowIsAWebAppWhateverItsClass() {
        assertTrue(BreathWebApps.isWebAppWindow("org.chromium.webapk.a1b2", "org.chromium.webapk.shell_apk.h2o.H2OMainActivity"))
    }
    @Test fun theMarkerNeverEqualsARealPackage() {
        assertNotEquals("com.android.chrome", BreathWebApps.marker("com.android.chrome"))
    }
}
