package com.riyadh.home

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class City(val name: String, val lat: Double, val lon: Double)
data class Weather(val temp: Int, val feels: Int, val humidity: Int, val wind: Int, val status: String)
data class Prayer(val name: String, val time: LocalTime)
data class Match(
    val league: String, val home: String, val homeLogo: String,
    val away: String, val awayLogo: String, val center: String,
    val note: String, val live: Boolean, val time: ZonedDateTime
)

object Data {
    val cities = listOf(
        City("الرياض", 24.7136, 46.6753), City("جدة", 21.4858, 39.1925),
        City("مكة", 21.3891, 39.8579), City("المدينة", 24.5247, 39.5692),
        City("الدمام", 26.4207, 50.0888), City("أبها", 18.2164, 42.5053),
        City("تبوك", 28.3835, 36.5662), City("بريدة", 26.3260, 43.9750),
        City("صبيا", 17.1500, 42.6256)
    )

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        return c.inputStream.bufferedReader().readText()
    }

    fun weather(c: City): Weather {
        val u = "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
        val w = JSONObject(get(u)).getJSONObject("current")
        val code = w.getInt("weather_code")
        val st = when {
            code == 0 -> "سماء صافية"; code <= 3 -> "غائم جزئياً"; code <= 48 -> "ضباب"
            code <= 67 -> "أمطار"; code <= 82 -> "زخات"; else -> "عواصف"
        }
        return Weather(
            Math.round(w.getDouble("temperature_2m")).toInt(),
            Math.round(w.getDouble("apparent_temperature")).toInt(),
            w.getInt("relative_humidity_2m"),
            Math.round(w.getDouble("wind_speed_10m")).toInt(), st
        )
    }

    fun prayers(c: City): List<Prayer> {
        val u = "https://api.aladhan.com/v1/timings?latitude=${c.lat}&longitude=${c.lon}&method=4&timezonestring=Asia/Riyadh"
        val t = JSONObject(get(u)).getJSONObject("data").getJSONObject("timings")
        return listOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
            .map { Prayer(it.second, LocalTime.parse(t.getString(it.first).substring(0, 5))) }
    }

    private val leagues = listOf(
        "ksa.1" to "دوري روشن السعودي", "esp.1" to "الدوري الإسباني",
        "eng.1" to "الدوري الإنجليزي", "uefa.champions" to "دوري أبطال أوروبا",
        "afc.champions" to "أبطال آسيا للنخبة"
    )

    private val westAsia = listOf(
        "persepolis", "esteghlal", "sepahan", "tractor", "nassaji", "gol gohar",
        "pakhtakor", "nasaf", "neftchi", "agmk", "bunyodkor", "sogdiana", "sharjah", "istiklol"
    )

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace("-", " ").trim()

    private fun isWest(n: String): Boolean {
        val s = norm(n)
        return s.startsWith("al ") || s.contains("shabab") || westAsia.any { s.contains(it) }
    }

    private val tr = mapOf(
        "al hilal" to "الهلال", "al nassr" to "النصر", "al ittihad" to "الاتحاد", "al ahli" to "الأهلي",
        "al ahli saudi" to "الأهلي", "al shabab" to "الشباب", "al ettifaq" to "الاتفاق",
        "al taawoun" to "التعاون", "al fateh" to "الفتح", "al fayha" to "الفيحاء", "al khaleej" to "الخليج",
        "al qadsiah" to "القادسية", "al raed" to "الرائد", "al riyadh" to "الرياض", "al okhdood" to "الأخدود",
        "al kholood" to "الخلود", "al orobah" to "العروبة", "damac" to "ضمك", "neom" to "نيوم",
        "al najma" to "النجمة", "al hazem" to "الحزم", "al ain" to "العين", "al sadd" to "السد",
        "al gharafa" to "الغرافة", "al duhail" to "الدحيل", "al rayyan" to "الريان", "al wakrah" to "الوكرة",
        "al wasl" to "الوصل", "al wahda" to "الوحدة", "al jazira" to "الجزيرة", "shabab al ahli" to "شباب الأهلي",
        "sharjah" to "الشارقة", "persepolis" to "برسبوليس", "esteghlal" to "استقلال طهران",
        "sepahan" to "سباهان", "tractor" to "تراكتور سازي", "nassaji mazandaran" to "نساجي مازندران",
        "pakhtakor" to "باختاكور", "nasaf" to "نسف", "neftchi fergana" to "نيفتشي فرغانة",
        "real madrid" to "ريال مدريد", "barcelona" to "برشلونة", "atletico madrid" to "أتلتيكو مدريد",
        "sevilla" to "إشبيلية", "valencia" to "فالنسيا", "villarreal" to "فياريال",
        "real sociedad" to "ريال سوسيداد", "athletic club" to "أتلتيك بيلباو", "real betis" to "ريال بيتيس",
        "girona" to "جيرونا", "osasuna" to "أوساسونا", "celta vigo" to "سيلتا فيغو", "getafe" to "خيتافي",
        "mallorca" to "مايوركا", "rayo vallecano" to "رايو فاليكانو", "espanyol" to "إسبانيول",
        "alaves" to "ألافيس", "levante" to "ليفانتي", "elche" to "إلتشي",
        "liverpool" to "ليفربول", "arsenal" to "أرسنال", "manchester city" to "مانشستر سيتي",
        "man city" to "مانشستر سيتي", "manchester united" to "مانشستر يونايتد", "man united" to "مانشستر يونايتد",
        "chelsea" to "تشيلسي", "tottenham hotspur" to "توتنهام", "spurs" to "توتنهام",
        "newcastle united" to "نيوكاسل", "newcastle" to "نيوكاسل", "aston villa" to "أستون فيلا",
        "west ham united" to "وست هام", "west ham" to "وست هام", "brighton" to "برايتون", "everton" to "إيفرتون",
        "crystal palace" to "كريستال بالاس", "fulham" to "فولهام", "brentford" to "برنتفورد",
        "wolverhampton wanderers" to "وولفرهامبتون", "wolves" to "وولفرهامبتون",
        "nottingham forest" to "نوتنغهام فورست", "bournemouth" to "بورنموث", "leeds united" to "ليدز",
        "sunderland" to "سندرلاند", "burnley" to "بيرنلي",
        "paris saint germain" to "باريس سان جيرمان", "psg" to "باريس سان جيرمان",
        "bayern munich" to "بايرن ميونخ", "bayern" to "بايرن ميونخ", "borussia dortmund" to "دورتموند",
        "inter milan" to "إنتر ميلان", "internazionale" to "إنتر ميلان", "ac milan" to "ميلان",
        "juventus" to "يوفنتوس", "napoli" to "نابولي", "atalanta" to "أتالانتا", "benfica" to "بنفيكا",
        "porto" to "بورتو", "sporting cp" to "سبورتينغ لشبونة", "ajax" to "أياكس", "psv eindhoven" to "آيندهوفن",
        "bayer leverkusen" to "ليفركوزن", "rb leipzig" to "لايبزيغ", "galatasaray" to "غلطة سراي",
        "marseille" to "مارسيليا", "monaco" to "موناكو", "club brugge" to "كلوب بروج", "celtic" to "سلتيك"
    )

    private fun ar(t: JSONObject): String {
        val full = t.optString("displayName", "")
        val short = t.optString("shortDisplayName", "")
        return tr[norm(full)] ?: tr[norm(short)] ?: short.ifEmpty { full }
    }

    fun matches(): List<Match> {
        val z = ZoneId.of("Asia/Riyadh")
        val today = LocalDate.now(z)
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale("ar", "SA"))
        val seen = HashSet<String>()
        val out = ArrayList<Match>()
        for ((slug, league) in leagues) {
            for (d in listOf(today.minusDays(1), today)) {
                try {
                    val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$slug/scoreboard?dates=" +
                        d.format(DateTimeFormatter.BASIC_ISO_DATE)
                    val events = JSONObject(get(url)).optJSONArray("events") ?: continue
                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        if (!seen.add(ev.getString("id"))) continue
                        val dt = OffsetDateTime.parse(ev.getString("date")).atZoneSameInstant(z)
                        if (dt.toLocalDate() != today) continue
                        val cs = ev.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
                        var h = cs.getJSONObject(0)
                        var a = cs.getJSONObject(1)
                        if (h.optString("homeAway") == "away") { val tmp = h; h = a; a = tmp }
                        val ht = h.getJSONObject("team")
                        val at = a.getJSONObject("team")
                        if (slug == "afc.champions" && !isWest(ht.optString("displayName")) &&
                            !isWest(at.optString("displayName"))) continue
                        val st = ev.getJSONObject("status").getJSONObject("type")
                        val state = st.optString("state", "pre")
                        out.add(Match(
                            league, ar(ht), ht.optString("logo", ""), ar(at), at.optString("logo", ""),
                            if (state == "pre") dt.format(fmt) else h.optString("score", "0") + " - " + a.optString("score", "0"),
                            when (state) { "in" -> st.optString("shortDetail", "مباشر"); "post" -> "انتهت"; else -> "" },
                            state == "in", dt
                        ))
                    }
                } catch (ex: Throwable) { }
            }
        }
        return out.sortedBy { it.time }
    }
}
