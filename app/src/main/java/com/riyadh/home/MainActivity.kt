package com.riyadh.home

import android.animation.ArgbEvaluator
import android.animation.LayoutTransition
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
import android.graphics.drawable.LayerDrawable
import android.net.Uri
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
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class AmbientView(c: Context) : View(c) {
    private var color = 0xFF7A1C24.toInt()
    private var from = color
    private var to = color
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 650
        addUpdateListener {
            color = ArgbEvaluator().evaluate(it.animatedValue as Float, from, to) as Int
            invalidate()
        }
    }

    private val pulseAnim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 16000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
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

        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + 35f) % 360f
        hsv[1] = (hsv[1] * 0.9f).coerceIn(0.4f, 1f)
        hsv[2] = (hsv[2] * 0.85f).coerceIn(0.35f, 0.95f)
        val sec = Color.HSVToColor(hsv)
        val sr = Color.red(sec)
        val sg = Color.green(sec)
        val sb = Color.blue(sec)

        val dx = Math.sin(phase.toDouble()).toFloat() * w * 0.035f
        val dy = Math.cos(phase.toDouble()).toFloat() * h * 0.035f

        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0xFF06070B.toInt(), 0xFF0C0F17.toInt(), 0xFF08090F.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.35f + dx, h * 0.18f + dy, w * 0.65f,
            intArrayOf(Color.argb(175, r, g, b), Color.argb(75, sr, sg, sb), Color.TRANSPARENT),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.85f - dx, h * 0.25f - dy, w * 0.55f,
            intArrayOf(Color.argb(130, r, g, b), Color.argb(45, r, g, b), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.5f - dy, h * 0.92f + dx * 0.4f, w * 0.6f,
            intArrayOf(Color.argb(120, sr, sg, sb), Color.argb(40, r, g, b), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(40, 0, 0, 0), Color.TRANSPARENT, Color.argb(95, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)
    }
}

class MainActivity : Activity() {
    private val ar = Locale("ar", "SA")
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private val accent = Color.parseColor("#2DD4BF")
    private val gold = Color.parseColor("#FBBF24")
    private val greenBadge = Color.parseColor("#22C55E")
    private val dim = Color.argb(195, 255, 255, 255)
    private val muted = Color.parseColor("#94A3B8")
    private val darkText = Color.parseColor("#0B0F19")
    private val homeGlow = 0xFF7A1C24.toInt()

    private lateinit var ambient: AmbientView
    private lateinit var backgroundImageView: ImageView
    private lateinit var overlay: View
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

    private var allApps = mutableListOf<ResolveInfo>()
    private var prayersData: List<Prayer> = emptyList()
    private var matchesData: List<Match>? = null
    private val statusViews = ArrayList<Pair<TextView, Match>>()
    private var lastMin = -1
    private val logoCache = ConcurrentHashMap<String, Bitmap>()
    private val bgCache = ConcurrentHashMap<String, Bitmap>()
    private val feedsTask = Runnable { fetchMatches(); refreshFeeds() }

    private var reorderingPkg: String? = null
    private var reorderList = mutableListOf<String>()

    private val weights = mapOf(
        "الهلال" to 100, "ريال مدريد" to 90, "برشلونة" to 90, "مانشستر سيتي" to 90,
        "النصر" to 60, "الاتحاد" to 60, "الأهلي" to 60,
        "ليفربول" to 70, "أرسنال" to 70, "بايرن ميونخ" to 70, "باريس سان جيرمان" to 70,
        "أتلتيكو مدريد" to 70, "مانشستر يونايتد" to 70, "تشيلسي" to 70, "إنتر ميلان" to 70,
        "ميلان" to 70, "يوفنتوس" to 70, "دورتموند" to 70
    )

    private fun score(m: Match) = (weights[m.home] ?: 0) + (weights[m.away] ?: 0)
    private fun rank(m: Match) = score(m) + (if (m.live) 60 else 0) - (if (m.note == "انتهت") 200 else 0)

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
    private fun city(): City = Data.cities[prefs.getInt("city_idx", 0).coerceIn(0, Data.cities.size - 1)]

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

    private fun slateGlass(
        fillAlpha: Int = 210,
        radius: Int = 24,
        strokeColor: Int = Color.argb(48, 255, 255, 255),
        strokeDp: Int = 1
    ): Drawable {
        val r = dp(radius).toFloat()
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(fillAlpha.coerceIn(0, 255), 30, 35, 48),
                Color.argb((fillAlpha * 0.94f).toInt().coerceIn(0, 255), 20, 24, 34)
            )
        ).apply {
            cornerRadius = r
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }
        return base
    }

    private fun glass(alpha: Int = 140, radius: Int = 20, strokeColor: Int = Color.argb(90, 255, 255, 255), strokeDp: Int = 1): Drawable {
        val r = dp(radius).toFloat()
        val base = GradientDrawable().apply {
            setColor(Color.argb((alpha * 0.55f).toInt().coerceAtMost(160), 18, 22, 34))
            cornerRadius = r
        }
        val shine = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb((alpha * 0.35f).toInt().coerceAtMost(255), 255, 255, 255),
                Color.argb((alpha * 0.10f).toInt().coerceAtMost(255), 255, 255, 255)
            )
        ).apply {
            cornerRadius = r
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }
        return LayerDrawable(arrayOf(base, shine))
    }

    private fun solidCard(color: Int, radius: Int = 18, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0) = GradientDrawable().apply {
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_TV,
            KeyEvent.KEYCODE_TV_INPUT,
            KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_TV_DATA_SERVICE,
            KeyEvent.KEYCODE_TV_CONTENTS_MENU,
            KeyEvent.KEYCODE_DVR -> return true
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
        if (reorderingPkg != null) {
            finishReorderMode()
            return
        }
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
        val frame = FrameLayout(this).unclip().apply { setBackgroundColor(0xFF06070B.toInt()) }
        ambient = AmbientView(this)
        frame.addView(ambient, FrameLayout.LayoutParams(-1, -1))
        backgroundImageView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        frame.addView(backgroundImageView, FrameLayout.LayoutParams(-1, -1))
        overlay = View(this).apply { setBackgroundColor(0x99080B12.toInt()); visibility = View.GONE }
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        applyCustomBackground()

        val root = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(34), dp(16), dp(34), dp(12))
        }
        root.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2))
        val box = FrameLayout(this).unclip()
        pages = listOf(buildHomePage(), buildMatchesPage(), buildAppsPage())
        pages.forEach { box.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        root.addView(box, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        showPage(0)
    }

    private fun showPage(i: Int) {
        if (reorderingPkg != null) finishReorderMode()
        currentPage = i
        pages.forEachIndexed { k, p ->
            if (k == i) {
                p.visibility = View.VISIBLE
                p.alpha = 0f
                p.translationY = dp(12).toFloat()
                p.animate().alpha(1f).translationY(0f).setDuration(260).start()
            } else p.visibility = View.GONE
        }
        updateTabStyles()
        ambient.glow(homeGlow)
    }

    private fun updateTabStyles() {
        tabPills.forEachIndexed { k, pill ->
            val f = pill.hasFocus()
            val sel = k == currentPage
            val txt = tabTexts[k]
            when {
                f -> {
                    pill.background = solidCard(Color.WHITE, 26)
                    txt.setTextColor(darkText)
                }
                sel -> {
                    pill.background = slateGlass(230, 26, accent, 1)
                    txt.setTextColor(accent)
                }
                else -> {
                    pill.background = slateGlass(165, 26, Color.argb(55, 255, 255, 255), 1)
                    txt.setTextColor(Color.WHITE)
                }
            }
        }
    }

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
        }
        val clockLine = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clockTv = tv(38f, true, Color.WHITE).apply {
            includeFontPadding = false
        }
        val vSep = View(this).apply {
            background = solidCard(Color.argb(90, 255, 255, 255), 2)
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

        val names = listOf("الرئيسية", "المباريات", "التطبيقات")
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
        h.addView(actionCapsule("الإعدادات", 1) { openSettingsDialog() }, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })

        return h
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
            background = slateGlass(185, 26, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(14), dp(7), dp(12), dp(7))
            isFocusable = true
            isClickable = true
            roundOutline(26)
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(title)
            addView(badge)
            setOnClickListener { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) solidCard(Color.WHITE, 26) else slateGlass(185, 26, Color.argb(70, 255, 255, 255), 1)
                title.setTextColor(if (f) darkText else Color.WHITE)
                glyph.setTint(if (f) darkText else Color.WHITE)
                v.translationZ = if (f) dp(10).toFloat() else 0f
                v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
            }
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
            background = slateGlass(185, 26, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = true
            isClickable = true
            roundOutline(26)
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(cityPillTv)
            setOnClickListener { openCityPicker() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) solidCard(Color.WHITE, 26) else slateGlass(185, 26, Color.argb(70, 255, 255, 255), 1)
                cityPillTv.setTextColor(if (f) darkText else Color.WHITE)
                glyph.setTint(if (f) darkText else Color.WHITE)
                v.translationZ = if (f) dp(10).toFloat() else 0f
                v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
            }
        }
    }

    private fun actionCapsule(label: String, kind: Int, onClick: () -> Unit): View {
        val glyph = GlyphView(this, kind)
        val title = tv(13f, true, Color.WHITE).apply {
            text = label
            setPadding(dp(7), 0, dp(4), 0)
        }
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(185, 26, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = true
            isClickable = true
            roundOutline(26)
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(title)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) solidCard(Color.WHITE, 26) else slateGlass(185, 26, Color.argb(70, 255, 255, 255), 1)
                title.setTextColor(if (f) darkText else Color.WHITE)
                glyph.setTint(if (f) darkText else Color.WHITE)
                v.translationZ = if (f) dp(10).toFloat() else 0f
                v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
            }
        }
    }

    private fun buildHomePage(): View {
        val col = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        // اللوحة الزجاجية الموحدة مع ترتيب عربي صحيح 100% (يمين: طقس، وسط: صلاة، يسار: مباريات)
        val unifiedBoard = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            background = slateGlass(215, 26, Color.argb(55, 255, 255, 255), 1)
            setPadding(dp(22), dp(16), dp(22), dp(16))
            roundOutline(26)
        }

        // 1. القسم الأيمن: الطقس
        val weatherCol = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(14), 0)
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
        unifiedBoard.addView(weatherCol, LinearLayout.LayoutParams(dp(210), -1))

        // فاصل عمودي 1
        unifiedBoard.addView(View(this).apply {
            background = solidCard(Color.argb(35, 255, 255, 255), 1)
        }, LinearLayout.LayoutParams(dp(1), -1).apply { setMargins(dp(4), dp(6), dp(14), dp(6)) })

        // 2. القسم الأوسط: مواقيت الصلاة
        val prayerCol = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(14), 0)
        }
        prayerCol.addView(tv(10.5f, true, muted).apply { text = "التوقيت المحلي" })
        prayerCol.addView(tv(15f, true, accent).apply {
            text = "مواقيت الصلاة"
            setPadding(0, dp(2), 0, dp(6))
        })
        prayerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        prayerCol.addView(prayerLayout, LinearLayout.LayoutParams(-1, 0, 1f))
        prayerTimerTv = tv(11.5f, true, gold).apply {
            text = "جاري تحديث المواقيت..."
            setPadding(0, dp(4), 0, 0)
        }
        prayerCol.addView(prayerTimerTv)
        unifiedBoard.addView(prayerCol, LinearLayout.LayoutParams(dp(235), -1))

        // فاصل عمودي 2
        unifiedBoard.addView(View(this).apply {
            background = solidCard(Color.argb(35, 255, 255, 255), 1)
        }, LinearLayout.LayoutParams(dp(1), -1).apply { setMargins(dp(4), dp(6), dp(14), dp(6)) })

        // 3. القسم الأيسر: مباريات اليوم (مع دعم اعلام الأندية واللوغوهات الأصلية)
        val matchesCol = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
        }
        val mHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mTitles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mTitles.addView(tv(10.5f, true, muted).apply { text = "نتائج ومواعيد مباشرة" })
        mTitles.addView(tv(15f, true, accent).apply { text = "مباريات اليوم"; setPadding(0, dp(2), 0, 0) })
        mHead.addView(mTitles)

        tickerTv = tv(11.5f, false, dim).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(14), 0, dp(8), 0)
        }
        mHead.addView(tickerTv, LinearLayout.LayoutParams(0, -2, 1f))
        matchesCol.addView(mHead, LinearLayout.LayoutParams(-1, -2))

        homeMatchRow = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(10))
        }
        val matchScroll = HorizontalScrollView(this).unclip().apply {
            isHorizontalScrollBarEnabled = false
            addView(homeMatchRow)
        }
        matchesCol.addView(matchScroll, LinearLayout.LayoutParams(-1, -1))
        unifiedBoard.addView(matchesCol, LinearLayout.LayoutParams(0, -1, 1f))

        col.addView(unifiedBoard, LinearLayout.LayoutParams(-1, dp(195)))
        col.addView(View(this), LinearLayout.LayoutParams(-1, dp(14)))

        // شريط التطبيقات السفلي (Dock)
        val dock = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(210, 28, Color.argb(55, 255, 255, 255), 1)
            setPadding(dp(18), dp(14), dp(18), dp(12))
            roundOutline(28)
        }

        reorderHintTv = tv(12.5f, true, gold).apply {
            text = "↔️  وضع تحريك التطبيق: استخدم الأسهم (يمين / يسار) لتغيير المكان، ثم اضغط (موافق OK) للتثبيت"
            gravity = Gravity.CENTER
            background = solidCard(Color.argb(180, 15, 23, 42), 14, accent, 1)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.GONE
        }
        dock.addView(reorderHintTv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        favRow = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            layoutTransition = LayoutTransition().apply {
                setDuration(180)
            }
        }
        dock.addView(HorizontalScrollView(this).unclip().apply {
            isHorizontalScrollBarEnabled = false
            addView(favRow)
        }, LinearLayout.LayoutParams(-1, -2))

        col.addView(dock, LinearLayout.LayoutParams(-1, -2))

        return ScrollView(this).unclip().apply {
            isVerticalScrollBarEnabled = false
            addView(col)
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

    private fun buildAppsPage(): View {
        val col = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(24))
        }
        col.addView(tv(18f, true, accent).apply {
            text = "جميع التطبيقات (اضغط مطولاً للإدارة أو الإضافة للمفضلة)"
            setPadding(dp(16), dp(4), dp(16), dp(6))
        })
        allGrid = GridLayout(this).unclip().apply {
            columnCount = 5
            setPadding(dp(18), dp(16), dp(18), dp(36))
        }
        col.addView(allGrid, LinearLayout.LayoutParams(-1, -2))
        return ScrollView(this).unclip().apply {
            isVerticalScrollBarEnabled = false
            addView(col)
        }
    }

    class GlyphView(c: Context, private val kind: Int) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
        fun setTint(c: Int) {
            p.color = c
            invalidate()
        }
        override fun onDraw(cv: Canvas) {
            super.onDraw(cv)
            val d = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            when (kind) {
                0 -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.2f * d
                    for (r in listOf(4.2f, 7.5f, 10.8f)) cv.drawArc(cx - r * d, cy + 5 * d - r * d, cx + r * d, cy + 5 * d + r * d, 225f, 90f, false, p)
                    p.style = Paint.Style.FILL
                    cv.drawCircle(cx, cy + 5 * d, 1.6f * d, p)
                }
                1 -> {
                    p.style = Paint.Style.FILL
                    val path = Path()
                    val big = 9.5f * d
                    val small = 7.2f * d
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
                    path.addCircle(cx, cy, 3.4f * d, Path.Direction.CW)
                    cv.drawPath(path, p)
                }
                else -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.0f * d
                    cv.drawCircle(cx, cy - 2.5f * d, 5f * d, p)
                    cv.drawLine(cx - 3.8f * d, cy + 1f * d, cx, cy + 8.5f * d, p)
                    cv.drawLine(cx + 3.8f * d, cy + 1f * d, cx, cy + 8.5f * d, p)
                    p.style = Paint.Style.FILL
                    cv.drawCircle(cx, cy - 2.5f * d, 1.8f * d, p)
                }
            }
        }
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
        val s = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = slateGlass(245, 28, Color.argb(80, 255, 255, 255), 1)
            setPadding(dp(28), dp(22), dp(28), dp(22))
        }
        return d to s
    }

    private fun optionRow(label: String, onClick: () -> Unit): View = tv(15f, true, Color.WHITE).apply {
        text = label
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = slateGlass(160, 16, Color.argb(40, 255, 255, 255), 1)
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            v.background = if (f) solidCard(Color.WHITE, 16) else slateGlass(160, 16, Color.argb(40, 255, 255, 255), 1)
            (v as TextView).setTextColor(if (f) darkText else Color.WHITE)
        }
    }

    private fun openSettingsDialog() {
        val (dialog, sheet) = shell()
        sheet.addView(tv(21f, true, accent).apply { text = "الإعدادات" })
        sheet.addView(tv(12f, false, dim).apply { text = "تحكم بواجهتك"; setPadding(0, dp(2), 0, dp(14)) })
        val g = GridLayout(this).unclip().apply { columnCount = 2 }
        fun card(icon: String, title: String, sub: String, act: () -> Unit) {
            val tTitle = tv(14.5f, true, Color.WHITE).apply { text = title; setPadding(0, dp(6), 0, 0) }
            val tSub = tv(11f, false, dim).apply { text = sub }
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = slateGlass(180, 18, Color.argb(60, 255, 255, 255), 1)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                isFocusable = true
                isClickable = true
                setOnClickListener { dialog.dismiss(); act() }
                setOnFocusChangeListener { v, f ->
                    v.background = if (f) solidCard(Color.WHITE, 18) else slateGlass(180, 18, Color.argb(60, 255, 255, 255), 1)
                    tTitle.setTextColor(if (f) darkText else Color.WHITE)
                    tSub.setTextColor(if (f) darkText else dim)
                    v.animate().scaleX(if (f) 1.05f else 1f).scaleY(if (f) 1.05f else 1f).setDuration(120).start()
                }
                addView(tv(22f).apply { text = icon })
                addView(tTitle)
                addView(tSub)
            }
            g.addView(c, GridLayout.LayoutParams().apply { width = dp(210); height = -2; setMargins(dp(6), dp(6), dp(6), dp(6)) })
        }
        card("📍", "المدينة", city().name) { openCityPicker() }
        card("📶", "الواي فاي", "الشبكة والاتصال") { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        card("⚙️", "إعدادات الجهاز", "النظام الأساسية") { go(Intent(Settings.ACTION_SETTINGS)) }
        card("🖼️", "صورة خلفية خاصة", "رابط صورة") { showBgUrlDialog() }
        card("✨", "الخلفية التفاعلية", "استعادة الافتراضية") {
            prefs.edit().remove("custom_bg_url").apply()
            applyCustomBackground()
        }
        card("⚽", "تحديث المباريات", "تحديث الآن") { fetchMatches() }
        card("⭐", "إعادة ضبط المفضلة", "الترتيب الافتراضي") {
            prefs.edit().remove("fav_apps").apply()
            loadAppsAndDock()
        }
        sheet.addView(ScrollView(this).unclip().apply { isVerticalScrollBarEnabled = false; addView(g) }, LinearLayout.LayoutParams(dp(470), dp(320)))
        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openCityPicker() {
        val (dialog, sheet) = shell()
        sheet.addView(tv(19f, true, accent).apply { text = "اختر مدينتك (الطقس والصلاة)"; setPadding(0, 0, 0, dp(12)) })
        val list = LinearLayout(this).unclip().apply { orientation = LinearLayout.VERTICAL }
        val cur = prefs.getInt("city_idx", 0)
        Data.cities.forEachIndexed { i, c ->
            list.addView(optionRow((if (i == cur) "✓  " else "") + c.name) {
                prefs.edit().putInt("city_idx", i).apply()
                cityHeaderTv.text = "الطقس • ${c.name}"
                cityPillTv.text = c.name
                dialog.dismiss()
                refreshFeeds()
            }, LinearLayout.LayoutParams(dp(340), -2).apply { bottomMargin = dp(8) })
        }
        sheet.addView(ScrollView(this).unclip().apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(dp(340), dp(300)))
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
        if (!::homeMatchRow.isInitialized) return
        homeMatchRow.removeAllViews()
        matchesGrid.removeAllViews()
        statusViews.clear()
        val all = matchesData
        val list = all?.sortedWith(compareByDescending<Match> { rank(it) }.thenBy { it.time })
        tickerTv.text = if (list == null || list.isEmpty()) "" else "⚽  " + list.take(5).joinToString("   •   ") { "${it.home} × ${it.away} (${it.center})" }
        if (list == null || list.isEmpty()) {
            val msg = if (all == null) "تعذر تحميل المباريات، تأكد من الاتصال" else "لا توجد مباريات مسجلة اليوم"
            homeMatchRow.addView(tv(14f, false, dim).apply { text = msg; setPadding(dp(8), dp(14), dp(8), dp(14)) })
            matchesGrid.addView(tv(15f, false, dim).apply { text = msg; setPadding(dp(8), dp(14), dp(8), dp(14)) })
            return
        }
        val cw = dp(185)
        for (m in list) {
            homeMatchRow.addView(miniMatch(m), LinearLayout.LayoutParams(cw, -2).apply { marginEnd = dp(12) })
        }
        val w = (resources.displayMetrics.widthPixels - dp(68) - dp(44) - 3 * dp(20)) / 3
        for (m in list) {
            matchesGrid.addView(matchCard(m), GridLayout.LayoutParams().apply {
                width = w
                height = -2
                setMargins(dp(10), dp(10), dp(10), dp(10))
            })
        }
    }

    private fun miniLogo(name: String, url: String): ImageView {
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(crest(name)) }
        if (url.isNotEmpty()) loadLogo(url, iv)
        return iv
    }

    private fun miniMatch(m: Match): View {
        val card = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            background = glass(175, 18, Color.argb(75, 255, 255, 255), 1)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isFocusable = true
            isClickable = true
            roundOutline(18)
            setOnClickListener { showPage(1) }
            setOnFocusChangeListener { v, f ->
                v.background = glass(if (f) 245 else 175, 18, if (f) Color.WHITE else Color.argb(75, 255, 255, 255), if (f) 2 else 1)
                v.translationZ = if (f) dp(16).toFloat() else 0f
                v.elevation = if (f) 24f else 2f
                v.animate()
                    .scaleX(if (f) 1.09f else 1f)
                    .scaleY(if (f) 1.09f else 1f)
                    .translationY(if (f) -dp(3).toFloat() else 0f)
                    .setDuration(140)
                    .start()
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(10f, true, accent).apply { text = m.league; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        val st = tv(9.5f, true, if (m.live) gold else dim).apply { text = statusText(m); maxLines = 1 }
        statusViews.add(st to m)
        head.addView(st)
        card.addView(head, LinearLayout.LayoutParams(-1, -2))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(4)) }
        row.addView(miniLogo(m.home, m.homeLogo), LinearLayout.LayoutParams(dp(28), dp(28)))
        row.addView(tv(14.5f, true, gold).apply { text = m.center; gravity = Gravity.CENTER; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(miniLogo(m.away, m.awayLogo), LinearLayout.LayoutParams(dp(28), dp(28)))
        card.addView(row, LinearLayout.LayoutParams(-1, -2))

        card.addView(tv(11f, true, Color.WHITE).apply {
            text = "${m.home} × ${m.away}"
            maxLines = 1
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun matchCard(m: Match): View {
        val card = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = slateGlass(210, 22, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            isClickable = true
            roundOutline(22)
            setOnFocusChangeListener { v, f ->
                v.background = slateGlass(if (f) 245 else 210, 22, if (f) Color.WHITE else Color.argb(70, 255, 255, 255), if (f) 3 else 1)
                v.translationZ = if (f) dp(16).toFloat() else 0f
                v.elevation = if (f) 26f else 4f
                v.animate()
                    .scaleX(if (f) 1.07f else 1f)
                    .scaleY(if (f) 1.07f else 1f)
                    .translationY(if (f) -dp(4).toFloat() else 0f)
                    .setDuration(140)
                    .start()
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(11f, true, accent).apply { text = m.league; maxLines = 1 })
        head.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        val st = tv(10.5f, true, if (m.live) gold else dim).apply { text = statusText(m) }
        statusViews.add(st to m)
        head.addView(st)
        card.addView(head, LinearLayout.LayoutParams(-1, -2))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        row.addView(clubColumn(m.home, m.homeLogo), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv(18f, true, gold).apply { text = m.center; gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) })
        row.addView(clubColumn(m.away, m.awayLogo), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun clubColumn(name: String, logoUrl: String): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(miniLogo(name, logoUrl), LinearLayout.LayoutParams(dp(42), dp(42)))
        col.addView(tv(12f, true).apply { text = name; maxLines = 2; gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
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

    private fun getSavedFavs(): MutableList<String> {
        val saved = prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        return if (saved.isNotEmpty()) saved.toMutableList()
        else allApps.take(6).map { it.activityInfo.packageName }.toMutableList()
    }

    private fun startReorderMode(pkg: String) {
        reorderingPkg = pkg
        reorderList = getSavedFavs()
        reorderHintTv.visibility = View.VISIBLE
        if (currentPage != 0) showPage(0)
        loadAppsAndDock(focusPkg = pkg)
    }

    private fun moveFavoriteLive(delta: Int) {
        val pkg = reorderingPkg ?: return
        val idx = reorderList.indexOf(pkg)
        if (idx == -1) return
        val targetIdx = idx + delta
        if (targetIdx < 0 || targetIdx >= reorderList.size) return

        val item = reorderList.removeAt(idx)
        reorderList.add(targetIdx, item)
        prefs.edit().putString("fav_apps", reorderList.joinToString(",")).apply()

        val neighborView = favRow.getChildAt(targetIdx) ?: return
        favRow.removeViewAt(targetIdx)
        favRow.addView(neighborView, idx)
    }

    private fun finishReorderMode() {
        val pkg = reorderingPkg
        reorderingPkg = null
        reorderHintTv.visibility = View.GONE
        if (reorderList.isNotEmpty()) {
            prefs.edit().putString("fav_apps", reorderList.joinToString(",")).apply()
        }
        loadAppsAndDock(focusPkg = pkg)
        Toast.makeText(this, "تم تثبيت مكان التطبيق ✓", Toast.LENGTH_SHORT).show()
    }

    private fun loadAppsAndDock(focusPkg: String? = null) {
        val pm = packageManager
        allApps = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .toMutableList()

        val favPkgs = if (reorderingPkg != null) reorderList else getSavedFavs()
        val sw = resources.displayMetrics.widthPixels

        favRow.removeAllViews()
        val favList = favPkgs.mapNotNull { pkg -> allApps.find { it.activityInfo.packageName == pkg } }
        val fw = (sw - dp(68) - dp(36) - dp(32) - 5 * dp(16)) / 5
        var viewToFocus: View? = null

        for (a in favList) {
            val cell = createCell(a, fw, isDock = true)
            if (a.activityInfo.packageName == focusPkg) {
                viewToFocus = (cell as? ViewGroup)?.getChildAt(0)
            }
            favRow.addView(cell, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })
        }

        allGrid.removeAllViews()
        val gw = (sw - dp(68) - dp(36) - 5 * dp(20)) / 5
        for (a in allApps) {
            allGrid.addView(createCell(a, gw, isDock = false), GridLayout.LayoutParams().apply {
                width = -2
                height = -2
                setMargins(dp(10), dp(10), dp(10), dp(10))
            })
        }

        if (currentPage == 0) {
            val target = viewToFocus ?: ((favRow.getChildAt(0) as? ViewGroup)?.getChildAt(0))
            target?.post { target.requestFocus() }
        }
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
        if (n < 10) 0xFF7A1C24.toInt() else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    } catch (e: Throwable) { 0xFF7A1C24.toInt() }

    private fun darker(c: Int) = Color.rgb((Color.red(c) * 0.55f).toInt(), (Color.green(c) * 0.55f).toInt(), (Color.blue(c) * 0.55f).toInt())

    private fun brandBg(label: String, pkg: String, icon: Drawable): Int {
        val key = "$label $pkg".lowercase(Locale.ROOT)
        return when {
            key.contains("youtube") || key.contains("يوتيوب") -> Color.WHITE
            key.contains("netflix") -> Color.parseColor("#141414")
            key.contains("spotify") -> Color.parseColor("#24292E")
            key.contains("twitch") -> Color.parseColor("#772CE8")
            key.contains("canal") || key.contains("shahid") || key.contains("شاهد") -> Color.parseColor("#1F242D")
            key.contains("prime") -> Color.parseColor("#00A8E1")
            key.contains("plex") -> Color.parseColor("#1F1F1F")
            key.contains("apple") -> Color.parseColor("#141414")
            key.contains("downloader") -> Color.parseColor("#F57C00")
            key.contains("aroma") -> Color.parseColor("#181B24")
            key.contains("viu") -> Color.parseColor("#FBC02D")
            key.contains("vlc") -> Color.parseColor("#FB8C00")
            else -> darker(dominant(icon))
        }
    }

    private fun createCell(app: ResolveInfo, w: Int, isDock: Boolean): View {
        val pm = packageManager
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(pm).toString()
        val icon = app.loadIcon(pm)
        val banner: Drawable? = try { app.activityInfo.loadBanner(pm) ?: app.activityInfo.applicationInfo.loadBanner(pm) } catch (e: Throwable) { null }
        val bg = if (banner != null) 0xFF141822.toInt() else brandBg(label, pkg, icon)
        val glowC = dominant(banner ?: icon)
        val th = (w * 0.54f).toInt()
        val isMovingThis = isDock && (reorderingPkg == pkg)

        val cell = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val titleTv = tv(11.5f, true, if (isMovingThis) gold else dim).apply {
            text = if (isMovingThis) "◀  $label  ▶" else label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), 0)
        }

        val borderOverlay = View(this).apply {
            background = solidCard(
                Color.TRANSPARENT,
                20,
                if (isMovingThis) accent else Color.argb(55, 255, 255, 255),
                if (isMovingThis) 3 else 1
            )
        }

        val tile = FrameLayout(this).unclip().apply {
            background = solidCard(bg, 20)
            roundOutline(20)
            clipToOutline = true
            isFocusable = true
            isClickable = true
            setOnClickListener {
                if (reorderingPkg != null) {
                    finishReorderMode()
                } else {
                    go(Intent(Intent.ACTION_MAIN).setClassName(pkg, app.activityInfo.name))
                }
            }
            setOnLongClickListener {
                if (reorderingPkg != null) {
                    finishReorderMode()
                } else {
                    showAppMenu(app)
                }
                true
            }
            setOnFocusChangeListener { v, f ->
                val movingNow = isDock && (reorderingPkg == pkg)
                borderOverlay.background = solidCard(
                    Color.TRANSPARENT,
                    20,
                    when {
                        movingNow -> gold
                        f -> Color.WHITE
                        else -> Color.argb(55, 255, 255, 255)
                    },
                    if (f || movingNow) 3 else 1
                )
                v.elevation = if (f || movingNow) 30f else 4f
                cell.translationZ = if (f || movingNow) dp(18).toFloat() else 0f
                titleTv.setTextColor(
                    when {
                        movingNow -> gold
                        f -> Color.WHITE
                        else -> dim
                    }
                )
                titleTv.text = if (movingNow) "◀  $label  ▶" else label

                val targetScale = when {
                    movingNow -> 1.13f
                    f -> 1.10f
                    else -> 1f
                }
                val targetY = when {
                    movingNow -> -dp(7).toFloat()
                    f -> -dp(4).toFloat()
                    else -> 0f
                }
                cell.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .translationY(targetY)
                    .setDuration(145)
                    .start()

                if (f) ambient.glow(glowC)
            }
        }

        if (banner != null) {
            tile.addView(ImageView(this).apply {
                setImageDrawable(banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val isz = (th * 0.64f).toInt()
            tile.addView(ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(isz, isz, Gravity.CENTER))
        }

        tile.addView(borderOverlay, FrameLayout.LayoutParams(-1, -1))
        cell.addView(tile, LinearLayout.LayoutParams(w, th))
        cell.addView(titleTv, LinearLayout.LayoutParams(w, -2))
        return cell
    }

    private fun showAppMenu(app: ResolveInfo) {
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(packageManager).toString()
        val favs = getSavedFavs()
        val inFav = favs.contains(pkg)

        fun save() {
            prefs.edit().putString("fav_apps", favs.joinToString(",")).apply()
            loadAppsAndDock(focusPkg = pkg)
        }

        val (dialog, box) = shell()
        box.addView(tv(18f, true, accent).apply { text = label; setPadding(0, 0, 0, dp(12)) })
        val opts = mutableListOf<Pair<String, () -> Unit>>()

        if (inFav && favs.size > 1) {
            opts.add("تحريك المكان بحرية (سحب بالأسهم) ↔️" to {
                startReorderMode(pkg)
            })
        }
        opts.add((if (inFav) "إزالة من المفضلة ✕" else "إضافة إلى المفضلة ⭐") to {
            if (inFav) favs.remove(pkg) else favs.add(pkg)
            save()
        })
        opts.add("معلومات التطبيق ℹ️" to {
            go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", pkg, null) })
        })
        opts.add("إلغاء تثبيت التطبيق 🗑️" to {
            go(Intent(Intent.ACTION_DELETE).apply { data = Uri.fromParts("package", pkg, null) })
        })
        for ((title, act) in opts) {
            box.addView(optionRow(title) { dialog.dismiss(); act() }, LinearLayout.LayoutParams(dp(350), -2).apply { bottomMargin = dp(8) })
        }
        dialog.setContentView(box)
        dialog.show()
    }

    private fun updateClock() {
        try {
            val now = LocalTime.now()
            greetingTv.text = if (now.hour in 4..11) "صباح الخير" else "مساء الخير"
            clockTv.text = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
            val day = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", ar))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", ar))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar)) } catch (e: Throwable) { "" }
            dateLineTv.text = if (h.isNotEmpty()) "$day $m\n$h" else "$day $m"

            if (now.minute != lastMin) {
                lastMin = now.minute
                renderPrayers()
                for ((t, mt) in statusViews) t.text = statusText(mt)
            }
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
                setPadding(dp(8), dp(2), dp(8), dp(2))
                if (on) background = solidCard(Color.argb(55, 45, 212, 191), 8, accent, 1)
                addView(tv(11.5f, on, if (on) accent else dim).apply { text = p.name }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(tv(11.5f, on, if (on) Color.WHITE else dim).apply { text = p.time.format(f) })
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
