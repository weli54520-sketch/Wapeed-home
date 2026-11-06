package com.wm.iptv.data

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * ترجمة تفاصيل الأفلام والمسلسلات للعربي.
 * - التصنيفات (Drama, Action...) من قاموس محلي: فوري وبدون نت.
 * - القصة: خدمة ترجمة Google المجانية، مع كاش في الذاكرة، ولو فشلت نرجع النص الأصلي.
 */
object Translate {
    private val cache = ConcurrentHashMap<String, String>()
    private val arabicChar = Regex("[\\u0600-\\u06FF]")
    private val latinChar = Regex("[A-Za-z]")

    /** النص عربي أصلاً؟ (أغلب حروفه عربية) */
    fun isArabic(s: String): Boolean {
        val ar = arabicChar.findAll(s).count()
        val la = latinChar.findAll(s).count()
        return ar > 0 && ar >= la
    }

    /** يترجم للعربي. يرجع النص الأصلي لو عربي أصلاً أو لو فشلت الترجمة. (يشتغل في خيط خلفي) */
    fun toArabic(text: String, timeoutMs: Int = 4000): String {
        val t = text.trim()
        if (t.isEmpty() || isArabic(t) || !latinChar.containsMatchIn(t)) return text
        cache[t]?.let { return it }
        return try {
            val c = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ar&dt=t").openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            c.outputStream.use { it.write(("q=" + URLEncoder.encode(t.take(4500), "UTF-8")).toByteArray()) }
            val body = try { c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } } finally { c.disconnect() }
            val parts = JSONArray(body).optJSONArray(0) ?: return text
            val sb = StringBuilder()
            for (i in 0 until parts.length()) parts.optJSONArray(i)?.optString(0)?.let { if (it != "null") sb.append(it) }
            val out = sb.toString().trim()
            if (out.isEmpty()) text else out.also { cache[t] = it }
        } catch (_: Exception) {
            text
        }
    }

    private val genres = mapOf(
        "action" to "أكشن", "adventure" to "مغامرة", "animation" to "رسوم متحركة", "anime" to "أنمي",
        "comedy" to "كوميدي", "crime" to "جريمة", "documentary" to "وثائقي", "drama" to "دراما",
        "family" to "عائلي", "fantasy" to "فانتازيا", "history" to "تاريخي", "historical" to "تاريخي",
        "horror" to "رعب", "music" to "موسيقى", "musical" to "موسيقي", "mystery" to "غموض",
        "romance" to "رومانسي", "science fiction" to "خيال علمي", "sci-fi" to "خيال علمي", "scifi" to "خيال علمي",
        "thriller" to "إثارة", "war" to "حرب", "western" to "غربي", "biography" to "سيرة ذاتية",
        "sport" to "رياضة", "sports" to "رياضة", "kids" to "أطفال", "news" to "أخبار", "reality" to "واقعي",
        "talk" to "حواري", "soap" to "مسلسل درامي", "tv movie" to "فيلم تلفزيوني", "short" to "قصير",
        "action & adventure" to "أكشن ومغامرة", "sci-fi & fantasy" to "خيال علمي وفانتازيا",
        "war & politics" to "حرب وسياسة", "suspense" to "تشويق", "superhero" to "أبطال خارقين",
        "psychological" to "نفسي", "disaster" to "كوارث", "martial arts" to "فنون قتالية", "teen" to "مراهقين",
    )

    /** يترجم التصنيفات من قاموس محلي، والكلمات اللي مو فيه تبقى مثل ما هي */
    fun genre(g: String): String {
        if (g.isBlank() || isArabic(g)) return g
        val whole = genres[g.trim().lowercase()]
        if (whole != null) return whole
        return g.split(',', '/', '|').map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("، ") { part -> genres[part.lowercase()] ?: part.split('&').joinToString(" و") { w -> genres[w.trim().lowercase()] ?: w.trim() } }
    }
}
