package me.kavishdevar.librepods.updates

import android.content.pm.PackageInstaller

/**
 * Plain-language reasons for a failed install, so "it didn't work" always says why and what to do.
 * Android reports a status number and sometimes its own message; both are kept for "Copy details".
 */
object InstallFailure {
    data class Explained(val text: String, val autoBlockerLikely: Boolean)

    const val AUTO_BLOCKER_HELP = "Samsung's Auto Blocker only lets Google Play and Galaxy Store install or update apps, " +
        "even for an app you allowed. To update pro: Settings > Security and privacy > Auto Blocker, turn it off, update, then turn it back on."

    fun explain(status: Int, message: String?, samsung: Boolean): Explained {
        val detail = message?.takeIf { it.isNotBlank() }?.let { " Android says: $it." }.orEmpty()
        val blocker = if (samsung) " $AUTO_BLOCKER_HELP" else ""
        return when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                Explained("Android blocked the install.$detail" + (if (samsung) blocker else " Check Install unknown apps for pro, then try again."), samsung)
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                Explained("The install was cancelled before it finished. The download is kept; try again.$detail", false)
            PackageInstaller.STATUS_FAILURE_INVALID ->
                Explained("Android did not accept the downloaded file. Check again to get a fresh copy.$detail", false)
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                Explained("This build does not match the installed app (a different signature or a conflicting copy). Updates only work from the same release signing.$detail", false)
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                Explained("Not enough storage to install. Free some space, then try again.$detail", false)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                Explained("This build is not compatible with this phone.$detail", false)
            PackageInstaller.STATUS_FAILURE_TIMEOUT ->
                Explained("Android could not install it in time. Tap Update while you are using the phone.$detail", false)
            else -> Explained("The install did not finish (code $status).$detail$blocker", samsung)
        }
    }
}
