package com.wm.iptv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** مباراة لعرضها في الواجهة */
data class SportMatch(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val homeLogo: String,
    val awayLogo: String,
    val startMs: Long,
    val state: String,      // pre / in / post
    val score: String,      // "2 - 1" أو فاضي قبل البداية
    val detail: String,     // الدقيقة أو "انتهت"
    val weight: Int,        // أهمية المباراة (أندية كبيرة + بطولة)
) {
    val live get() = state == "in"
    val finished get() = state == "post"
}

/** مباريات اليوم من ESPN (مجاني بدون مفتاح) — نفس مصدر اللانشر */
object Sports {
    private class League(val slug: String, val name: String, val weight: Int)

    private val leagues = listOf(
        League("ksa.1", "دوري روشن السعودي", 30),
        League("uefa.champions", "دوري أبطال أوروبا", 40),
        League("eng.1", "الدوري الإنجليزي", 30),
        League("esp.1", "الدوري الإسباني", 30),
        League("afc.champions", "أبطال آسيا للنخبة", 25),
        League("ksa.kings.cup", "كأس الملك", 25),
        League("ita.1", "الدوري الإيطالي", 15),
        League("ger.1", "الدوري الألماني", 15),
        League("fra.1", "الدوري الفرنسي", 10),
        League("uefa.europa", "الدوري الأوروبي", 15),
        League("fifa.world", "كأس العالم", 50),
    )

    // الأندية الكبيرة ترفع أهمية المباراة
    private val bigClubs = mapOf(
        "al hilal" to 40, "al nassr" to 40, "al ittihad" to 35, "al ahli" to 30,
        "real madrid" to 40, "barcelona" to 40, "manchester city" to 30, "liverpool" to 32, "arsenal" to 28,
        "manchester united" to 28, "chelsea" to 24, "bayern munich" to 28, "paris saint germain" to 28,
        "inter milan" to 22, "internazionale" to 22, "juventus" to 20, "ac milan" to 20, "atletico madrid" to 22,
        "tottenham hotspur" to 18, "borussia dortmund" to 18, "napoli" to 16,
    )

    private val tr = mapOf(
        "al hilal" to "الهلال", "al nassr" to "النصر", "al ittihad" to "الاتحاد", "al ahli" to "الأهلي",
        "al ahli saudi" to "الأهلي", "al shabab" to "الشباب", "al ettifaq" to "الاتفاق", "al taawoun" to "التعاون",
        "al fateh" to "الفتح", "al fayha" to "الفيحاء", "al khaleej" to "الخليج", "al qadsiah" to "القادسية",
        "al riyadh" to "الرياض", "al okhdood" to "الأخدود", "al kholood" to "الخلود", "al orobah" to "العروبة",
        "damac" to "ضمك", "neom" to "نيوم", "al najma" to "النجمة", "al hazem" to "الحزم", "al ain" to "العين",
        "al sadd" to "السد", "al gharafa" to "الغرافة", "al duhail" to "الدحيل", "al rayyan" to "الريان",
        "al wasl" to "الوصل", "al wahda" to "الوحدة", "shabab al ahli" to "شباب الأهلي", "sharjah" to "الشارقة",
        "persepolis" to "برسبوليس", "esteghlal" to "استقلال طهران",
        "real madrid" to "ريال مدريد", "barcelona" to "برشلونة", "atletico madrid" to "أتلتيكو مدريد",
        "sevilla" to "إشبيلية", "valencia" to "فالنسيا", "villarreal" to "فياريال", "real sociedad" to "ريال سوسيداد",
        "athletic club" to "أتلتيك بيلباو", "real betis" to "ريال بيتيس", "girona" to "جيرونا",
        "liverpool" to "ليفربول", "arsenal" to "أرسنال", "manchester city" to "مانشستر سيتي",
        "manchester united" to "مانشستر يونايتد", "chelsea" to "تشيلسي", "tottenham hotspur" to "توتنهام",
        "newcastle united" to "نيوكاسل", "aston villa" to "أستون فيلا", "west ham united" to "وست هام",
        "brighton & hove albion" to "برايتون", "everton" to "إيفرتون", "crystal palace" to "كريستال بالاس",
        "fulham" to "فولهام", "brentford" to "برنتفورد", "wolverhampton wanderers" to "وولفرهامبتون",
        "nottingham forest" to "نوتنغهام فورست", "afc bournemouth" to "بورنموث", "leeds united" to "ليدز",
        "paris saint germain" to "باريس سان جيرمان", "bayern munich" to "بايرن ميونخ",
        "borussia dortmund" to "دورتموند", "inter milan" to "إنتر ميلان", "internazionale" to "إنتر ميلان",
        "ac milan" to "ميلان", "juventus" to "يوفنتوس", "napoli" to "نابولي", "atalanta" to "أتالانتا",
        "benfica" to "بنفيكا", "porto" to "بورتو", "sporting cp" to "سبورتينغ لشبونة", "ajax" to "أياكس",
        "bayer leverkusen" to "ليفركوزن", "rb leipzig" to "لايبزيغ", "galatasaray" to "غلطة سراي",
        "marseille" to "مارسيليا", "as roma" to "روما", "lazio" to "لاتسيو", "monaco" to "موناكو",
        "celtic" to "سلتيك", "club brugge" to "كلوب بروج", "psv eindhoven" to "آيندهوفن",
    )

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace("-", " ").trim()

    @Volatile private var cache: Pair<Long, List<SportMatch>>? = null

    /** آخر نتيجة محفوظة (للعرض الفوري) */
    fun cached(): List<SportMatch> = cache?.second ?: emptyList()

    /** مباريات اليوم مرتبة: المباشر أولاً ثم الأهم */
    suspend fun today(): List<SportMatch> {
        cache?.let { (t, l) -> if (System.currentTimeMillis() - t < 3 * 60_000) return l }
        val list = withContext(Dispatchers.IO) {
            val z = ZoneId.systemDefault()
            val today = LocalDate.now(z)
            coroutineScope {
                leagues.flatMap { lg -> listOf(today.minusDays(1), today).map { d -> async { fetch(lg, d, today, z) } } }.awaitAll().flatten()
            }.distinctBy { it.id }
        }
        val sorted = list.sortedWith(compareByDescending<SportMatch> { it.live }.thenBy { it.finished }.thenByDescending { it.weight }.thenBy { it.startMs })
        if (sorted.isNotEmpty() || cache == null) cache = System.currentTimeMillis() to sorted
        return sorted
    }

    private fun fetch(lg: League, d: LocalDate, today: LocalDate, z: ZoneId): List<SportMatch> = try {
        val body = get("https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.slug}/scoreboard?dates=" + d.format(DateTimeFormatter.BASIC_ISO_DATE))
        val events = JSONObject(body).optJSONArray("events")
        val out = ArrayList<SportMatch>()
        if (events != null) for (i in 0 until events.length()) {
            try {
                val ev = events.getJSONObject(i)
                val dt = OffsetDateTime.parse(ev.getString("date")).atZoneSameInstant(z)
                if (dt.toLocalDate() != today) continue
                val cs = ev.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
                var h = cs.getJSONObject(0); var a = cs.getJSONObject(1)
                if (h.optString("homeAway") == "away") { val t = h; h = a; a = t }
                val ht = h.getJSONObject("team"); val at = a.getJSONObject("team")
                val st = ev.getJSONObject("status").getJSONObject("type")
                val state = st.optString("state", "pre")
                val hn = norm(ht.optString("displayName")); val an = norm(at.optString("displayName"))
                out.add(SportMatch(
                    id = ev.getString("id"),
                    league = lg.name,
                    home = name(ht), away = name(at),
                    homeLogo = logo(ht), awayLogo = logo(at),
                    startMs = dt.toInstant().toEpochMilli(),
                    state = state,
                    score = if (state == "pre") "" else "${h.optString("score", "0")} - ${a.optString("score", "0")}",
                    detail = when (state) { "in" -> st.optString("shortDetail", "مباشر"); "post" -> "انتهت"; else -> "" },
                    weight = lg.weight + (bigClubs[hn] ?: 0) + (bigClubs[an] ?: 0),
                ))
            } catch (_: Exception) {}
        }
        out
    } catch (_: Exception) { emptyList() }

    private fun name(t: JSONObject): String {
        val full = t.optString("displayName"); val short = t.optString("shortDisplayName")
        return tr[norm(full)] ?: tr[norm(short)] ?: short.ifEmpty { full }
    }

    private fun logo(t: JSONObject): String =
        t.optString("logo").ifEmpty { t.optString("id").takeIf { it.isNotEmpty() }?.let { "https://a.espncdn.com/i/teamlogos/soccer/500/$it.png" } ?: "" }

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
