package com.wm.iptv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.wm.iptv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val version: Int, val url: String, val size: Long) : UpdateState()
    data class Downloading(val version: Int, val percent: Int) : UpdateState()
    object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * تحديث التطبيق من نفسه: يشيك على إصدارات GitHub، ينزّل ملف النسخة المناسبة
 * (تلفزيون أو جوال) ويثبّته فوق الحالي بدون حذف.
 */
object Updater {
    private const val REPO = "weli54520-sketch/Wapeed-home"
    private const val TAG_PREFIX = "iptv-v"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    fun reset() { _state.value = UpdateState.Idle }

    /** المستخدم قال "لاحقاً" لهذا الإصدار */
    var dismissedVersion = 0

    val currentVersion: Int get() = BuildConfig.VERSION_CODE
    private val assetName: String get() = if (BuildConfig.FLAVOR == "mobile") "wmtv-mobile.apk" else "wmtv.apk"

    suspend fun check(quiet: Boolean = false) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        if (!quiet) _state.value = UpdateState.Checking
        try {
            val found = withContext(Dispatchers.IO) { latest() }
            _state.value = if (found != null && found.version > currentVersion) found
            else if (quiet) UpdateState.Idle else UpdateState.UpToDate
        } catch (e: Exception) {
            _state.value = if (quiet) UpdateState.Idle else UpdateState.Error("ما قدرت أشيك على التحديثات — تأكد من الإنترنت")
        }
    }

    private fun latest(): UpdateState.Available? {
        val c = URL("https://api.github.com/repos/$REPO/releases?per_page=15").openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "WMTV-Updater")
        val text = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
        val arr = JSONArray(text)
        var best: UpdateState.Available? = null
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name")
            if (!tag.startsWith(TAG_PREFIX)) continue
            val v = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                if (a.optString("name") == assetName && (best == null || v > best.version)) {
                    best = UpdateState.Available(v, a.optString("browser_download_url"), a.optLong("size"))
                }
            }
        }
        return best
    }

    /** يحتاج إذن "تثبيت تطبيقات غير معروفة" لهذا التطبيق */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context): Boolean = try {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }

    suspend fun downloadAndInstall(ctx: Context, a: UpdateState.Available) {
        val app = ctx.applicationContext
        try {
            _state.value = UpdateState.Downloading(a.version, 0)
            val file = withContext(Dispatchers.IO) { download(app, a) }
            _state.value = UpdateState.Installing
            withContext(Dispatchers.IO) { install(app, file) }
        } catch (e: Exception) {
            _state.value = UpdateState.Error("فشل التحديث: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    private fun download(ctx: Context, a: UpdateState.Available): File {
        val out = File(ctx.cacheDir, "update.apk")
        var url = a.url
        var conn: HttpURLConnection? = null
        var hops = 0
        while (conn == null) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "WMTV-Updater")
            val code = c.responseCode
            if (code in 300..399 && hops < 6) {
                url = URL(URL(url), c.getHeaderField("Location")).toString(); c.disconnect(); hops++
            } else if (code !in 200..299) {
                c.disconnect(); throw Exception("رد غير متوقع من السيرفر ($code)")
            } else conn = c
        }
        val cn = conn!!
        val total = cn.contentLengthLong.takeIf { it > 0 } ?: a.size
        try {
            cn.inputStream.use { ins ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L; var last = -1
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        os.write(buf, 0, n); done += n
                        val p = if (total > 0) (done * 100 / total).toInt() else 0
                        if (p != last) { last = p; _state.value = UpdateState.Downloading(a.version, p) }
                    }
                }
            }
        } finally { cn.disconnect() }
        return out
    }

    private fun install(ctx: Context, file: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("wmtv", 0, file.length()).use { os ->
                file.inputStream().use { it.copyTo(os, 64 * 1024) }
                s.fsync(os)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            s.commit(pending.intentSender)
        }
    }

    internal fun onResult(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // أندرويد يطلب منك تأكيد التثبيت
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    try { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { _state.value = UpdateState.Error("ما قدرت أفتح نافذة التثبيت") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                _state.value = UpdateState.Error("النسخة المثبتة موقّعة بمفتاح قديم. احذف التطبيق مرة وحدة وثبّت الجديد، وبعدها التحديثات تنزل من داخل التطبيق.")
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_STORAGE -> _state.value = UpdateState.Error("ما فيه مساحة كافية في الجهاز")
            else -> _state.value = UpdateState.Error("فشل التثبيت (${status}) ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""}".trim())
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onResult(context, intent)
}
