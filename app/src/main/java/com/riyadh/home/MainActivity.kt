package com.riyadh.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class AmbientView(c: Context) : View(c) {
    private var color = 0xFF3B2A6B.toInt()
    private var from = color
    private var to = color
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 700
        addUpdateListener {
            color = ArgbEvaluator().evaluate(it.animatedValue as Float, from, to) as Int
            invalidate()
        }
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
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF05070F.toInt(), 0xFF0D1530.toInt(), 0xFF150F2B.toInt()), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.85f, h * 0.15f, w * 0.6f, Color.argb(125, r, g, b), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * 0.1f, h * 0.95f, w * 0.55f, Color.argb(80, r, g, b), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)
    }
}

class MainActivity : Activity() {
    private val ar = Locale("ar", "SA")
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private val accent = Color.parseColor("#4DEEEA")
    private val gold = Color.parseColor("#FFEAA7")
    private val dim = Color.argb(200, 255, 255, 255)
    private val homeGlow = 0xFF3B2A6B.toInt()

    private lateinit var ambient: AmbientView
    private lateinit var backgroundImageView: ImageView
    private lateinit var overlay: View
    private lateinit var pages: List<View>
    private lateinit var tabViews: List<TextView>
    private var currentPage = 0
    private lateinit var favRow: LinearLayout
    private lateinit var allGrid: GridLayout
    private lateinit var matchesGrid: GridLayout
    private lateinit var heroBox: LinearLayout
    private lateinit var clockTv: TextView
    private lateinit var miniClockTv: TextView
    private lateinit var dayTv: TextView
    private lateinit var datesTv: TextView
    private lateinit var tempTv: TextView
    private lateinit var cityTv: TextView
    private lateinit var weatherStatusTv: TextView
    private lateinit var weatherSubTv: TextView
    private lateinit var prayerLayout: LinearLayout
    private lateinit var prayerTimerTv: TextView

    private var allApps = mutableListOf<ResolveInfo>()
    private var prayersData: List<Prayer> = emptyList()
    private var matchesData: List<Match>? = null
    private var lastMin = -1
    private val logoCache = ConcurrentHashMap<String, Bitmap>()
    private val bgCache = ConcurrentHashMap<String, Bitmap>()
    private val feedsTask = Runnable { fetchMatches(); refreshFeeds() }

    private val weights = mapOf(
        "الهلال" to 100, "ريال مدريد" to 90, "برشلونة" to 90, "مانشستر سيتي" to 90,
        "النصر" to 60, "الاتحاد" to 60, "الأهلي" to 60,
        "ليفربول" to 70, "أرسنال" to 70, "بايرن ميونخ" to 70, "باريس سان جيرمان" to 70,
        "أتلتيكو مدريد" to 70, "مانشستر يونايتد" to 70, "تشيلسي" to 70, "إنتر ميلان" to 70,
        "ميلان" to 70, "يوفنتوس" to 70, "دورتموند" to 70
    )

    private fun score(m: Match) = (weights[m.home] ?: 0) + (weights[m.away] ?: 0)
    private fun strong(m: Match): Boolean {
        val s = score(m)
        return if (m.league == "دوري روشن السعودي" || m.league == "أبطال آسيا للنخبة") s >= 60 else s >= 90
    }
    private fun rank(m: Match) = score(m) + (if (m.live) 60 else 0) - (if (m.note == "انتهت") 200 else 0)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun city(): City = Data.cities[prefs.getInt("city_idx", 0).coerceIn(0, Data.cities.size - 1)]

    private fun glass(alpha: Int = 140, radius: Int = 20, strokeColor: Int = Color.argb(80, 255, 255, 255), strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(Color.argb(alpha, 15, 23, 36))
        cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun solidCard(color: Int, radius: Int = 16, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun tv(sp: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun go(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Throwable) { Toast.makeText(this, "غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("argon_home_prefs", Context.MODE_PRIVATE)
        try {
            buildInterface()
            fetchMatches()
            updateClock()
            refreshFeeds()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply { text = "خطأ: $e"; textSize = 20f; setTextColor(Color.RED); setPadding(40, 40, 40, 40) })
        }
    }

    override fun onResume() {
        super.onResume()
        if (::favRow.isInitialized) loadAppsAndDock()
    }

    @Deprecated("") override fun onBackPressed() {
        if (::pages.isInitialized && currentPage != 0) showPage(0)
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

    private fun buildInterface() {
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF05070F.toInt()) }
        ambient = AmbientView(this)
        frame.addView(ambient, FrameLayout.LayoutParams(-1, -1))
        backgroundImageView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        frame.addView(backgroundImageView, FrameLayout.LayoutParams(-1, -1))
        overlay = View(this).apply { setBackgroundColor(0x99080C14.toInt()); visibility = View.GONE }
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        applyCustomBackground()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(32), dp(18), dp(32), dp(14))
            clipChildren = false
            clipToPadding = false
        }
        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2))
        val box = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        pages = listOf(buildHomePage(), buildMatchesPage(), buildAppsPage())
        pages.forEach { box.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(box, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        showPage(0)
    }

    private fun showPage(i: Int) {
        currentPage = i
        pages.forEachIndexed { k, p ->
            if (k == i) {
                p.visibility = View.VISIBLE
                p.alpha = 0f
                p.translationY = dp(14).toFloat()
                p.animate().alpha(1f).translationY(0f).setDuration(280).start()
            } else p.visibility = View.GONE
        }
        tabViews.forEachIndexed { k, t -> t.setTextColor(if (k == i) accent else Color.WHITE) }
        ambient.glow(homeGlow)
    }

    private fun buildHeader(): View {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(6))
        }
        val names = listOf("الرئيسية", "المباريات", "التطبيقات")
        tabViews = names.mapIndexed { i, n ->
            val t = tv(16f, true).apply {
                text = n
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(9), dp(22), dp(9))
                isFocusable = true
                isClickable = true
                background = glass(0, 22, Color.TRANSPARENT, 0)
                setOnClickListener { showPage(i) }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 200 else 0, 22, if (f) Color.WHITE else Color.TRANSPARENT, if (f) 2 else 0)
                    v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120).start()
                }
            }
            h.addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            t
        }
        h.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        miniClockTv = tv(14f, true, dim).apply { setPadding(dp(10), 0, dp(6), 0) }
        h.addView(miniClockTv)
        h.addView(capsule("المدينة", 3) { openCityPicker() })
        h.addView(capsule("واي فاي", 0) { go(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        h.addView(capsule("الإعدادات", 1) { openSettingsDialog() })
        return h
    }

    private fun buildHomePage(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(24))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val ww = dp(230)
        val wh = dp(145)

        val wCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(140, 22)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val head = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(tv(13f, true, dim).apply { text = "الطقس" })
                addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                cityTv = tv(12f, true, accent).apply { text = city().name + " ▾" }
                addView(cityTv)
            }
            addView(head)
            val tempRow = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            tempRow.addView(sunView(), LinearLayout.LayoutParams(dp(34), dp(34)))
            tempTv = tv(36f, true).apply { text = "--°"; setPadding(dp(10), 0, 0, 0) }
            tempRow.addView(tempTv)
            addView(tempRow)
            weatherStatusTv = tv(12f, true).apply { text = "جاري التحديث..." }
            weatherSubTv = tv(10.5f, false, dim).apply { text = ""; setLineSpacing(0f, 1.15f) }
            addView(weatherStatusTv)
            addView(weatherSubTv)
        }
        top.addView(wCard, LinearLayout.LayoutParams(ww, wh))

        val pCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(140, 22)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(tv(13f, true, dim).apply { text = "أوقات الصلاة"; gravity = Gravity.CENTER })
            prayerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            addView(prayerLayout, LinearLayout.LayoutParams(-1, 0, 1f))
            prayerTimerTv = tv(11f, true, gold).apply { text = "جاري تحديث المواقيت..."; gravity = Gravity.CENTER }
            addView(prayerTimerTv)
        }
        top.addView(pCard, LinearLayout.LayoutParams(ww, wh).apply { marginStart = dp(14) })
        top.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockTv = tv(68f, true).apply { gravity = Gravity.END }
        dayTv = tv(18f, true).apply { gravity = Gravity.END }
        datesTv = tv(12.5f, false, dim).apply { gravity = Gravity.END; setLineSpacing(0f, 1.2f) }
        clockBox.addView(clockTv)
        clockBox.addView(dayTv)
        clockBox.addView(datesTv)
        top.addView(clockBox)
        col.addView(top, LinearLayout.LayoutParams(-1, -2))

        col.addView(tv(15f, true).apply { text = "أهم مباراة اليوم"; setPadding(dp(4), dp(16), 0, dp(6)) })
        heroBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(heroBox, LinearLayout.LayoutParams(-1, -2))

        col.addView(tv(15f, true).apply { text = "المفضلة"; setPadding(dp(4), dp(16), 0, dp(6)) })
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(110, 28)
            setPadding(dp(18), dp(14), dp(18), dp(8))
            clipChildren = false
            clipToPadding = false
        }
        favRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(6))
        }
        dock.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(favRow)
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(dock, LinearLayout.LayoutParams(-1, -2))

        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(col)
        }
    }

    private fun buildMatchesPage(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(24))
        }
        col.addView(tv(20f, true).apply { text = "أقوى مباريات اليوم"; setPadding(dp(4), dp(6), 0, dp(8)) })
        matchesGrid = GridLayout(this).apply {
            columnCount = 3
            clipChildren = false
            clipToPadding = false
        }
        col.addView(matchesGrid, LinearLayout.LayoutParams(-1, -2))
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(col)
        }
    }

    private fun buildAppsPage(): View {
        allGrid = GridLayout(this).apply {
            columnCount = 5
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(10), dp(4), dp(30))
        }
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(allGrid)
        }
    }

    class GlyphView(c: Context, private val kind: Int) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
        override fun onDraw(cv: Canvas) {
            super.onDraw(cv)
            val d = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            when (kind) {
                0 -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.4f * d
                    for (r in listOf(5f, 9f, 13f)) cv.drawArc(cx - r * d, cy + 6 * d - r * d, cx + r * d, cy + 6 * d + r * d, 225f, 90f, false, p)
                    p.style = Paint.Style.FILL
                    cv.drawCircle(cx, cy + 6 * d, 1.8f * d, p)
                }
                1 -> {
                    p.style = Paint.Style.FILL
                    val path = Path()
                    val big = 11f * d
                    val small = 8.5f * d
                    for (k in 0 until 8) {
                        for ((a, rad) in listOf(-11f to small, -7f to big, 7f to big, 11f to small)) {
                            val ang = Math.toRadians((k * 45 + a).toDouble())
                            val x = cx + rad * Math.cos(ang).toFloat()
                            val y = cy + rad * Math.sin(ang).toFloat()
                            if (k == 0 && a == -11f) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                    }
                    path.close()
                    path.fillType = Path.FillType.EVEN_ODD
                    path.addCircle(cx, cy, 4f * d, Path.Direction.CW)
                    cv.drawPath(path, p)
                }
                else -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.2f * d
                    cv.drawCircle(cx, cy - 3 * d, 6f * d, p)
                    cv.drawLine(cx - 4.5f * d, cy + 1.5f * d, cx, cy + 10 * d, p)
                    cv.drawLine(cx + 4.5f * d, cy + 1.5f * d, cx, cy + 10 * d, p)
                    p.style = Paint.Style.FILL
                    cv.drawCircle(cx, cy - 3 * d, 2f * d, p)
                }
            }
        }
    }

    private fun capsule(label: String, kind: Int, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glass(130, 24, Color.argb(90, 255, 255, 255), 1)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            isFocusable = true
            isClickable = true
            clipChildren = false
            setOnClickListener { onClick() }
        }
        val title = tv(12.5f, true).apply { text = label; visibility = View.GONE; setPadding(dp(8), 0, dp(4), 0) }
        box.addView(GlyphView(this, kind), LinearLayout.LayoutParams(dp(26), dp(26)))
        box.addView(title)
        box.setOnFocusChangeListener { v, f ->
            title.visibility = if (f) View.VISIBLE else View.GONE
            v.background = glass(if (f) 215 else 130, 24, if (f) Color.WHITE else Color.argb(90, 255, 255, 255), if (f) 2 else 1)
            v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start()
        }
        box.layoutParams = LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(8) }
        return box
    }

    private fun sunView(): View = object : View(this) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val cx = width / 2f
            val cy = height / 2f
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#FFB703")
            c.drawCircle(cx, cy, width * 0.28f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val r1 = width * 0.36f
                val r2 = width * 0.46f
                c.drawLine(cx + r1 * Math.cos(a).toFloat(), cy + r1 * Math.sin(a).toFloat(), cx + r2 * Math.cos(a).toFloat(), cy + r2 * Math.sin(a).toFloat(), p)
            }
        }
    }

    private fun shell(): Pair<Dialog, LinearLayout> {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val s = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = solidCard(0xF0101A2E.toInt(), 28, Color.argb(90, 255, 255, 255), 1)
            setPadding(dp(28), dp(22), dp(28), dp(22))
        }
        return d to s
    }

    private fun optionRow(label: String, onClick: () -> Unit): View = tv(15f, true).apply {
        text = label
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = glass(100, 14, Color.TRANSPARENT, 0)
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f -> v.background = glass(if (f) 190 else 100, 14, if (f) Color.WHITE else Color.TRANSPARENT, if (f) 2 else 0) }
    }

    private fun openSettingsDialog() {
        val (dialog, sheet) = shell()
        sheet.addView(tv(21f, true).apply { text = "الإعدادات" })
        sheet.addView(tv(12f, false, dim).apply { text = "تحكم بواجهتك"; setPadding(0, dp(2), 0, dp(14)) })
        val g = GridLayout(this).apply { columnCount = 2 }
        fun card(icon: String, title: String, sub: String, act: () -> Unit) {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glass(110, 18, Color.argb(60, 255, 255, 255), 1)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                isFocusable = true
                isClickable = true
                setOnClickListener { dialog.dismiss(); act() }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 190 else 110, 18, if (f) Color.WHITE else Color.argb(60, 255, 255, 255), if (f) 2 else 1)
                    v.animate().scaleX(if (f) 1.04f else 1f).scaleY(if (f) 1.04f else 1f).setDuration(110).start()
                }
                addView(tv(24f).apply { text = icon })
                addView(tv(14.5f, true).apply { text = title; setPadding(0, dp(6), 0, 0) })
                addView(tv(11f, false, dim).apply { text = sub })
            }
            g.addView(c, GridLayout.LayoutParams().apply { width = dp(210); height = -2; setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        card("📍", "المدينة", city().name) { openCityPicker() }
        card("📶", "الواي فاي", "الشبكة والاتصال") { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        card("⚙️", "إعدادات الجهاز", "النظام الأساسية") { go(Intent(Settings.ACTION_SETTINGS)) }
        card("🖼️", "صورة خلفية خاصة", "رابط صورة") { showBgUrlDialog() }
        card("✨", "الخلفية المضيئة", "استعادة الافتراضية") {
            prefs.edit().remove("custom_bg_url").apply()
            applyCustomBackground()
        }
        card("⚽", "تحديث المباريات", "الآن") { fetchMatches() }
        card("⭐", "إعادة ضبط المفضلة", "الافتراضية") {
            prefs.edit().remove("fav_apps").apply()
            loadAppsAndDock()
        }
        sheet.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(g) }, LinearLayout.LayoutParams(dp(470), dp(320)))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openCityPicker() {
        val (dialog, sheet) = shell()
        sheet.addView(tv(19f, true, accent).apply { text = "اختر مدينتك (الطقس والصلاة)"; setPadding(0, 0, 0, dp(12)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        val cur = prefs.getInt("city_idx", 0)
        Data.cities.forEachIndexed { i, c ->
            list.addView(optionRow((if (i == cur) "✓  " else "") + c.name) {
                prefs.edit().putInt("city_idx", i).apply()
                cityTv.text = c.name + " ▾"
                dialog.dismiss()
                refreshFeeds()
            }, LinearLayout.LayoutParams(dp(340), -2).apply { bottomMargin = dp(8) })
        }
        sheet.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(dp(340), dp(300)))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun showBgUrlDialog() {
        val input = EditText(this).apply {
            hint = "أدخل رابط صورة مباشر (HTTPS)"
            setText(prefs.getString("custom_bg_url", ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle("صورة خلفية الواجهة")
            .setView(input)
            .setPositiveButton("تطبيق") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    prefs.edit().putString("custom_bg_url", url).apply()
                    applyCustomBackground()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun fetchMatches() {
        thread {
            val r: List<Match>? = try { Data.matches() } catch (e: Throwable) { null }
            ui.post { matchesData = r; renderMatches() }
        }
    }

    private fun renderMatches() {
        if (!::heroBox.isInitialized) return
        heroBox.removeAllViews()
        matchesGrid.removeAllViews()
        val all = matchesData
        val list = all?.filter { strong(it) }?.sortedByDescending { rank(it) }
        if (list == null || list.isEmpty()) {
            val msg = if (all == null) "تعذر تحميل المباريات، تأكد من الاتصال" else "لا توجد مباريات قوية اليوم"
            heroBox.addView(tv(15f, false, dim).apply { text = msg; setPadding(dp(8), dp(14), dp(8), dp(14)) })
            matchesGrid.addView(tv(15f, false, dim).apply { text = msg; setPadding(dp(8), dp(14), dp(8), dp(14)) })
            return
        }
        heroBox.addView(matchCard(list.first(), true), LinearLayout.LayoutParams(dp(620), -2))
        val w = ((resources.displayMetrics.widthPixels - dp(90)) / 3) - dp(16)
        for (m in list) {
            matchesGrid.addView(matchCard(m, false), GridLayout.LayoutParams().apply {
                width = w
                height = -2
                setMargins(dp(8), dp(8), dp(8), dp(8))
            })
        }
    }

    private fun matchCard(m: Match, big: Boolean): View {
        val logo = if (big) dp(56) else dp(40)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = glass(150, 22)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { v, f ->
                v.background = glass(if (f) 210 else 150, 22, if (f) Color.WHITE else Color.argb(80, 255, 255, 255), if (f) 2 else 1)
                v.animate().scaleX(if (f) 1.05f else 1f).scaleY(if (f) 1.05f else 1f).setDuration(120).start()
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(if (big) 12.5f else 11f, true, accent).apply { text = m.league; maxLines = 1 })
        head.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        val status = when { m.live -> "مباشر الآن 🔴"; m.note.isNotEmpty() -> m.note; else -> "اليوم" }
        head.addView(tv(if (big) 12f else 10.5f, false, dim).apply { text = status })
        card.addView(head, LinearLayout.LayoutParams(-1, -2))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(4)) }
        row.addView(clubColumn(m.home, m.homeLogo, logo), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv(if (big) 28f else 18f, true, gold).apply { text = m.center; gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) })
        row.addView(clubColumn(m.away, m.awayLogo, logo), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun clubColumn(name: String, logoUrl: String, size: Int): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(crest(name))
        }
        if (logoUrl.isNotEmpty()) loadLogo(logoUrl, iv)
        col.addView(iv, LinearLayout.LayoutParams(size, size))
        col.addView(tv(12f, true).apply { text = name; maxLines = 2; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
        return col
    }

    private fun crest(name: String): Drawable {
        val size = dp(40)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val clubColor = when {
            name.contains("الهلال") -> Color.parseColor("#00529B")
            name.contains("النصر") -> Color.parseColor("#F5C400")
            name.contains("الاتحاد") -> Color.parseColor("#D4AF37")
            name.contains("الأهلي") -> Color.parseColor("#006747")
            else -> Color.parseColor("#28354A")
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = clubColor }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun loadLogo(url: String, iv: ImageView) {
        val cached = logoCache[url]
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                if (bmp != null) {
                    logoCache[url] = bmp
                    ui.post { iv.setImageBitmap(bmp) }
                }
            } catch (e: Throwable) { }
        }
    }

    private fun loadAppsAndDock() {
        val pm = packageManager
        allApps = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .toMutableList()
        val favPkgs = prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

        favRow.removeAllViews()
        val favList = if (favPkgs.isNotEmpty()) favPkgs.mapNotNull { pkg -> allApps.find { it.activityInfo.packageName == pkg } } else allApps.take(7)
        for (a in favList) favRow.addView(createCell(a, dp(176)), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(14) })

        allGrid.removeAllViews()
        val gw = ((resources.displayMetrics.widthPixels - dp(72)) / 5) - dp(20)
        for (a in allApps) {
            allGrid.addView(createCell(a, gw), GridLayout.LayoutParams().apply {
                width = -2
                height = -2
                setMargins(dp(10), dp(10), dp(10), dp(10))
            })
        }
        if (currentPage == 0) ((favRow.getChildAt(0) as? LinearLayout)?.getChildAt(0))?.requestFocus()
    }

    private fun dominant(d: Drawable): Int = try {
        val dd = d.constantState?.newDrawable()?.mutate() ?: d
        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        dd.setBounds(0, 0, 24, 24)
        dd.draw(Canvas(bmp))
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (x in 0 until 24) for (y in 0 until 24) {
            val px = bmp.getPixel(x, y)
            val pr = Color.red(px)
            val pg = Color.green(px)
            val pb = Color.blue(px)
            if (Color.alpha(px) > 128 && !(pr > 235 && pg > 235 && pb > 235) && pr + pg + pb > 60) {
                r += pr; g += pg; b += pb; n++
            }
        }
        if (n < 10) 0xFF3B4A7A.toInt() else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    } catch (e: Throwable) { 0xFF3B4A7A.toInt() }

    private fun darker(c: Int) = Color.rgb((Color.red(c) * 0.55f).toInt(), (Color.green(c) * 0.55f).toInt(), (Color.blue(c) * 0.55f).toInt())

    private fun brandBg(label: String, pkg: String, icon: Drawable): Int {
        val key = "$label $pkg".lowercase(Locale.ROOT)
        return when {
            key.contains("youtube") || key.contains("يوتيوب") -> Color.WHITE
            key.contains("netflix") -> Color.parseColor("#181818")
            key.contains("prime") -> Color.parseColor("#00A8E1")
            key.contains("plex") -> Color.parseColor("#1F1F1F")
            key.contains("apple") -> Color.parseColor("#141414")
            key.contains("downloader") -> Color.parseColor("#F57C00")
            key.contains("aroma") -> Color.parseColor("#B71C1C")
            key.contains("viu") -> Color.parseColor("#FBC02D")
            key.contains("vlc") -> Color.parseColor("#FB8C00")
            else -> darker(dominant(icon))
        }
    }

    private fun createCell(app: ResolveInfo, w: Int): View {
        val pm = packageManager
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(pm).toString()
        val icon = app.loadIcon(pm)
        val banner: Drawable? = try { app.activityInfo.loadBanner(pm) ?: app.activityInfo.applicationInfo.loadBanner(pm) } catch (e: Throwable) { null }
        val bg = if (banner != null) 0xFF121826.toInt() else brandBg(label, pkg, icon)
        val glowC = dominant(banner ?: icon)
        val th = w * 9 / 16

        val tile = FrameLayout(this).apply {
            background = solidCard(bg, 20, Color.argb(60, 255, 255, 255), 1)
            isFocusable = true
            isClickable = true
            setOnClickListener { go(Intent(Intent.ACTION_MAIN).setClassName(pkg, app.activityInfo.name)) }
            setOnLongClickListener { showAppMenu(app); true }
            setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f)
                    .translationY(if (f) -dp(4).toFloat() else 0f).setDuration(140).start()
                v.background = solidCard(bg, 20, if (f) Color.WHITE else Color.argb(60, 255, 255, 255), if (f) 3 else 1)
                v.elevation = if (f) 26f else 4f
                if (f) ambient.glow(glowC)
            }
        }
        if (banner != null) {
            tile.addView(ImageView(this).apply {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
                    }
                }
            }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        } else {
            tile.addView(ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        cell.addView(tile, LinearLayout.LayoutParams(w, th))
        cell.addView(tv(12f, true, dim).apply { text = label; maxLines = 1; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) },
            LinearLayout.LayoutParams(w, -2))
        return cell
    }

    private fun showAppMenu(app: ResolveInfo) {
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(packageManager).toString()
        val favs = (prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
        if (favs.isEmpty()) favs.addAll(allApps.take(7).map { it.activityInfo.packageName })
        val inFav = favs.contains(pkg)
        val idx = favs.indexOf(pkg)

        fun save() {
            prefs.edit().putString("fav_apps", favs.joinToString(",")).apply()
            loadAppsAndDock()
        }

        val (dialog, box) = shell()
        box.addView(tv(17f, true, accent).apply { text = label; setPadding(0, 0, 0, dp(12)) })
        val opts = mutableListOf<Pair<String, () -> Unit>>()
        opts.add((if (inFav) "إزالة من المفضلة ✕" else "تثبيت في المفضلة ⭐") to {
            if (inFav) favs.remove(pkg) else favs.add(pkg)
            save()
        })
        if (inFav && idx > 0) opts.add("تحريك لليمين (تقديم) ▶" to { favs.removeAt(idx); favs.add(idx - 1, pkg); save() })
        if (inFav && idx < favs.size - 1) opts.add("تحريك لليسار (تأخير) ◀" to { favs.removeAt(idx); favs.add(idx + 1, pkg); save() })
        opts.add("معلومات التطبيق ℹ️" to { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", pkg, null) }) })
        opts.add("إلغاء تثبيت التطبيق 🗑️" to { go(Intent(Intent.ACTION_DELETE).apply { data = Uri.fromParts("package", pkg, null) }) })
        for ((title, act) in opts) {
            box.addView(optionRow(title) { dialog.dismiss(); act() }, LinearLayout.LayoutParams(dp(330), -2).apply { bottomMargin = dp(8) })
        }
        dialog.setContentView(box)
        dialog.show()
    }

    private fun updateClock() {
        try {
            val now = LocalTime.now()
            clockTv.text = now.format(DateTimeFormatter.ofPattern("h:mm a", ar))
            miniClockTv.text = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
            dayTv.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", ar))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy م", ar))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar)) } catch (e: Throwable) { "" }
            datesTv.text = if (h.isNotEmpty()) "$h\n$m" else m
            if (now.minute != lastMin) { lastMin = now.minute; renderPrayers() }
        } catch (e: Throwable) { }
        ui.postDelayed({ updateClock() }, 10000)
    }

    private fun refreshFeeds() {
        val c = city()
        ui.removeCallbacks(feedsTask)
        ui.postDelayed(feedsTask, 15 * 60 * 1000L)
        thread {
            try {
                val w = Data.weather(c)
                ui.post {
                    tempTv.text = "${w.temp}°"
                    weatherStatusTv.text = w.status
                    weatherSubTv.text = "المحسوسة ${w.feels}° • الرطوبة ${w.humidity}%\nالرياح ${w.wind} كم/س"
                }
            } catch (e: Throwable) { }
            try {
                val p = Data.prayers(c)
                ui.post { prayersData = p; renderPrayers() }
            } catch (e: Throwable) { }
        }
    }

    private fun renderPrayers() {
        if (prayersData.isEmpty()) return
        val now = LocalTime.now()
        var next = prayersData.indexOfFirst { it.time.isAfter(now) }
        val wrap = next == -1
        if (wrap) next = 0
        val f = DateTimeFormatter.ofPattern("h:mm a", ar)
        prayerLayout.removeAllViews()
        for ((i, p) in prayersData.withIndex()) {
            val on = i == next
            prayerLayout.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(1), dp(6), dp(1))
                if (on) background = glass(180, 8)
                addView(tv(11.5f, on, if (on) accent else dim).apply { text = p.name }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(tv(11.5f, on).apply { text = p.time.format(f) })
            })
        }
        val t = prayersData[next].time
        val mins = if (wrap) Duration.between(now, LocalTime.MAX).toMinutes() + Duration.between(LocalTime.MIN, t).toMinutes()
        else Duration.between(now, t).toMinutes()
        val hh = mins / 60
        val mm = mins % 60
        prayerTimerTv.text = "الأذان القادم بعد ${if (hh > 0) "$hh س و " else ""}$mm د"
    }
}
