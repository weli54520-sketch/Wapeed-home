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
        val items = ArrayList<DropItem>()
        for (c in list) {
            val sub: String? = if (showCountry) WmCities.countryName(c.cc) else null
            val idx = WmCities.all.indexOf(c)
            items.add(DropItem(c.name, sub, isCurrent(c)) { setCity(c, idx) })
        }
        showDropdown(anchor, title, items, 320)
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
        askText("بحث عن مدينة", "اكتب اسم المدينة (عربي أو إنجليزي)", "") { q: String ->
            if (q.trim().length >= 2) {
                Toast.makeText(this@MainActivity, "جاري البحث…", Toast.LENGTH_SHORT).show()
                thread {
                    var found: List<GeoHit>? = null
                    try { found = WmNet.search(q) } catch (e: Throwable) { found = null }
                    val result: List<GeoHit>? = found
                    ui.post {
                        showSearchResults(anchor, result)
                    }
                }
            }
        }
    }

    private fun showSearchResults(anchor: View?, result: List<GeoHit>?) {
        if (result == null) {
            Toast.makeText(this, "تعذر البحث، تأكد من الاتصال", Toast.LENGTH_SHORT).show()
            return
        }
        if (result.isEmpty()) {
            Toast.makeText(this, "ما لقيت مدينة بهذا الاسم", Toast.LENGTH_SHORT).show()
            return
        }
        val items = ArrayList<DropItem>()
        for (hit in result) {
            val hc: WmCity = hit.city
            items.add(DropItem(hc.name, hit.sub, isCurrent(hc)) { setCity(hc, -1) })
        }
        showDropdown(anchor, "نتائج البحث", items, 340)
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

    private fun makeCrest(name: String): Drawable {
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

    private fun fetchLogo(url: String, iv: ImageView) {
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

    private fun miniLogo(name: String, url: String): ImageView {
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setImageDrawable(makeCrest(name))
        if (url.isNotEmpty()) fetchLogo(url, iv)
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
            background = slateGlass(210, 22, Color.argb(60, 25            setPadding(dp(16), dp(12), dp(16), dp(12))
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

    /* ─────────── المفضلة وجميع التطبيقات ─────────── */
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
        if (!::favRow.isInitialized) return
        val pm = packageManager
        allApps = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .toMutableList()

        val favPkgs = if (reorderingPkg != null) reorderList else getSavedFavs()
        val sw = resources.displayMetrics.widthPixels
        val n = favCols()

        favRow.removeAllViews()
        val favList = favPkgs.mapNotNull { pkg -> allApps.find { it.activityInfo.packageName == pkg } }
        val fw = (sw - dp(68) - dp(36) - dp(32) - n * dp(16)) / n
        var viewToFocus: View? = null

        for (a in favList) {
            val cell = createCell(a, fw, isDock = true)
            if (a.activityInfo.packageName == focusPkg) {
                viewToFocus = (cell as? ViewGroup)?.getChildAt(0)
            }
            favRow.addView(cell, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })
        }

        allGrid.removeAllViews()
        if (pBool("show_all_apps", true)) {
            val cols = gridCols()
            val gw = (sw - dp(68) - dp(28) - cols * dp(20)) / cols
            for (a in allApps) {
                allGrid.addView(createCell(a, gw, isDock = false), GridLayout.LayoutParams().apply {
                    width = -2
                    height = -2
                    setMargins(dp(10), dp(10), dp(10), dp(10))
                })
            }
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
        if (n < 10) 0xFF141925.toInt() else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    } catch (e: Throwable) { 0xFF141925.toInt() }

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
                if (isMovingThis) accent else Color.argb(45, 255, 255, 255),
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
                        f -> focusRing()
                        else -> Color.argb(45, 255, 255, 255)
                    },
                    if (f || movingNow) 3 else 1
                )
                v.elevation = if (f || movingNow) 14f else 3f
                cell.translationZ = if (f || movingNow) dp(12).toFloat() else 0f
                titleTv.setTextColor(
                    when {
                        movingNow -> gold
                        f -> Color.WHITE
                        else -> dim
                    }
                )
                titleTv.text = if (movingNow) "◀  $label  ▶" else label

                val base = focusScale()
                val targetScale = when {
                    movingNow -> base + 0.03f
                    f -> base
                    else -> 1f
                }
                val targetY = when {
                    movingNow -> -dp(6).toFloat()
                    f -> -dp(3).toFloat()
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

5
    /* ─────────── الساعة والطقس والصلاة ─────────── */
    private fun updateClock() {
        tick()
        ui.removeCallbacks(clockTask)
        ui.postDelayed(clockTask, 10000)
    }

    private fun tick() {
        try {
            val now = LocalTime.now()
            val loc = dateLoc()
            val en = pInt("date_lang", 0) == 1
            greetingTv.text = if (en) (if (now.hour in 4..11) "GOOD MORNING" else "GOOD EVENING")
            else (if (now.hour in 4..11) "صباح الخير" else "مساء الخير")
            clockTv.text = nd(
                if (pInt("clock_mode", 0) == 1) now.format(DateTimeFormatter.ofPattern("h:mm a", loc))
                else now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
            )
            val day = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", loc))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", loc))
            val h = if (pBool("show_hijri", true)) {
                try { HijrahDate.now().format(DateTimeFormatter.ofPattern(if (en) "d MMMM yyyy 'AH'" else "d MMMM yyyy هـ", loc)) } catch (e: Throwable) { "" }
            } else ""
            dateLineTv.text = nd(if (h.isNotEmpty()) "$day $m\n$h" else "$day $m")

            if (now.minute != lastMin) {
                lastMin = now.minute
                renderPrayers()
                for ((t, mt) in statusViews) t.text = nd(statusText(mt))
            }
        } catch (e: Throwable) { }
    }

    private fun refreshFeeds() {
        val c = city()
        ui.removeCallbacks(feedsTask)
        val mins = listOf(15, 30, 60)[pInt("refresh_idx", 0).coerceIn(0, 2)]
        ui.postDelayed(feedsTask, mins * 60 * 1000L)
        thread {
            try {
                val w = WmNet.weather(c)
                ui.post {
                    lastTemp = w.temp
                    lastFeels = w.feels
                    lastHumidity = w.humidity
                    lastWind = w.wind
                    lastStatus = w.status.toString()
                    applyWeather()
                }
            } catch (e: Throwable) { }
            try {
                val p = WmNet.prayers(c)
                ui.post { prayersData = p; renderPrayers() }
            } catch (e: Throwable) { }
        }
    }

    private fun tempText(raw: Any?): String {
        val d = raw?.toString()?.toDoubleOrNull() ?: return raw?.toString() ?: "--"
        val v = if (pInt("temp_unit", 0) == 1) d * 9.0 / 5.0 + 32.0 else d
        return Math.round(v).toString()
    }

    private fun applyWeather() {
        if (!::tempTv.isInitialized) return
        tempTv.text = if (lastTemp == null) "--°" else nd("${tempText(lastTemp)}°")
        weatherStatusTv.text = lastStatus ?: "جاري التحديث..."
        weatherSubTv.text = if (!pBool("weather_details", true) || lastFeels == null) ""
        else nd("المحسوسة ${tempText(lastFeels)}° • الرطوبة $lastHumidity%\nالرياح $lastWind كم/س")
    }

    private fun renderPrayers() {
        if (!::prayerLayout.isInitialized || prayersData.isEmpty()) return
        val now = ZonedDateTime.now(city().zone()).toLocalTime()
        var next = prayersData.indexOfFirst { it.time.isAfter(now) }
        val wrap = next == -1
        if (wrap) next = 0
        val f = DateTimeFormatter.ofPattern("h:mm a", dateLoc())
        prayerLayout.removeAllViews()
        for ((i, p) in prayersData.withIndex()) {
            val on = i == next
            prayerLayout.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(1), dp(6), dp(1))
                if (on) background = solidCard(Color.argb(50, Color.red(accent), Color.green(accent), Color.blue(accent)), 8, accent, 1)
                addView(tv(11f, on, if (on) accent else dim).apply { text = p.name }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(tv(11f, on, if (on) Color.WHITE else dim).apply { text = nd(p.time.format(f)) })
            })
        }
        val t = prayersData[next].time
        val mins = if (wrap) Duration.between(now, LocalTime.MAX).toMinutes() + Duration.between(LocalTime.MIN, t).toMinutes()
        else Duration.between(now, t).toMinutes()
        val hh = mins / 60
        val mm = mins % 60
        prayerTimerTv.text = nd("الأذان القادم بعد ${if (hh > 0) "$hh س و " else ""}$mm د")
    }
}

/* ───────────────────────── المدن والطقس والصلاة ───────────────────────── */
data class WmCity(val name: String, val cc: String, val lat: Double, val lon: Double, val tz: String) {
    fun zone(): ZoneId = try { ZoneId.of(tz) } catch (e: Throwable) { ZoneId.systemDefault() }
}

data class WmPrayer(val name: String, val time: LocalTime)
data class WmWeather(val temp: Double, val feels: Double, val humidity: Int, val wind: Int, val status: String)
data class GeoHit(val city: WmCity, val sub: String)

object WmCities {
    private val tzByCc = mapOf(
        "SA" to "Asia/Riyadh", "AE" to "Asia/Dubai", "KW" to "Asia/Kuwait", "QA" to "Asia/Qatar",
        "BH" to "Asia/Bahrain", "OM" to "Asia/Muscat", "YE" to "Asia/Aden", "IQ" to "Asia/Baghdad",
        "SY" to "Asia/Damascus", "LB" to "Asia/Beirut", "JO" to "Asia/Amman", "PS" to "Asia/Hebron",
        "EG" to "Africa/Cairo", "LY" to "Africa/Tripoli", "TN" to "Africa/Tunis", "DZ" to "Africa/Algiers",
        "MA" to "Africa/Casablanca", "MR" to "Africa/Nouakchott", "SD" to "Africa/Khartoum",
        "SO" to "Africa/Mogadishu", "DJ" to "Africa/Djibouti", "KM" to "Indian/Comoro",
        "GB" to "Europe/London", "FR" to "Europe/Paris", "DE" to "Europe/Berlin", "ES" to "Europe/Madrid",
        "IT" to "Europe/Rome", "TR" to "Europe/Istanbul", "RU" to "Europe/Moscow", "MX" to "America/Mexico_City",
        "BR" to "America/Sao_Paulo", "AR" to "America/Argentina/Buenos_Aires", "JP" to "Asia/Tokyo",
        "KR" to "Asia/Seoul", "CN" to "Asia/Shanghai", "HK" to "Asia/Hong_Kong", "SG" to "Asia/Singapore",
        "TH" to "Asia/Bangkok", "MY" to "Asia/Kuala_Lumpur", "ID" to "Asia/Jakarta", "PH" to "Asia/Manila",
        "IN" to "Asia/Kolkata", "PK" to "Asia/Karachi", "BD" to "Asia/Dhaka", "IR" to "Asia/Tehran",
        "AF" to "Asia/Kabul", "UZ" to "Asia/Tashkent", "NZ" to "Pacific/Auckland", "ZA" to "Africa/Johannesburg",
        "NG" to "Africa/Lagos", "KE" to "Africa/Nairobi", "ET" to "Africa/Addis_Ababa", "NL" to "Europe/Amsterdam",
        "BE" to "Europe/Brussels", "AT" to "Europe/Vienna", "CH" to "Europe/Zurich", "SE" to "Europe/Stockholm",
        "NO" to "Europe/Oslo", "DK" to "Europe/Copenhagen", "GR" to "Europe/Athens", "PT" to "Europe/Lisbon",
        "IE" to "Europe/Dublin", "PL" to "Europe/Warsaw", "UA" to "Europe/Kyiv", "BA" to "Europe/Sarajevo",
        "LK" to "Asia/Colombo", "NP" to "Asia/Kathmandu", "AZ" to "Asia/Baku"
    )

    private val countryAr = mapOf(
        "SA" to "السعودية", "AE" to "الإمارات", "KW" to "الكويت", "QA" to "قطر", "BH" to "البحرين",
        "OM" to "عُمان", "YE" to "اليمن", "IQ" to "العراق", "SY" to "سوريا", "LB" to "لبنان",
        "JO" to "الأردن", "PS" to "فلسطين", "EG" to "مصر", "LY" to "ليبيا", "TN" to "تونس",
        "DZ" to "الجزائر", "MA" to "المغرب", "MR" to "موريتانيا", "SD" to "السودان", "SO" to "الصومال",
        "DJ" to "جيبوتي", "KM" to "جزر القمر", "GB" to "بريطانيا", "FR" to "فرنسا", "DE" to "ألمانيا",
        "ES" to "إسبانيا", "IT" to "إيطاليا", "TR" to "تركيا", "RU" to "روسيا", "US" to "أمريكا",
        "CA" to "كندا", "MX" to "المكسيك", "BR" to "البرازيل", "AR" to "الأرجنتين", "JP" to "اليابان",
        "KR" to "كوريا الجنوبية", "CN" to "الصين", "HK" to "هونغ كونغ", "SG" to "سنغافورة",
        "TH" to "تايلاند", "MY" to "ماليزيا", "ID" to "إندونيسيا", "PH" to "الفلبين", "IN" to "الهند",
        "PK" to "باكستان", "BD" to "بنغلاديش", "IR" to "إيران", "AF" to "أفغانستان", "UZ" to "أوزبكستان",
        "AU" to "أستراليا", "NZ" to "نيوزيلندا", "ZA" to "جنوب أفريقيا", "NG" to "نيجيريا", "KE" to "كينيا",
        "ET" to "إثيوبيا", "NL" to "هولندا", "BE" to "بلجيكا", "AT" to "النمسا", "CH" to "سويسرا",
        "SE" to "السويد", "NO" to "النرويج", "DK" to "الدنمارك", "GR" to "اليونان", "PT" to "البرتغال",
        "IE" to "أيرلندا", "PL" to "بولندا", "UA" to "أوكرانيا", "BA" to "البوسنة", "LK" to "سريلانكا",
        "NP" to "نيبال", "AZ" to "أذربيجان"
    )

    // طريقة حساب الصلاة (Aladhan) حسب الدولة
    private val methodByCc = mapOf(
        "SA" to 4, "AE" to 16, "KW" to 9, "QA" to 10, "BH" to 8, "OM" to 8, "YE" to 4, "EG" to 5,
        "SD" to 5, "LY" to 5, "SY" to 5, "LB" to 5, "PS" to 5, "IQ" to 3, "JO" to 23, "TR" to 13,
        "MA" to 21, "TN" to 18, "DZ" to 19, "PK" to 1, "IN" to 1, "BD" to 1, "AF" to 1, "ID" to 20,
        "MY" to 17, "SG" to 11, "FR" to 12, "RU" to 14, "IR" to 7, "US" to 2, "CA" to 2
    )

    fun method(cc: String): Int = methodByCc[cc.uppercase()] ?: 3
    fun countryName(cc: String): String = countryAr[cc.uppercase()] ?: cc

    private fun c(name: String, cc: String, lat: Double, lon: Double, tz: String? = null) =
        WmCity(name, cc, lat, lon, tz ?: tzByCc[cc] ?: "UTC")

    private fun sa(name: String, lat: Double, lon: Double) = c(name, "SA", lat, lon)

    /* ───────── جميع مدن المملكة ───────── */
    val saudi: List<WmCity> = listOf(
        sa("الرياض", 24.71, 46.68), sa("جدة", 21.54, 39.17), sa("مكة المكرمة", 21.42, 39.83),
        sa("المدينة المنورة", 24.47, 39.61), sa("الدمام", 26.43, 50.10), sa("الخبر", 26.28, 50.21),
        sa("الظهران", 26.29, 50.11), sa("القطيف", 26.56, 50.01), sa("الجبيل", 27.00, 49.66),
        sa("الهفوف", 25.38, 49.59), sa("الأحساء", 25.38, 49.59), sa("المبرز", 25.43, 49.65),
        sa("الطائف", 21.27, 40.42), sa("تبوك", 28.38, 36.57), sa("بريدة", 26.33, 43.97),
        sa("عنيزة", 26.09, 43.99), sa("الرس", 25.87, 43.50), sa("حائل", 27.52, 41.70),
        sa("أبها", 18.22, 42.50), sa("خميس مشيط", 18.31, 42.73), sa("نجران", 17.49, 44.13),
        sa("جازان", 16.89, 42.55), sa("صبيا", 17.15, 42.63), sa("أبو عريش", 16.97, 42.83),
        sa("صامطة", 16.60, 42.94), sa("الباحة", 20.01, 41.47), sa("سكاكا", 29.97, 40.21),
        sa("عرعر", 30.98, 41.02), sa("طريف", 31.67, 38.66), sa("القريات", 31.33, 37.34),
        sa("دومة الجندل", 29.81, 39.87), sa("ينبع", 24.09, 38.06), sa("العلا", 26.61, 37.92),
        sa("بدر", 23.78, 38.79), sa("رابغ", 22.80, 39.03), sa("الليث", 20.15, 40.27),
        sa("القنفذة", 19.13, 41.08), sa("ضباء", 27.35, 35.69), sa("حقل", 29.29, 34.94),
        sa("الوجه", 26.24, 36.47), sa("أملج", 25.02, 37.27), sa("تيماء", 27.63, 38.55),
        sa("خيبر", 25.70, 39.29), sa("المهد", 23.50, 40.86), sa("الحناكية", 24.86, 40.50),
        sa("حفر الباطن", 28.43, 45.97), sa("الخرج", 24.15, 47.31), sa("المجمعة", 25.90, 45.35),
        sa("الزلفي", 26.30, 44.80), sa("شقراء", 25.25, 45.25), sa("الدوادمي", 24.51, 44.39),
        sa("عفيف", 23.91, 42.92), sa("القويعية", 24.07, 45.28), sa("وادي الدواسر", 20.49, 44.79),
        sa("السليل", 20.46, 45.58), sa("ليلى (الأفلاج)", 22.28, 46.73), sa("حوطة بني تميم", 23.52, 46.84),
        sa("الحريق", 23.60, 46.51), sa("الدرعية", 24.73, 46.58), sa("حريملاء", 25.13, 46.12),
        sa("المزاحمية", 24.47, 46.26), sa("ضرما", 24.60, 46.13), sa("الدلم", 23.99, 47.16),
        sa("رماح", 25.56, 47.15), sa("ثادق", 25.29, 45.87), sa("الغاط", 26.03, 44.97),
        sa("المذنب", 26.04, 44.20), sa("البكيرية", 26.14, 43.66), sa("رياض الخبراء", 26.01, 43.50),
        sa("البدائع", 26.00, 43.77), sa("عيون الجواء", 26.52, 43.60), sa("بيشة", 19.99, 42.60),
        sa("تثليث", 19.53, 43.50), sa("محايل عسير", 18.55, 42.05), sa("رجال ألمع", 18.22, 42.17),
        sa("أحد رفيدة", 18.22, 42.80), sa("سراة عبيدة", 18.17, 43.00), sa("النماص", 19.12, 42.13),
        sa("تنومة", 19.07, 42.11), sa("بلجرشي", 19.86, 41.56), sa("المخواة", 19.78, 41.42),
        sa("ظهران الجنوب", 17.67, 43.50), sa("شرورة", 17.47, 47.11), sa("رأس تنورة", 26.64, 50.16),
        sa("الخفجي", 28.43, 48.49), sa("النعيرية", 27.47, 48.49), sa("بقيق", 25.94, 49.67),
        sa("سلوى", 24.74, 50.78), sa("صفوى", 26.65, 49.95), sa("سيهات", 26.48, 50.04),
        sa("تاروت", 26.58, 50.04), sa("تربة", 21.21, 41.64), sa("رنية", 21.27, 42.85),
        sa("الخرمة", 21.91, 42.04), sa("ثول", 22.28, 39.10), sa("الجموم", 21.62, 39.70),
        sa("منى", 21.41, 39.89)
    )

    /* ───────── مدن عربية ───────── */
    val arab: List<WmCity> = listOf(
        c("دبي", "AE", 25.20, 55.27), c("أبوظبي", "AE", 24.45, 54.38), c("الشارقة", "AE", 25.35, 55.39),
        c("عجمان", "AE", 25.41, 55.44), c("رأس الخيمة", "AE", 25.79, 55.94), c("الفجيرة", "AE", 25.12, 56.33),
        c("أم القيوين", "AE", 25.56, 55.55), c("العين", "AE", 24.21, 55.76),
        c("مدينة الكويت", "KW", 29.38, 47.99), c("حولي", "KW", 29.33, 48.03), c("الجهراء", "KW", 29.34, 47.66),
        c("الدوحة", "QA", 25.29, 51.53), c("الريان", "QA", 25.29, 51.42), c("الوكرة", "QA", 25.17, 51.60),
        c("المنامة", "BH", 26.23, 50.59), c("المحرق", "BH", 26.26, 50.61),
        c("مسقط", "OM", 23.59, 58.41), c("صلالة", "OM", 17.02, 54.09), c("صحار", "OM", 24.35, 56.71),
        c("نزوى", "OM", 22.93, 57.53), c("صور", "OM", 22.57, 59.53),
        c("صنعاء", "YE", 15.35, 44.21), c("عدن", "YE", 12.79, 45.02), c("تعز", "YE", 13.58, 44.02),
        c("الحديدة", "YE", 14.80, 42.95), c("المكلا", "YE", 14.54, 49.12),
        c("بغداد", "IQ", 33.31, 44.37), c("البصرة", "IQ", 30.51, 47.78), c("الموصل", "IQ", 36.34, 43.13),
        c("أربيل", "IQ", 36.19, 44.01), c("النجف", "IQ", 32.03, 44.34), c("كربلاء", "IQ", 32.62, 44.03),
        c("السليمانية", "IQ", 35.56, 45.43), c("كركوك", "IQ", 35.47, 44.39),
        c("دمشق", "SY", 33.51, 36.28), c("حلب", "SY", 36.20, 37.16), c("حمص", "SY", 34.73, 36.72),
        c("اللاذقية", "SY", 35.52, 35.79), c("حماة", "SY", 35.13, 36.76),
        c("بيروت", "LB", 33.89, 35.50), c("طرابلس (لبنان)", "LB", 34.44, 35.83), c("صيدا", "LB", 33.56, 35.37),
        c("عمّان", "JO", 31.95, 35.93), c("الزرقاء", "JO", 32.07, 36.09), c("إربد", "JO", 32.56, 35.85),
        c("العقبة", "JO", 29.53, 35.01),
        c("القدس", "PS", 31.77, 35.21, "Asia/Jerusalem"), c("غزة", "PS", 31.50, 34.47, "Asia/Gaza"),
        c("رام الله", "PS", 31.90, 35.20, "Asia/Hebron"), c("الخليل", "PS", 31.53, 35.10, "Asia/Hebron"),
        c("نابلس", "PS", 32.22, 35.26, "Asia/Hebron"),
        c("القاهرة", "EG", 30.04, 31.24), c("الإسكندرية", "EG", 31.20, 29.92), c("الجيزة", "EG", 30.01, 31.21),
        c("الأقصر", "EG", 25.69, 32.64), c("أسوان", "EG", 24.09, 32.90), c("بورسعيد", "EG", 31.27, 32.30),
        c("السويس", "EG", 29.97, 32.55), c("شرم الشيخ", "EG", 27.91, 34.33), c("الغردقة", "EG", 27.26, 33.81),
        c("المنصورة", "EG", 31.04, 31.38), c("طنطا", "EG", 30.79, 31.00),
        c("طرابلس (ليبيا)", "LY", 32.89, 13.19), c("بنغازي", "LY", 32.12, 20.09), c("مصراتة", "LY", 32.38, 15.09),
        c("تونس", "TN", 36.81, 10.18), c("صفاقس", "TN", 34.74, 10.76), c("سوسة", "TN", 35.83, 10.64),
        c("الجزائر", "DZ", 36.75, 3.06), c("وهران", "DZ", 35.70, -0.63), c("قسنطينة", "DZ", 36.37, 6.61),
        c("الدار البيضاء", "MA", 33.57, -7.59), c("الرباط", "MA", 34.02, -6.84), c("مراكش", "MA", 31.63, -8.01),
        c("فاس", "MA", 34.03, -5.00), c("طنجة", "MA", 35.77, -5.80), c("أكادير", "MA", 30.43, -9.60),
        c("نواكشوط", "MR", 18.09, -15.98),
        c("الخرطوم", "SD", 15.50, 32.56), c("أم درمان", "SD", 15.64, 32.48), c("بورتسودان", "SD", 19.62, 37.22),
        c("مقديشو", "SO", 2.05, 45.32), c("جيبوتي", "DJ", 11.59, 43.15), c("موروني", "KM", -11.70, 43.26)
    )

    /* ───────── مدن عالمية ───────── */
    val world: List<WmCity> = listOf(
        c("لندن", "GB", 51.51, -0.13), c("باريس", "FR", 48.86, 2.35), c("برلين", "DE", 52.52, 13.40),
        c("مدريد", "ES", 40.42, -3.70), c("روما", "IT", 41.90, 12.50), c("إسطنبول", "TR", 41.01, 28.98),
        c("أنقرة", "TR", 39.93, 32.86), c("موسكو", "RU", 55.76, 37.62), c("أمستردام", "NL", 52.37, 4.90),
        c("بروكسل", "BE", 50.85, 4.35), c("فيينا", "AT", 48.21, 16.37), c("زيورخ", "CH", 47.38, 8.54),
        c("ستوكهولم", "SE", 59.33, 18.07), c("أوسلو", "NO", 59.91, 10.75), c("كوبنهاغن", "DK", 55.68, 12.57),
        c("أثينا", "GR", 37.98, 23.73), c("لشبونة", "PT", 38.72, -9.14), c("دبلن", "IE", 53.35, -6.26),
        c("وارسو", "PL", 52.23, 21.01), c("كييف", "UA", 50.45, 30.52), c("سراييفو", "BA", 43.86, 18.41),
        c("نيويورك", "US", 40.71, -74.01, "America/New_York"), c("واشنطن", "US", 38.91, -77.04, "America/New_York"),
        c("ميامي", "US", 25.76, -80.19, "America/New_York"), c("شيكاغو", "US", 41.88, -87.63, "America/Chicago"),
        c("هيوستن", "US", 29.76, -95.37, "America/Chicago"), c("لوس أنجلوس", "US", 34.05, -118.24, "America/Los_Angeles"),
        c("سان فرانسيسكو", "US", 37.77, -122.42, "America/Los_Angeles"), c("تورنتو", "CA", 43.65, -79.38, "America/Toronto"),
        c("مكسيكو سيتي", "MX", 19.43, -99.13), c("ساو باولو", "BR", -23.55, -46.63),
        c("بوينس آيرس", "AR", -34.60, -58.38),
        c("طوكيو", "JP", 35.68, 139.69), c("سيول", "KR", 37.57, 126.98), c("بكين", "CN", 39.90, 116.41),
        c("شنغهاي", "CN", 31.23, 121.47), c("هونغ كونغ", "HK", 22.32, 114.17), c("سنغافورة", "SG", 1.35, 103.82),
        c("بانكوك", "TH", 13.76, 100.50), c("كوالالمبور", "MY", 3.14, 101.69), c("جاكرتا", "ID", -6.21, 106.85),
        c("مانيلا", "PH", 14.60, 120.98), c("دلهي", "IN", 28.61, 77.21), c("مومباي", "IN", 19.08, 72.88),
        c("كراتشي", "PK", 24.86, 67.01), c("لاهور", "PK", 31.55, 74.34), c("إسلام آباد", "PK", 33.68, 73.05),
        c("دكا", "BD", 23.81, 90.41), c("طهران", "IR", 35.69, 51.39), c("كابول", "AF", 34.53, 69.17),
        c("طشقند", "UZ", 41.30, 69.24), c("كولومبو", "LK", 6.93, 79.86), c("كاتماندو", "NP", 27.72, 85.32),
        c("باكو", "AZ", 40.41, 49.87),
        c("سيدني", "AU", -33.87, 151.21, "Australia/Sydney"), c("ملبورن", "AU", -37.81, 144.96, "Australia/Melbourne"),
        c("أوكلاند", "NZ", -36.85, 174.76), c("كيب تاون", "ZA", -33.92, 18.42), c("جوهانسبرغ", "ZA", -26.20, 28.05),
        c("لاغوس", "NG", 6.52, 3.38), c("نيروبي", "KE", -1.29, 36.82), c("أديس أبابا", "ET", 9.03, 38.74)
    )

    val all: List<WmCity> = saudi + arab + world

    fun encode(c: WmCity) = "${c.name}|${c.cc}|${c.lat}|${c.lon}|${c.tz}"

    fun parse(s: String): WmCity? = try {
        val p = s.split("|")
        WmCity(p[0], p[1], p[2].toDouble(), p[3].toDouble(), p[4])
    } catch (e: Throwable) { null }
}

object WmNet {
    private fun http(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "WMLauncher/1.0")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun codeText(code: Int): String = when (code) {
        0 -> "صافٍ"
        1 -> "صافٍ غالباً"
        2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55 -> "رذاذ"
        56, 57 -> "رذاذ متجمد"
        61 -> "مطر خفيف"
        63 -> "مطر"
        65 -> "مطر غزير"
        66, 67 -> "مطر متجمد"
        71, 73, 75, 77 -> "ثلوج"
        80, 81, 82 -> "زخات مطر"
        85, 86 -> "زخات ثلج"
        95 -> "عاصفة رعدية"
        96, 99 -> "عاصفة رعدية مع برد"
        else -> "—"
    }

    fun weather(c: WmCity): WmWeather {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code&timezone=auto"
        val cur = JSONObject(http(url)).getJSONObject("current")
        return WmWeather(
            cur.getDouble("temperature_2m"),
            cur.getDouble("apparent_temperature"),
            cur.getInt("relative_humidity_2m"),
            Math.round(cur.getDouble("wind_speed_10m")).toInt(),
            codeText(cur.getInt("weather_code"))
        )
    }

    fun prayers(c: WmCity): List<WmPrayer> {
        val url = "https://api.aladhan.com/v1/timings?latitude=${c.lat}&longitude=${c.lon}" +
                "&method=${WmCities.method(c.cc)}&timezonestring=${URLEncoder.encode(c.tz, "UTF-8")}"
        val t = JSONObject(http(url)).getJSONObject("data").getJSONObject("timings")
        fun p(k: String) = LocalTime.parse(t.getString(k).trim().take(5))
        return listOf(
            WmPrayer("الفجر", p("Fajr")),
            WmPrayer("الظهر", p("Dhuhr")),
            WmPrayer("العصر", p("Asr")),
            WmPrayer("المغرب", p("Maghrib")),
            WmPrayer("العشاء", p("Isha"))
        )
    }

    /** بحث يدوي عن أي مدينة في العالم (Open-Meteo Geocoding) */
    fun search(q: String): List<GeoHit> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q.trim(), "UTF-8")}&count=10&language=ar&format=json"
        val arr = JSONObject(http(url)).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<GeoHit>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cc = o.optString("country_code", "")
            val city = WmCity(o.optString("name", "؟"), cc, o.getDouble("latitude"), o.getDouble("longitude"), o.optString("timezone", "UTC"))
            val sub = listOf(o.optString("admin1", ""), o.optString("country", "")).filter { it.isNotEmpty() }.joinToString("، ")
            out.add(GeoHit(city, sub))
        }
        return out
    }
}                                                        
