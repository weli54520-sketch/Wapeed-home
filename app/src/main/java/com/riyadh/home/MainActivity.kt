package com.riyadh.home

import android.animation.ArgbEvaluator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.Dialog
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/* ───────────────────────── بيانات المدن والطقس والصلاة والمباريات ───────────────────────── */
class WmCity(val name: String, val lat: Double, val lon: Double, val cc: String)

object WmCities {
    val all = listOf(
        WmCity("الرياض", 24.7136, 46.6753, "SA"),
        WmCity("مكة المكرمة", 21.3891, 39.8579, "SA"),
        WmCity("المدينة المنورة", 24.4539, 39.6133, "SA"),
        WmCity("جدة", 21.5433, 39.1728, "SA"),
        WmCity("الدمام", 26.4207, 50.0888, "SA"),
        WmCity("تبوك", 28.3835, 36.5662, "SA"),
        WmCity("أبها", 18.2164, 42.5053, "SA"),
        WmCity("بريدة", 26.3260, 43.9750, "SA")
    )
    val saudi = all
    val arab = listOf(
        WmCity("دبي", 25.2048, 55.2708, "AE"),
        WmCity("الدوحة", 25.2854, 51.5310, "QA"),
        WmCity("الكويت", 29.3759, 47.9774, "KW"),
        WmCity("القاهرة", 30.0444, 31.2357, "EG"),
        WmCity("عمان", 31.9454, 35.9284, "JO")
    )
    val world = listOf(
        WmCity("لندن", 51.5074, -0.1278, "GB"),
        WmCity("باريس", 48.8566, 2.3522, "FR"),
        WmCity("نيويورك", 40.7128, -74.0060, "US")
    )
    fun countryName(cc: String) = when (cc) {
        "SA" -> "السعودية"
        "AE" -> "الإمارات"
        "QA" -> "قطر"
        "KW" -> "الكويت"
        "EG" -> "مصر"
        "JO" -> "الأردن"
        else -> cc
    }
    fun parse(s: String): WmCity? {
        val p = s.split(",")
        return if (p.size >= 4) WmCity(p[0], p[1].toDoubleOrNull() ?: 24.7, p[2].toDoubleOrNull() ?: 46.6, p[3]) else null
    }
    fun encode(c: WmCity) = "${c.name},${c.lat},${c.lon},${c.cc}"
}

class WmPrayer(val name: String, val time: LocalTime)

object WmNet {
    class Hit(val city: WmCity, val sub: String)
    fun search(q: String): List<Hit> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(q, "UTF-8") + "&count=10&language=ar"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val arr = json.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<Hit>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name", q)
            val country = o.optString("country", "")
            val lat = o.optDouble("latitude", 24.7)
            val lon = o.optDouble("longitude", 46.6)
            val cc = o.optString("country_code", "SA")
            list.add(Hit(WmCity(name, lat, lon, cc), country))
        }
        return list
    }
    class WeatherInfo(val temp: Int, val feels: Int, val humidity: Int, val wind: Int, val status: String)
    fun weather(c: WmCity): WeatherInfo {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val cur = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
        val code = cur.optInt("weather_code", 0)
        val st = when (code) {
            0 -> "صو صافٍ ☀️"
            1, 2, 3 -> "غائم جزئياً 🌤️"
            45, 48 -> "ضباب 🌫️"
            51, 53, 55, 56, 57 -> "رذاذ 🌦️"
            61, 63, 65, 66, 67 -> "أمطار 🌧️"
            71, 73, 75, 77 -> "ثلوج ❄️"
            95, 96, 99 -> "عواصف رعدية ⚡"
            else -> "مستقر 🌤️"
        }
        return WeatherInfo(
            cur.optDouble("temperature_2m", 25.0).toInt(),
            cur.optDouble("apparent_temperature", 25.0).toInt(),
            cur.optInt("relative_humidity_2m", 40),
            cur.optDouble("wind_speed_10m", 10.0).toInt(),
            st
        )
    }
    fun prayers(c: WmCity): List<WmPrayer> {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val url = "https://api.aladhan.com/v1/timings/$today?latitude=${c.lat}&longitude=${c.lon}&method=4"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val t = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("data").getJSONObject("timings")
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        fun p(key: String) = LocalTime.parse(t.optString(key, "00:00").substringBefore(" "), fmt)
        return listOf(
            WmPrayer("الفجر", p("Fajr")),
            WmPrayer("الشروق", p("Sunrise")),
            WmPrayer("الظهر", p("Dhuhr")),
            WmPrayer("العصر", p("Asr")),
            WmPrayer("المغرب", p("Maghrib")),
            WmPrayer("العشاء", p("Isha"))
        )
    }
}

class Match(val home: String, val away: String, val homeLogo: String, val awayLogo: String, val center: String, val live: Boolean, val note: String, val league: String, val time: ZonedDateTime)

object Data {
    val cities = WmCities.all
    fun weather(c: WmCity) = WmNet.weather(c)
    fun prayers(c: WmCity) = WmNet.prayers(c)
    fun matches(): List<Match> {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Riyadh"))
        return listOf(
            Match("الهلال", "النصر", "", "", "1 - 0", true, "الدقيقة 75", "دوري روشن السعودي", now.minusMinutes(75)),
            Match("الاتحاد", "الأهلي", "", "", "2 - 1", true, "الشوط الثاني", "دوري روشن السعودي", now.minusMinutes(50)),
            Match("ريال مدريد", "برشلونة", "", "", "س 10:00", false, "", "الدوري الإسباني", now.plusHours(3)),
            Match("مانشستر سيتي", "ليفربول", "", "", "س 8:30", false, "", "الدوري الإنجليزي", now.plusHours(1))
        )
    }
}

/* ───────────────────────── خلفية متحركة ───────────────────────── */
class AmbientView(c: Context, initial: Int) : View(c) {
    private var color = initial
    private var from = initial
    private var to = initial
    private var phase = 0f
    private var base = intArrayOf(0xFF04060A.toInt(), 0xFF0B0E16.toInt(), 0xFF06080F.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 700
        addUpdateListener {
            color = ArgbEvaluator().evaluate(it.animatedValue as Float, from, to) as Int
            invalidate()
        }
    }

    private val pulseAnim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 18000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setPalette(p: IntArray) {
        base = p
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!pulseAnim.isStarted) pulseAnim.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnim.cancel()
        anim.cancel()
        super.onDetachedFromWindow()
    }

    fun glow(target: Int) {
        if (target == to) return
        from = color
        to = target
        anim.cancel()
        anim.start()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val dx = Math.sin(phase.toDouble()).toFloat() * w * 0.02f
        val dy = Math.cos(phase.toDouble()).toFloat() * h * 0.02f

        paint.shader = LinearGradient(0f, 0f, w, h, base, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(w * 0.25f + dx, h * 0.15f + dy, w * 0.6f, intArrayOf(Color.argb(90, r, g, b), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(w * 0.85f - dx, h * 0.2f - dy, w * 0.5f, intArrayOf(Color.argb(70, r, g, b), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)
    }
}

/* ───────────────────────── زجاج حقيقي ───────────────────────── */
class GlassDrawable(
    private val radius: Float,
    private val strokePx: Float,
    private val fillTop: Int,
    private val fillBot: Int,
    private val borderColor: Int,
    private val solidBorder: Boolean,
    private val sheenA: Int
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val inset = RectF()

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val t = b.top.toFloat()
        val bt = b.bottom.toFloat()
        val h = bt - t
        fill.shader = LinearGradient(0f, t, 0f, bt, fillTop, fillBot, Shader.TileMode.CLAMP)
        sheen.shader = LinearGradient(0f, t, 0f, t + h * 0.55f, Color.argb(sheenA, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        line.strokeWidth = strokePx
        val r = Color.red(borderColor)
        val g = Color.green(borderColor)
        val bl = Color.blue(borderColor)
        val a = Color.alpha(borderColor)
        line.shader = if (solidBorder) {
            LinearGradient(b.left.toFloat(), t, b.right.toFloat(), bt, intArrayOf(Color.argb(a, r, g, bl), Color.argb((a * 0.9f).toInt(), r, g, bl), Color.argb(a, r, g, bl)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(b.left.toFloat(), t, b.right.toFloat(), bt, intArrayOf(Color.argb(a, r, g, bl), Color.argb((a * 0.22f).toInt(), r, g, bl), Color.argb((a * 0.65f).toInt(), r, g, bl)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        c.drawRoundRect(rect, radius, radius, fill)
        if (sheenA > 0) c.drawRoundRect(rect, radius, radius, sheen)
        if (strokePx > 0f) {
            val h = strokePx / 2f
            inset.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
            val rr = (radius - h).coerceAtLeast(0f)
            c.drawRoundRect(inset, rr, rr, line)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/* ───────────────────────── شعار WM ───────────────────────── */
class LogoView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun wPath(s: Float, flip: Boolean) {
        path.reset()
        val xs = floatArrayOf(0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
        val ys = floatArrayOf(0.18f, 0.50f, 0.33f, 0.50f, 0.18f)
        for (i in xs.indices) {
            val y = if (flip) 1.08f - ys[i] else ys[i]
            if (i == 0) path.moveTo(xs[i] * s, y * s) else path.lineTo(xs[i] * s, y * s)
        }
    }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val r = s * 0.26f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, s, s, 0xFF2DD4BF.toInt(), 0xFF3B82F6.toInt(), Shader.TileMode.CLAMP)
        cv.drawRoundRect(0f, 0f, s, s, r, r, p)
        p.shader = LinearGradient(0f, 0f, 0f, s * 0.55f, Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        cv.drawRoundRect(0f, 0f, s, s, r, r, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.085f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND

        p.color = Color.argb(150, 255, 255, 255)
        wPath(s, true)
        cv.drawPath(path, p)

        p.color = Color.WHITE
        wPath(s, false)
        cv.drawPath(path, p)

        p.style = Paint.Style.FILL
        p.color = 0xFFFBBF24.toInt()
        cv.drawCircle(0.5f * s, 0.54f * s, s * 0.034f, p)
    }
}

/* ───────────────────────── عناصر الإعدادات ───────────────────────── */
class SwitchView(c: Context) : View(c) {
    var on = false
    var onColor = 0xFF2DD4BF.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((46 * d).toInt(), (26 * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = if (on) onColor else Color.argb(80, 255, 255, 255)
        cv.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, p)
        p.color = Color.WHITE
        val kr = h / 2 - 3 * d
        cv.drawCircle(if (on) w - h / 2 else h / 2, h / 2, kr, p)
    }
}

class BarView(c: Context) : View(c) {
    var frac = 0f
    var barColor = 0xFF2DD4BF.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val th = 6 * d
        val top = (h - th) / 2
        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 255, 255, 255)
        cv.drawRoundRect(0f, top, w, top + th, th / 2, th / 2, p)
        p.color = barColor
        val fx = (w * frac.coerceIn(0f, 1f)).coerceAtLeast(th)
        cv.drawRoundRect(0f, top, fx, top + th, th / 2, th / 2, p)
        p.color = Color.WHITE
        cv.drawCircle((fx - 2 * d).coerceAtLeast(7 * d), h / 2, 7 * d, p)
    }
}

class MaxHeightScroll(c: Context, private val maxPx: Int) : ScrollView(c) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}

class MainActivity : Activity() {
    private val ar = Locale("ar", "SA")
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private val accents: List<Pair<String, Int>> = listOf(
        "تركوازي" to Color.parseColor("#2DD4BF"),
        "سماوي" to Color.parseColor("#38BDF8"),
        "بنفسجي" to Color.parseColor("#A78BFA"),
        "وردي" to Color.parseColor("#F472B6"),
        "برتقالي" to Color.parseColor("#FB923C"),
        "أخضر" to Color.parseColor("#4ADE80"),
        "ذهبي" to Color.parseColor("#FBBF24"),
        "مرجاني" to Color.parseColor("#FB7185")
    )
    private val accent: Int get() = accents[pInt("accent_idx", 0).coerceIn(0, accents.size - 1)].second
    private val gold = Color.parseColor("#FBBF24")
    private val greenBadge = Color.parseColor("#22C55E")
    private val dim = Color.argb(195, 255, 255, 255)
    private val muted = Color.parseColor("#94A3B8")
    private val darkText = Color.parseColor("#0B0F19")

    private val palettes: List<Triple<String, IntArray, Int>> = listOf(
        Triple("ليلي داكن", intArrayOf(0xFF04060A.toInt(), 0xFF0B0E16.toInt(), 0xFF06080F.toInt()), 0xFF141925.toInt()),
        Triple("أزرق عميق", intArrayOf(0xFF071426.toInt(), 0xFF10315A.toInt(), 0xFF081A33.toInt()), 0xFF1E4E8C.toInt()),
        Triple("بنفسجي", intArrayOf(0xFF0C0618.toInt(), 0xFF1E0F3F.toInt(), 0xFF0E0720.toInt()), 0xFF4C2A85.toInt()),
        Triple("أحمر داكن", intArrayOf(0xFF14050A.toInt(), 0xFF3A0B14.toInt(), 0xFF1A060C.toInt()), 0xFF8A1C2B.toInt()),
        Triple("أخضر زمردي", intArrayOf(0xFF03120D.toInt(), 0xFF0A2E22.toInt(), 0xFF04160F.toInt()), 0xFF14705A.toInt()),
        Triple("فحمي", intArrayOf(0xFF09090B.toInt(), 0xFF18181C.toInt(), 0xFF0D0D10.toInt()), 0xFF2A2A31.toInt())
    )

    private lateinit var ambient: AmbientView
    private lateinit var backgroundImageView: ImageView
    private lateinit var overlay: View
    private lateinit var headerView: View
    private lateinit var pages: List<View>
    private lateinit var tabPills: List<LinearLayout>
    private lateinit var tabTexts: List<TextView>
    private var currentPage = 0

    private lateinit var favRow: LinearLayout
    private lateinit var reorderHintTv: TextView
    private lateinit var allGrid: GridLayout
    private lateinit var matchesGrid: GridLayout
    private lateinit var homeMatchRow: LinearLayout
    private lateinit var tickerTv: TextView
    private lateinit var greetingTv: TextView
    private lateinit var clockTv: TextView
    private lateinit var dateLineTv: TextView
    private lateinit var tempTv: TextView
    private lateinit var cityHeaderTv: TextView
    private lateinit var cityPillTv: TextView
    private lateinit var weatherStatusTv: TextView
    private lateinit var weatherSubTv: TextView
    private lateinit var prayerLayout: LinearLayout
    private lateinit var prayerTimerTv: TextView

    // الإعدادات
    private lateinit var settingsNav: List<LinearLayout>
    private lateinit var settingsContent: LinearLayout
    private lateinit var settingsScroll: ScrollView
    private lateinit var settingsTitleTv: TextView
    private lateinit var settingsSubTv: TextView
    private var currentSection = 0
    private var lastFocusedRow = 0

    private var allApps = mutableListOf<ResolveInfo>()
    private var prayersData: List<WmPrayer> = emptyList()
    private var matchesData: List<Match>? = null
    private val statusViews = ArrayList<Pair<TextView, Match>>()
    private var lastMin = -1
    private val logoCache = ConcurrentHashMap<String, Bitmap>()
    private val bgCache = ConcurrentHashMap<String, Bitmap>()
    private val feedsTask = Runnable { fetchMatches(); refreshFeeds() }
    private val clockTask = Runnable { updateClock() }
    private val rebuildTask = Runnable { doRebuild() }

    private var lastTemp: Any? = null
    private var lastFeels: Any? = null
    private var lastHumidity: Any? = null
    private var lastWind: Any? = null
    private var lastStatus: String? = null

    private var reorderingPkg: String? = null
    private var reorderList = mutableListOf<String>()

    // التحديث
    private class UpdateInfo(val code: Int, val name: String, val apk: String, val notes: String, val direct: Boolean)
    private var updateInfo: UpdateInfo? = null
    private var updateMsg = ""
    private var downloading = false
    private var updateSubTv: TextView? = null
    private val installAction = "com.riyadh.home.INSTALL_RESULT"
    private var installReceiver: BroadcastReceiver? = null

    private val weights = mapOf(
        "الهلال" to 100, "ريال مدريد" to 90, "برشلونة" to 90, "مانشستر سيتي" to 90,
        "النصر" to 60, "الاتحاد" to 60, "الأهلي" to 60,
        "ليفربول" to 70, "أرسنال" to 70, "بايرن ميونخ" to 70, "باريس سان جيرمان" to 70,
        "أتلتيكو مدريد" to 70, "مانشستر يونايتد" to 70, "تشيلسي" to 70, "إنتر ميلان" to 70,
        "ميلان" to 70, "يوفنتوس" to 70, "دورتموند" to 70
    )

    private fun score(m: Match) = (weights[m.home] ?: 0) + (weights[m.away] ?: 0)
    private fun rank(m: Match) = score(m) + (if (m.live) 60 else 0) - (if (m.note == "انتهت") 200 else 0)

    /* ─────────── تفضيلات ─────────── */
    private fun pInt(k: String, d: Int) = prefs.getInt(k, d)
    private fun pBool(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun putInt(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
    private fun putBool(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()

    private fun palette() = palettes[pInt("bg_palette", 0).coerceIn(0, palettes.size - 1)]
    private fun homeGlow() = palette().third
    private fun panelOpacity() = (100 - pInt("panel_clear", 30)).coerceIn(0, 100)
    private fun borderPct() = pInt("border_strength", 50)
    private fun borderSharp() = pInt("border_style", 0) == 1
    private fun focusSharp() = pInt("focus_style", 0) == 0
    private fun focusGlassPct() = pInt("focus_glass", 60)
    private fun focusScale() = when (pInt("focus_scale", 1)) { 0 -> 1.05f; 2 -> 1.14f; else -> 1.09f }
    private fun favCols() = listOf(4, 5, 6)[pInt("fav_cols", 1).coerceIn(0, 2)]
    private fun gridCols() = listOf(4, 5, 6)[pInt("grid_cols", 1).coerceIn(0, 2)]
    private fun dateLoc(): Locale = if (pInt("date_lang", 0) == 1) Locale.ENGLISH else ar

    private val indDigits = "٠١٢٣٤٥٦٧٨٩"
    private fun nd(s: String): String = when (pInt("digits", 0)) {
        1 -> buildString { for (ch in s) append(if (ch in '0'..'9') indDigits[ch - '0'] else ch) }
        2 -> buildString { for (ch in s) { val i = indDigits.indexOf(ch); append(if (i >= 0) ('0' + i) else ch) } }
        else -> s
    }

    private fun statusText(m: Match): String = when {
        m.live -> "مباشر 🔴"
        m.note == "انتهت" -> "انتهت"
        else -> {
            val mins = Duration.between(ZonedDateTime.now(), m.time).toMinutes()
            when {
                mins <= 0 -> "تبدأ الآن"
                mins < 180 -> "بعد ${if (mins >= 60) "${mins / 60} س و " else ""}${mins % 60} د"
                else -> "بعد ${mins / 60} س"
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun city(): WmCity {
        val custom = prefs.getString("city_custom", "") ?: ""
        if (pInt("city_idx", 0) == -1 && custom.isNotEmpty()) WmCities.parse(custom)?.let { return it }
        return WmCities.all[pInt("city_idx", 0).coerceIn(0, WmCities.all.size - 1)]
    }

    private fun <T : ViewGroup> T.unclip(): T {
        clipChildren = false
        clipToPadding = false
        return this
    }

    private fun View.roundOutline(radiusDp: Int) {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width > 0 && view.height > 0) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(radiusDp).toFloat())
                }
            }
        }
    }

    /* ─────────── الزجاج والتحديد ─────────── */
    private fun glassOf(fillAlpha: Int, radius: Int, strokeColor: Int, strokeDp: Int): Drawable {
        val a = (fillAlpha * 0.62f * (panelOpacity() / 70f)).toInt().coerceIn(0, 255)
        val sa = Color.alpha(strokeColor)
        val explicitSolid = sa == 255
        val sharp = borderSharp()
        val borderA = if (explicitSolid) 255 else (sa * 2.4f * (borderPct() / 50f)).toInt().coerceIn(0, 255)
        val border = Color.argb(borderA, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor))
        val sp = if (strokeDp <= 0) 0f else maxOf(strokeDp.toFloat(), 1f) * resources.displayMetrics.density * (if (sharp) 1f else 1.15f)
        val k = if (pInt("glass_tint", 0) == 1) 0.32f else 0f
        val ac = accent
        fun mixc(r: Int, g: Int, b: Int, al: Int): Int = Color.argb(
            al,
            (r + (Color.red(ac) - r) * k).toInt(),
            (g + (Color.green(ac) - g) * k).toInt(),
            (b + (Color.blue(ac) - b) * k).toInt()
        )
        val top = if (sharp) mixc(34, 40, 58, a) else mixc(62, 68, 98, a)
        val bot = if (sharp) mixc(34, 40, 58, a) else mixc(20, 24, 38, (a * 0.9f).toInt())
        return GlassDrawable(dp(radius).toFloat(), sp, top, bot, border, explicitSolid || sharp, if (sharp) 0 else 34)
    }

    private fun slateGlass(
        fillAlpha: Int = 210,
        radius: Int = 24,
        strokeColor: Int = Color.argb(40, 255, 255, 255),
        strokeDp: Int = 1
    ): Drawable = glassOf(fillAlpha, radius, strokeColor, strokeDp)

    private fun glass(alpha: Int = 140, radius: Int = 20, strokeColor: Int = Color.argb(80, 255, 255, 255), strokeDp: Int = 1): Drawable =
        glassOf(alpha, radius, strokeColor, strokeDp)

    private fun solidCard(color: Int, radius: Int = 18, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun focusBg(radius: Int): Drawable {
        if (focusSharp()) return solidCard(Color.WHITE, radius)
        val fa = (focusGlassPct() * 2.55f).toInt().coerceIn(30, 255)
        return GlassDrawable(
            dp(radius).toFloat(), dp(2).toFloat(),
            Color.argb((fa * 0.55f).toInt(), 255, 255, 255), Color.argb((fa * 0.35f).toInt(), 210, 225, 255),
            Color.argb(255, 255, 255, 255), true, 60
        )
    }

    private fun focusFg(): Int = if (focusSharp()) darkText else Color.WHITE
    private fun focusRing(): Int = if (focusSharp()) Color.WHITE else Color.argb((focusGlassPct() * 2.55f).toInt().coerceIn(120, 255), 255, 255, 255)

    private fun tv(sp: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun go(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Throwable) { Toast.makeText(this, "غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show() }
    }

    private fun launchPkg(pkg: String) {
        if (pkg.isEmpty()) return
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) go(i) else Toast.makeText(this, "التطبيق غير موجود", Toast.LENGTH_SHORT).show()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Throwable) { pkg }

    /* ─────────── الريموت ─────────── */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (pBool("block_keys", true)) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_TV,
                KeyEvent.KEYCODE_TV_INPUT,
                KeyEvent.KEYCODE_GUIDE,
                KeyEvent.KEYCODE_TV_DATA_SERVICE,
                KeyEvent.KEYCODE_TV_CONTENTS_MENU,
                KeyEvent.KEYCODE_DVR -> return true
            }
        }

        val colorKey = when (event.keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> "key_red"
            KeyEvent.KEYCODE_PROG_GREEN -> "key_green"
            KeyEvent.KEYCODE_PROG_YELLOW -> "key_yellow"
            KeyEvent.KEYCODE_PROG_BLUE -> "key_blue"
            else -> null
        }
        if (colorKey != null) {
            val pkg = prefs.getString(colorKey, "") ?: ""
            if (pkg.isNotEmpty()) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) launchPkg(pkg)
                return true
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && pBool("menu_key", true) && reorderingPkg == null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && currentPage != 2) showPage(2)
            return true
        }

        val moving = reorderingPkg
        if (moving != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        moveFavoriteLive(-1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        moveFavoriteLive(1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BACK -> {
                        finishReorderMode()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> return true
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /* ─────────── دورة الحياة ─────────── */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("argon_home_prefs", Context.MODE_PRIVATE)
        registerInstallReceiver()
        try {
            buildInterface(pInt("start_page", 0).coerceIn(0, 1), false)
            fetchMatches()
            updateClock()
            refreshFeeds()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply { text = "خطأ: $e"; textSize = 20f; setTextColor(Color.RED); setPadding(40, 40, 40, 40) })
        }
    }

    override fun onDestroy() {
        installReceiver?.let { try { unregisterReceiver(it) } catch (e: Throwable) { } }
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::favRow.isInitialized) loadAppsAndDock()
    }

    @Deprecated("") override fun onBackPressed() {
        if (reorderingPkg != null) {
            finishReorderMode()
            return
        }
        if (!::pages.isInitialized) return
        if (currentPage == 2) {
            val nav = settingsNav.getOrNull(currentSection)
            if (nav != null && !nav.hasFocus()) nav.requestFocus() else showPage(0)
            return
        }
        if (currentPage != 0) showPage(0)
    }

    private fun applyCustomBackground() {
        val url = prefs.getString("custom_bg_url", "") ?: ""
        if (url.isEmpty()) {
            backgroundImageView.visibility = View.GONE
            overlay.visibility = View.GONE
            return
        }
        val cached = bgCache[url]
        if (cached != null) { showBg(cached); return }
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                if (bmp != null) {
                    bgCache[url] = bmp
                    ui.post { showBg(bmp) }
                }
            } catch (e: Throwable) { }
        }
    }

    private fun showBg(b: Bitmap) {
        backgroundImageView.setImageBitmap(b)
        backgroundImageView.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
    }

    /* ─────────── بناء الواجهة وإعادة البناء ─────────── */
    private fun buildInterface(startPage: Int, animate: Boolean) {
        val frame = FrameLayout(this).unclip().apply { setBackgroundColor(palette().second[0]) }
        ambient = AmbientView(this, homeGlow())
        ambient.setPalette(palette().second)
        frame.addView(ambient, FrameLayout.LayoutParams(-1, -1))
        backgroundImageView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        frame.addView(backgroundImageView, FrameLayout.LayoutParams(-1, -1))
        overlay = View(this).apply { setBackgroundColor(0x88060911.toInt()); visibility = View.GONE }
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        applyCustomBackground()

        val root = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(34), dp(16), dp(34), dp(12))
        }
        headerView = buildHeader()
        root.addView(headerView, LinearLayout.LayoutParams(-1, -2))
        val box = FrameLayout(this).unclip()
        pages = listOf(buildHomePage(), buildMatchesPage(), buildSettingsPage())
        pages.forEach { box.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(box, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        showPage(startPage, animate)
    }

    private fun scheduleRebuild() {
        ui.removeCallbacks(rebuildTask)
        ui.postDelayed(rebuildTask, 450)
    }

    private fun doRebuild() {
        val page = currentPage
        val sec = currentSection
        val row = lastFocusedRow
        buildInterface(page, false)
        lastMin = -1
        updateClock()
        applyWeather()
        renderPrayers()
        renderMatches()
        loadAppsAndDock()
        if (page == 2) {
            showSection(sec)
            settingsContent.post { settingsContent.getChildAt(row.coerceIn(0, maxOf(0, settingsContent.childCount - 1)))?.requestFocus() }
        }
    }

    private fun showPage(i: Int, animate: Boolean = true) {
        if (reorderingPkg != null) finishReorderMode()
        currentPage = i
        pages.forEachIndexed { k, p ->
            if (k == i) {
                p.visibility = View.VISIBLE
                if (animate) {
                    p.alpha = 0f
                    p.translationY = dp(12).toFloat()
                    p.animate().alpha(1f).translationY(0f).setDuration(260).start()
                } else {
                    p.alpha = 1f
                    p.translationY = 0f
                }
            } else p.visibility = View.GONE
        }
        headerView.visibility = if (i == 2) View.GONE else View.VISIBLE
        updateTabStyles()
        ambient.glow(homeGlow())
        if (i == 2) {
            settingsContent.post { settingsNav.getOrNull(currentSection)?.requestFocus() }
        }
    }

    private fun updateTabStyles() {
        tabPills.forEachIndexed { k, pill ->
            val f = pill.hasFocus()
            val sel = k == currentPage
            val txt = tabTexts[k]
            when {
                f -> {
                    pill.background = focusBg(26)
                    txt.setTextColor(focusFg())
                }
                sel -> {
                    pill.background = slateGlass(230, 26, accent, 1)
                    txt.setTextColor(accent)
                }
                else -> {
                    pill.background = slateGlass(165, 26, Color.argb(45, 255, 255, 255), 1)
                    txt.setTextColor(Color.WHITE)
                }
            }
        }
    }

    /* ─────────── الهيدر ─────────── */
    private fun buildHeader(): View {
        val h = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(10))
        }

        val rightCluster = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        greetingTv = tv(11f, true, dim).apply {
            text = "مساء الخير"
            letterSpacing = 0.08f
            setPadding(dp(2), 0, dp(2), 0)
            visibility = if (pBool("show_greeting", true)) View.VISIBLE else View.GONE
        }
        val clockLine = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clockTv = tv(38f, true, Color.WHITE).apply {
            includeFontPadding = false
        }
        val vSep = View(this).apply {
            background = solidCard(Color.argb(80, 255, 255, 255), 2)
        }
        dateLineTv = tv(12.5f, true, dim).apply {
            maxLines = 2
            setLineSpacing(0f, 1.1f)
        }
        clockLine.addView(clockTv)
        clockLine.addView(vSep, LinearLayout.LayoutParams(dp(2), dp(28)).apply {
            marginStart = dp(14)
            marginEnd = dp(14)
        })
        clockLine.addView(dateLineTv)
        rightCluster.addView(greetingTv)
        rightCluster.addView(clockLine)
        h.addView(rightCluster, LinearLayout.LayoutParams(-2, -2))

        h.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val names = listOf("الرئيسية", "المباريات")
        val pList = mutableListOf<LinearLayout>()
        val tList = mutableListOf<TextView>()

        names.forEachIndexed { i, n ->
            val txt = tv(13.5f, true, Color.WHITE).apply { text = n }
            val pill = LinearLayout(this).unclip().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(9), dp(18), dp(9))
                isFocusable = true
                isClickable = true
                roundOutline(26)
                addView(txt)
                setOnClickListener { showPage(i) }
                setOnFocusChangeListener { v, f ->
                    updateTabStyles()
                    v.translationZ = if (f) dp(10).toFloat() else 0f
                    v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
                }
            }
            pList.add(pill)
            tList.add(txt)
            h.addView(pill, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })
        }
        tabPills = pList
        tabTexts = tList

        h.addView(wifiCapsule(), LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(10) })
        h.addView(cityCapsule(), LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })
        h.addView(actionCapsule("الإعدادات", 1) { openSettingsMenu(it) }, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })

        return h
    }

    private fun capsuleFocus(v: View, f: Boolean, title: TextView?, glyph: GlyphView) {
        v.background = if (f) focusBg(26) else slateGlass(185, 26, Color.argb(55, 255, 255, 255), 1)
        title?.setTextColor(if (f) focusFg() else Color.WHITE)
        glyph.setTint(if (f) focusFg() else Color.WHITE)
        v.translationZ = if (f) dp(10).toFloat() else 0f
        v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
    }

    private fun wifiCapsule(): View {
        val glyph = GlyphView(this, 0)
        val title = tv(13f, true, Color.WHITE).apply {
            text = "Wi-Fi"
            setPadding(dp(8), 0, dp(8), 0)
        }
        val badge = tv(10.5f, true, Color.WHITE).apply {
            text = "متصل"
            background = solidCard(greenBadge, 12)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(185, 26, Color.argb(55, 255, 255, 255), 1)
            setPadding(dp(14), dp(7), dp(12), dp(7))
            isFocusable = true
            isClickable = true
            roundOutline(26)
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(title)
            addView(badge)
            setOnClickListener { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            setOnFocusChangeListener { v, f -> capsuleFocus(v, f, title, glyph) }
        }
    }

    private fun cityCapsule(): View {
        val glyph = GlyphView(this, 2)
        cityPillTv = tv(13f, true, Color.WHITE).apply {
            text = city().name
            setPadding(dp(6), 0, dp(4), 0)
        }
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(185, 26, Color.argb(55, 255, 255, 255), 1)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = true
            isClickable = true
            roundOutline(26)
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(cityPillTv)
            setOnClickListener { openCityPicker(this) }
            setOnFocusChangeListener { v, f -> capsuleFocus(v, f, cityPillTv, glyph) }
        }
    }

    private fun actionCapsule(label: String, kind: Int, onClick: (View) -> Unit): View {
        val glyph = GlyphView(this, kind)
        val title = tv(13f, true, Color.WHITE).apply {
            text = label
            setPadding(dp(7), 0, dp(4), 0)
        }
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(185, 26, Color.argb(55, 255, 255, 255), 1)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = true
            isClickable = true
            roundOutline(26)
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(title)
            setOnClickListener { onClick(this) }
            setOnFocusChangeListener { v, f -> capsuleFocus(v, f, title, glyph) }
        }
    }

    /* ─────────── الصفحة الرئيسية ─────────── */
    private fun vDivider(): View = View(this).apply {
        background = solidCard(Color.argb(30, 255, 255, 255), 1)
    }

    private fun buildHomePage(): View {
        val col = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        val unifiedBoard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(215, 28, Color.argb(45, 255, 255, 255), 1)
            setPadding(dp(22), dp(14), dp(22), dp(14))
            roundOutline(28)
        }

        // 1. الطقس
        val weatherCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(12), 0)
        }
        weatherCol.addView(tv(10.5f, true, muted).apply { text = "محدث تلقائياً" })
        cityHeaderTv = tv(15f, true, accent).apply {
            text = "الطقس • ${city().name}"
            setPadding(0, dp(2), 0, dp(6))
        }
        weatherCol.addView(cityHeaderTv)
        val tempRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tempRow.addView(sunView(), LinearLayout.LayoutParams(dp(34), dp(34)))
        tempTv = tv(34f, true, Color.WHITE).apply {
            text = "--°"
            includeFontPadding = false
            setPadding(dp(10), 0, 0, 0)
        }
        tempRow.addView(tempTv)
        weatherCol.addView(tempRow)
        weatherStatusTv = tv(13f, true, Color.WHITE).apply {
            text = "جاري التحديث..."
            setPadding(0, dp(4), 0, dp(2))
        }
        weatherSubTv = tv(11f, false, dim).apply {
            text = ""
            setLineSpacing(0f, 1.15f)
        }
        weatherCol.addView(weatherStatusTv)
        weatherCol.addView(weatherSubTv)

        // 2. مواقيت الصلاة
        val prayerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(12), 0)
        }
        prayerCol.addView(tv(10.5f, true, muted).apply { text = "التوقيت المحلي" })
        prayerCol.addView(tv(15f, true, accent).apply {
            text = "مواقيت الصلاة"
            setPadding(0, dp(2), 0, dp(6))
        })
        prayerLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        prayerCol.addView(prayerLayout, LinearLayout.LayoutParams(-1, dp(95)))
        prayerTimerTv = tv(11f, true, gold).apply {
            text = "جاري تحديث المواقيت..."
            setPadding(0, dp(4), 0, 0)
            visibility = if (pBool("prayer_countdown", true)) View.VISIBLE else View.GONE
        }
        prayerCol.addView(prayerTimerTv)

        // 3. مباريات اليوم (مقصوصة داخل حدودها)
        val matchesCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val mHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mTitles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mTitles.addView(tv(10.5f, true, muted).apply { text = "نتائج ومواعيد مباشرة" })
        mTitles.addView(tv(15f, true, accent).apply { text = "مباريات اليوم"; setPadding(0, dp(2), 0, 0) })
        mHead.addView(mTitles)
        tickerTv = tv(11f, false, dim).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(14), 0, dp(8), 0)
            visibility = if (pBool("show_ticker", true)) View.VISIBLE else View.GONE
        }
        mHead.addView(tickerTv, LinearLayout.LayoutParams(0, -2, 1f))
        matchesCol.addView(mHead, LinearLayout.LayoutParams(-1, -2))
        homeMatchRow = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(14), dp(10), dp(14))
        }
        val matchScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true
            clipToPadding = false
            addView(homeMatchRow)
        }
        matchesCol.addView(matchScroll, LinearLayout.LayoutParams(-1, -2))

        val showW = pBool("show_weather", true)
        val showP = pBool("show_prayers", true)
        val showM = pBool("show_matches", true)
        var added = 0
        fun sep() {
            if (added > 0) unifiedBoard.addView(vDivider(), LinearLayout.LayoutParams(dp(1), dp(130)).apply { setMargins(dp(8), 0, dp(12), 0) })
        }
        if (showW) { sep(); unifiedBoard.addView(weatherCol, LinearLayout.LayoutParams(dp(195), -2)); added++ }
        if (showP) { sep(); unifiedBoard.addView(prayerCol, LinearLayout.LayoutParams(dp(220), -2)); added++ }
        if (showM) { sep(); unifiedBoard.addView(matchesCol, LinearLayout.LayoutParams(0, -2, 1f)); added++ }
        else if (added > 0) unifiedBoard.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        if (added == 0) unifiedBoard.visibility = View.GONE

        col.addView(unifiedBoard, LinearLayout.LayoutParams(-1, -2))
        col.addView(View(this), LinearLayout.LayoutParams(-1, dp(14)))

        // المفضلة (Dock)
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(210, 30, Color.argb(45, 255, 255, 255), 1)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            roundOutline(30)
        }
        reorderHintTv = tv(12.5f, true, gold).apply {
            text = "↔️  وضع تحريك التطبيق: استخدم الأسهم (يمين / يسار) لتغيير المكان، ثم اضغط (موافق OK) للتثبيت"
            gravity = Gravity.CENTER
            background = solidCard(Color.argb(190, 15, 23, 42), 14, accent, 1)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.GONE
        }
        dock.addView(reorderHintTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        favRow = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            layoutTransition = LayoutTransition().apply { setDuration(180) }
        }
        dock.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true
            clipToPadding = false
            addView(favRow)
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(dock, LinearLayout.LayoutParams(-1, -2))

        // جميع التطبيقات تحت المفضلة
        allGrid = GridLayout(this).unclip().apply {
            columnCount = gridCols()
            setPadding(dp(14), dp(10), dp(14), dp(36))
        }
        if (pBool("show_all_apps", true)) {
            col.addView(tv(16f, true, accent).apply {
                text = "جميع التطبيقات"
                setPadding(dp(14), dp(22), dp(14), dp(4))
            })
            col.addView(tv(11f, false, muted).apply {
                text = "اضغط مطولاً على أي تطبيق للإدارة أو الإضافة للمفضلة"
                setPadding(dp(14), 0, dp(14), dp(2))
            })
            col.addView(allGrid, LinearLayout.LayoutParams(-1, -2))
        }

        return ScrollView(this).unclip().apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(col, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun buildMatchesPage(): View {
        val col = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(24))
        }
        col.addView(tv(20f, true, accent).apply { text = "جدول مباريات اليوم"; setPadding(dp(8), dp(4), dp(8), dp(10)) })
        matchesGrid = GridLayout(this).unclip().apply {
            columnCount = 3
            setPadding(dp(12), dp(12), dp(12), dp(20))
        }
        col.addView(matchesGrid, LinearLayout.LayoutParams(-1, -2))
        return ScrollView(this).unclip().apply {
            isVerticalScrollBarEnabled = false
            addView(col)
        }
    }
}
