package com.wm.iptv.data

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

class ApiException(message: String) : IOException(message)

/**
 * عميل Xtream Codes.
 * القوائم الكبيرة تنزل لملف أولاً ثم تنقرأ بشكل متدفق (Streaming)
 * عشان ما يمتلي الرام في أجهزة التلفزيون الضعيفة.
 */
class XtreamApi(private val account: Account) {

    companion object {
        /** هوية الاتصال الافتراضية لأندرويد (نفس اللي ترسلها أغلب التطبيقات) */
        val DEFAULT_UA: String = System.getProperty("http.agent")?.takeIf { it.isNotBlank() } ?: "Dalvik/2.1.0 (Linux; U; Android)"

        /** هوية الاتصال الحالية — المستخدم يقدر يغيرها لو المزود يطلب وحدة معينة */
        @Volatile
        var USER_AGENT: String = DEFAULT_UA

        /** يرتب رابط السيرفر مهما كتبه المستخدم */
        fun normalizeServer(input: String): String {
            var s = input.trim().replace(" ", "")
            if (s.isEmpty()) return s
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            val cut = listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php", "/c/")
            for (c in cut) {
                val i = s.indexOf(c, ignoreCase = true)
                if (i > 0) s = s.substring(0, i)
            }
            val q = s.indexOf('?')
            if (q > 0) s = s.substring(0, q)
            s = s.trimEnd('/')
            // المنفذ 80 يشتغل http فقط — لو انكتب https معه نصلحه
            if (s.startsWith("https://", true) && s.endsWith(":80")) s = "http://" + s.substring(8)
            return s
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }

    private val base get() = "${account.server}/player_api.php?username=${enc(account.username)}&password=${enc(account.password)}"

    // ───────────── روابط التشغيل ─────────────
    fun liveUrl(id: Int, hls: Boolean): String =
        "${account.server}/live/${enc(account.username)}/${enc(account.password)}/$id.${if (hls) "m3u8" else "ts"}"

    fun movieUrl(id: Int, ext: String): String =
        "${account.server}/movie/${enc(account.username)}/${enc(account.password)}/$id.${ext.ifBlank { "mp4" }}"

    fun episodeUrl(id: Int, ext: String): String =
        "${account.server}/series/${enc(account.username)}/${enc(account.password)}/$id.${ext.ifBlank { "mp4" }}"

    // ───────────── HTTP ─────────────
    private fun open(url: String, timeoutMs: Int = 20000): Pair<HttpURLConnection, InputStream> {
        var current = url
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept-Encoding", "gzip")
            c.setRequestProperty("Accept", "application/json, */*")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw ApiException("تحويل غير صالح من السيرفر")
                current = URL(URL(current), loc).toString()
                return@repeat
            }
            if (code == 401 || code == 403) {
                c.disconnect()
                throw ApiException("السيرفر رفض الدخول ($code). تأكد إن البيانات نفسها تشتغل في تطبيق ثاني وإن الاشتراك فعال. لو المزود يطلب User-Agent معين، اكتبه في الخانة الاختيارية تحت.")
            }
            if (code !in 200..299) {
                c.disconnect()
                throw ApiException("السيرفر لا يستجيب بشكل صحيح ($code)")
            }
            var ins: InputStream = BufferedInputStream(c.inputStream, 64 * 1024)
            if ("gzip".equals(c.contentEncoding, true)) ins = GZIPInputStream(ins, 64 * 1024)
            return c to ins
        }
        throw ApiException("تحويلات كثيرة من السيرفر")
    }

    private fun getText(url: String): String {
        val (c, ins) = open(url)
        try {
            return ins.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            try { ins.close() } catch (_: Exception) {}
            c.disconnect()
        }
    }

    /** ينزل الرد لملف (مع ملف مؤقت عشان ما يخرب الكاش لو انقطع) */
    fun download(action: String, target: File) {
        val (c, ins) = open("$base&action=$action", 60000)
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.outputStream().use { out -> ins.copyTo(out, 64 * 1024) }
        } finally {
            try { ins.close() } catch (_: Exception) {}
            c.disconnect()
        }
        if (tmp.length() == 0L) { tmp.delete(); throw ApiException("رد فاضي من السيرفر") }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) { tmp.copyTo(target, true); tmp.delete() }
    }

    // ───────────── الدخول ─────────────
    fun authenticate(): UserInfo {
        val text = try { getText(base) } catch (e: ApiException) { throw e } catch (e: java.net.UnknownHostException) {
            throw ApiException("ما قدرت أوصل للسيرفر — تأكد من الرابط")
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException("السيرفر ما رد في الوقت — حاول مرة ثانية")
        } catch (e: javax.net.ssl.SSLException) {
            throw ApiException("الرابط مكتوب https لكن السيرفر يشتغل http — جرّب تكتبه http://")
        } catch (e: IOException) {
            throw ApiException("مشكلة في الاتصال: ${e.message ?: "غير معروفة"}")
        }
        val t = text.trim()
        if (!t.startsWith("{")) throw ApiException("بيانات الدخول غير صحيحة أو الرابط ليس سيرفر Xtream")
        val r = JsonReader(StringReader(t))
        var info: UserInfo? = null
        var auth = 1
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "user_info" -> if (r.peek() == JsonToken.BEGIN_OBJECT) {
                    var status = ""; var exp: Long? = null; var max = 1; var active = 0; var trial = false; var created: Long? = null
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "auth" -> auth = r.str().toIntOrNull() ?: 1
                            "status" -> status = r.str()
                            "exp_date" -> exp = r.str().toLongOrNull()
                            "max_connections" -> max = r.str().toIntOrNull() ?: 1
                            "active_cons" -> active = r.str().toIntOrNull() ?: 0
                            "is_trial" -> trial = r.str() == "1"
                            "created_at" -> created = r.str().toLongOrNull()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    info = UserInfo(status, exp, max, active, trial, created)
                } else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (auth == 0 || info == null) throw ApiException("اسم المستخدم أو كلمة المرور غير صحيحة")
        val st = info.status.lowercase()
        if (st == "expired") throw ApiException("الاشتراك منتهي")
        if (st == "banned" || st == "disabled") throw ApiException("الاشتراك موقوف")
        return info
    }

    // ───────────── التفاصيل ─────────────
    fun vodInfo(id: Int): VodInfo {
        val r = JsonReader(StringReader(getText("$base&action=get_vod_info&vod_id=$id")))
        var plot = ""; var cast = ""; var director = ""; var genre = ""; var year = ""; var dur = 0
        var rating = ""; var backdrop = ""; var poster = ""; var ext = ""
        if (r.peek() != JsonToken.BEGIN_OBJECT) return VodInfo("", "", "", "", "", 0, "", "", "", "")
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "info" -> if (r.peek() == JsonToken.BEGIN_OBJECT) {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "plot", "description" -> r.str().let { if (it.isNotBlank() && plot.isBlank()) plot = it }
                            "cast", "actors" -> r.str().let { if (it.isNotBlank() && cast.isBlank()) cast = it }
                            "director" -> director = r.str()
                            "genre" -> genre = r.str()
                            "releasedate", "release_date" -> r.str().let { if (it.length >= 4 && year.isBlank()) year = it.take(4) }
                            "year" -> r.str().let { if (it.isNotBlank()) year = it.take(4) }
                            "duration_secs" -> dur = r.str().toIntOrNull() ?: dur
                            "duration" -> r.str().let { if (dur == 0) dur = parseDuration(it) }
                            "rating" -> rating = r.str()
                            "backdrop_path" -> r.firstString().let { if (it.isNotBlank()) backdrop = it }
                            "movie_image", "cover_big" -> r.str().let { if (it.isNotBlank() && poster.isBlank()) poster = it }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                } else r.skipValue()
                "movie_data" -> if (r.peek() == JsonToken.BEGIN_OBJECT) {
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() == "container_extension") ext = r.str() else r.skipValue()
                    }
                    r.endObject()
                } else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return VodInfo(plot, cast, director, genre, year, dur, cleanRating(rating), backdrop, poster, ext)
    }

    fun seriesInfo(id: Int): SeriesInfo {
        val r = JsonReader(StringReader(getText("$base&action=get_series_info&series_id=$id")))
        var name = ""; var plot = ""; var cast = ""; var genre = ""; var year = ""; var rating = ""; var cover = ""; var backdrop = ""
        val seasons = sortedSetOf<Int>()
        val eps = HashMap<Int, MutableList<Episode>>()
        if (r.peek() != JsonToken.BEGIN_OBJECT) return SeriesInfo("", "", "", "", "", "", "", "", emptyList(), emptyMap())
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "info" -> if (r.peek() == JsonToken.BEGIN_OBJECT) {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> name = r.str()
                            "plot" -> plot = r.str()
                            "cast" -> cast = r.str()
                            "genre" -> genre = r.str()
                            "releaseDate", "release_date", "releasedate" -> r.str().let { if (it.length >= 4) year = it.take(4) }
                            "rating" -> rating = r.str()
                            "cover" -> cover = r.str()
                            "backdrop_path" -> backdrop = r.firstString()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                } else r.skipValue()
                "episodes" -> when (r.peek()) {
                    // الشكل المعتاد: {"1":[...], "2":[...]}
                    JsonToken.BEGIN_OBJECT -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val sn = r.nextName().toIntOrNull() ?: 0
                            readEpisodes(r, sn, eps)
                        }
                        r.endObject()
                    }
                    // بعض السيرفرات ترجع مصفوفة مصفوفات
                    JsonToken.BEGIN_ARRAY -> {
                        r.beginArray()
                        var idx = 1
                        while (r.hasNext()) {
                            if (r.peek() == JsonToken.BEGIN_ARRAY) readEpisodes(r, idx, eps)
                            else if (r.peek() == JsonToken.BEGIN_OBJECT) readEpisode(r, idx)?.let { eps.getOrPut(it.season) { mutableListOf() }.add(it) }
                            else r.skipValue()
                            idx++
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        eps.keys.forEach { seasons.add(it) }
        val sorted = eps.mapValues { (_, v) -> v.distinctBy { it.id }.sortedBy { it.number } }
        return SeriesInfo(name, plot, cast, genre, year, cleanRating(rating), cover, backdrop, seasons.toList(), sorted)
    }

    private fun readEpisodes(r: JsonReader, season: Int, out: HashMap<Int, MutableList<Episode>>) {
        if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return }
        r.beginArray()
        while (r.hasNext()) {
            val e = readEpisode(r, season)
            if (e != null) out.getOrPut(e.season) { mutableListOf() }.add(e)
        }
        r.endArray()
    }

    private fun readEpisode(r: JsonReader, defSeason: Int): Episode? {
        if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return null }
        var id = 0; var num = 0; var title = ""; var ext = ""; var season = defSeason; var dur = 0; var plot = ""; var img = ""
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "id" -> id = r.str().toIntOrNull() ?: 0
                "episode_num" -> num = r.str().toIntOrNull() ?: 0
                "title" -> title = r.str()
                "container_extension" -> ext = r.str()
                "season" -> season = r.str().toIntOrNull() ?: defSeason
                "info" -> if (r.peek() == JsonToken.BEGIN_OBJECT) {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "duration_secs" -> dur = r.str().toIntOrNull() ?: dur
                            "duration" -> r.str().let { if (dur == 0) dur = parseDuration(it) }
                            "plot" -> plot = r.str()
                            "movie_image", "cover_big" -> r.str().let { if (img.isBlank()) img = it }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                } else r.skipValue()
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (id == 0) return null
        return Episode(id, season, num, title, ext, dur, plot, img)
    }

    fun shortEpg(streamId: Int, limit: Int = 4): List<EpgEntry> {
        val text = getText("$base&action=get_short_epg&stream_id=$streamId&limit=$limit")
        val r = JsonReader(StringReader(text))
        val out = ArrayList<EpgEntry>()
        if (r.peek() != JsonToken.BEGIN_OBJECT) return out
        r.beginObject()
        while (r.hasNext()) {
            if (r.nextName() == "epg_listings" && r.peek() == JsonToken.BEGIN_ARRAY) {
                r.beginArray()
                while (r.hasNext()) {
                    var title = ""; var desc = ""; var start = 0L; var end = 0L
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "title" -> title = b64(r.str())
                            "description" -> desc = b64(r.str())
                            "start_timestamp" -> start = (r.str().toLongOrNull() ?: 0L) * 1000
                            "stop_timestamp" -> end = (r.str().toLongOrNull() ?: 0L) * 1000
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (start > 0 && end > start) out.add(EpgEntry(title.trim(), desc.trim(), start, end))
                }
                r.endArray()
            } else r.skipValue()
        }
        r.endObject()
        val now = System.currentTimeMillis()
        return out.filter { it.end > now }.sortedBy { it.start }
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) { s }

    private fun parseDuration(s: String): Int {
        // "01:52:00"
        val p = s.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (p.size) {
            3 -> p[0] * 3600 + p[1] * 60 + p[2]
            2 -> p[0] * 60 + p[1]
            else -> 0
        }
    }
}

// ───────────── قراءة الملفات المحفوظة (متدفق) ─────────────
object CatalogParser {

    private fun reader(f: File) = JsonReader(InputStreamReader(BufferedInputStream(FileInputStream(f), 64 * 1024), Charsets.UTF_8))

    /** يمشي على كل عنصر في المصفوفة (أو قيم الكائن لو السيرفر رجع كائن) */
    private inline fun eachObject(f: File, block: (JsonReader) -> Unit) {
        if (!f.exists() || f.length() == 0L) return
        reader(f).use { r ->
            when (r.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        if (r.peek() == JsonToken.BEGIN_OBJECT) { r.beginObject(); block(r); r.endObject() } else r.skipValue()
                    }
                    r.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        r.nextName()
                        if (r.peek() == JsonToken.BEGIN_OBJECT) { r.beginObject(); block(r); r.endObject() } else r.skipValue()
                    }
                    r.endObject()
                }
                else -> {}
            }
        }
    }

    fun categories(f: File): List<Category> {
        val out = ArrayList<Category>()
        val seen = HashSet<String>()
        eachObject(f) { r ->
            var id = ""; var name = ""
            while (r.hasNext()) {
                when (r.nextName()) {
                    "category_id" -> id = r.str()
                    "category_name" -> name = r.str()
                    else -> r.skipValue()
                }
            }
            if (id.isNotEmpty() && seen.add(id)) out.add(Category(id, name.trim().ifEmpty { "بدون اسم" }))
        }
        return out
    }

    fun channels(f: File): List<Channel> {
        val out = ArrayList<Channel>(4096)
        val seen = HashSet<Int>()
        eachObject(f) { r ->
            var id = 0; var num = 0; var name = ""; var icon = ""; var epg = ""; var cat = ""; var arch = false
            while (r.hasNext()) {
                when (r.nextName()) {
                    "stream_id" -> id = r.str().toIntOrNull() ?: 0
                    "num" -> num = r.str().toIntOrNull() ?: 0
                    "name" -> name = r.str()
                    "stream_icon" -> icon = r.str()
                    "epg_channel_id" -> epg = r.str()
                    "category_id" -> cat = r.str()
                    "tv_archive" -> arch = r.str() == "1"
                    else -> r.skipValue()
                }
            }
            if (id != 0 && seen.add(id)) {
                val n = name.trim()
                out.add(Channel(id, num, n, icon.trim(), epg, cat, arch, TextNorm.key(n)))
            }
        }
        // ترقيم تلقائي لو السيرفر ما يرسل أرقام
        if (out.isNotEmpty() && out.all { it.num == 0 }) {
            return out.mapIndexed { i, c -> c.copy(num = i + 1) }
        }
        return out
    }

    fun movies(f: File): List<Movie> {
        val out = ArrayList<Movie>(8192)
        val seen = HashSet<Int>()
        eachObject(f) { r ->
            var id = 0; var name = ""; var icon = ""; var rating = ""; var added = 0L; var cat = ""; var ext = ""
            while (r.hasNext()) {
                when (r.nextName()) {
                    "stream_id" -> id = r.str().toIntOrNull() ?: 0
                    "name" -> name = r.str()
                    "stream_icon" -> icon = r.str()
                    "rating" -> rating = r.str()
                    "rating_5based" -> r.str().toDoubleOrNull()?.let { if (rating.isBlank() && it > 0) rating = (it * 2).toString() }
                    "added" -> added = r.str().toLongOrNull() ?: 0L
                    "category_id" -> cat = r.str()
                    "container_extension" -> ext = r.str()
                    else -> r.skipValue()
                }
            }
            if (id != 0 && seen.add(id)) {
                val n = name.trim()
                out.add(Movie(id, n, icon.trim(), cleanRating(rating), added, cat, ext, TextNorm.key(n)))
            }
        }
        return out
    }

    fun series(f: File): List<Series> {
        val out = ArrayList<Series>(4096)
        val seen = HashSet<Int>()
        eachObject(f) { r ->
            var id = 0; var name = ""; var cover = ""; var rating = ""; var upd = 0L; var cat = ""
            while (r.hasNext()) {
                when (r.nextName()) {
                    "series_id" -> id = r.str().toIntOrNull() ?: 0
                    "name" -> name = r.str()
                    "cover" -> cover = r.str()
                    "rating" -> rating = r.str()
                    "last_modified" -> upd = r.str().toLongOrNull() ?: 0L
                    "category_id" -> cat = r.str()
                    else -> r.skipValue()
                }
            }
            if (id != 0 && seen.add(id)) {
                val n = name.trim()
                out.add(Series(id, n, cover.trim(), cleanRating(rating), upd, cat, TextNorm.key(n)))
            }
        }
        return out
    }
}

// ───────────── أدوات JsonReader ─────────────

/** يقرأ أي قيمة كنص (السيرفرات ترسل الأرقام أحياناً نص وأحياناً رقم) */
internal fun JsonReader.str(): String = when (peek()) {
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> if (nextBoolean()) "1" else "0"
    JsonToken.NULL -> { nextNull(); "" }
    else -> { skipValue(); "" }
}

/** أول نص في مصفوفة أو النص نفسه */
internal fun JsonReader.firstString(): String = when (peek()) {
    JsonToken.BEGIN_ARRAY -> {
        beginArray()
        var first = ""
        while (hasNext()) {
            val v = str()
            if (first.isEmpty() && v.isNotBlank()) first = v
        }
        endArray()
        first
    }
    else -> str()
}

internal fun cleanRating(r: String): String {
    val d = r.trim().toDoubleOrNull() ?: return ""
    if (d <= 0.0) return ""
    val v = if (d > 10) d / 10 else d
    return String.format(java.util.Locale.US, "%.1f", v)
}
