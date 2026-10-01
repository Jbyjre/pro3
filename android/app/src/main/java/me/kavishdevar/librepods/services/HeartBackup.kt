/*
    Glint, a fork of LibrePods - AirPods liberated from Apple's ecosystem
    Copyright (C) 2026 Glint contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package me.kavishdevar.librepods.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Backs up heart-rate sessions to a **private** GitHub repository, so they survive a lost
 * or reset phone. By default that's the app's own repository (`pro3`), so there's no second
 * repository to make: the files live on their own branch, [BRANCH], which holds nothing but
 * heart data (it shares no history with the app's code, and the app's cloud build skips it).
 * Uses GitHub's REST API with a personal access token you create once:
 * - `GET /user` finds your username. A fine-grained token limited to that one repository
 *   with "Contents: write" covers everything below (checked against GitHub's permission
 *   tables); a classic `repo` token works too. `POST /user/repos` only runs when you name
 *   a repository that doesn't exist yet (that needs a classic token).
 * - The branch is made once from an empty start (`git/trees`, `git/commits` with no parent,
 *   `git/refs`). `PUT /repos/{owner}/{repo}/contents/{path}` with `branch` uploads sessions.
 * - Each session is one CSV at `heart/YYYY-MM/<start, UTC>_<start ms>.csv`, plus a summary
 *   `heart/index.csv`. Restore reads the file list (`git/trees/<branch>?recursive=1`) and
 *   downloads sessions this phone doesn't have.
 *
 * The token is encrypted with a key kept in Android's Keystore (it never leaves the phone's
 * secure hardware), and it is only ever sent to api.github.com. Uploads happen after a
 * session is saved: straight away when one ends, every 10 minutes for one that's still going. If
 * the phone is offline it waits for the internet to come back and then uploads on its own. Public
 * repositories are refused: heart data is health data.
 */
object HeartBackup {
    private const val TAG = "HeartBackup"
    private const val API = "https://api.github.com"
    const val DEFAULT_REPO = "pro3"
    /** The branch that holds the backup (only heart files, no app code). */
    const val BRANCH = "heart-backup"
    /**
     * Opens GitHub's fine-grained token page pre-filled (GitHub's documented template
     * parameters): a name, "Contents: write", and no expiry so backups never silently stop.
     * The one thing GitHub can't pre-fill is which repository, so the steps say to pick it.
     */
    const val TOKEN_PAGE = "https://github.com/settings/personal-access-tokens/new" +
        "?name=pro%20heart%20backup&description=Backs%20up%20heart%20rate%20sessions&expires_in=none&contents=write"

    private const val PREF_ON = "glint_backup_on"
    private const val PREF_TOKEN = "glint_backup_token"
    private const val PREF_REPO = "glint_backup_repo"
    /** Set for backups made on [BRANCH]; older backups (their own repository) have none. */
    private const val PREF_BRANCH = "glint_backup_branch"
    private const val PREF_LAST = "glint_backup_last"
    private const val PREF_SENT = "glint_backup_sent"
    private const val KEY_ALIAS = "glint_backup_key"
    /** A session still being measured is re-uploaded at most this often (it's saved every 10). */
    const val LIVE_UPLOAD_EVERY_MS = 10 * 60_000L

    sealed interface Status {
        data object Off : Status
        data object Idle : Status
        data class Working(val what: String) : Status
        data class Done(val atMs: Long, val sent: Int) : Status
        data class Problem(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var job: Job? = null

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isOn(context: Context) = prefs(context).getBoolean(PREF_ON, false) && prefs(context).contains(PREF_TOKEN)
    fun repo(context: Context): String? = prefs(context).getString(PREF_REPO, null)
    private fun branch(context: Context): String? = prefs(context).getString(PREF_BRANCH, null)
    fun lastBackup(context: Context): Long = prefs(context).getLong(PREF_LAST, 0L)

    /** Sets the status line from saved settings (call when a screen opens). */
    fun refresh(context: Context) {
        if (job?.isActive == true) return
        _status.value = when {
            !isOn(context) -> Status.Off
            lastBackup(context) > 0 -> Status.Done(lastBackup(context), 0)
            else -> Status.Idle
        }
    }

    @Volatile private var again = false

    /**
     * Called whenever a session is saved: uploads right away if backup is on. A save that
     * lands while an upload is running is picked up as soon as that upload finishes.
     */
    fun onSessionSaved(context: Context) {
        if (!isOn(context)) return
        val app = context.applicationContext
        if (job?.isActive == true) { again = true; return }
        job = scope.launch {
            delay(500) // let back-to-back saves settle into one upload
            do {
                again = false
                sync(app)
            } while (again)
        }
    }

    /** Uploads everything that's new now (the "Back up now" button). */
    fun backUpNow(context: Context) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        job = scope.launch { sync(app, force = true) }
    }

    /**
     * Checks the token, finds or creates the private repository [repoName], saves both and
     * runs a first backup. [onDone] gets null on success or a plain-language problem.
     */
    fun connect(context: Context, token: String, repoName: String, onDone: (String?) -> Unit) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            _status.value = Status.Working("Checking your token…")
            val result = runCatching {
                val t = token.trim()
                val (owner, name) = splitRepo(repoName.trim().ifEmpty { DEFAULT_REPO })
                require(validRepoName(name) && (owner == null || validRepoName(owner))) { "Use only letters, numbers, - _ and . in the name." }
                val (code, body) = call("GET", "/user", t)
                if (code == 401) error("GitHub didn't accept that token. Check it was copied completely.")
                if (code != 200) error("GitHub answered $code. Try again in a moment.")
                val login = owner ?: JSONObject(body).getString("login")
                ensurePrivateRepo(t, login, name)
                _status.value = Status.Working("Preparing the backup branch…")
                ensureBranch(t, "$login/$name")
                prefs(app).edit()
                    .putString(PREF_TOKEN, encrypt(t))
                    .putString(PREF_REPO, "$login/$name")
                    .putString(PREF_BRANCH, BRANCH)
                    .putBoolean(PREF_ON, true)
                    .remove(PREF_SENT)
                    .apply()
            }
            val problem = result.exceptionOrNull()?.let { friendly(it) }
            if (problem != null) {
                _status.value = Status.Problem(problem)
                onDone(problem)
                return@launch
            }
            onDone(null)
            // An existing backup (a new phone): bring its sessions here first, so the first
            // upload doesn't replace the backup's summary with this phone's shorter list.
            runCatching { pull(app) }.onFailure { Log.w(TAG, "Restore during connect failed", it) }
            sync(app, force = true)
        }
    }

    /** Stops backing up and forgets the token (the repository and its files stay on GitHub). */
    fun disconnect(context: Context) {
        job?.cancel()
        prefs(context).edit().remove(PREF_TOKEN).remove(PREF_ON).remove(PREF_SENT).remove(PREF_LAST).remove(PREF_BRANCH).apply()
        _status.value = Status.Off
    }

    /** Downloads sessions from the backup that this phone doesn't have. */
    fun restore(context: Context, onDone: (Int?, String?) -> Unit) {
        val app = context.applicationContext
        if (job?.isActive == true) { onDone(null, "A backup is running; try again in a moment."); return }
        job = scope.launch {
            val r = runCatching { pull(app) }
            r.exceptionOrNull()?.let {
                val msg = friendly(it)
                _status.value = Status.Problem(msg)
                onDone(null, msg)
                return@launch
            }
            refresh(app)
            onDone(r.getOrNull(), null)
        }
    }

    /** Fetches sessions that are in the backup but not on this phone; returns how many. */
    private fun pull(app: Context): Int {
        val token = token(app) ?: error("Connect GitHub again (the token couldn't be read on this phone).")
        val repo = repo(app) ?: error("No backup repository set.")
        _status.value = Status.Working("Looking for saved sessions…")
        val branch = branch(app) ?: run {
            val (rc, rb) = call("GET", "/repos/$repo", token)
            if (rc != 200) error("GitHub answered $rc while opening the backup.")
            JSONObject(rb).optString("default_branch").ifEmpty { "main" }
        }
        val (code, body) = call("GET", "/repos/$repo/git/trees/${encodePath(branch)}?recursive=1", token)
        if (code == 404 || code == 409) return 0 // empty repository
        if (code != 200) error("GitHub answered $code while listing the backup.")
        val tree = JSONObject(body).getJSONArray("tree")
        val have = HeartHistory.sessions(app).filter { it.readings > 0 }.map { it.startMs }.toSet()
        val wanted = (0 until tree.length()).mapNotNull { i ->
            val path = tree.getJSONObject(i).optString("path")
            parseRemotePath(path)?.takeIf { it !in have }?.let { it to path }
        }
        var added = 0
        wanted.forEachIndexed { i, (start, path) ->
            _status.value = Status.Working("Restoring ${i + 1} of ${wanted.size}…")
            val (c, csv) = call("GET", "/repos/$repo/contents/${encodePath(path)}?ref=${encodePath(branch)}", token, accept = "application/vnd.github.raw+json")
            if (c == 200 && HeartHistory.restore(app, start, csv)) added++
        }
        return added
    }

    private suspend fun sync(context: Context, force: Boolean = false) {
        val token = token(context)
        val repo = repo(context)
        if (token == null || repo == null) {
            _status.value = Status.Problem("Connect GitHub again (the token couldn't be read on this phone).")
            return
        }
        if (!online(context)) {
            _status.value = Status.Problem("Waiting for internet. It'll back up as soon as you're online.")
            retryWhenOnline(context)
            return
        }
        val sent = decodeSent(prefs(context).getString(PREF_SENT, "").orEmpty())
        val now = System.currentTimeMillis()
        val hr = HeartRate.state.value
        val liveStart = if (hr.status != HeartRate.Status.Off) hr.sessionStartMs else 0L
        val todo = pending(HeartHistory.sessions(context), sent, liveStart, now, force)
        if (todo.isEmpty()) {
            if (force) prefs(context).edit().putLong(PREF_LAST, now).apply()
            _status.value = Status.Done(if (force) now else lastBackup(context), 0)
            return
        }
        var count = 0
        try {
            todo.forEachIndexed { i, s ->
                _status.value = Status.Working("Backing up ${i + 1} of ${todo.size}…")
                val file = HeartHistory.sessionFile(context, s.startMs)
                val content = if (file.exists()) file.readText() else HeartInsights.compactCsv(emptyList())
                val sha = upload(token, repo, branch(context), remotePath(s.startMs), content, sent[s.startMs]?.sha, "Heart session ${isoStamp(s.startMs)}")
                sent[s.startMs] = Sent(sha, now, s.readings)
                prefs(context).edit().putString(PREF_SENT, encodeSent(sent)).apply()
                count++
            }
            val index = buildString {
                append("start_utc,end_utc,minutes,average_bpm,lowest_bpm,highest_bpm,resting_bpm,readings\n")
                HeartHistory.sessions(context).forEach {
                    append(isoStamp(it.startMs)).append(',').append(isoStamp(it.endMs)).append(',').append(it.minutes).append(',')
                        .append(it.average).append(',').append(it.min).append(',').append(it.max).append(',')
                        .append(it.resting).append(',').append(it.readings).append('\n')
                }
            }
            upload(token, repo, branch(context), "heart/index.csv", index, sent[INDEX_KEY]?.sha, "Update heart index").let {
                sent[INDEX_KEY] = Sent(it, now, 0)
            }
            prefs(context).edit().putString(PREF_SENT, encodeSent(sent)).putLong(PREF_LAST, now).apply()
            _status.value = Status.Done(now, count)
        } catch (e: Exception) {
            Log.w(TAG, "Backup failed", e)
            _status.value = Status.Problem(friendly(e))
            // A dropped connection mid-upload: pick up where it stopped once the internet is back.
            if (e is java.io.IOException) retryWhenOnline(context)
        }
    }

    @Volatile private var waiting: ConnectivityManager.NetworkCallback? = null

    /**
     * Uploads by itself as soon as the phone has working internet again, instead of waiting
     * for the next session to end. Listens once, then stops listening.
     */
    private fun retryWhenOnline(context: Context) {
        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        if (waiting != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
                if (waiting !== this) return
                waiting = null
                runCatching { cm.unregisterNetworkCallback(this) }
                onSessionSaved(app)
            }
        }
        waiting = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onFailure { waiting = null }
    }

    /** Uploads one file, retrying once with the current file id if GitHub says it changed. */
    private fun upload(token: String, repo: String, branch: String?, path: String, content: String, knownSha: String?, message: String): String {
        fun put(sha: String?): Pair<Int, String> {
            val body = JSONObject()
                .put("message", message)
                .put("content", Base64.getEncoder().encodeToString(content.toByteArray()))
            if (sha != null) body.put("sha", sha)
            if (branch != null) body.put("branch", branch)
            return call("PUT", "/repos/$repo/contents/${encodePath(path)}", token, body.toString())
        }
        var (code, body) = put(knownSha)
        if (code == 409 || code == 422) {
            // The file exists but our id is missing or stale: look it up and try once more.
            val (c, b) = call("GET", "/repos/$repo/contents/${encodePath(path)}" + (branch?.let { "?ref=${encodePath(it)}" } ?: ""), token)
            val sha = if (c == 200) JSONObject(b).optString("sha").ifEmpty { null } else null
            val retry = put(sha)
            code = retry.first; body = retry.second
        }
        if (code == 401) error("GitHub no longer accepts the token. Connect again with a new one.")
        if (code == 404) error("The backup repository wasn't found, or the token can't write to it.")
        if (code !in 200..201) error("GitHub answered $code while uploading.")
        return JSONObject(body).getJSONObject("content").getString("sha")
    }

    private fun ensurePrivateRepo(token: String, login: String, name: String) {
        val (code, body) = call("GET", "/repos/$login/$name", token)
        when (code) {
            200 -> if (!JSONObject(body).optBoolean("private", false)) error(
                "\"$name\" is public, so anyone could read your heart data. On GitHub, open $name > Settings, " +
                    "scroll to Danger Zone > Change visibility > Private, then tap Connect again."
            )
            404 -> {
                val req = JSONObject()
                    .put("name", name)
                    .put("private", true)
                    .put("auto_init", true)
                    .put("description", "pro heart-rate backup (private)")
                val (c, _) = call("POST", "/user/repos", token, req.toString())
                if (c == 403 || c == 404) error("This token can't create repositories. Make a classic token with the \"repo\" box ticked.")
                if (c != 201) error("GitHub couldn't create the repository (answer $c).")
            }
            401 -> error("GitHub didn't accept that token.")
            else -> error("GitHub answered $code while checking the repository.")
        }
    }

    /**
     * Makes [BRANCH] if it isn't there yet, starting from nothing (no parent commit), so the
     * branch only ever holds heart files. A brand-new empty repository gets a first file on
     * its default branch first, since GitHub's git endpoints need a non-empty repository.
     */
    private fun ensureBranch(token: String, repo: String) {
        val (code, _) = call("GET", "/repos/$repo/branches/${encodePath(BRANCH)}", token)
        if (code == 200) return
        if (code == 401) error("GitHub no longer accepts the token.")
        fun tree(): Pair<Int, String> = call(
            "POST", "/repos/$repo/git/trees", token,
            JSONObject().put(
                "tree", JSONArray().put(
                    JSONObject().put("path", "README.md").put("mode", "100644").put("type", "blob")
                        .put("content", "# Heart-rate backup\n\nSaved by the pro app: one CSV per session in `heart/`, plus `heart/index.csv`.\n")
                )
            ).toString()
        )
        var (tc, tb) = tree()
        if (tc == 409) { // empty repository
            call(
                "PUT", "/repos/$repo/contents/README.md", token,
                JSONObject().put("message", "Start repository")
                    .put("content", Base64.getEncoder().encodeToString("# ${repo.substringAfter('/')}\n".toByteArray())).toString()
            )
            tree().let { tc = it.first; tb = it.second }
        }
        if (tc == 403 || tc == 404) error("This token can't write to $repo. Check it has Contents: Read and write, and that $repo is picked under Repository access.")
        if (tc != 201) error("GitHub answered $tc while preparing the backup.")
        val (cc, cb) = call(
            "POST", "/repos/$repo/git/commits", token,
            JSONObject().put("message", "Start heart-rate backup").put("tree", JSONObject(tb).getString("sha"))
                .put("parents", JSONArray()).toString()
        )
        if (cc != 201) error("GitHub answered $cc while preparing the backup.")
        val (rc, _) = call(
            "POST", "/repos/$repo/git/refs", token,
            JSONObject().put("ref", "refs/heads/$BRANCH").put("sha", JSONObject(cb).getString("sha")).toString()
        )
        if (rc != 201 && rc != 422) error("GitHub answered $rc while preparing the backup.") // 422: made meanwhile
    }

    // ---- Pure helpers (unit-tested) ----

    /** "pro3" -> (null, "pro3"); "Jbyjre/pro3" or a pasted github.com link -> ("Jbyjre", "pro3"). */
    fun splitRepo(input: String): Pair<String?, String> {
        val t = input.trim().removeSuffix("/").removeSuffix(".git")
            .replace(Regex("""^(https?://)?(www\.)?github\.com/"""), "")
        val parts = t.split('/').filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts[0] to parts[1] else null to t
    }

    internal const val INDEX_KEY = -1L

    data class Sent(val sha: String, val atMs: Long, val readings: Int)

    /** `heart/2026-10/2026-10-01T09-12-05Z_1759309925000.csv` */
    fun remotePath(startMs: Long): String {
        val t = Instant.ofEpochMilli(startMs).atOffset(ZoneOffset.UTC)
        return "heart/${t.format(DateTimeFormatter.ofPattern("yyyy-MM"))}/${isoStamp(startMs).replace(':', '-')}_$startMs.csv"
    }

    /** The session start from a backup file path, or null for other files. */
    fun parseRemotePath(path: String): Long? =
        Regex("""^heart/\d{4}-\d{2}/[^/]*_(\d{10,15})\.csv$""").find(path)?.groupValues?.get(1)?.toLongOrNull()

    /**
     * What to upload: sessions never sent or with more readings since; a session that's still
     * being measured ([liveStart]) at most once an hour unless [force].
     */
    fun pending(sessions: List<HeartInsights.Session>, sent: Map<Long, Sent>, liveStart: Long, now: Long, force: Boolean): List<HeartInsights.Session> =
        sessions.filter { s ->
            if (s.readings == 0) return@filter false // summary only: nothing to restore from
            val prev = sent[s.startMs]
            val changed = prev == null || prev.readings != s.readings
            if (!changed) return@filter false
            if (s.startMs == liveStart && !force && prev != null && now - prev.atMs < LIVE_UPLOAD_EVERY_MS) return@filter false
            true
        }.sortedBy { it.startMs }

    fun encodeSent(m: Map<Long, Sent>) = m.entries.joinToString("\n") { "${it.key},${it.value.sha},${it.value.atMs},${it.value.readings}" }

    fun decodeSent(s: String): MutableMap<Long, Sent> = s.lines().mapNotNull { line ->
        val p = line.split(',')
        if (p.size != 4) return@mapNotNull null
        val k = p[0].toLongOrNull() ?: return@mapNotNull null
        k to Sent(p[1], p[2].toLongOrNull() ?: 0L, p[3].toIntOrNull() ?: 0)
    }.toMap().toMutableMap()

    fun validRepoName(name: String) = name.length in 1..100 && name.all { it.isLetterOrDigit() || it in "-_." } && name != "." && name != ".."

    private fun isoStamp(ms: Long) = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms / 1000 * 1000))

    private fun encodePath(path: String) = path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    // ---- Plumbing ----

    private fun call(method: String, path: String, token: String, body: String? = null, accept: String = "application/vnd.github+json"): Pair<Int, String> {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("User-Agent", "pro-app")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException -> "Couldn't reach GitHub. Check your internet connection."
        is IllegalStateException, is IllegalArgumentException -> e.message ?: "Something went wrong."
        else -> "Backup problem: ${e.javaClass.simpleName}"
    }

    private fun token(context: Context): String? = prefs(context).getString(PREF_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.getEncoder().encodeToString(c.iv) + ":" + Base64.getEncoder().encodeToString(c.doFinal(plain.toByteArray()))
    }

    private fun decrypt(stored: String): String {
        val (iv, ct) = stored.split(':').let { Base64.getDecoder().decode(it[0]) to Base64.getDecoder().decode(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(ct))
    }
}
