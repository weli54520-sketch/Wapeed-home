package com.wm.iptv.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** كل الإعدادات والمفضلة وأماكن التوقف — محفوظة لكل حساب لحاله */
class Store(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wmtv", Context.MODE_PRIVATE)

    // ───────────── الحسابات ─────────────
    fun accounts(): List<Account> {
        val arr = try { JSONArray(prefs.getString("accounts", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Account(o.optString("id"), o.optString("name"), o.optString("server"), o.optString("user"), o.optString("pass"), o.optString("ua"))
        }
    }

    fun saveAccount(a: Account) {
        val list = accounts().filter { it.id != a.id } + a
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("server", it.server).put("user", it.username).put("pass", it.password).put("ua", it.userAgent)) }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    fun deleteAccount(id: String) {
        val list = accounts().filter { it.id != id }
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("server", it.server).put("user", it.username).put("pass", it.password).put("ua", it.userAgent)) }
        val e = prefs.edit().putString("accounts", arr.toString())
        if (currentAccountId == id) e.putString("current", list.firstOrNull()?.id ?: "")
        e.apply()
    }

    var currentAccountId: String
        get() = prefs.getString("current", "") ?: ""
        set(v) { prefs.edit().putString("current", v).apply() }

    fun currentAccount(): Account? = accounts().let { l -> l.firstOrNull { it.id == currentAccountId } ?: l.firstOrNull() }

    private fun k(name: String) = "${currentAccountId}:$name"

    // ───────────── مجموعات (مفضلة، مخفي، مشاهد) ─────────────
    private fun getSet(name: String): Set<String> = prefs.getStringSet(k(name), emptySet())?.toSet() ?: emptySet()
    private fun putSet(name: String, v: Set<String>) { prefs.edit().putStringSet(k(name), HashSet(v)).apply() }

    fun favorites(kind: Kind): Set<Int> = getSet("fav_${kind.name}").mapNotNull { it.toIntOrNull() }.toSet()

    /** يرجع true إذا صار مفضل */
    fun toggleFavorite(kind: Kind, id: Int): Boolean {
        val s = getSet("fav_${kind.name}").toMutableSet()
        val added = if (s.contains(id.toString())) { s.remove(id.toString()); false } else { s.add(id.toString()); true }
        putSet("fav_${kind.name}", s)
        // نحفظ ترتيب الإضافة
        val order = favoriteOrder(kind).toMutableList()
        order.remove(id)
        if (added) order.add(0, id)
        prefs.edit().putString(k("favorder_${kind.name}"), order.joinToString(",")).apply()
        return added
    }

    fun favoriteOrder(kind: Kind): List<Int> {
        val favs = favorites(kind)
        val order = (prefs.getString(k("favorder_${kind.name}"), "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.filter { it in favs }
        return order + favs.filter { it !in order }
    }

    fun hiddenCategories(kind: Kind): Set<String> = getSet("hidden_${kind.name}")
    fun setCategoryHidden(kind: Kind, catId: String, hidden: Boolean) {
        val s = getSet("hidden_${kind.name}").toMutableSet()
        if (hidden) s.add(catId) else s.remove(catId)
        putSet("hidden_${kind.name}", s)
    }

    // ───────────── القنوات الأخيرة ─────────────
    fun recentChannels(): List<Int> = (prefs.getString(k("recent_ch"), "") ?: "").split(',').mapNotNull { it.toIntOrNull() }

    fun pushRecentChannel(id: Int) {
        val l = recentChannels().toMutableList()
        val prev = l.firstOrNull()
        l.remove(id); l.add(0, id)
        val e = prefs.edit().putString(k("recent_ch"), l.take(30).joinToString(","))
        if (prev != null && prev != id) e.putInt(k("prev_ch"), prev)
        e.apply()
    }

    val lastChannel: Int get() = recentChannels().firstOrNull() ?: 0
    val previousChannel: Int get() = prefs.getInt(k("prev_ch"), 0)

    // ───────────── أماكن التوقف ─────────────
    private fun progressJson(): JSONObject = try { JSONObject(prefs.getString(k("progress"), "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    fun progress(key: String): Progress? = progressJson().optJSONObject(key)?.let { toProgress(key, it) }

    fun allProgress(): List<Progress> {
        return allProgressFrom(progressJson())
    }

    private fun toProgress(key: String, j: JSONObject) = Progress(
        key, j.optLong("p"), j.optLong("d"), j.optLong("u"), j.optString("t"), j.optString("s"), j.optString("i"), j.optString("x"), j.optInt("sid"),
    )

    fun saveProgress(p: Progress) {
        val o = progressJson()
        o.put(p.key, JSONObject().put("p", p.position).put("d", p.duration).put("u", p.updated)
            .put("t", p.title).put("s", p.subtitle).put("i", p.image).put("x", p.ext).put("sid", p.seriesId))
        // نخلي آخر 400 بس
        if (o.length() > 400) {
            val keep = allProgressFrom(o).take(400).map { it.key }.toSet()
            val rm = ArrayList<String>()
            o.keys().forEach { if (it !in keep) rm.add(it) }
            rm.forEach { o.remove(it) }
        }
        prefs.edit().putString(k("progress"), o.toString()).apply()
    }

    private fun allProgressFrom(o: JSONObject): List<Progress> {
        val out = ArrayList<Progress>()
        o.keys().forEach { key -> o.optJSONObject(key)?.let { out.add(toProgress(key, it)) } }
        return out.sortedByDescending { it.updated }
    }

    fun removeProgress(key: String) {
        val o = progressJson(); o.remove(key)
        prefs.edit().putString(k("progress"), o.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(k("progress")).remove(k("recent_ch")).remove(k("prev_ch")).remove(k("watched")).apply()
    }

    /** حلقات معلّمة كمشاهدة يدوياً أو تلقائياً */
    fun watchedEpisodes(): Set<Int> = getSet("watched").mapNotNull { it.toIntOrNull() }.toSet()
    fun setWatched(id: Int, watched: Boolean) {
        val s = getSet("watched").toMutableSet()
        if (watched) s.add(id.toString()) else s.remove(id.toString())
        putSet("watched", s)
    }

    // ───────────── سجل البحث ─────────────
    fun searchHistory(): List<String> = (prefs.getString("search_hist", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addSearch(q: String) {
        val l = searchHistory().toMutableList()
        l.removeAll { it.equals(q, true) }
        l.add(0, q)
        prefs.edit().putString("search_hist", l.take(12).joinToString("\n")).apply()
    }

    // ───────────── الإعدادات ─────────────
    var lastRefresh: Long
        get() = prefs.getLong(k("last_refresh"), 0L)
        set(v) { prefs.edit().putLong(k("last_refresh"), v).apply() }

    /** 0 تلقائي (حسب عدد الاتصالات) — 1 دايماً — 2 أبداً */
    var seekPreviewMode: Int
        get() = prefs.getInt("seek_preview", 0)
        set(v) { prefs.edit().putInt("seek_preview", v).apply() }

    var livePreview: Boolean
        get() = prefs.getBoolean("live_preview", true)
        set(v) { prefs.edit().putBoolean("live_preview", v).apply() }

    var liveHls: Boolean
        get() = prefs.getBoolean("live_hls", false)
        set(v) { prefs.edit().putBoolean("live_hls", v).apply() }

    /** 0 صغير 1 متوسط 2 كبير 3 كبير جداً */
    var subtitleSize: Int
        get() = prefs.getInt("sub_size", 1)
        set(v) { prefs.edit().putInt("sub_size", v).apply() }

    var preferredAudio: String
        get() = prefs.getString("pref_audio", "ar") ?: "ar"
        set(v) { prefs.edit().putString("pref_audio", v).apply() }

    var preferredSubtitle: String
        get() = prefs.getString("pref_sub", "ar") ?: "ar"
        set(v) { prefs.edit().putString("pref_sub", v).apply() }

    /** تحديث القوائم كل ما ينفتح التطبيق */
    var refreshOnOpen: Boolean
        get() = prefs.getBoolean("refresh_on_open", true)
        set(v) { prefs.edit().putBoolean("refresh_on_open", v).apply() }

    /** ترجمة قصة الفيلم/المسلسل وتصنيفه للعربي */
    var arabicDetails: Boolean
        get() = prefs.getBoolean("arabic_details", true)
        set(v) { prefs.edit().putBoolean("arabic_details", v).apply() }

    /** تقسيم شاشة القنوات (جوال): 0 تلقائي (لما تلف الجهاز بالعرض) — 1 دايماً — 2 إيقاف */
    var liveSplit: Int
        get() = prefs.getInt("live_split", 0)
        set(v) { prefs.edit().putInt("live_split", v).apply() }

    /** الشريط السفلي (جوال): 0 يختفي مع التمرير — 1 دايماً ظاهر — 2 مخفي (يظهر بسحبة من أسفل الشاشة) */
    var barMode: Int
        get() = prefs.getInt("bar_mode", 0)
        set(v) { prefs.edit().putInt("bar_mode", v).apply() }

    var autoNextEpisode: Boolean
        get() = prefs.getBoolean("auto_next", true)
        set(v) { prefs.edit().putBoolean("auto_next", v).apply() }

    var maxConnections: Int
        get() = prefs.getInt(k("max_cons"), 1)
        set(v) { prefs.edit().putInt(k("max_cons"), v).apply() }

    fun saveUserInfo(u: UserInfo) {
        prefs.edit()
            .putString(k("ui_status"), u.status)
            .putLong(k("ui_exp"), u.expDate ?: -1L)
            .putInt(k("max_cons"), u.maxConnections)
            .putInt(k("ui_active"), u.activeConnections)
            .putBoolean(k("ui_trial"), u.isTrial)
            .putLong(k("ui_created"), u.createdAt ?: -1L)
            .apply()
    }

    fun userInfo(): UserInfo? {
        val st = prefs.getString(k("ui_status"), null) ?: return null
        val exp = prefs.getLong(k("ui_exp"), -1L)
        val cr = prefs.getLong(k("ui_created"), -1L)
        return UserInfo(st, if (exp > 0) exp else null, prefs.getInt(k("max_cons"), 1), prefs.getInt(k("ui_active"), 0),
            prefs.getBoolean(k("ui_trial"), false), if (cr > 0) cr else null)
    }
}
