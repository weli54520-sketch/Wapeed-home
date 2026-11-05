// WM Launcher — نسخة الكود رقم 4
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

data class City(val name: String, val lat: Double, val lon: Double, val cc: String = "SA", val sub: String = "")
data class PrayerDay(val list: List<Prayer>, val tz: String)
data class League(val slug: String, val name: String, val def: Boolean)
data class Weather(val temp: Int, val feels: Int, val humidity: Int, val wind: Int, val status: String, val cat: Int = 0, val isDay: Boolean = true)
data class Prayer(val name: String, val time: LocalTime)
data class Match(
    val league: String, val home: String, val homeLogo: String,
    val away: String, val awayLogo: String, val center: String,
    val note: String, val live: Boolean, val time: ZonedDateTime
)

object Data {
    private fun c(n: String, lat: Double, lon: Double, cc: String = "SA") = City(n, lat, lon, cc)

    // مناطق المملكة الـ13 ومدنها
    val saudiRegions: List<Pair<String, List<City>>> = listOf(
        "منطقة الرياض" to listOf(
            c("الرياض", 24.7136, 46.6753), c("الخرج", 24.1556, 47.3346), c("الدرعية", 24.7341, 46.5750),
            c("المجمعة", 25.9039, 45.3456), c("الزلفي", 26.2994, 44.8150), c("شقراء", 25.2483, 45.2528),
            c("الدوادمي", 24.5077, 44.3924), c("عفيف", 23.9065, 42.9172), c("القويعية", 24.0737, 45.2806),
            c("وادي الدواسر", 20.4607, 44.7809), c("الأفلاج", 22.2833, 46.7333), c("حوطة بني تميم", 23.5167, 46.8333)
        ),
        "منطقة مكة المكرمة" to listOf(
            c("مكة المكرمة", 21.3891, 39.8579), c("جدة", 21.4858, 39.1925), c("الطائف", 21.2703, 40.4158),
            c("رابغ", 22.7986, 39.0349), c("القنفذة", 19.1281, 41.0787), c("الليث", 20.1500, 40.2667),
            c("الجموم", 21.6167, 39.7000), c("الخرمة", 21.9214, 42.0418), c("رنية", 21.2600, 42.8500), c("تربة", 21.2142, 41.6331)
        ),
        "منطقة المدينة المنورة" to listOf(
            c("المدينة المنورة", 24.5247, 39.5692), c("ينبع", 24.0895, 38.0618), c("العلا", 26.6085, 37.9232),
            c("بدر", 23.7800, 38.7906), c("خيبر", 25.6980, 39.2920), c("الحناكية", 24.8790, 40.5160), c("مهد الذهب", 23.5000, 40.8667)
        ),
        "منطقة القصيم" to listOf(
            c("بريدة", 26.3260, 43.9750), c("عنيزة", 26.0840, 43.9940), c("الرس", 25.8694, 43.4973),
            c("البكيرية", 26.1392, 43.6575), c("المذنب", 25.8601, 44.2223), c("البدائع", 25.9800, 43.7300)
        ),
        "المنطقة الشرقية" to listOf(
            c("الدمام", 26.4207, 50.0888), c("الخبر", 26.2172, 50.1971), c("الظهران", 26.2361, 50.0393),
            c("الأحساء", 25.3833, 49.5867), c("الجبيل", 27.0174, 49.6225), c("القطيف", 26.5196, 50.0115),
            c("حفر الباطن", 28.4328, 45.9708), c("الخفجي", 28.4391, 48.4913), c("رأس تنورة", 26.6436, 50.1597),
            c("بقيق", 25.9350, 49.6650), c("النعيرية", 27.4700, 48.4900)
        ),
        "منطقة عسير" to listOf(
            c("أبها", 18.2164, 42.5053), c("خميس مشيط", 18.3000, 42.7333), c("بيشة", 19.9930, 42.6000),
            c("النماص", 19.1200, 42.1300), c("محايل عسير", 18.5500, 42.0500), c("رجال ألمع", 18.2050, 42.2700),
            c("تثليث", 19.5330, 43.5000), c("سراة عبيدة", 18.0900, 43.1100), c("ظهران الجنوب", 17.6700, 43.5100)
        ),
        "منطقة تبوك" to listOf(
            c("تبوك", 28.3835, 36.5662), c("الوجه", 26.2333, 36.4667), c("ضباء", 27.3500, 35.6900),
            c("تيماء", 27.6333, 38.5500), c("أملج", 25.0200, 37.2600), c("حقل", 29.2900, 34.9400)
        ),
        "منطقة حائل" to listOf(
            c("حائل", 27.5114, 41.7208), c("بقعاء", 27.8900, 42.4100), c("الغزالة", 26.7700, 41.3200), c("الشنان", 27.1800, 42.4400)
        ),
        "منطقة الحدود الشمالية" to listOf(
            c("عرعر", 30.9753, 41.0381), c("رفحاء", 29.6264, 43.4930), c("طريف", 31.6725, 38.6637), c("العويقيلة", 30.3400, 42.2500)
        ),
        "منطقة جازان" to listOf(
            c("جازان", 16.8892, 42.5511), c("صبيا", 17.1500, 42.6256), c("أبو عريش", 16.9689, 42.8325),
            c("صامطة", 16.5967, 42.9447), c("بيش", 17.3770, 42.5390), c("الدرب", 17.7250, 42.2550),
            c("فرسان", 16.7022, 42.1183), c("العارضة", 17.0480, 43.0750)
        ),
        "منطقة نجران" to listOf(
            c("نجران", 17.4924, 44.1277), c("شرورة", 17.4850, 47.1100), c("حبونا", 17.7700, 44.6400)
        ),
        "منطقة الباحة" to listOf(
            c("الباحة", 20.0129, 41.4677), c("بلجرشي", 19.8580, 41.5580), c("المخواة", 19.7800, 41.4300), c("العقيق", 20.2700, 41.6500)
        ),
        "منطقة الجوف" to listOf(
            c("سكاكا", 29.9697, 40.2064), c("القريات", 31.3317, 37.3428), c("دومة الجندل", 29.8125, 39.8700), c("طبرجل", 30.4999, 38.2160)
        )
    )

    // الدول العربية
    val arabCountries: List<Pair<String, List<City>>> = listOf(
        "الإمارات" to listOf(
            c("دبي", 25.2048, 55.2708, "AE"), c("أبوظبي", 24.4539, 54.3773, "AE"), c("الشارقة", 25.3463, 55.4209, "AE"),
            c("العين", 24.2075, 55.7447, "AE"), c("عجمان", 25.4052, 55.5136, "AE"), c("رأس الخيمة", 25.8007, 55.9762, "AE"), c("الفجيرة", 25.1288, 56.3265, "AE")
        ),
        "الكويت" to listOf(c("مدينة الكويت", 29.3759, 47.9774, "KW"), c("الجهراء", 29.3375, 47.6581, "KW"), c("الأحمدي", 29.0769, 48.0838, "KW")),
        "قطر" to listOf(c("الدوحة", 25.2854, 51.5310, "QA"), c("الوكرة", 25.1659, 51.6034, "QA"), c("الخور", 25.6839, 51.4969, "QA")),
        "البحرين" to listOf(c("المنامة", 26.2285, 50.5860, "BH"), c("المحرق", 26.2572, 50.6119, "BH"), c("الرفاع", 26.1300, 50.5550, "BH")),
        "عُمان" to listOf(
            c("مسقط", 23.5880, 58.3829, "OM"), c("صلالة", 17.0151, 54.0924, "OM"), c("صحار", 24.3470, 56.7090, "OM"),
            c("نزوى", 22.9333, 57.5333, "OM"), c("صور", 22.5667, 59.5289, "OM")
        ),
        "اليمن" to listOf(
            c("صنعاء", 15.3694, 44.1910, "YE"), c("عدن", 12.7855, 45.0187, "YE"), c("تعز", 13.5795, 44.0209, "YE"),
            c("الحديدة", 14.7978, 42.9545, "YE"), c("المكلا", 14.5425, 49.1242, "YE")
        ),
        "الأردن" to listOf(c("عمّان", 31.9454, 35.9284, "JO"), c("إربد", 32.5556, 35.8500, "JO"), c("الزرقاء", 32.0728, 36.0880, "JO"), c("العقبة", 29.5321, 35.0063, "JO")),
        "فلسطين" to listOf(
            c("القدس", 31.7683, 35.2137, "PS"), c("غزة", 31.5017, 34.4668, "PS"), c("رام الله", 31.9038, 35.2034, "PS"),
            c("نابلس", 32.2211, 35.2544, "PS"), c("الخليل", 31.5326, 35.0998, "PS")
        ),
        "لبنان" to listOf(c("بيروت", 33.8938, 35.5018, "LB"), c("طرابلس", 34.4367, 35.8497, "LB"), c("صيدا", 33.5631, 35.3689, "LB")),
        "سوريا" to listOf(c("دمشق", 33.5138, 36.2765, "SY"), c("حلب", 36.2021, 37.1343, "SY"), c("حمص", 34.7324, 36.7137, "SY"), c("اللاذقية", 35.5317, 35.7914, "SY")),
        "العراق" to listOf(
            c("بغداد", 33.3152, 44.3661, "IQ"), c("البصرة", 30.5085, 47.7804, "IQ"), c("الموصل", 36.3350, 43.1189, "IQ"),
            c("أربيل", 36.1911, 44.0092, "IQ"), c("النجف", 32.0259, 44.3462, "IQ"), c("كربلاء", 32.6160, 44.0249, "IQ")
        ),
        "مصر" to listOf(
            c("القاهرة", 30.0444, 31.2357, "EG"), c("الإسكندرية", 31.2001, 29.9187, "EG"), c("الجيزة", 30.0131, 31.2089, "EG"),
            c("المنصورة", 31.0409, 31.3785, "EG"), c("الأقصر", 25.6872, 32.6396, "EG"), c("أسوان", 24.0889, 32.8998, "EG"), c("شرم الشيخ", 27.9158, 34.3300, "EG")
        ),
        "السودان" to listOf(c("الخرطوم", 15.5007, 32.5599, "SD"), c("أم درمان", 15.6445, 32.4777, "SD"), c("بورتسودان", 19.6158, 37.2164, "SD")),
        "ليبيا" to listOf(c("طرابلس الغرب", 32.8872, 13.1913, "LY"), c("بنغازي", 32.1167, 20.0667, "LY"), c("مصراتة", 32.3754, 15.0925, "LY")),
        "تونس" to listOf(c("تونس العاصمة", 36.8065, 10.1815, "TN"), c("صفاقس", 34.7406, 10.7603, "TN"), c("سوسة", 35.8256, 10.6084, "TN")),
        "الجزائر" to listOf(c("الجزائر العاصمة", 36.7538, 3.0588, "DZ"), c("وهران", 35.6971, -0.6308, "DZ"), c("قسنطينة", 36.3650, 6.6147, "DZ")),
        "المغرب" to listOf(
            c("الرباط", 34.0209, -6.8416, "MA"), c("الدار البيضاء", 33.5731, -7.5898, "MA"), c("مراكش", 31.6295, -7.9811, "MA"),
            c("فاس", 34.0181, -5.0078, "MA"), c("طنجة", 35.7595, -5.8340, "MA")
        ),
        "موريتانيا" to listOf(c("نواكشوط", 18.0735, -15.9582, "MR")),
        "جيبوتي" to listOf(c("جيبوتي", 11.8251, 42.5903, "DJ")),
        "الصومال" to listOf(c("مقديشو", 2.0469, 45.3182, "SO")),
        "جزر القمر" to listOf(c("موروني", -11.7172, 43.2473, "KM"))
    )

    // القائمة القديمة (لترحيل اختيار المستخدم السابق)
    val legacyCities = listOf(
        c("الرياض", 24.7136, 46.6753), c("جدة", 21.4858, 39.1925), c("مكة المكرمة", 21.3891, 39.8579),
        c("المدينة المنورة", 24.5247, 39.5692), c("الدمام", 26.4207, 50.0888), c("أبها", 18.2164, 42.5053),
        c("تبوك", 28.3835, 36.5662), c("بريدة", 26.3260, 43.9750), c("صبيا", 17.1500, 42.6256)
    )

    fun encode(x: City) = listOf(x.name.replace("|", " "), x.lat, x.lon, x.cc, x.sub.replace("|", " ")).joinToString("|")
    fun decode(s: String): City? = try {
        val p = s.split("|")
        City(p[0], p[1].toDouble(), p[2].toDouble(), p.getOrElse(3) { "SA" }, p.getOrElse(4) { "" })
    } catch (e: Throwable) { null }

    // البحث عن أي مدينة عبر الشبكة (Open-Meteo Geocoding) بالعربي أو الإنجليزي
    fun searchCities(q: String): List<City> {
        val u = "https://geocoding-api.open-meteo.com/v1/search?count=15&language=ar&format=json&name=" +
            java.net.URLEncoder.encode(q.trim(), "UTF-8")
        val arr = JSONObject(get(u)).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<City>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val sub = listOf(o.optString("admin1", ""), o.optString("country", ""))
                .filter { it.isNotEmpty() }.distinct().joinToString(" • ")
            out.add(City(o.optString("name"), o.getDouble("latitude"), o.getDouble("longitude"), o.optString("country_code", ""), sub))
        }
        return out
    }

    // تحديد الموقع تلقائياً من عنوان الإنترنت (بدون GPS)
    fun locate(): City? {
        var name = ""; var lat = 0.0; var lon = 0.0; var cc = ""
        try {
            val o = JSONObject(get("https://ipwho.is/?lang=en"))
            if (o.optBoolean("success", false)) {
                name = o.optString("city"); lat = o.getDouble("latitude"); lon = o.getDouble("longitude"); cc = o.optString("country_code")
            }
        } catch (e: Throwable) { }
        if (name.isEmpty()) {
            try {
                val o = JSONObject(get("https://ipapi.co/json/"))
                name = o.optString("city"); lat = o.getDouble("latitude"); lon = o.getDouble("longitude"); cc = o.optString("country_code")
            } catch (e: Throwable) { }
        }
        if (name.isEmpty()) return null
        // نحاول نجيب الاسم بالعربي
        val ar = try { searchCities(name).firstOrNull { it.cc.equals(cc, true) } } catch (e: Throwable) { null }
        return if (ar != null) City(ar.name, lat, lon, cc, ar.sub) else City(name, lat, lon, cc)
    }

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // cat: 0 صافي، 1 غائم جزئياً، 2 غائم، 3 ضباب، 4 مطر، 5 ثلج، 6 عاصفة (لأيقونة الطقس)
    fun weather(c: City): Weather {
        val u = "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
        val w = JSONObject(get(u)).getJSONObject("current")
        val code = w.getInt("weather_code")
        val st = when (code) {
            0 -> "سماء صافية"
            1, 2 -> "غائم جزئياً"
            3 -> "غائم"
            45, 48 -> "ضباب"
            in 51..57 -> "رذاذ"
            in 61..67 -> "أمطار"
            in 71..77, 85, 86 -> "ثلوج"
            in 80..82 -> "زخات"
            else -> if (code >= 95) "عواصف رعدية" else "غائم"
        }
        val cat = when (code) {
            0 -> 0
            1, 2 -> 1
            3 -> 2
            45, 48 -> 3
            in 51..67, in 80..82 -> 4
            in 71..77, 85, 86 -> 5
            else -> if (code >= 95) 6 else 2
        }
        val day = w.optInt("is_day", -1).let { if (it >= 0) it == 1 else LocalTime.now(ZoneId.of("Asia/Riyadh")).hour in 6..17 }
        return Weather(
            Math.round(w.getDouble("temperature_2m")).toInt(),
            Math.round(w.getDouble("apparent_temperature")).toInt(),
            w.getInt("relative_humidity_2m"),
            Math.round(w.getDouble("wind_speed_10m")).toInt(), st, cat, day
        )
    }

    // طريقة الحساب حسب الدولة (أم القرى للسعودية)
    private fun method(cc: String): Int = when (cc.uppercase(Locale.ROOT)) {
        "SA", "", "YE" -> 4; "AE" -> 16; "KW" -> 9; "QA" -> 10; "BH", "OM" -> 8
        "EG", "SD", "LY", "SY", "LB" -> 5; "JO", "PS" -> 23; "TN" -> 18; "DZ" -> 19; "MA" -> 21
        else -> 3
    }

    fun prayers(c: City): PrayerDay {
        // بدون timezonestring: الخادم يحسب المنطقة الزمنية من الإحداثيات
        val u = "https://api.aladhan.com/v1/timings?latitude=${c.lat}&longitude=${c.lon}&method=${method(c.cc)}"
        val data = JSONObject(get(u)).getJSONObject("data")
        val t = data.getJSONObject("timings")
        val tz = data.optJSONObject("meta")?.optString("timezone", "") ?: ""
        val list = listOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
            .map { Prayer(it.second, LocalTime.parse(t.getString(it.first).substring(0, 5))) }
        return PrayerDay(list, tz)
    }

    // البطولات المتاحة (اللي قيمتها true تكون مفعلة افتراضياً)
    val leagues = listOf(
        League("ksa.1", "دوري روشن السعودي", true),
        League("ksa.kings.cup", "كأس الملك", true),
        League("afc.champions", "أبطال آسيا للنخبة (الغرب)", true),
        League("uefa.champions", "دوري أبطال أوروبا", true),
        League("eng.1", "الدوري الإنجليزي", true),
        League("esp.1", "الدوري الإسباني", true),
        League("ita.1", "الدوري الإيطالي", false),
        League("ger.1", "الدوري الألماني", false),
        League("fra.1", "الدوري الفرنسي", false),
        League("uefa.europa", "الدوري الأوروبي", false)
    )

    // أندية غرب آسيا (السعودية، الخليج، إيران، العراق، الأردن، أوزبكستان...)
    private val westAsia = listOf(
        "al ", "shabab", "sharjah", "persepolis", "esteghlal", "sepahan", "tractor", "foolad", "zob ahan", "nassaji",
        "gol gohar", "pakhtakor", "nasaf", "navbahor", "neftchi", "agmk", "bunyodkor", "sogdiana", "istiklol",
        "ahal", "arkadag", "zakho", "erbil", "duhok", "damac", "neom", "ettifaq", "taawoun", "fateh", "fayha",
        "khaleej", "qadsiah", "riyadh", "okhdood", "kholood", "orobah", "hazem", "najma", "sadd", "gharafa",
        "duhail", "rayyan", "wakrah", "wasl", "wahda", "jazira", "kuwait", "faisaly", "hussein", "mohun bagan"
    )

    // أندية شرق آسيا (اليابان، كوريا، الصين، أستراليا، جنوب شرق آسيا) — تُستبعد دائماً
    private val eastAsia = listOf(
        "kobe", "hiroshima", "yokohama", "kawasaki", "urawa", "machida", "kashima", "tokyo", "nagoya", "cerezo",
        "gamba", "kashiwa", "ulsan", "pohang", "gwangju", "jeonbuk", "seoul", "gangwon", "daegu", "suwon",
        "incheon", "daejeon", "jeju", "shanghai", "shandong", "chengdu", "beijing", "wuhan", "tianjin", "zhejiang",
        "shenzhen", "melbourne", "sydney", "central coast", "brisbane", "adelaide", "perth", "johor", "selangor",
        "sabah", "buriram", "bangkok", "port fc", "muangthong", "ratchaburi", "bg pathum", "lion city", "tampines",
        "kitchee", "eastern", "persib", "bali", "hanoi", "hai phong", "nam dinh", "cong an", "kaya", "dynamic herb"
    )

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace("-", " ").trim()

    private fun isEast(n: String): Boolean { val s = norm(n); return eastAsia.any { s.contains(it) } }
    private fun isWest(n: String): Boolean { val s = norm(n); return !isEast(n) && westAsia.any { s.startsWith(it) || s.contains(it) } }

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
        "marseille" to "مارسيليا", "as roma" to "روما", "roma" to "روما", "lazio" to "لاتسيو",
        "fiorentina" to "فيورنتينا", "bologna" to "بولونيا", "como" to "كومو", "torino" to "تورينو",
        "vfb stuttgart" to "شتوتغارت", "eintracht frankfurt" to "آينتراخت فرانكفورت", "wolfsburg" to "فولفسبورغ",
        "lyon" to "ليون", "lille" to "ليل", "nice" to "نيس", "lens" to "لانس", "aston villa fc" to "أستون فيلا",
        "al ahli jeddah" to "الأهلي", "al ittihad jeddah" to "الاتحاد", "al taawon" to "التعاون", "monaco" to "موناكو", "club brugge" to "كلوب بروج", "celtic" to "سلتيك"
    )

    // مصادر الشعار بالترتيب: شعار ESPN، رابط ESPN برقم النادي، ثم اسم النادي (للبحث في TheSportsDB)
    private fun logo(t: JSONObject): String {
        val out = ArrayList<String>()
        val l = t.optString("logo", "")
        if (l.isNotEmpty()) out.add(l)
        val id = t.optString("id", "")
        if (id.isNotEmpty()) out.add("https://a.espncdn.com/i/teamlogos/soccer/500/$id.png")
        val en = t.optString("displayName", "").ifEmpty { t.optString("shortDisplayName", "") }.replace("|", " ")
        if (en.isNotEmpty()) out.add("@$en")
        return out.joinToString("|")
    }

    // البحث عن شعار نادي في TheSportsDB (مجاني)
    fun badgeFor(en: String): String? {
        val names = linkedSetOf(en, en.replace(Regex("\\b(FC|SC|CF|FK|AFC|Club)\\b"), "").trim(), en.replace("-", " "))
        for (key in listOf("123", "3")) for (n in names) {
            if (n.length < 3) continue
            try {
                val j = JSONObject(get("https://www.thesportsdb.com/api/v1/json/$key/searchteams.php?t=" + java.net.URLEncoder.encode(n, "UTF-8")))
                val arr = j.optJSONArray("teams") ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("strSport") != "Soccer") continue
                    val b = o.optString("strBadge", "").ifEmpty { o.optString("strTeamBadge", "") }
                    if (b.startsWith("http")) return b
                }
            } catch (e: Throwable) { }
        }
        return null
    }

    private fun ar(t: JSONObject): String {
        val full = t.optString("displayName", "")
        val short = t.optString("shortDisplayName", "")
        return tr[norm(full)] ?: tr[norm(short)] ?: short.ifEmpty { full }
    }

    fun matches(enabled: Set<String>): List<Match> {
        val z = ZoneId.of("Asia/Riyadh")
        val today = LocalDate.now(z)
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale("ar", "SA"))
        val active = leagues.filter { it.slug in enabled }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
        val jobs = active.flatMap { lg -> listOf(today.minusDays(1), today).map { d ->
            pool.submit(java.util.concurrent.Callable {
                try {
                    lg to JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/${lg.slug}/scoreboard?dates=" +
                        d.format(DateTimeFormatter.BASIC_ISO_DATE))).optJSONArray("events")
                } catch (ex: Throwable) { lg to null }
            })
        } }
        val seen = HashSet<String>()
        val out = ArrayList<Match>()
        for (job in jobs) {
            val (lg, events) = try { job.get(20, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Throwable) { continue }
            if (events == null) continue
            for (i in 0 until events.length()) {
                try {
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
                    if (lg.slug == "afc.champions") {
                        val hn = ht.optString("displayName"); val an = at.optString("displayName")
                        // نستبعد أي مباراة فيها نادي من شرق آسيا، ونقبل فقط مباريات أندية الغرب
                        if (isEast(hn) || isEast(an)) continue
                        if (!isWest(hn) && !isWest(an)) continue
                    }
                    val st = ev.getJSONObject("status").getJSONObject("type")
                    val state = st.optString("state", "pre")
                    out.add(Match(
                        lg.name.replace(" (الغرب)", ""), ar(ht), logo(ht), ar(at), logo(at),
                        if (state == "pre") dt.format(fmt) else h.optString("score", "0") + " - " + a.optString("score", "0"),
                        when (state) { "in" -> st.optString("shortDetail", "مباشر"); "post" -> "انتهت"; else -> "" },
                        state == "in", dt
                    ))
                } catch (ex: Throwable) { }
            }
        }
        pool.shutdown()
        return out.sortedBy { it.time }
    }
}
