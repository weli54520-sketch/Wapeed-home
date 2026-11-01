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

        paint.shader = LinearGradient(
            0f, 0f, w, h,
            base,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.25f + dx, h * 0.15f + dy, w * 0.6f,
            intArrayOf(Color.argb(90, r, g, b), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.85f - dx, h * 0.2f - dy, w * 0.5f,
            intArrayOf(Color.argb(70, r, g, b), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
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
        sheen.shader = LinearGradient(
            0f, t, 0f, t + h * 0.55f,
            Color.argb(sheenA, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        line.strokeWidth = strokePx
        val r = Color.red(borderColor)
        val g = Color.green(borderColor)
        val bl = Color.blue(borderColor)
        val a = Color.alpha(borderColor)
        line.shader = if (solidBorder) {
            LinearGradient(
                b.left.toFloat(), t, b.right.toFloat(), bt,
                intArrayOf(Color.argb(a, r, g, bl), Color.argb((a * 0.9f).toInt(), r, g, bl), Color.argb(a, r, g, bl)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                b.left.toFloat(), t, b.right.toFloat(), bt,
                intArrayOf(Color.argb(a, r, g, bl), Color.argb((a * 0.22f).toInt(), r, g, bl), Color.argb((a * 0.65f).toInt(), r, g, bl)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
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

/* ───────────────────────── شعار WM ─────────────────────────
   حرف W (وليد) ينعكس في مرآة ذهبية فيتحول إلى M (الواجهة) */
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

    /* ─────────── صفحة إعدادات WM Launcher ─────────── */
    private val sectionNames = listOf("عام", "المظهر", "اللغة والأرقام", "التطبيقات", "الريموت", "الطقس", "المباريات", "أوقات الصلاة", "حول والتحديث")
    private val sectionIcons = listOf("🏠", "🎨", "🌐", "📱", "🎮", "☀️", "⚽", "🕌", "ℹ️")
    private val sectionSubs = listOf(
        "الإعدادات الأساسية واختصارات النظام",
        "الألوان والزجاج والحدود والتحديد",
        "لغة التاريخ ونوع الأرقام والساعة",
        "المفضلة وجميع التطبيقات وآخر المستخدم",
        "أزرار الريموت والاختصارات",
        "المدينة ووحدة الحرارة والتحديث",
        "مباريات اليوم وبطاقات الرئيسية",
        "أوقات الصلاة والعد التنازلي",
        "الإصدار والتحديث من الواجهة نفسها"
    )

    private fun buildSettingsPage(): View {
        val page = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }

        // الشريط الجانبي: الشعار والاسم والأقسام
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(220, 28, Color.argb(45, 255, 255, 255), 1)
            setPadding(dp(14), dp(14), dp(14), dp(10))
            roundOutline(28)
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(12))
        }
        brand.addView(LogoView(this), LinearLayout.LayoutParams(dp(46), dp(46)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandText.addView(tv(17f, true).apply { text = "WM Launcher"; layoutDirection = View.LAYOUT_DIRECTION_LTR })
        brandText.addView(tv(10.5f, false, muted).apply { text = "واجهة وليد" })
        brand.addView(brandText)
        side.addView(brand)

        val navList = mutableListOf<LinearLayout>()
        val navBox = LinearLayout(this).unclip().apply { orientation = LinearLayout.VERTICAL }
        sectionNames.forEachIndexed { i, n ->
            val ic = tv(14f).apply { text = sectionIcons[i]; setPadding(0, 0, dp(10), 0) }
            val tx = tv(13f, true).apply { text = n }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(9), dp(14), dp(9))
                isFocusable = true
                isClickable = true
                roundOutline(18)
                addView(ic)
                addView(tx)
                background = slateGlass(130, 18, Color.argb(30, 255, 255, 255), 1)
                setOnClickListener { settingsContent.getChildAt(firstFocusableRow())?.requestFocus() }
                setOnFocusChangeListener { v, f ->
                    if (f && currentSection != i) showSection(i)
                    styleNav()
                    v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f).setDuration(120).start()
                }
            }
            navList.add(item)
            navBox.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        settingsNav = navList
        side.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(navBox)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(side, LinearLayout.LayoutParams(dp(250), -1))

        // المحتوى
        val content = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        settingsTitleTv = tv(26f, true).apply { text = sectionNames[0] }
        settingsSubTv = tv(12f, false, dim).apply { setPadding(0, dp(2), 0, dp(12)) }
        content.addView(settingsTitleTv)
        content.addView(settingsSubTv)
        settingsContent = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(30))
        }
        settingsScroll = ScrollView(this).unclip().apply {
            isVerticalScrollBarEnabled = false
            addView(settingsContent)
        }
        content.addView(settingsScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(content, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(16) })

        showSection(currentSection.coerceIn(0, sectionNames.size - 1))
        return page
    }

    private fun styleNav() {
        if (!::settingsNav.isInitialized) return
        settingsNav.forEachIndexed { i, item ->
            val f = item.hasFocus()
            val sel = i == currentSection
            val tx = item.getChildAt(1) as TextView
            when {
                f -> { item.background = focusBg(18); tx.setTextColor(focusFg()) }
                sel -> { item.background = slateGlass(220, 18, accent, 1); tx.setTextColor(accent) }
                else -> { item.background = slateGlass(130, 18, Color.argb(30, 255, 255, 255), 1); tx.setTextColor(Color.WHITE) }
            }
        }
    }

    private fun firstFocusableRow(): Int {
        for (i in 0 until settingsContent.childCount) if (settingsContent.getChildAt(i).isFocusable) return i
        return 0
    }

    private fun showSection(i: Int) {
        currentSection = i
        settingsTitleTv.text = sectionNames[i]
        settingsSubTv.text = sectionSubs[i]
        settingsContent.removeAllViews()
        updateSubTv = null
        when (i) {
            0 -> buildGeneral()
            1 -> buildAppearance()
            2 -> buildLanguage()
            3 -> buildAppsSection()
            4 -> buildRemote()
            5 -> buildWeatherSection()
            6 -> buildMatchesSection()
            7 -> buildPrayerSection()
            else -> buildAbout()
        }
        settingsScroll.scrollTo(0, 0)
        styleNav()
    }

    private fun refreshSection() {
        val row = lastFocusedRow
        showSection(currentSection)
        settingsContent.post { settingsContent.getChildAt(row.coerceIn(0, maxOf(0, settingsContent.childCount - 1)))?.requestFocus() }
    }

    /* ─────────── أدوات صفوف الإعدادات ─────────── */
    private fun rowLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }

    private fun groupTitle(text: String) {
        settingsContent.addView(tv(12f, true, accent).apply {
            this.text = text
            setPadding(dp(6), dp(10), dp(6), dp(6))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun makeRow(title: String, sub: String?, trailing: View?, valueTv: TextView?, onKey: ((Int) -> Boolean)? = null): LinearLayout {
        val titleTv = tv(14.5f, true).apply { text = title }
        val subTv = tv(11f, false, dim).apply { text = sub ?: ""; visibility = if (sub == null) View.GONE else View.VISIBLE }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(170, 18, Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            isFocusable = true
            isClickable = true
            roundOutline(18)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(titleTv)
        texts.addView(subTv)
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) row.addView(trailing, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14) })
        row.setOnFocusChangeListener { v, f ->
            v.background = if (f) focusBg(18) else slateGlass(170, 18, Color.argb(40, 255, 255, 255), 1)
            titleTv.setTextColor(if (f) focusFg() else Color.WHITE)
            subTv.setTextColor(if (f) focusFg() else dim)
            valueTv?.setTextColor(if (f) focusFg() else accent)
            v.animate().scaleX(if (f) 1.015f else 1f).scaleY(if (f) 1.015f else 1f).setDuration(110).start()
            if (f) lastFocusedRow = settingsContent.indexOfChild(v)
        }
        if (onKey != null) {
            row.setOnKeyListener { _, code, ev -> if (ev.action == KeyEvent.ACTION_DOWN) onKey(code) else false }
        }
        if (row.parent == null) settingsContent.addView(row, rowLp())
        if (updateSubTv == null && title == "تحديث الآن") updateSubTv = subTv
        return row
    }

    private fun addToggle(title: String, sub: String?, key: String, def: Boolean, rebuild: Boolean = false, after: (() -> Unit)? = null) {
        val sw = SwitchView(this).apply { on = pBool(key, def); onColor = accent }
        val row = makeRow(title, sub, sw, null)
        row.setOnClickListener {
            val nv = !pBool(key, def)
            putBool(key, nv)
            sw.on = nv
            sw.invalidate()
            after?.invoke()
            if (rebuild) scheduleRebuild()
        }
    }

    private fun addChoice(title: String, sub: String?, key: String, options: List<String>, def: Int, rebuild: Boolean = false, after: (() -> Unit)? = null) {
        val valueTv = tv(13f, true, accent).apply { text = options[pInt(key, def).coerceIn(0, options.size - 1)] }
        val row = makeRow(title, sub, valueTv, valueTv)
        row.setOnClickListener {
            val cur = pInt(key, def)
            showDropdown(row, title, options.mapIndexed { i, o ->
                DropItem(o, null, i == cur) {
                    putInt(key, i)
                    valueTv.text = o
                    after?.invoke()
                    if (rebuild) scheduleRebuild()
                }
            })
        }
    }

    private fun addSlider(title: String, sub: String?, key: String, min: Int, max: Int, step: Int, def: Int, rebuild: Boolean = false, after: (() -> Unit)? = null) {
        val bar = BarView(this).apply { barColor = accent }
        val valTv = tv(13f, true, accent).apply { gravity = Gravity.CENTER }
        fun cur() = pInt(key, def).coerceIn(min, max)
        fun refresh() {
            bar.frac = (cur() - min).toFloat() / (max - min).toFloat()
            bar.invalidate()
            valTv.text = "${cur()}%"
        }
        fun setV(v: Int) {
            val nv = v.coerceIn(min, max)
            if (nv == cur()) return
            putInt(key, nv)
            refresh()
            after?.invoke()
            if (rebuild) scheduleRebuild()
        }
        refresh()
        val trailing = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            addView(bar, LinearLayout.LayoutParams(dp(150), dp(22)))
            addView(valTv, LinearLayout.LayoutParams(dp(54), -2).apply { marginStart = dp(8) })
        }
        val row = makeRow(title, sub, trailing, valTv) { code ->
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { setV(cur() - step); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { setV(cur() + step); true }
                else -> false
            }
        }
        row.setOnClickListener { if (cur() + step > max) setV(min) else setV(cur() + step) }
    }

    private fun addAction(title: String, sub: String?, value: String? = null, onClick: (View) -> Unit) {
        val valueTv = if (value != null) tv(12.5f, true, accent).apply { text = value; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(260) } else null
        val row = makeRow(title, sub, valueTv, valueTv)
        row.setOnClickListener { onClick(row) }
    }

    private fun addInfo(text: String) {
        settingsContent.addView(tv(11.5f, false, muted).apply {
            this.text = text
            setPadding(dp(8), dp(2), dp(8), dp(8))
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun refreshAllTexts() {
        lastMin = -1
        updateClock()
        applyWeather()
        renderPrayers()
        renderMatches()
    }

    /* ─────────── الأقسام ─────────── */
    private fun buildGeneral() {
        groupTitle("البداية")
        addChoice("الشاشة الافتراضية", "الصفحة التي تفتح عند تشغيل الواجهة", "start_page", listOf("الرئيسية", "المباريات"), 0)
        addToggle("رسالة الترحيب", "إظهار «مساء الخير» فوق الساعة", "show_greeting", true, rebuild = true)
        groupTitle("اختصارات النظام")
        addAction("الواي فاي", "الشبكة والاتصال") { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        addAction("إعدادات النظام", "إعدادات الجهاز الأساسية") { go(Intent(Settings.ACTION_SETTINGS)) }
        addAction("الصورة والشاشة", "العرض والدقة") { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        addAction("الصوت", "مستوى الصوت والمخرجات") { go(Intent(Settings.ACTION_SOUND_SETTINGS)) }
        addAction("شاشة التوقف", "حافظة الشاشة") { go(Intent(Settings.ACTION_DREAM_SETTINGS)) }
    }

    private fun buildAppearance() {
        groupTitle("الخلفية")
        addChoice("ألوان الخلفية", "لون الواجهة العام", "bg_palette", palettes.map { it.first }, 0, rebuild = true)
        addChoice("لون التمييز", "العناوين والمفاتيح والعناصر الملونة", "accent_idx", accents.map { it.first }, 0, rebuild = true)
        addChoice("لون الزجاج", "محايد أو مائل للون التمييز", "glass_tint", listOf("محايد", "مائل للون التمييز"), 0, rebuild = true)
        addAction("صورة خلفية خاصة", "رابط صورة مباشر (HTTPS)", prefs.getString("custom_bg_url", "")?.takeIf { it.isNotEmpty() }?.let { "مفعّلة" }) { showBgUrlDialog() }
        addAction("استعادة الخلفية الافتراضية", "إلغاء الصورة الخاصة") {
            prefs.edit().remove("custom_bg_url").apply()
            applyCustomBackground()
            refreshSection()
        }
        groupTitle("الزجاج والحدود")
        addSlider("نسبة الشفافية", "كلما زادت صارت اللوحات أشفّ", "panel_clear", 0, 100, 10, 30, rebuild = true)
        addChoice("نمط الحدود", "زجاجي: حد متدرج ولمعة • حاد: خط واضح بلا لمعة", "border_style", listOf("زجاجي", "حاد"), 0, rebuild = true)
        addSlider("قوة الحدود", "وضوح خط الحد حول اللوحات والبطاقات", "border_strength", 0, 100, 10, 50, rebuild = true)
        groupTitle("التحديد (التركيز)")
        addChoice("نمط التحديد", "حاد: أبيض صلب • زجاجي: أبيض شفاف بحد لامع", "focus_style", listOf("حاد", "زجاجي"), 0, rebuild = true)
        addSlider("نسبة التحديد الزجاجي", "تؤثر عند اختيار النمط الزجاجي", "focus_glass", 10, 100, 10, 60, rebuild = true)
        addChoice("حجم التكبير عند التحديد", null, "focus_scale", listOf("خفيف", "متوسط", "كبير"), 1)
    }

    private fun buildLanguage() {
        groupTitle("التاريخ والأيام")
        addChoice("لغة التاريخ والأيام", "أسماء الأيام والشهور والترحيب", "date_lang", listOf("العربية", "English"), 0) { refreshAllTexts() }
        addToggle("التاريخ الهجري", "إظهاره تحت التاريخ الميلادي", "show_hijri", true) { refreshAllTexts() }
        groupTitle("الأرقام والساعة")
        addChoice("نوع الأرقام", "تلقائي يترك الأرقام كما هي", "digits", listOf("تلقائي", "هندية ٠١٢", "إنجليزية 012"), 0) { refreshAllTexts() }
        addChoice("نظام الساعة", null, "clock_mode", listOf("24 ساعة", "12 ساعة"), 0) { refreshAllTexts() }
    }

    private fun buildAppsSection() {
        groupTitle("الوصول السريع")
        addAction("آخر التطبيقات استخدام", "قائمة التطبيقات التي فُتحت مؤخراً في النظام") { showRecentsPanel() }
        addAction("جميع التطبيقات (النظام)", "إدارة التطبيقات من إعدادات الجهاز") { go(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)) }
        groupTitle("الرئيسية")
        addChoice("عدد تطبيقات المفضلة الظاهرة", "في صف المفضلة", "fav_cols", listOf("4", "5", "6"), 1, rebuild = true)
        addToggle("إظهار جميع التطبيقات تحت المفضلة", "تنزل تحت المفضلة وتشوفها كاملة", "show_all_apps", true, rebuild = true)
        addChoice("أعمدة جميع التطبيقات", null, "grid_cols", listOf("4", "5", "6"), 1, rebuild = true)
        groupTitle("المفضلة")
        addAction("إعادة ضبط المفضلة", "الترتيب الافتراضي") {
            prefs.edit().remove("fav_apps").apply()
            loadAppsAndDock()
            Toast.makeText(this, "تمت إعادة الضبط ✓", Toast.LENGTH_SHORT).show()
        }
        addInfo("لتحريك تطبيق في المفضلة: اضغط مطولاً عليه ثم اختر «تحريك المكان».")
    }

    private fun colorKeyRow(title: String, key: String) {
        val cur = prefs.getString(key, "") ?: ""
        addAction(title, "يفتح التطبيق المحدد من الريموت", if (cur.isEmpty()) "غير محدد" else appLabel(cur)) { row ->
            val items = mutableListOf(DropItem("بدون تعيين", null, cur.isEmpty()) {
                prefs.edit().remove(key).apply()
                refreshSection()
            })
            for (a in allApps) {
                val p = a.activityInfo.packageName
                items.add(DropItem(a.loadLabel(packageManager).toString(), null, p == cur) {
                    prefs.edit().putString(key, p).apply()
                    refreshSection()
                })
            }
            showDropdown(row, title, items, 320)
        }
    }

    private fun buildRemote() {
        groupTitle("الأزرار")
        addToggle("حظر أزرار TV / Guide / DVR", "تمنع فتح واجهات النظام الأخرى من الريموت", "block_keys", true)
        addToggle("زر القائمة (Menu)", "يفتح إعدادات WM Launcher", "menu_key", true)
        groupTitle("الأزرار الملونة")
        colorKeyRow("الزر الأحمر", "key_red")
        colorKeyRow("الزر الأخضر", "key_green")
        colorKeyRow("الزر الأصفر", "key_yellow")
        colorKeyRow("الزر الأزرق", "key_blue")
        addInfo("الأزرار الملونة تعمل فقط في الريموتات التي فيها أزرار ملونة.")
    }

    private fun buildWeatherSection() {
        groupTitle("الموقع")
        addAction("المدينة", "تُستخدم للطقس والصلاة", city().name) { openCityPicker(it) }
        addAction("بحث يدوي عن مدينة", "أي مدينة في العالم من الشبكة") { manualCitySearch(it) }
        groupTitle("العرض")
        addToggle("إظهار الطقس في الرئيسية", null, "show_weather", true, rebuild = true)
        addChoice("وحدة الحرارة", null, "temp_unit", listOf("مئوية °C", "فهرنهايت °F"), 0) { applyWeather() }
        addToggle("التفاصيل (الرطوبة والرياح)", "تحت درجة الحرارة", "weather_details", true) { applyWeather() }
        groupTitle("التحديث")
        addChoice("تحديث تلقائي كل", null, "refresh_idx", listOf("15 دقيقة", "30 دقيقة", "ساعة"), 0) { refreshFeeds() }
        addAction("تحديث الآن", null) {
            refreshFeeds()
            Toast.makeText(this, "جاري التحديث…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildMatchesSection() {
        groupTitle("الرئيسية")
        addToggle("إظهار مباريات اليوم", null, "show_matches", true, rebuild = true)
        addChoice("عدد البطاقات", null, "match_count", listOf("5", "8", "12", "الكل"), 3) { renderMatches() }
        addToggle("الشريط المتحرك للنتائج", null, "show_ticker", true, rebuild = true)
        groupTitle("التصفية")
        addToggle("إخفاء المباريات المنتهية", null, "hide_finished", false) { renderMatches() }
        groupTitle("التحديث")
        addAction("تحديث المباريات الآن", null) {
            fetchMatches()
            Toast.makeText(this, "جاري التحديث…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildPrayerSection() {
        groupTitle("العرض")
        addToggle("إظهار أوقات الصلاة", "في اللوحة الرئيسية", "show_prayers", true, rebuild = true)
        addToggle("العد التنازلي للأذان القادم", null, "prayer_countdown", true, rebuild = true)
        groupTitle("الموقع")
        addAction("المدينة", "تُحسب الأوقات حسب المدينة", city().name) { openCityPicker(it) }
        addInfo("نظام الساعة (12/24) من قسم «اللغة والأرقام».")
    }

    private fun versionInfo(): Pair<String, Int> = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
        (pi.versionName ?: "1.0") to code
    } catch (e: Throwable) { "1.0" to 1 }

    private fun buildAbout() {
        val (vn, vc) = versionInfo()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(190, 22, Color.argb(50, 255, 255, 255), 1)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        card.addView(LogoView(this), LinearLayout.LayoutParams(dp(72), dp(72)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, 0, 0) }
        col.addView(tv(22f, true).apply { text = "WM Launcher"; layoutDirection = View.LAYOUT_DIRECTION_LTR })
        col.addView(tv(12f, false, dim).apply { text = nd("الإصدار $vn (رقم $vc)") })
        col.addView(tv(11f, false, muted).apply { text = "واجهة وليد • مصممة للتلفزيونات والبروجكترات"; setPadding(0, dp(3), 0, 0) })
        card.addView(col)
        settingsContent.addView(card, rowLp())

        groupTitle("التحديث")
        val url = prefs.getString("update_url", "") ?: ""
        addAction("رابط التحديث", "ملف JSON أو رابط APK مباشر", if (url.isEmpty()) "غير محدد" else url.substringAfter("://").take(34)) {
            askText("رابط التحديث", "https://example.com/update.json", url) { v ->
                prefs.edit().putString("update_url", v.trim()).apply()
                updateInfo = null
                updateMsg = ""
                refreshSection()
            }
        }
        addAction("التحقق من التحديث", if (updateMsg.isEmpty()) "يفحص إذا في نسخة أحدث" else updateMsg) { checkUpdate() }
        val info = updateInfo
        if (info != null && (info.direct || info.code > vc)) {
            addAction("تحديث الآن", if (downloading) "جاري التنزيل…" else (if (info.notes.isNotEmpty()) info.notes else "تنزيل وتثبيت ${info.name}")) {
                if (!downloading) downloadAndInstall(info)
            }
        }
        groupTitle("إعادة الضبط")
        addAction("إعادة ضبط كل الإعدادات", "يرجّع المظهر والخيارات (يبقي المفضلة والمدينة والرابط)") {
            val keep = setOf("fav_apps", "city_idx", "city_custom", "update_url", "custom_bg_url")
            val ed = prefs.edit()
            for (k in prefs.all.keys) if (k !in keep) ed.remove(k)
            ed.apply()
            scheduleRebuild()
        }
    }

    /* ─────────── القوائم المنسدلة (تحت الزر) ─────────── */
    private class DropItem(val label: String, val sub: String? = null, val selected: Boolean = false, val onClick: () -> Unit)

    private fun dropRow(item: DropItem, d: Dialog): View {
        val t = tv(14.5f, true).apply { text = (if (item.selected) "✓  " else "") + item.label }
        val s = tv(10.5f, false, dim).apply { text = item.sub ?: ""; visibility = if (item.sub == null) View.GONE else View.VISIBLE }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(150, 16, Color.argb(35, 255, 255, 255), 1)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            isFocusable = true
            isClickable = true
            addView(t)
            addView(s)
            setOnClickListener { d.dismiss(); item.onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(16) else slateGlass(150, 16, Color.argb(35, 255, 255, 255), 1)
                t.setTextColor(if (f) focusFg() else Color.WHITE)
                s.setTextColor(if (f) focusFg() else dim)
            }
        }
    }

    private fun showDropdown(anchor: View?, title: String?, items: List<DropItem>, widthDp: Int = 300) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        var x = (sw - dp(widthDp)) / 2
        var y = dp(80)
        var maxH = sh - dp(160)
        if (anchor != null) {
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            x = (loc[0] + anchor.width / 2 - dp(widthDp) / 2).coerceIn(dp(16), maxOf(dp(16), sw - dp(widthDp) - dp(16)))
            y = loc[1] + anchor.height + dp(8)
            maxH = sh - y - dp(24)
            if (maxH < dp(220)) {
                y = dp(24)
                maxH = sh - dp(48)
            }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = slateGlass(245, 24, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(12), dp(12), dp(12), dp(8))
        }
        if (title != null) panel.addView(tv(14f, true, accent).apply { text = title; setPadding(dp(8), dp(2), dp(8), dp(8)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var sel: View? = null
        for (it in items) {
            val v = dropRow(it, d)
            list.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            if (it.selected && sel == null) sel = v
        }
        val scroll = MaxHeightScroll(this, maxH - dp(70)).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        d.setContentView(panel, ViewGroup.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT))
        val win = d.window
        if (win != null) {
            win.setLayout(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.TOP or Gravity.LEFT)
            val lp = win.attributes
            lp.x = x
            lp.y = y
            lp.dimAmount = 0.4f
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.attributes = lp
        }
        d.show()
        (sel ?: list.getChildAt(0))?.post { (sel ?: list.getChildAt(0))?.requestFocus() }
    }

    private fun openSettingsMenu(anchor: View) {
        showDropdown(anchor, null, listOf(
            DropItem("⚙️  إعدادات WM Launcher", "المظهر واللغة والريموت والمزيد") { showPage(2) },
            DropItem("🛠️  إعدادات النظام", null) { go(Intent(Settings.ACTION_SETTINGS)) },
            DropItem("🖥️  الصورة والشاشة", null) { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            DropItem("🔊  الصوت", null) { go(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            DropItem("📱  التطبيقات", "آخر التطبيقات استخداماً") { showRecentsPanel() },
            DropItem("🔄  التحديث وحول التطبيق", null) { showSection(8); showPage(2) }
        ), 330)
    }

    private fun setCity(c: WmCity, idx: Int) {
        putInt("city_idx", idx)
        if (idx == -1) prefs.edit().putString("city_custom", WmCities.encode(c)).apply()
        else prefs.edit().remove("city_custom").apply()
        cityHeaderTv.text = "الطقس • ${c.name}"
        cityPillTv.text = c.name
        lastTemp = null
        lastFeels = null
        lastStatus = null
        prayersData = emptyList()
        applyWeather()
        refreshFeeds()
        if (currentPage == 2) refreshSection()
    }

    private fun isCurrent(c: WmCity): Boolean {
        val cur = city()
        return cur.name == c.name && Math.abs(cur.lat - c.lat) < 0.01 && Math.abs(cur.lon - c.lon) < 0.01
    }

    private fun showCityList(anchor: View?, title: String, list: List<WmCity>, showCountry: Boolean) {
        showDropdown(anchor, title, list.map { c ->
            DropItem(c.name, if (showCountry) WmCities.countryName(c.cc) else null, isCurrent(c)) {
                setCity(c, WmCities.all.indexOf(c))
            }
        }, 320)
    }

    private fun openCityPicker(anchor: View?) {
        showDropdown(anchor, "اختر مدينتك (الطقس والصلاة)", listOf(
            DropItem("🇸🇦  مدن المملكة", "${WmCities.saudi.size} مدينة") { showCityList(anchor, "مدن المملكة", WmCities.saudi, false) },
            DropItem("🌙  مدن عربية", "${WmCities.arab.size} مدينة") { showCityList(anchor, "مدن عربية", WmCities.arab, true) },
            DropItem("🌍  مدن عالمية", "${WmCities.world.size} مدينة") { showCityList(anchor, "مدن عالمية", WmCities.world, true) },
            DropItem("🔎  بحث يدوي (من الشبكة)", "اكتب اسم أي مدينة في العالم") { manualCitySearch(anchor) }
        ), 330)
    }

    private fun manualCitySearch(anchor: View?) {
        askText("بحث عن مدينة", "اكتب اسم المدينة (عربي أو إنجليزي)", "") { q ->
            if (q.trim().length < 2) return@askText
            Toast.makeText(this, "جاري البحث…", Toast.LENGTH_SHORT).show()
            thread {
                val hits = try { WmNet.search(q) } catch (e: Throwable) { null }
                ui.post {
                    if (hits == null) Toast.makeText(this, "تعذر البحث، تأكد من الاتصال", Toast.LENGTH_SHORT).show()
                    else if (hits.isEmpty()) Toast.makeText(this, "ما لقيت مدينة بهذا الاسم", Toast.LENGTH_SHORT).show()
                    else showDropdown(anchor, "نتائج البحث", hits.map { h ->
                        DropItem(h.city.name, h.sub, isCurrent(h.city)) { setCity(h.city, -1) }
                    }, 340)
                }
            }
        }
    }

    /* ─────────── آخر التطبيقات استخداماً (النظام) ─────────── */
    private fun hasUsageAccess(): Boolean = try {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Throwable) { false }

    private fun recentPackages(): List<Pair<String, Long>> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val ev = usm.queryEvents(end - 3L * 24 * 3600 * 1000, end)
        val e = UsageEvents.Event()
        val last = HashMap<String, Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last[e.packageName] = e.timeStamp
        }
        val pm = packageManager
        return last.entries
            .filter { it.key != packageName && (pm.getLeanbackLaunchIntentForPackage(it.key) != null || pm.getLaunchIntentForPackage(it.key) != null) }
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key to it.value }
    }

    private fun agoText(t: Long): String {
        val mins = ((System.currentTimeMillis() - t) / 60000).toInt()
        return nd(when {
            mins < 1 -> "الآن"
            mins < 60 -> "قبل $mins دقيقة"
            mins < 1440 -> "قبل ${mins / 60} ساعة"
            else -> "قبل ${mins / 1440} يوم"
        })
    }

    private fun panelRow(d: Dialog, title: String, sub: String?, icon: Drawable?, onClick: () -> Unit): View {
        val t = tv(14f, true).apply { text = title }
        val s = tv(10.5f, false, dim).apply { text = sub ?: ""; visibility = if (sub == null) View.GONE else View.VISIBLE }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(130, 16, Color.argb(30, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            isClickable = true
            if (icon != null) addView(ImageView(this@MainActivity).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) })
            val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            c.addView(t)
            c.addView(s)
            addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { d.dismiss(); onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(16) else slateGlass(130, 16, Color.argb(30, 255, 255, 255), 1)
                t.setTextColor(if (f) focusFg() else Color.WHITE)
                s.setTextColor(if (f) focusFg() else dim)
            }
        }
    }

    private fun showRecentsPanel() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = slateGlass(250, 26, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(18), dp(18), dp(18), dp(14))
        }
        panel.addView(tv(20f, true).apply { text = "التطبيقات" })
        panel.addView(tv(12f, true, accent).apply { text = "التطبيقات التي تم فتحها مؤخراً"; setPadding(0, dp(4), 0, dp(10)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lp = { LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) } }
        if (!hasUsageAccess()) {
            list.addView(tv(12f, false, dim).apply {
                text = "لعرض آخر التطبيقات لازم تفعّل إذن «الوصول إلى بيانات الاستخدام» للواجهة."
                setLineSpacing(0f, 1.15f)
                setPadding(dp(4), 0, dp(4), dp(8))
            })
        } else {
            val recents = try { recentPackages() } catch (e: Throwable) { emptyList() }
            if (recents.isEmpty()) {
                list.addView(tv(12f, false, dim).apply { text = "لا توجد تطبيقات حديثة بعد."; setPadding(dp(4), 0, dp(4), dp(8)) })
            }
            for ((pkg, t) in recents) {
                val ic = try { packageManager.getApplicationIcon(pkg) } catch (e: Throwable) { null }
                list.addView(panelRow(d, appLabel(pkg), agoText(t), ic) { launchPkg(pkg) }, lp())
            }
        }
        panel.addView(MaxHeightScroll(this, resources.displayMetrics.heightPixels - dp(260)).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
        }, LinearLayout.LayoutParams(-1, -2))
        panel.addView(panelRow(d, "الاطلاع على كل التطبيقات", null, null) { go(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)) }, lp())
        panel.addView(panelRow(d, "الأذونات", "الوصول إلى بيانات الاستخدام", null) { go(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, lp())
        d.setContentView(panel, ViewGroup.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT))
        val win = d.window
        if (win != null) {
            win.setLayout(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.TOP or Gravity.LEFT)
            val a = win.attributes
            a.x = dp(24)
            a.y = dp(24)
            a.dimAmount = 0.45f
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.attributes = a
        }
        d.show()
        list.getChildAt(0)?.let { v -> if (v.isFocusable) v.post { v.requestFocus() } }
    }

    /* ─────────── نصوص وخلفية ─────────── */
    private fun askText(title: String, hint: String, current: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            this.hint = hint
            setText(current)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("تطبيق") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showBgUrlDialog() {
        askText("صورة خلفية الواجهة", "أدخل رابط صورة مباشر (HTTPS)", prefs.getString("custom_bg_url", "") ?: "") { url ->
            val u = url.trim()
            if (u.isNotEmpty()) {
                prefs.edit().putString("custom_bg_url", u).apply()
                applyCustomBackground()
                refreshSection()
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
            background = slateGlass(245, 28, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(28), dp(22), dp(28), dp(22))
        }
        return d to s
    }

    private fun optionRow(label: String, onClick: () -> Unit): View = tv(15f, true, Color.WHITE).apply {
        text = label
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = slateGlass(160, 16, Color.argb(35, 255, 255, 255), 1)
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            v.background = if (f) focusBg(16) else slateGlass(160, 16, Color.argb(35, 255, 255, 255), 1)
            (v as TextView).setTextColor(if (f) focusFg() else Color.WHITE)
        }
    }

    /* ─────────── التحديث من نفس الواجهة ─────────── */
    private fun registerInstallReceiver() {
        installReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirm != null) {
                            try { startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { }
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        downloading = false
                        Toast.makeText(this@MainActivity, "تم التحديث ✓", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        downloading = false
                        val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                        Toast.makeText(this@MainActivity, "تعذر التثبيت: $msg", Toast.LENGTH_LONG).show()
                        updateSubTv?.text = "فشل التثبيت — تأكد أن التوقيع نفسه ورقم الإصدار أعلى"
                    }
                }
            }
        }
        val f = IntentFilter(installAction)
        val r = installReceiver!!
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(r, f)
    }

    private fun checkUpdate() {
        val url = (prefs.getString("update_url", "") ?: "").trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "حدّد رابط التحديث أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        updateMsg = "جاري الفحص…"
        refreshSection()
        thread {
            var info: UpdateInfo? = null
            var msg = ""
            try {
                if (url.substringBefore('?').lowercase(Locale.ROOT).endsWith(".apk")) {
                    info = UpdateInfo(Int.MAX_VALUE, "", url, "رابط مباشر للنسخة الأحدث", true)
                    msg = "رابط APK مباشر — جاهز للتنزيل"
                } else {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    val j = JSONObject(txt)
                    val code = j.optInt("versionCode", 0)
                    val name = j.optString("versionName", "")
                    val apk = j.optString("apkUrl", "")
                    val notes = j.optString("notes", "")
                    val cur = versionInfo()
                    if (apk.isNotEmpty() && code > cur.second) {
                        info = UpdateInfo(code, name, apk, notes, false)
                        msg = nd("يوجد تحديث: $name (رقم $code)")
                    } else {
                        msg = "أنت على آخر إصدار ✓"
                    }
                }
            } catch (e: Throwable) {
                msg = "تعذر الفحص: تأكد من الرابط والاتصال"
            }
            ui.post {
                updateInfo = info
                updateMsg = msg
                if (currentPage == 2 && currentSection == 8) refreshSection()
            }
        }
    }

    private fun downloadAndInstall(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "فعّل «التثبيت من مصادر غير معروفة» للواجهة ثم ارجع واضغط تحديث", Toast.LENGTH_LONG).show()
            go(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        downloading = true
        updateSubTv?.text = "جاري التنزيل… 0%"
        thread {
            try {
                val conn = URL(info.apk).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                val total = conn.contentLength
                val file = File(cacheDir, "wm_update.apk")
                if (file.exists()) file.delete()
                var done = 0L
                var lastPct = -1
                conn.inputStream.use { ins ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    ui.post { updateSubTv?.text = nd("جاري التنزيل… $pct%") }
                                }
                            }
                        }
                    }
                }
                ui.post { updateSubTv?.text = "جاري التثبيت… وافق على نافذة النظام" }
                installApk(file)
            } catch (e: Throwable) {
                downloading = false
                ui.post {
                    updateSubTv?.text = "فشل التنزيل — تأكد من الرابط والاتصال"
                    Toast.makeText(this, "فشل التنزيل", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun installApk(file: File) {
        val pi = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = pi.createSession(params)
        val session = pi.openSession(id)
        file.inputStream().use { ins ->
            session.openWrite("wm_update", 0, file.length()).use { out ->
                ins.copyTo(out)
                session.fsync(out)
            }
        }
        val intent = Intent(installAction).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(this, id, intent, flags)
        session.commit(pending.intentSender)
        session.close()
    }

    /* ─────────── المباريات ─────────── */
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
        var list = all?.sortedWith(compareByDescending<Match> { rank(it) }.thenBy { it.time })
        if (list != null && pBool("hide_finished", false)) list = list.filter { it.note != "انتهت" }
        tickerTv.text = if (list == null || list.isEmpty()) "" else nd("⚽  " + list.take(5).joinToString("   •   ") { "${it.home} × ${it.away} (${it.center})" })
        if (list == null || list.isEmpty()) {
            val msg = if (all == null) "تعذر تحميل المباريات، تأكد من الاتصال" else "لا توجد مباريات مسجلة اليوم"
            homeMatchRow.addView(tv(14f, false, dim).apply { text = msg; setPadding(dp(8), dp(14), dp(8), dp(14)) })
            matchesGrid.addView(tv(15f, false, dim).apply { text = msg; setPadding(dp(8), dp(14), dp(8), dp(14)) })
            return
        }
        val limit = when (pInt("match_count", 3)) { 0 -> 5; 1 -> 8; 2 -> 12; else -> Int.MAX_VALUE }
        for (m in list.take(limit)) {
            homeMatchRow.addView(miniMatch(m), LinearLayout.LayoutParams(dp(176), dp(132)).apply { marginEnd = dp(10) })
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

    private fun miniClub(name: String, url: String): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        c.addView(miniLogo(name, url), LinearLayout.LayoutParams(dp(30), dp(30)))
        c.addView(tv(10f, true).apply {
            text = name
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        })
        return c
    }

    private fun miniMatch(m: Match): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(165, 20, Color.argb(60, 255, 255, 255), 1)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isFocusable = true
            isClickable = true
            roundOutline(20)
            setOnClickListener { showPage(1) }
            setOnFocusChangeListener { v, f ->
                v.background = glass(if (f) 240 else 165, 20, if (f) focusRing() else Color.argb(60, 255, 255, 255), if (f) 2 else 1)
                v.translationZ = if (f) dp(10).toFloat() else 0f
                v.elevation = if (f) 14f else 2f
                v.animate()
                    .scaleX(if (f) 1.06f else 1f)
                    .scaleY(if (f) 1.06f else 1f)
                    .translationY(if (f) -dp(3).toFloat() else 0f)
                    .setDuration(140)
                    .start()
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(9.5f, true, accent).apply { text = m.league; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        val st = tv(9f, true, if (m.live) gold else dim).apply { text = nd(statusText(m)); maxLines = 1 }
        statusViews.add(st to m)
        head.addView(st)
        card.addView(head, LinearLayout.LayoutParams(-1, -2))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(miniClub(m.home, m.homeLogo), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv(14f, true, gold).apply { text = nd(m.center); gravity = Gravity.CENTER; maxLines = 1; setPadding(dp(4), 0, dp(4), 0) })
        row.addView(miniClub(m.away, m.awayLogo), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        return card
    }

    private fun matchCard(m: Match): View {
        val card = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = slateGlass(210, 22, Color.argb(60, 255, 255, 255), 1)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isFocusable = true
            isClickable = true
            roundOutline(22)
            setOnFocusChangeListener { v, f ->
                v.background = slateGlass(if (f) 245 else 210, 22, if (f) focusRing() else Color.argb(60, 255, 255, 255), if (f) 3 else 1)
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
        val st = tv(10.5f, true, if (m.live) gold else dim).apply { text = nd(statusText(m)) }
        statusViews.add(st to m)
        head.addView(st)
        card.addView(head, LinearLayout.LayoutParams(-1, -2))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        row.addView(clubColumn(m.home, m.homeLogo), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv(18f, true, gold).apply { text = nd(m.center); gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) })
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

    private f
