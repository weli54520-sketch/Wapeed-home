// WM Launcher — نسخة الكود رقم 4
package com.riyadh.home

import android.animation.ArgbEvaluator
import android.animation.LayoutTransition
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.Dialog
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
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
class WmAmbient(c: Context, initial: Int) : View(c) {
    private var color = initial
    private var from = initial
    private var to = initial
    private var phase = 0f
    private var lastPulse = 0L
    private var base = intArrayOf(0xFF04060A.toInt(), 0xFF0B0E16.toInt(), 0xFF06080F.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val eval = ArgbEvaluator()

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 700
        addUpdateListener {
            color = eval.evaluate(it.animatedValue as Float, from, to) as Int
            invalidate()
        }
    }

    // الحركة بطيئة جداً (دورة 18 ثانية) فلا نحتاج أكثر من ~16 إطار بالثانية — يوفّر المعالج للتنقل
    private val pulseAnim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 18000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            phase = it.animatedValue as Float
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastPulse >= 60) {
                lastPulse = now
                invalidate()
            }
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

    // التدرجات تُبنى مرة وحدة بلون أبيض، واللون يتغير بفلتر خفيف (بدل إنشاء تدرجات جديدة كل إطار)
    private var bgShader: Shader? = null
    private var g1: RadialGradient? = null
    private var g2: RadialGradient? = null
    private var builtW = 0f
    private var builtH = 0f
    private var builtColor = 0
    private var tintFilter: ColorFilter? = null
    private var builtBase: IntArray? = null
    private val m1 = Matrix()
    private val m2 = Matrix()
    private val p1 = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val p2 = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (bgShader == null || w != builtW || h != builtH || builtBase !== base) {
            bgShader = LinearGradient(0f, 0f, w, h, base, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            builtBase = base
        }
        if (g1 == null || w != builtW || h != builtH) {
            g1 = RadialGradient(0f, 0f, w * 0.6f, intArrayOf(Color.argb(90, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            g2 = RadialGradient(0f, 0f, w * 0.5f, intArrayOf(Color.argb(70, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            p1.shader = g1
            p2.shader = g2
            tintFilter = null
        }
        if (tintFilter == null || builtColor != color) {
            tintFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
            p1.colorFilter = tintFilter
            p2.colorFilter = tintFilter
            builtColor = color
        }
        builtW = w
        builtH = h
        val dx = Math.sin(phase.toDouble()).toFloat() * w * 0.02f
        val dy = Math.cos(phase.toDouble()).toFloat() * h * 0.02f
        paint.shader = bgShader
        cv.drawRect(0f, 0f, w, h, paint)
        m1.setTranslate(w * 0.25f + dx, h * 0.15f + dy); g1?.setLocalMatrix(m1)
        cv.drawRect(0f, 0f, w, h, p1)
        m2.setTranslate(w * 0.85f - dx, h * 0.2f - dy); g2?.setLocalMatrix(m2)
        cv.drawRect(0f, 0f, w, h, p2)
    }
}

/* ───────────────────────── زجاج حقيقي ───────────────────────── */
class WmGlass(
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

/* ───────────────────────── تحديد زجاجي متحرك ───────────────────────── */
class WmFocusGlass(private val radius: Float, private val strokePx: Float, private val strength: Int) : Drawable() {
    var phase = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val inset = RectF()
    private val clip = Path()
    private val m = Matrix()
    private var bandW = 1f

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        rect.set(b)
        val a = strength.coerceIn(20, 100) / 100f
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.argb((95 * a).toInt(), 255, 255, 255), Color.argb((38 * a).toInt(), 200, 220, 255), Shader.TileMode.CLAMP)
        bandW = rect.width() * 0.45f
        shine.shader = LinearGradient(0f, 0f, bandW, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb((85 * a).toInt() + 20, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        ring.strokeWidth = strokePx
        glow.strokeWidth = strokePx * 3.2f
        val h = strokePx / 2f
        inset.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
    }

    override fun draw(c: Canvas) {
        if (bounds.isEmpty) return
        c.drawRoundRect(rect, radius, radius, fill)
        // لمعة خفيفة تمر على الزجاج
        val t = (phase % 1f)
        if (t < 0.55f) {
            val x = rect.left - bandW + (rect.width() + bandW * 2f) * (t / 0.55f)
            m.setTranslate(x, 0f)
            shine.shader?.setLocalMatrix(m)
            c.save()
            c.clipPath(clip)
            c.drawRect(rect, shine)
            c.restore()
        }
        // إطار يتنفس بهدوء
        val breathe = 0.5f + 0.5f * Math.sin(phase * Math.PI * 2).toFloat()
        glow.color = Color.argb((26 + 34 * breathe).toInt(), 255, 255, 255)
        val rr = (radius - strokePx / 2f).coerceAtLeast(0f)
        c.drawRoundRect(inset, rr, rr, glow)
        ring.color = Color.argb((190 + 65 * breathe).toInt(), 255, 255, 255)
        c.drawRoundRect(inset, rr, rr, ring)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/* ───────────────────────── أيقونة الطقس المتحركة (شمس / قمر / غيوم) ───────────────────────── */
class WmWeatherIcon(c: Context) : View(c) {
    private var cat = 0          // 0 صافي، 1 غائم جزئياً، 2 غائم، 3 ضباب، 4 مطر، 5 ثلج، 6 عاصفة
    private var day = true
    private var t = 0f
    private var lastInv = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rr = RectF()
    private val bolt = Path()
    private val moonPath = Path()
    private val cutPath = Path()
    private var moonKey = ""

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastInv >= 32) {
                lastInv = now
                invalidate()
            }
        }
    }

    fun setState(category: Int, isDay: Boolean) {
        if (category == cat && isDay == day) return
        cat = category
        day = isDay
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!anim.isStarted) anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (isAttachedToWindow && !anim.isStarted) anim.start()
        } else anim.cancel()
    }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val ph = t * 2f * Math.PI.toFloat()
        val sw = Math.sin(ph.toDouble()).toFloat()
        when (cat) {
            0 -> if (day) sun(cv, s * 0.5f, s * 0.5f, s * 0.26f, sw)
                 else moon(cv, s * 0.5f, s * 0.5f, s * 0.26f, sw, true, s, ph)
            1 -> {
                if (day) sun(cv, s * 0.37f, s * 0.37f, s * 0.17f, sw)
                else moon(cv, s * 0.37f, s * 0.37f, s * 0.17f, sw, false, s, ph)
                cloud(cv, s * 0.58f + sw * s * 0.015f, s * 0.64f, s * 0.64f, false)
            }
            2 -> {
                cloud(cv, s * 0.36f - sw * s * 0.010f, s * 0.40f, s * 0.46f, true)
                cloud(cv, s * 0.56f + sw * s * 0.015f, s * 0.58f, s * 0.68f, false)
            }
            3 -> {
                cloud(cv, s * 0.5f + sw * s * 0.012f, s * 0.38f, s * 0.70f, false)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = s * 0.05f
                for (i in 0..2) {
                    val dx = Math.sin((ph + i * 1.3f).toDouble()).toFloat() * s * 0.04f
                    val y = s * (0.66f + i * 0.11f)
                    p.color = Color.argb(210 - i * 35, 196, 208, 228)
                    cv.drawLine(s * 0.22f + dx, y, s * 0.78f + dx, y, p)
                }
            }
            4 -> {
                cloud(cv, s * 0.5f + sw * s * 0.012f, s * 0.40f, s * 0.72f, true)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = s * 0.045f
                for (i in 0..2) {
                    val f = (t * 2f + i / 3f) % 1f
                    val x = s * (0.34f + i * 0.16f) - f * s * 0.03f
                    val y = s * 0.60f + f * s * 0.22f
                    p.color = Color.argb((255 * Math.sin(f * Math.PI)).toInt(), 110, 182, 255)
                    cv.drawLine(x, y, x - s * 0.03f, y + s * 0.09f, p)
                }
            }
            5 -> {
                cloud(cv, s * 0.5f + sw * s * 0.012f, s * 0.40f, s * 0.72f, false)
                p.style = Paint.Style.FILL
                for (i in 0..2) {
                    val f = (t + i / 3f) % 1f
                    val x = s * (0.34f + i * 0.16f) + Math.sin((ph + i * 2f).toDouble()).toFloat() * s * 0.025f
                    val y = s * 0.60f + f * s * 0.30f
                    p.color = Color.argb((255 * Math.sin(f * Math.PI)).toInt(), 255, 255, 255)
                    cv.drawCircle(x, y, s * 0.032f, p)
                }
            }
            else -> {
                cloud(cv, s * 0.5f + sw * s * 0.012f, s * 0.38f, s * 0.74f, true)
                val f = (t * 2f) % 1f
                val flash = if (f < 0.10f || (f > 0.20f && f < 0.28f)) 1f else 0.6f
                bolt.reset()
                bolt.moveTo(s * 0.55f, s * 0.54f)
                bolt.lineTo(s * 0.40f, s * 0.78f)
                bolt.lineTo(s * 0.50f, s * 0.78f)
                bolt.lineTo(s * 0.43f, s * 0.97f)
                bolt.lineTo(s * 0.66f, s * 0.68f)
                bolt.lineTo(s * 0.55f, s * 0.68f)
                bolt.close()
                p.style = Paint.Style.FILL
                p.color = Color.argb((255 * flash).toInt(), 255, 210, 64)
                cv.drawPath(bolt, p)
            }
        }
    }

    // شمس: هالة تنبض + أشعة تدور ببطء + قرص بتدرج
    private fun sun(cv: Canvas, cx: Float, cy: Float, r: Float, sw: Float) {
        val pulse = 0.5f + 0.5f * sw
        p.shader = null
        p.style = Paint.Style.FILL
        for (i in 0..2) {
            p.color = Color.argb(((30 + 22 * pulse) * (1f - i * 0.28f)).toInt(), 255, 190, 50)
            cv.drawCircle(cx, cy, r * (1.72f - i * 0.22f + 0.08f * pulse), p)
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.17f
        p.color = Color.argb(235, 255, 196, 52)
        val rot = Math.toRadians((t * 45f).toDouble())
        val r1 = r * 1.34f
        val r2 = r * (1.60f + 0.14f * pulse)
        for (i in 0 until 8) {
            val a = rot + Math.toRadians(i * 45.0)
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            cv.drawLine(cx + r1 * ca, cy + r1 * sa, cx + r2 * ca, cy + r2 * sa, p)
        }
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 183, 3)
        cv.drawCircle(cx, cy, r, p)
        p.color = Color.rgb(255, 205, 64)
        cv.drawCircle(cx - r * 0.10f, cy - r * 0.10f, r * 0.80f, p)
        p.color = Color.argb(200, 255, 236, 150)
        cv.drawCircle(cx - r * 0.24f, cy - r * 0.26f, r * 0.42f, p)
    }

    // قمر هلال: هالة زرقاء ناعمة + تمايل خفيف + نجوم تلمع
    private fun moon(cv: Canvas, cx: Float, cy: Float, r: Float, sw: Float, stars: Boolean, s: Float, ph: Float) {
        val pulse = 0.5f + 0.5f * sw
        p.shader = null
        p.style = Paint.Style.FILL
        for (i in 0..2) {
            p.color = Color.argb(((26 + 18 * pulse) * (1f - i * 0.28f)).toInt(), 150, 180, 255)
            cv.drawCircle(cx, cy, r * (1.75f - i * 0.25f), p)
        }
        val key = "$cx/$cy/$r"
        if (key != moonKey) {
            moonKey = key
            moonPath.reset()
            moonPath.addCircle(cx, cy, r, Path.Direction.CW)
            cutPath.reset()
            cutPath.addCircle(cx + r * 0.58f, cy - r * 0.30f, r * 0.86f, Path.Direction.CW)
            moonPath.op(cutPath, Path.Op.DIFFERENCE)
        }
        cv.save()
        cv.rotate(sw * 4f, cx, cy)
        p.color = Color.rgb(238, 242, 253)
        cv.drawPath(moonPath, p)
        p.color = Color.argb(150, 176, 188, 216)
        cv.drawCircle(cx - r * 0.46f, cy + r * 0.20f, r * 0.13f, p)
        cv.drawCircle(cx - r * 0.18f, cy + r * 0.52f, r * 0.09f, p)
        cv.restore()
        if (stars) {
            star(cv, s * 0.82f, s * 0.24f, s * 0.075f, ph * 2f)
            star(cv, s * 0.84f, s * 0.70f, s * 0.050f, ph * 2f + 2.1f)
            star(cv, s * 0.16f, s * 0.20f, s * 0.050f, ph * 2f + 4.2f)
        }
    }

    private fun star(cv: Canvas, x: Float, y: Float, h: Float, a: Float) {
        val tw = 0.25f + 0.75f * Math.abs(Math.sin(a.toDouble())).toFloat()
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = h * 0.35f
        p.color = Color.argb((235 * tw).toInt(), 255, 244, 205)
        val l = h * (0.6f + 0.4f * tw)
        cv.drawLine(x - l, y, x + l, y, p)
        cv.drawLine(x, y - l, x, y + l, p)
    }

    private fun cloud(cv: Canvas, cx: Float, cy: Float, w: Float, dark: Boolean) {
        val shade = if (dark) Color.rgb(112, 124, 146) else Color.rgb(184, 198, 220)
        val main = if (dark) Color.rgb(178, 190, 210) else Color.rgb(246, 249, 253)
        p.shader = null
        p.style = Paint.Style.FILL
        for (pass in 0..1) {
            p.color = if (pass == 0) shade else main
            val dy = if (pass == 0) w * 0.03f else 0f
            rr.set(cx - w * 0.5f, cy - w * 0.02f + dy, cx + w * 0.5f, cy + w * 0.20f + dy)
            cv.drawRoundRect(rr, w * 0.11f, w * 0.11f, p)
            cv.drawCircle(cx - w * 0.17f, cy - w * 0.05f + dy, w * 0.19f, p)
            cv.drawCircle(cx + w * 0.09f, cy - w * 0.13f + dy, w * 0.26f, p)
            cv.drawCircle(cx + w * 0.30f, cy - w * 0.01f + dy, w * 0.16f, p)
        }
    }
}

/* ───────────────────────── شعار WM ───────────────────────── */
class WmLogo(c: Context) : View(c) {
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
class WmSwitch(c: Context) : View(c) {
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

class WmBar(c: Context) : View(c) {
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

    private val sectionNames = listOf("المظهر", "اللغة والأرقام", "الريموت", "الطقس والصلاة", "المباريات", "الرئيسية", "حول والتحديث")
    private val sectionIcons = listOf("🎨", "🌐", "🎮", "⛅", "⚽", "🏠", "ℹ️")
    private val sectionSubs = listOf(
        "الألوان والزجاج والتحديد",
        "لغة التاريخ وشكل الأرقام",
        "أزرار الريموت والأزرار الملونة",
        "المدينة ومواقيت الصلاة",
        "عرض المباريات والشريط",
        "ترتيب الصفحة الرئيسية",
        "الإصدار والتحديث من الواجهة"
    )

    private lateinit var ambient: WmAmbient
    private lateinit var backgroundImageView: ImageView
    private lateinit var overlay: View
    private lateinit var headerView: View
    private lateinit var pages: List<View>
    private lateinit var tabPills: List<LinearLayout>
    private lateinit var tabTexts: List<TextView>
    private lateinit var tabGlyphs: List<GlyphView>
    private var currentPage = 0

    // درج جميع التطبيقات
    private lateinit var rootFrame: FrameLayout
    private var host: FrameLayout? = null
    private lateinit var homeRoot: LinearLayout
    private lateinit var drawerView: FrameLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerCountTv: TextView
    private lateinit var drawerScrim: View
    private var drawerOpen = false
    private var lastDockPkg: String? = null
    private var lastDrawerPkg: String? = null

    // منتقي الخيارات داخل الإعدادات
    private var openPicker: View? = null
    private var prayerZone: ZoneId? = null

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
    private lateinit var settingsNavLabels: List<TextView>
    private lateinit var settingsContent: LinearLayout
    private lateinit var settingsScroll: ScrollView
    private lateinit var settingsTitleTv: TextView
    private lateinit var settingsSubTv: TextView
    private var currentSection = 0
    private var lastFocusedRow = 0
    private var menuPopup: PopupWindow? = null

    private var allApps = mutableListOf<ResolveInfo>()
    private var prayersData: List<Prayer> = emptyList()
    private var matchesData: List<Match>? = null
    private val statusViews = ArrayList<Pair<TextView, Match>>()
    private var lastMin = -1
    private val logoCache = ConcurrentHashMap<String, Bitmap>()
    private val bgCache = ConcurrentHashMap<String, Bitmap>()
    private val feedsTask = Runnable { fetchMatches(); refreshFeeds() }
    private val matchesTask = Runnable { fetchMatches() }
    private val clockTask = Runnable { updateClock() }
    private val rebuildTask = Runnable { doRebuild() }
    private var navTarget = 0
    private val navTask = Runnable {
        val i = navTarget
        if (::settingsNav.isInitialized && settingsNav.getOrNull(i)?.hasFocus() == true &&
            (i != currentSection || settingsContent.childCount == 0)) showSection(i, true)
    }

    private var wTemp = "--°"
    private var wCat = 0
    private var wDay = LocalTime.now().hour in 6..17
    private lateinit var weatherIcon: WmWeatherIcon
    private var wStatus = "جاري التحديث..."
    private var wSub = ""

    private var reorderingPkg: String? = null
    private var reorderList = mutableListOf<String>()

    // التحديث
    private class UpdateInfo(val code: Int, val name: String, val apk: String, val notes: String)
    private val updateUrl = "https://api.github.com/repos/weli54520-sketch/Wapeed-home/releases/latest"
    private val updateTask = Runnable { checkUpdate(silent = true) }
    private val installAction = "com.riyadh.home.INSTALL_RESULT"
    private var updateInfo: UpdateInfo? = null
    private var updateMsg = ""
    private var downloading = false
    private var updateSubTv: TextView? = null

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
    private fun focusSharp() = pInt("focus_style2", 1) == 0
    private fun focusGlassPct() = pInt("focus_glass", 50)
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

    private fun statusText(m: Match): String = nd(when {
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
    })

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun city(): City {
        prefs.getString("city_v2", null)?.let { s -> Data.decode(s)?.let { return it } }
        return Data.legacyCities[prefs.getInt("city_idx", 0).coerceIn(0, Data.legacyCities.size - 1)]
    }

    private fun setCity(c: City) {
        prefs.edit().putString("city_v2", Data.encode(c)).apply()
        prayerZone = null
        prayersData = emptyList()
        wTemp = "--°"; wStatus = "جاري التحديث..."; wSub = ""; wCat = 0
        applyWeather()
        if (::cityHeaderTv.isInitialized) cityHeaderTv.text = "الطقس • ${c.name}"
        if (::cityPillTv.isInitialized) cityPillTv.text = c.name
        if (::prayerLayout.isInitialized) prayerLayout.removeAllViews()
        if (::prayerTimerTv.isInitialized) prayerTimerTv.text = "جاري تحديث المواقيت..."
        refreshFeeds()
        if (currentPage == 2) refreshSection()
    }

    private fun enabledLeagues(): Set<String> = Data.leagues.filter { pBool("lg_" + it.slug, it.def) }.map { it.slug }.toSet()

    private fun isInside(v: View?, parent: View): Boolean {
        var x: View? = v
        while (x != null) {
            if (x === parent) return true
            x = x.parent as? View
        }
        return false
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
        val k = if (pBool("glass_tint", false)) 0.32f else 0f
        val ac = accent
        fun mixc(r: Int, g: Int, b: Int, al: Int): Int = Color.argb(
            al,
            (r + (Color.red(ac) - r) * k).toInt(),
            (g + (Color.green(ac) - g) * k).toInt(),
            (b + (Color.blue(ac) - b) * k).toInt()
        )
        val top = if (sharp) mixc(34, 40, 58, a) else mixc(62, 68, 98, a)
        val bot = if (sharp) mixc(34, 40, 58, a) else mixc(20, 24, 38, (a * 0.9f).toInt())
        return WmGlass(dp(radius).toFloat(), sp, top, bot, border, explicitSolid || sharp, if (sharp) 0 else 34)
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

    // محرك واحد للحركة يحرّك التحديد الحالي فقط (خفيف جداً)
    private var activeFocus: WmFocusGlass? = null
    private val focusAnim: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3200
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { va ->
            activeFocus?.let { it.phase = va.animatedValue as Float; it.invalidateSelf() }
        }
    }

    private fun focusBg(radius: Int): Drawable {
        if (focusSharp()) return solidCard(Color.WHITE, radius)
        val d = WmFocusGlass(dp(radius).toFloat(), dp(2).toFloat(), focusGlassPct())
        d.phase = (focusAnim.animatedValue as? Float) ?: 0f
        activeFocus = d
        if (!focusAnim.isStarted) focusAnim.start()
        return d
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

        // درج التطبيقات: السهم لتحت من المفضلة يفتحه، والسهم لفوق من أول صف يقفله
        if (drawerOpen) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val f = currentFocus
                val n = f?.focusSearch(View.FOCUS_UP)
                if (f == null || n == null || !isInside(n, drawerPanel)) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) closeDrawer()
                    return true
                }
            }
        } else if (currentPage == 0 && reorderingPkg == null && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
            ::favRow.isInitialized && favRow.hasFocus() && pBool("show_all_apps", true)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openDrawer()
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
        try {
            buildInterface(pInt("start_page", 0).coerceIn(0, 1), false)
            fetchMatches()
            updateClock()
            refreshFeeds()
            ui.postDelayed(updateTask, 8000)
            if (intent?.action == installAction) handleInstallResult(intent)
            val vc = curVersionCode()
            val last = prefs.getInt("last_version", 0)
            if (last in 1 until vc) Toast.makeText(this, nd("تم التحديث إلى الإصدار ${curVersionName()} ✓"), Toast.LENGTH_LONG).show()
            if (last != vc) prefs.edit().putInt("last_version", vc).apply()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply { text = "خطأ: $e"; textSize = 20f; setTextColor(Color.RED); setPadding(40, 40, 40, 40) })
        }
    }

    override fun onDestroy() {
        focusAnim.cancel()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onPause() {
        focusAnim.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (focusAnim.isPaused) focusAnim.resume()
        if (::pages.isInitialized && (resumeUpdate || pBool("resume_update", false))) {
            resumeUpdate = false
            putBool("resume_update", false)
            openSection(6)
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
                if (updateInfo != null) ui.postDelayed({ startUpdate() }, 400)
                else checkUpdate(silent = false, thenInstall = true)
            } else setUpdateMsg("ما تم السماح بالتثبيت، اضغط تحميل وتثبيت مرة ثانية")
        }
        // لا نعيد بناء التطبيقات إلا إذا تغيّرت فعلاً (قبل كان يحمّل كل الأيقونات في كل رجوع)
        if (::favRow.isInitialized) loadAppsAndDock(force = false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && intent.action == installAction) { handleInstallResult(intent); return }
        if (drawerOpen) closeDrawer()
    }

    @Deprecated("") override fun onBackPressed() {
        if (reorderingPkg != null) {
            finishReorderMode()
            return
        }
        if (!::pages.isInitialized) return
        if (drawerOpen) {
            closeDrawer()
            return
        }
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
        ambient = WmAmbient(this, homeGlow())
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
        rootFrame = frame
        homeRoot = root
        drawerOpen = false
        openPicker = null
        drawerView = buildDrawer()
        frame.addView(drawerView, FrameLayout.LayoutParams(-1, -1))
        val h = host ?: FrameLayout(this).also { host = it; setContentView(it) }
        h.addView(frame, FrameLayout.LayoutParams(-1, -1))
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
        // الواجهة القديمة تبقى تحت والجديدة تظهر فوقها تدريجياً ثم نشيل القديمة (بدون وميض)
        val old: FrameLayout? = if (::rootFrame.isInitialized) rootFrame else null
        old?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        buildInterface(page, false)
        val fresh = rootFrame
        if (old != null && old !== fresh) {
            fresh.alpha = 0f
            fresh.animate().alpha(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).withLayer()
                .withEndAction { host?.removeView(old) }.start()
        }
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
        if (drawerOpen) closeDrawer(false)
        if (reorderingPkg != null) finishReorderMode()
        currentPage = i
        pages.forEachIndexed { k, p ->
            if (k == i) {
                p.visibility = View.VISIBLE
                if (animate) {
                    p.alpha = 0f
                    p.translationY = dp(12).toFloat()
                    p.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator(1.6f)).withLayer().start()
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
        } else if (i == 0 && animate && ::favRow.isInitialized) {
            favRow.post { (favRow.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.requestFocus() }
        }
    }

    private fun updateTabStyles() {
        tabPills.forEachIndexed { k, pill ->
            paintCapsule(pill, pill.hasFocus(), k == currentPage, tabTexts[k], tabGlyphs[k])
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

        val pList = mutableListOf<LinearLayout>()
        val tList = mutableListOf<TextView>()
        val gList = mutableListOf<GlyphView>()
        listOf(Triple("الرئيسية", 3, 0), Triple("المباريات", 4, 1)).forEach { (n, kind, i) ->
            val cap = iconCapsule(kind, n, isSelected = { currentPage == i }) { showPage(i) }
            pList.add(cap.first)
            gList.add(cap.second)
            tList.add(cap.third)
            h.addView(cap.first, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })
        }
        tabPills = pList
        tabTexts = tList
        tabGlyphs = gList

        val badge = tv(10.5f, true, Color.WHITE).apply {
            text = "متصل"
            background = solidCard(greenBadge, 12)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val wifi = iconCapsule(0, "Wi-Fi", badge = badge, onFocus = {
            val on = isOnline()
            badge.text = if (on) "متصل" else "غير متصل"
            badge.background = solidCard(if (on) greenBadge else Color.parseColor("#EF4444"), 12)
        }) { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        h.addView(wifi.first, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(10) })

        val cityCap = iconCapsule(2, city().name) { openCityPicker() }
        cityPillTv = cityCap.third
        h.addView(cityCap.first, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })

        val settingsCap = iconCapsule(1, "الإعدادات") { openSettingsMenu(it) }
        h.addView(settingsCap.first, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) })

        return h
    }

    // يفتح/يقفل الكتابة جنب الأيقونة بحركة ناعمة (الأيقونة تبقى دايماً)
    private fun setExpanded(extra: View, on: Boolean) {
        (extra.tag as? ValueAnimator)?.cancel()
        extra.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val full = extra.measuredWidth
        val lp = extra.layoutParams
        val from = if (lp.width < 0) full else lp.width
        val to = if (on) full else 0
        val va = ValueAnimator.ofInt(from, to).apply {
            duration = 240
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                lp.width = it.animatedValue as Int
                extra.layoutParams = lp
            }
        }
        extra.tag = va
        va.start()
        extra.animate().alpha(if (on) 1f else 0f).setDuration(if (on) 260 else 140).start()
    }

    private fun paintCapsule(v: View, f: Boolean, sel: Boolean, title: TextView?, glyph: GlyphView) {
        v.background = when {
            f -> focusBg(26)
            sel -> slateGlass(230, 26, accent, 1)
            else -> slateGlass(185, 26, Color.argb(55, 255, 255, 255), 1)
        }
        val col = when {
            f -> focusFg()
            sel -> accent
            else -> Color.WHITE
        }
        title?.setTextColor(col)
        glyph.setTint(col)
    }

    private fun capsuleFocus(v: View, f: Boolean, sel: Boolean, title: TextView?, glyph: GlyphView) {
        paintCapsule(v, f, sel, title, glyph)
        v.translationZ = if (f) dp(10).toFloat() else 0f
        v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f).setDuration(130).start()
    }

    private fun isOnline(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n = cm.activeNetwork
        val caps = if (n != null) cm.getNetworkCapabilities(n) else null
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Throwable) { true }

    // كبسولة أيقونة موحّدة: الأيقونة دايماً ظاهرة، والكتابة تنفتح بجنبها لما يجي عليها المؤشر
    private fun iconCapsule(
        kind: Int,
        label: String,
        badge: View? = null,
        isSelected: () -> Boolean = { false },
        onFocus: (() -> Unit)? = null,
        onClick: (View) -> Unit
    ): Triple<LinearLayout, GlyphView, TextView> {
        val glyph = GlyphView(this, kind)
        val title = tv(13f, true, Color.WHITE).apply {
            text = label
            setSingleLine(true)
            setPadding(dp(8), 0, dp(6), 0)
        }
        val extra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = true
            alpha = 0f
            addView(title)
            if (badge != null) addView(badge)
        }
        val root = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(185, 26, Color.argb(55, 255, 255, 255), 1)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            isFocusable = true
            isClickable = true
            outlineProvider = null
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(extra, LinearLayout.LayoutParams(0, -2))
            setOnClickListener { onClick(this) }
            setOnFocusChangeListener { v, f ->
                if (f) onFocus?.invoke()
                capsuleFocus(v, f, isSelected(), title, glyph)
                setExpanded(extra, f)
            }
        }
        return Triple(root, glyph, title)
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
        weatherIcon = WmWeatherIcon(this).also { it.setState(wCat, wDay) }
        tempRow.addView(weatherIcon, LinearLayout.LayoutParams(dp(34), dp(34)))
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
        // مساحة مرنة تدفع شريط المفضلة لأسفل الشاشة
        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // المفضلة (Dock) في أسفل الشاشة
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(210, 30, Color.argb(45, 255, 255, 255), 1)
            setPadding(dp(18), dp(10), dp(18), dp(8))
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
            setPadding(dp(16), dp(14), dp(16), dp(8))
            layoutTransition = LayoutTransition().apply { setDuration(180) }
        }
        dock.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = true
            clipToPadding = false
            addView(favRow)
        }, LinearLayout.LayoutParams(-1, -2))
        if (pBool("show_all_apps", true)) {
            dock.addView(tv(10.5f, true, muted).apply {
                text = "▼   جميع التطبيقات"
                gravity = Gravity.CENTER
                letterSpacing = 0.05f
            }, LinearLayout.LayoutParams(-1, -2))
        }
        col.addView(dock, LinearLayout.LayoutParams(-1, -2))
        return col
    }

    /* ─────────── درج جميع التطبيقات ─────────── */
    private fun buildDrawer(): FrameLayout {
        val d = FrameLayout(this).apply {
            visibility = View.GONE
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        drawerScrim = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(130, 0, 0, 0), Color.argb(215, 0, 0, 0)))
            alpha = 0f
        }
        d.addView(drawerScrim, FrameLayout.LayoutParams(-1, -1))

        drawerPanel = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(250, 30, Color.argb(60, 255, 255, 255), 1)
            setPadding(dp(22), dp(10), dp(22), dp(44))
            roundOutline(30)
        }
        drawerPanel.addView(View(this).apply { background = solidCard(Color.argb(90, 255, 255, 255), 3) },
            LinearLayout.LayoutParams(dp(56), dp(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) })

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), dp(4))
        }
        head.addView(tv(19f, true, accent).apply { text = "جميع التطبيقات" })
        drawerCountTv = tv(12f, true, muted).apply { setPadding(dp(12), dp(3), 0, 0) }
        head.addView(drawerCountTv)
        head.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        head.addView(tv(11f, false, muted).apply { text = "▲ للرجوع للرئيسية   •   ضغطة مطولة للخيارات" })
        drawerPanel.addView(head, LinearLayout.LayoutParams(-1, -2))

        allGrid = GridLayout(this).unclip().apply {
            columnCount = gridCols()
            setPadding(dp(14), dp(16), dp(14), dp(36))
        }
        drawerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(allGrid, ViewGroup.LayoutParams(-1, -2))
        }
        drawerPanel.addView(drawerScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        d.addView(drawerPanel, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(26), dp(28), dp(26), -dp(40)) })
        return d
    }

    private fun tileOf(group: ViewGroup, pkg: String?): View? {
        if (pkg == null) return null
        for (i in 0 until group.childCount) {
            val cell = group.getChildAt(i)
            if (cell.tag == pkg) return (cell as? ViewGroup)?.getChildAt(0)
        }
        return null
    }

    private fun firstTile(group: ViewGroup): View? = (group.getChildAt(0) as? ViewGroup)?.getChildAt(0)

    private fun openDrawer() {
        if (drawerOpen || !::drawerView.isInitialized) return
        if (allApps.isEmpty()) loadAppsAndDock()
        ensureGrid()
        drawerOpen = true
        val h = (if (rootFrame.height > 0) rootFrame.height else resources.displayMetrics.heightPixels).toFloat()
        drawerScrim.animate().cancel()
        drawerPanel.animate().cancel()
        drawerView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (drawerView.visibility != View.VISIBLE) {
            drawerScrim.alpha = 0f
            drawerPanel.translationY = h
            drawerScroll.scrollTo(0, 0)
            drawerView.visibility = View.VISIBLE
        }
        // الحركة على خيط الرسم مباشرة (translation فقط) = سلسة حتى على الأجهزة الضعيفة
        drawerScrim.animate().alpha(1f).setDuration(260).start()
        drawerPanel.animate().translationY(0f).setDuration(340).setInterpolator(DecelerateInterpolator(2.2f)).start()
        val target = tileOf(allGrid, lastDrawerPkg) ?: firstTile(allGrid)
        target?.requestFocus()
        homeRoot.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun closeDrawer(animate: Boolean = true) {
        if (!drawerOpen) return
        drawerOpen = false
        homeRoot.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        drawerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        if (currentPage == 0) (tileOf(favRow, lastDockPkg) ?: firstTile(favRow))?.requestFocus()
        if (animate) {
            val h = (if (rootFrame.height > 0) rootFrame.height else resources.displayMetrics.heightPixels).toFloat()
            drawerPanel.animate().translationY(h).setDuration(280).setInterpolator(AccelerateInterpolator(1.4f))
                .withEndAction { if (!drawerOpen) drawerView.visibility = View.GONE }.start()
            drawerScrim.animate().alpha(0f).setDuration(260).start()
        } else {
            drawerScrim.animate().cancel()
            drawerPanel.animate().cancel()
            drawerView.visibility = View.GONE
            drawerScrim.alpha = 0f
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
                2 -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.0f * d
                    cv.drawCircle(cx, cy - 2.5f * d, 5f * d, p)
                    cv.drawLine(cx - 3.8f * d, cy + 1f * d, cx, cy + 8.5f * d, p)
                    cv.drawLine(cx + 3.8f * d, cy + 1f * d, cx, cy + 8.5f * d, p)
                    p.style = Paint.Style.FILL
                    cv.drawCircle(cx, cy - 2.5f * d, 1.8f * d, p)
                }
                3 -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.0f * d
                    p.strokeJoin = Paint.Join.ROUND
                    val roof = Path()
                    roof.moveTo(cx - 9.5f * d, cy - 0.5f * d)
                    roof.lineTo(cx, cy - 8.5f * d)
                    roof.lineTo(cx + 9.5f * d, cy - 0.5f * d)
                    cv.drawPath(roof, p)
                    val body = Path()
                    body.moveTo(cx - 6.5f * d, cy - 2.5f * d)
                    body.lineTo(cx - 6.5f * d, cy + 8f * d)
                    body.lineTo(cx + 6.5f * d, cy + 8f * d)
                    body.lineTo(cx + 6.5f * d, cy - 2.5f * d)
                    cv.drawPath(body, p)
                    val door = Path()
                    door.moveTo(cx - 2f * d, cy + 8f * d)
                    door.lineTo(cx - 2f * d, cy + 3.2f * d)
                    door.lineTo(cx + 2f * d, cy + 3.2f * d)
                    door.lineTo(cx + 2f * d, cy + 8f * d)
                    cv.drawPath(door, p)
                }
                4 -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 1.9f * d
                    cv.drawCircle(cx, cy, 9.6f * d, p)
                    val pent = Path()
                    for (k in 0 until 5) {
                        val a = Math.toRadians((-90 + k * 72).toDouble())
                        val x = cx + 3.6f * d * Math.cos(a).toFloat()
                        val y = cy + 3.6f * d * Math.sin(a).toFloat()
                        if (k == 0) pent.moveTo(x, y) else pent.lineTo(x, y)
                        cv.drawLine(x, y, cx + 9.4f * d * Math.cos(a).toFloat(), cy + 9.4f * d * Math.sin(a).toFloat(), p)
                    }
                    pent.close()
                    p.style = Paint.Style.FILL
                    cv.drawPath(pent, p)
                }
            }
        }
    }

    /* ─────────── النوافذ والقوائم ─────────── */
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

    // القائمة المنسدلة تحت زر الإعدادات
    private fun openSettingsMenu(anchor: View) {
        try { menuPopup?.dismiss() } catch (e: Throwable) { }
        val popW = dp(300)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = slateGlass(245, 22, Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(10), dp(10), dp(10), dp(6))
        }
        val pw = PopupWindow(box, popW, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.isOutsideTouchable = true
        menuPopup = pw

        val items = listOf<Pair<String, () -> Unit>>(
            "⚙️   إعدادات WM Launcher" to { showPage(2) },
            "🛠️   إعدادات النظام" to { go(Intent(Settings.ACTION_SETTINGS)) },
            "🖥️   الصورة والشاشة" to { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            "🔊   الصوت" to { go(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            "🧩   التطبيقات" to { showRecentApps() },
            "🔄   التحديث وحول الواجهة" to { openSection(6) }
        )
        for ((label, act) in items) {
            box.addView(optionRow(label) { pw.dismiss(); act() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val sw = resources.displayMetrics.widthPixels
        val x = loc[0].coerceIn(dp(12), maxOf(dp(12), sw - popW - dp(12)))
        val y = loc[1] + anchor.height + dp(8)
        pw.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, x, y)
        box.post { box.getChildAt(0)?.requestFocus() }
    }

    private fun autoLocate() {
        Toast.makeText(this, "جاري تحديد موقعك…", Toast.LENGTH_SHORT).show()
        thread {
            val c = try { Data.locate() } catch (e: Throwable) { null }
            ui.post {
                if (c == null) Toast.makeText(this, "تعذر تحديد الموقع، اختر المدينة يدوياً", Toast.LENGTH_LONG).show()
                else {
                    setCity(c)
                    Toast.makeText(this, "تم تحديد موقعك: ${c.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pickRow(label: String, sub: String, selected: Boolean, onFocus: (() -> Unit)?, onClick: () -> Unit): View {
        val t = tv(14.5f, true, if (selected) accent else Color.WHITE).apply { text = if (selected) "✓  $label" else label }
        val st = tv(10.5f, false, dim).apply { text = sub; visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE }
        fun idle(): Drawable = slateGlass(if (selected) 210 else 150, 14, if (selected) accent else Color.argb(35, 255, 255, 255), 1)
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = idle()
            isFocusable = true
            isClickable = true
            roundOutline(14)
            addView(t)
            addView(st)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(14) else idle()
                t.setTextColor(if (f) focusFg() else if (selected) accent else Color.WHITE)
                st.setTextColor(if (f) focusFg() else dim)
                if (f) onFocus?.invoke()
            }
        }
    }

    // اختيار المدينة: مناطق المملكة + الدول العربية + بحث عن أي مدينة + تحديد تلقائي
    private fun openCityPicker() {
        val (dialog, sheet) = shell()
        val cur = city()
        sheet.addView(tv(19f, true, accent).apply { text = "اختر مدينتك (الطقس والصلاة)" })
        sheet.addView(tv(11.5f, false, dim).apply {
            text = "الحالية: ${cur.name}" + (if (cur.sub.isNotEmpty()) "  •  ${cur.sub}" else "")
            setPadding(0, dp(2), 0, dp(12))
        })

        val W = dp(780)
        val bar = LinearLayout(this).unclip().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun inputBg(f: Boolean): Drawable = if (f) slateGlass(225, 16, accent, 2) else slateGlass(170, 16, Color.argb(60, 255, 255, 255), 1)
        val input = EditText(this).apply {
            hint = "🔎  اكتب اسم أي مدينة (عربي أو English)"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            background = inputBg(false)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            setOnFocusChangeListener { v, f -> v.background = inputBg(f) }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val panes = LinearLayout(this).unclip().apply { orientation = LinearLayout.HORIZONTAL }
        val groupsList = LinearLayout(this).unclip().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        val citiesList = LinearLayout(this).unclip().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        val groupsScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(groupsList) }
        val citiesScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(citiesList) }
        panes.addView(groupsScroll, LinearLayout.LayoutParams(dp(260), -1))
        panes.addView(citiesScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(12) })

        fun choose(c: City) {
            dialog.dismiss()
            setCity(c)
        }
        fun isCur(c: City) = c.name == cur.name && Math.abs(c.lat - cur.lat) < 0.02 && Math.abs(c.lon - cur.lon) < 0.02
        fun message(m: String) {
            citiesList.removeAllViews()
            citiesList.addView(tv(13f, false, dim).apply { text = m; setPadding(dp(8), dp(12), dp(8), dp(12)) })
        }
        fun showCities(title: String, list: List<City>, focusFirst: Boolean) {
            citiesList.removeAllViews()
            citiesList.addView(tv(12f, true, muted).apply { text = title; setPadding(dp(6), 0, dp(6), dp(6)) })
            for (c in list) {
                citiesList.addView(pickRow(c.name, c.sub, isCur(c), null) { choose(c) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
            citiesScroll.scrollTo(0, 0)
            citiesList.animate().cancel()
            citiesList.alpha = 0f
            citiesList.translationX = -dp(14).toFloat()
            citiesList.animate().alpha(1f).translationX(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
            if (focusFirst) citiesList.post { citiesList.getChildAt(1)?.requestFocus() }
        }

        var searchSeq = 0
        fun search(q: String, focus: Boolean) {
            if (q.trim().length < 2) return
            val my = ++searchSeq
            message("جاري البحث عن «${q.trim()}»…")
            thread {
                val r = try { Data.searchCities(q) } catch (e: Throwable) { null }
                ui.post {
                    if (my != searchSeq || !dialog.isShowing) return@post
                    when {
                        r == null -> message("تعذر البحث، تأكد من الاتصال بالإنترنت")
                        r.isEmpty() -> message("ما لقيت مدينة بهذا الاسم، جرّب كتابة ثانية")
                        else -> showCities("نتائج البحث عن «${q.trim()}»", r, focus)
                    }
                }
            }
        }
        val debounce = Runnable { search(input.text.toString(), false) }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                ui.removeCallbacks(debounce)
                if ((s?.trim()?.length ?: 0) >= 2) ui.postDelayed(debounce, 800)
            }
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                ui.removeCallbacks(debounce)
                search(input.text.toString(), true)
                true
            } else false
        }
        bar.addView(optionRow("📍  موقعي تلقائياً") {
            dialog.dismiss()
            autoLocate()
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        sheet.addView(bar, LinearLayout.LayoutParams(W, -2).apply { bottomMargin = dp(12) })
        sheet.addView(panes, LinearLayout.LayoutParams(W, dp(330)))

        var focusGroup: View? = null
        fun addGroup(name: String, list: List<City>, sub: String) {
            val has = list.any { isCur(it) }
            val row = pickRow(name, sub, has, { showCities(name, list, false) }) {
                showCities(name, list, true)
            }
            groupsList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            if (has && focusGroup == null) focusGroup = row
        }
        groupsList.addView(tv(12f, true, muted).apply { text = "🇸🇦  المملكة العربية السعودية"; setPadding(dp(6), 0, dp(6), dp(6)) })
        for ((name, list) in Data.saudiRegions) addGroup(name, list, "${list.size} مدن")
        groupsList.addView(tv(12f, true, muted).apply { text = "الدول العربية"; setPadding(dp(6), dp(10), dp(6), dp(6)) })
        for ((name, list) in Data.arabCountries) addGroup(name, list, "${list.size} مدن")

        val start = focusGroup ?: groupsList.getChildAt(1)
        val startIdx = groupsList.indexOfChild(start)
        val startList = (Data.saudiRegions + Data.arabCountries).getOrNull(if (startIdx <= Data.saudiRegions.size) startIdx - 1 else startIdx - 2)
        if (startList != null) showCities(startList.first, startList.second, false)
        dialog.setContentView(sheet)
        dialog.show()
        start?.post { start.requestFocus() }
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

    private fun pickAppFor(key: String, label: String) {
        val (dialog, box) = shell()
        box.addView(tv(18f, true, accent).apply { text = "اختر تطبيقاً للزر $label"; setPadding(0, 0, 0, dp(12)) })
        val list = LinearLayout(this).unclip().apply { orientation = LinearLayout.VERTICAL }
        list.addView(optionRow("بدون تعيين") {
            prefs.edit().remove(key).apply()
            dialog.dismiss()
            refreshSection()
        }, LinearLayout.LayoutParams(dp(340), -2).apply { bottomMargin = dp(8) })
        val pm = packageManager
        for (a in allApps) {
            val pkg = a.activityInfo.packageName
            list.addView(optionRow(a.loadLabel(pm).toString()) {
                prefs.edit().putString(key, pkg).apply()
                dialog.dismiss()
                refreshSection()
            }, LinearLayout.LayoutParams(dp(340), -2).apply { bottomMargin = dp(8) })
        }
        box.addView(ScrollView(this).unclip().apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(dp(340), dp(320)))
        dialog.setContentView(box)
        dialog.show()
    }

    /* ─────────── آخر التطبيقات استخداماً ─────────── */
    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean = try {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    } catch (e: Throwable) { false }

    private fun recentPackages(): List<Pair<String, Long>> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 7 * 24 * 3600 * 1000L, end) ?: return emptyList()
        val launchable = allApps.map { it.activityInfo.packageName }.toSet()
        return stats
            .filter { it.lastTimeUsed > 0 && it.packageName in launchable && it.packageName != packageName }
            .groupBy { it.packageName }
            .map { (p, l) -> p to l.maxOf { it.lastTimeUsed } }
            .sortedByDescending { it.second }
            .take(8)
    }

    private fun ago(ms: Long): String {
        val m = (System.currentTimeMillis() - ms) / 60000
        return nd(when {
            m < 1 -> "الآن"
            m < 60 -> "قبل $m دقيقة"
            m < 1440 -> "قبل ${m / 60} ساعة"
            else -> "قبل ${m / 1440} يوم"
        })
    }

    private fun recentRow(pkg: String, t: Long, dialog: Dialog): View {
        val icon: Drawable? = try { packageManager.getApplicationIcon(pkg) } catch (e: Throwable) { null }
        val nameTv = tv(14f, true, Color.WHITE).apply { text = appLabel(pkg) }
        val subTv = tv(10.5f, false, dim).apply { text = ago(t) }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameTv)
            addView(subTv)
        }
        val iconView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (icon != null) setImageDrawable(icon)
        }
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(160, 16, Color.argb(35, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            isClickable = true
            roundOutline(16)
            addView(iconView, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { dialog.dismiss(); launchPkg(pkg) }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(16) else slateGlass(160, 16, Color.argb(35, 255, 255, 255), 1)
                nameTv.setTextColor(if (f) focusFg() else Color.WHITE)
                subTv.setTextColor(if (f) focusFg() else dim)
            }
        }
    }

    private fun showRecentApps() {
        val (dialog, box) = shell()
        box.addView(tv(19f, true, accent).apply { text = "التطبيقات" })
        box.addView(tv(11.5f, false, dim).apply { text = "التطبيقات التي تم فتحها مؤخراً"; setPadding(0, dp(2), 0, dp(12)) })
        val list = LinearLayout(this).unclip().apply { orientation = LinearLayout.VERTICAL }
        if (!hasUsageAccess()) {
            list.addView(tv(12f, false, dim).apply {
                text = "لعرض آخر التطبيقات، امنح الواجهة صلاحية «الوصول لبيانات الاستخدام» من الإعدادات."
                setPadding(dp(4), 0, dp(4), dp(10))
            })
            list.addView(optionRow("منح الصلاحية الآن") {
                dialog.dismiss()
                go(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        } else {
            val recents: List<Pair<String, Long>> = try { recentPackages() } catch (e: Throwable) { emptyList() }
            if (recents.isEmpty()) {
                list.addView(tv(13f, false, dim).apply { text = "لا توجد تطبيقات مستخدمة مؤخراً"; setPadding(dp(6), dp(8), dp(6), dp(12)) })
            }
            for ((pkg, t) in recents) {
                list.addView(recentRow(pkg, t, dialog), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        list.addView(optionRow("الاطلاع على كل التطبيقات") {
            dialog.dismiss()
            showPage(0, false)
            ui.postDelayed({ openDrawer() }, 150)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        box.addView(ScrollView(this).unclip().apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(dp(380), dp(330)))
        dialog.setContentView(box)
        dialog.show()
    }

    /* ─────────── التحديث من الواجهة ─────────── */
    @Suppress("DEPRECATION")
    private fun curVersionCode(): Int = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    } catch (e: Throwable) { 0 }

    private fun curVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Throwable) { "" }

    private fun setUpdateMsg(m: String) {
        updateMsg = m
        updateSubTv?.text = m
    }

    // يقرأ آخر إصدار من GitHub مباشرة (رقم الإصدار = رقم البناء)
    private fun checkUpdate(silent: Boolean = false, thenInstall: Boolean = false) {
        if (downloading) return
        if (!silent) setUpdateMsg("جاري التحقق...")
        ui.removeCallbacks(updateTask)
        ui.postDelayed(updateTask, 6 * 60 * 60 * 1000L)
        thread {
            try {
                val conn = URL(updateUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "WM-Launcher")
                val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name", "")
                val code = tag.filter { it.isDigit() }.toIntOrNull() ?: 0
                var apk = ""
                val assets = j.optJSONArray("assets")
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
                val info = UpdateInfo(code, tag, apk, j.optString("body", "").take(120))
                ui.post {
                    if (info.code > curVersionCode() && info.apk.isNotEmpty()) {
                        updateInfo = info
                        setUpdateMsg("يوجد إصدار جديد: ${info.name}")
                        if (silent) offerUpdate(info)
                    } else {
                        updateInfo = null
                        if (!silent) setUpdateMsg("أنت على آخر إصدار ✓")
                    }
                    if (currentPage == 2 && currentSection == 6) refreshSection()
                    if (thenInstall && updateInfo != null) startUpdate()
                }
            } catch (e: Throwable) {
                if (!silent) ui.post { setUpdateMsg("تعذر التحقق، تأكد من الاتصال بالإنترنت") }
            }
        }
    }

    // نافذة صغيرة تعرض التحديث الجديد (مرة وحدة لكل إصدار)
    private fun offerUpdate(info: UpdateInfo) {
        if (prefs.getInt("update_offered", 0) == info.code || isFinishing) return
        prefs.edit().putInt("update_offered", info.code).apply()
        val (dialog, box) = shell()
        box.addView(tv(19f, true, accent).apply { text = "⬇️  تحديث جديد للواجهة"; setPadding(0, 0, 0, dp(6)) })
        box.addView(tv(13f, false, dim).apply {
            text = nd("الإصدار ${info.name} جاهز (عندك ${curVersionCode()})")
            setPadding(0, 0, 0, dp(14))
        })
        box.addView(optionRow("تحديث الآن") {
            dialog.dismiss()
            openSection(6)
            startUpdate()
        }, LinearLayout.LayoutParams(dp(340), -2).apply { bottomMargin = dp(8) })
        box.addView(optionRow("لاحقاً") { dialog.dismiss() }, LinearLayout.LayoutParams(dp(340), -2))
        dialog.setContentView(box)
        try { dialog.show() } catch (e: Throwable) { }
    }

    private var resumeUpdate = false

    private fun startUpdate() {
        val info = updateInfo ?: return
        if (downloading) return
        // أول مرة: نطلب إذن تثبيت التطبيقات، ونكمل التحديث تلقائياً لما يرجع
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            resumeUpdate = true
            putBool("resume_update", true)
            setUpdateMsg("فعّل «السماح» ثم ارجع بزر الرجوع، والتحديث يكمل لحاله")
            go(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        downloading = true
        setUpdateMsg("جاري التحميل 0%")
        thread {
            try {
                val conn = URL(info.apk).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                val total = conn.contentLength.toLong()
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installer.createSession(params)
                val session = installer.openSession(id)
                var lastPct = -1
                conn.inputStream.use { input ->
                    session.openWrite("wm_update", 0, if (total > 0) total else -1L).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct && pct % 4 == 0) {
                                    lastPct = pct
                                    ui.post { setUpdateMsg("جاري التحميل $pct%") }
                                }
                            }
                        }
                        session.fsync(out)
                    }
                }
                val cb = Intent(this, MainActivity::class.java).setAction(installAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) 33554432 else 0)
                val pending = PendingIntent.getActivity(this, id, cb, flags)
                ui.post { setUpdateMsg("جاري التثبيت… وافق على رسالة النظام") }
                session.commit(pending.intentSender)
                session.close()
            } catch (e: Throwable) {
                ui.post {
                    downloading = false
                    setUpdateMsg("فشل التحميل، تأكد من الاتصال وحاول مرة ثانية")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleInstallResult(i: Intent) {
        setIntent(Intent(Intent.ACTION_MAIN))   // ما نعيد معالجة نفس النتيجة لو انبنت الواجهة من جديد
        if (::pages.isInitialized) openSection(6)
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) go(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                downloading = false
                setUpdateMsg("تم التثبيت ✓")
            }
            else -> {
                downloading = false
                setUpdateMsg("لم يكتمل التثبيت: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""))
            }
        }
    }

    /* ─────────── صفحة الإعدادات ─────────── */
    private fun buildSettingsPage(): View {
        val root = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        // الشريط الجانبي
        val side = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(225, 26, Color.argb(45, 255, 255, 255), 1)
            setPadding(dp(16), dp(18), dp(16), dp(18))
            roundOutline(26)
        }
        val brandTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(17f, true, Color.WHITE).apply { text = "WM Launcher" })
            addView(tv(10.5f, false, muted).apply { text = "الإعدادات" })
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(WmLogo(this), LinearLayout.LayoutParams(dp(40), dp(40)))
        brand.addView(brandTexts, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        side.addView(brand, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val navList = mutableListOf<LinearLayout>()
        val labelList = mutableListOf<TextView>()
        sectionNames.forEachIndexed { i, n ->
            val ic = tv(15f).apply { text = sectionIcons[i] }
            val label = tv(14f, true, Color.WHITE).apply { text = n }
            val item = LinearLayout(this).unclip().apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                isFocusable = true
                isClickable = true
                roundOutline(16)
                addView(ic)
                addView(label, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
                setOnClickListener {
                    ui.removeCallbacks(navTask)
                    showSection(i)
                    settingsContent.getChildAt(0)?.requestFocus()
                }
                setOnFocusChangeListener { _, f ->
                    if (f && (i != currentSection || settingsContent.childCount == 0)) {
                        navTarget = i
                        ui.removeCallbacks(navTask)
                        ui.postDelayed(navTask, 60)
                    }
                    styleNav()
                }
            }
            navList.add(item)
            labelList.add(label)
            side.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        settingsNav = navList
        settingsNavLabels = labelList

        // المحتوى
        settingsTitleTv = tv(22f, true, accent)
        settingsSubTv = tv(12f, false, dim).apply { setPadding(0, dp(2), 0, dp(10)) }
        settingsContent = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(24))
        }
        settingsScroll = ScrollView(this).unclip().apply {
            isVerticalScrollBarEnabled = false
            addView(settingsContent)
        }
        val contentCol = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), dp(6))
        }
        contentCol.addView(settingsTitleTv)
        contentCol.addView(settingsSubTv)
        contentCol.addView(settingsScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(side, LinearLayout.LayoutParams(dp(260), -1))
        root.addView(contentCol, LinearLayout.LayoutParams(0, -1, 1f))
        showSection(currentSection)
        return root
    }

    private fun styleNav() {
        if (!::settingsNav.isInitialized) return
        settingsNav.forEachIndexed { k, item ->
            val f = item.hasFocus()
            val sel = k == currentSection
            val lab = settingsNavLabels[k]
            when {
                f -> {
                    item.background = focusBg(16)
                    lab.setTextColor(focusFg())
                }
                sel -> {
                    item.background = slateGlass(230, 16, accent, 1)
                    lab.setTextColor(accent)
                }
                else -> {
                    item.background = slateGlass(140, 16, Color.argb(30, 255, 255, 255), 1)
                    lab.setTextColor(Color.WHITE)
                }
            }
        }
    }

    // يفتح الإعدادات على قسم معيّن ويبني محتواه فعلياً (قبل كان القسم يتحدد بالقائمة والمحتوى يبقى على أول قسم)
    private fun openSection(i: Int) {
        if (!::settingsContent.isInitialized) return
        ui.removeCallbacks(navTask)
        showSection(i)
        showPage(2)
    }

    private fun showSection(i: Int, animate: Boolean = false) {
        currentSection = i.coerceIn(0, sectionNames.size - 1)
        settingsTitleTv.text = sectionNames[currentSection]
        settingsSubTv.text = sectionSubs[currentSection]
        openPicker = null
        settingsContent.layoutTransition = null
        settingsContent.removeAllViews()
        updateSubTv = null
        when (currentSection) {
            0 -> buildAppearance()
            1 -> buildLanguage()
            2 -> buildRemote()
            3 -> buildWeatherSection()
            4 -> buildMatchesSection()
            5 -> buildHomeSection()
            else -> buildAboutSection()
        }
        settingsScroll.scrollTo(0, 0)
        styleNav()
        if (animate) {
            settingsContent.animate().cancel()
            settingsContent.alpha = 0f
            settingsContent.translationX = -dp(18).toFloat()
            settingsContent.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator(1.6f)).withLayer().start()
            settingsTitleTv.alpha = 0.3f
            settingsTitleTv.animate().alpha(1f).setDuration(230).start()
        }
    }

    private fun refreshSection() {
        if (!::settingsContent.isInitialized) return
        val r = lastFocusedRow
        showSection(currentSection)
        settingsContent.post {
            settingsContent.getChildAt(r.coerceIn(0, maxOf(0, settingsContent.childCount - 1)))?.requestFocus()
        }
    }

    private fun addRow(v: View) {
        settingsContent.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun addHeader(text: String) {
        settingsContent.addView(tv(12.5f, true, muted).apply {
            this.text = text
            setPadding(dp(4), dp(10), dp(4), dp(2))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    // صف عام في الإعدادات
    private fun sRow(
        title: String,
        sub: String = "",
        trailing: View? = null,
        onKey: ((Int) -> Boolean)? = null,
        onClick: () -> Unit
    ): LinearLayout {
        val t = tv(14.5f, true, Color.WHITE).apply { text = title }
        val s = tv(11f, false, dim).apply {
            text = sub
            visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(t)
            addView(s)
        }
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(170, 16, Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            isFocusable = true
            isClickable = true
            roundOutline(16)
            tag = s
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            if (trailing != null) addView(trailing, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14) })
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                if (f) lastFocusedRow = settingsContent.indexOfChild(v)
                v.background = if (f) focusBg(16) else slateGlass(170, 16, Color.argb(40, 255, 255, 255), 1)
                t.setTextColor(if (f) focusFg() else Color.WHITE)
                s.setTextColor(if (f) focusFg() else dim)
            }
            if (onKey != null) {
                setOnKeyListener { _, code, ev -> ev.action == KeyEvent.ACTION_DOWN && onKey(code) }
            }
        }
    }

    private fun sToggle(title: String, sub: String, key: String, def: Boolean, rebuild: Boolean = true, onChange: (() -> Unit)? = null): View {
        val sw = WmSwitch(this).apply {
            on = pBool(key, def)
            onColor = accent
        }
        return sRow(title, sub, sw) {
            val nv = !pBool(key, def)
            putBool(key, nv)
            sw.on = nv
            sw.invalidate()
            onChange?.invoke()
            if (rebuild) scheduleRebuild()
        }
    }

    // اختيار من قائمة: يفتح شريط خيارات صغير متناسق تحت الصف مباشرة
    private fun sChoice(
        title: String, sub: String, key: String, opts: List<String>, def: Int,
        swatches: List<Int>? = null, rebuild: Boolean = true
    ): View {
        var cur = pInt(key, def).coerceIn(0, opts.size - 1)
        val dot = View(this).apply {
            visibility = if (swatches != null) View.VISIBLE else View.GONE
            if (swatches != null) background = solidCard(swatches[cur], 8, Color.argb(220, 255, 255, 255), 1)
        }
        val valTv = tv(13f, true, accent).apply { text = opts[cur] }
        val arrow = tv(11f, true, dim).apply { text = "▾" }
        val trailing = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(dot, LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) })
            addView(valTv)
            addView(arrow, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        }
        var row: View? = null
        row = sRow(title, sub, trailing) {
            val r = row ?: return@sRow
            togglePicker(r, opts, swatches, cur) { i ->
                if (i == cur) return@togglePicker
                cur = i
                putInt(key, i)
                valTv.text = opts[i]
                if (swatches != null) dot.background = solidCard(swatches[i], 8, Color.argb(220, 255, 255, 255), 1)
                if (rebuild) scheduleRebuild()
            }
        }
        return row!!
    }

    private fun pickerTransition() = LayoutTransition().apply {
        disableTransitionType(LayoutTransition.APPEARING)
        disableTransitionType(LayoutTransition.DISAPPEARING)
        enableTransitionType(LayoutTransition.CHANGE_APPEARING)
        enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        setDuration(220)
        setInterpolator(LayoutTransition.CHANGE_APPEARING, DecelerateInterpolator(1.6f))
        setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, DecelerateInterpolator(1.6f))
    }

    private fun buildChip(label: String, swatch: Int?, selected: Boolean): LinearLayout {
        val txt = tv(13.5f, true, if (selected) accent else Color.WHITE).apply {
            text = if (selected) "✓ $label" else label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        fun idle(): Drawable = slateGlass(if (selected) 215 else 150, 22, if (selected) accent else Color.argb(40, 255, 255, 255), 1)
        return LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            isFocusable = true
            isClickable = true
            outlineProvider = null
            background = idle()
            if (swatch != null) {
                addView(View(context).apply { background = solidCard(swatch, 9, Color.argb(200, 255, 255, 255), 1) },
                    LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
            }
            addView(txt)
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(22) else idle()
                txt.setTextColor(if (f) focusFg() else if (selected) accent else Color.WHITE)
                v.translationZ = if (f) dp(8).toFloat() else 0f
                v.animate().scaleX(if (f) 1.05f else 1f).scaleY(if (f) 1.05f else 1f).setDuration(130).start()
            }
        }
    }

    private fun togglePicker(row: View, opts: List<String>, swatches: List<Int>?, cur: Int, onSelect: (Int) -> Unit) {
        val existing = openPicker
        if (existing != null) {
            val owner = existing.tag
            closePicker(true)
            if (owner === row) return
        }
        val ac = accent
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = slateGlass(190, 18, Color.argb(120, Color.red(ac), Color.green(ac), Color.blue(ac)), 1)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            tag = row
            clipChildren = true
        }
        // نوزع الخيارات على صفوف (4 بكل صف) داخل حدود اللوحة فقط
        val perRow = 4
        var curChip: View? = null
        var line: LinearLayout? = null
        opts.forEachIndexed { i, o ->
            if (i % perRow == 0) {
                line = LinearLayout(this).unclip().apply { orientation = LinearLayout.HORIZONTAL }
                panel.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (i == 0) 0 else dp(10) })
            }
            val chip = buildChip(o, swatches?.getOrNull(i), i == cur)
            chip.setOnClickListener {
                closePicker(true)
                ui.postDelayed({ onSelect(i) }, 180)
            }
            chip.setOnKeyListener { v, code, ev ->
                when (code) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) closePicker(true)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val dir = when (code) {
                            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                            else -> View.FOCUS_RIGHT
                        }
                        val n = v.focusSearch(dir)
                        if (n != null && isInside(n, panel)) false
                        else {
                            // فوق/تحت خارج الخيارات يقفل الشريط، ويمين/يسار ما يطلع للقائمة الجانبية
                            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0 &&
                                (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) closePicker(true)
                            true
                        }
                    }
                    else -> false
                }
            }
            line?.addView(chip, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginStart = dp(5); marginEnd = dp(5)
            })
            if (i == cur) curChip = chip
        }
        // نكمل آخر صف بفراغات عشان كل الخيارات بنفس العرض
        val rem = opts.size % perRow
        if (rem != 0 && opts.size > perRow) repeat(perRow - rem) {
            line?.addView(View(this), LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
        }
        val idx = settingsContent.indexOfChild(row)
        settingsContent.layoutTransition = pickerTransition()
        settingsContent.addView(panel, idx + 1, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        openPicker = panel
        panel.pivotY = 0f
        panel.alpha = 0f
        panel.scaleY = 0.7f
        panel.translationY = -dp(8).toFloat()
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator(1.8f)).start()
        val focusChip = curChip
        panel.post { focusChip?.requestFocus() }
        ui.postDelayed({ if (openPicker === panel) settingsContent.layoutTransition = null }, 320)
    }

    private fun closePicker(focusRow: Boolean) {
        val p = openPicker ?: return
        openPicker = null
        val row = p.tag as? View
        if (focusRow) row?.requestFocus()
        p.animate().cancel()
        p.animate().alpha(0f).scaleY(0.7f).translationY(-dp(8).toFloat()).setDuration(170)
            .setInterpolator(AccelerateInterpolator()).withEndAction {
                if (p.parent === settingsContent) {
                    settingsContent.layoutTransition = pickerTransition()
                    settingsContent.removeView(p)
                    ui.postDelayed({ if (openPicker == null) settingsContent.layoutTransition = null }, 300)
                }
            }.start()
    }

    private fun sSlider(title: String, sub: String, key: String, min: Int, max: Int, step: Int, def: Int): View {
        val bar = WmBar(this).apply { barColor = accent }
        val valTv = tv(13f, true, accent).apply { gravity = Gravity.CENTER }
        var cur = pInt(key, def).coerceIn(min, max)
        fun upd() {
            bar.frac = (cur - min).toFloat() / (max - min).toFloat()
            bar.invalidate()
            valTv.text = nd("$cur%")
        }
        upd()
        val trailing = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(bar, LinearLayout.LayoutParams(dp(180), dp(24)))
            addView(valTv, LinearLayout.LayoutParams(dp(58), -2).apply { marginStart = dp(10) })
        }
        fun setTo(v: Int) {
            cur = v
            putInt(key, cur)
            upd()
            scheduleRebuild()
        }
        return sRow(title, sub, trailing, { code ->
            when (code) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> { setTo((cur + step).coerceIn(min, max)); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { setTo((cur - step).coerceIn(min, max)); true }
                else -> false
            }
        }) {
            setTo(if (cur + step > max) min else cur + step)
        }
    }

    private fun keyRow(label: String, key: String): View {
        val cur = prefs.getString(key, "") ?: ""
        val valTv = tv(13f, true, accent).apply { text = if (cur.isEmpty()) "بدون" else appLabel(cur) }
        return sRow("الزر $label", "يفتح التطبيق المحدد من أي مكان", valTv) { pickAppFor(key, label) }
    }

    private fun buildAppearance() {
        addHeader("الألوان")
        addRow(sChoice("لون الخلفية", "تدرج خلفية الواجهة", "bg_palette", palettes.map { it.first }, 0, palettes.map { it.third }))
        addRow(sChoice("لون التمييز", "لون العناوين والعناصر المحددة", "accent_idx", accents.map { it.first }, 0, accents.map { it.second }))
        addRow(sToggle("صبغة الزجاج بلون التمييز", "يضيف لمسة من لون التمييز على اللوحات", "glass_tint", false))
        addHeader("الزجاج والحدود")
        addRow(sChoice("نمط الحدود", "زجاجي ناعم أو حاد وواضح", "border_style", listOf("زجاجي", "حاد"), 0))
        addRow(sSlider("قوة الحدود", "◀ ▶ للتغيير", "border_strength", 0, 100, 10, 50))
        addRow(sSlider("نسبة الشفافية", "كلما زادت صارت اللوحات أشفّ ◀ ▶", "panel_clear", 0, 90, 10, 30))
        addHeader("التحديد عند التنقل بالريموت")
        addRow(sChoice("نمط التحديد", "زجاجي متحرك أو أبيض حاد", "focus_style2", listOf("حاد (أبيض)", "زجاجي متحرك"), 1))
        addRow(sSlider("نسبة زجاج التحديد", "تظهر مع النمط الزجاجي ◀ ▶", "focus_glass", 10, 100, 10, 50))
        addRow(sChoice("حجم تكبير التحديد", "", "focus_scale", listOf("صغير", "متوسط", "كبير"), 1))
        addHeader("خلفية خاصة")
        addRow(sRow("صورة خلفية خاصة", "ضع رابط صورة مباشر", null) { showBgUrlDialog() })
        addRow(sRow("إزالة الخلفية الخاصة", "الرجوع للخلفية المتحركة", null) {
            prefs.edit().remove("custom_bg_url").apply()
            applyCustomBackground()
        })
        addRow(sRow("استعادة إعدادات المظهر الافتراضية", "لا يمس المفضلة", null) {
            val e = prefs.edit()
            for (k in listOf("bg_palette", "accent_idx", "glass_tint", "border_style", "border_strength", "panel_clear", "focus_style2", "focus_glass", "focus_scale")) e.remove(k)
            e.apply()
            scheduleRebuild()
        })
    }

    private fun buildLanguage() {
        addRow(sChoice("لغة التاريخ والتحية", "عربي أو إنجليزي", "date_lang", listOf("العربية", "English"), 0))
        addRow(sChoice("شكل الأرقام", "في الساعة والتاريخ والطقس والصلاة", "digits", listOf("كما هي", "هندية ٠١٢", "إنجليزية 012"), 0))
        addRow(sToggle("إظهار التحية", "صباح الخير / مساء الخير فوق الساعة", "show_greeting", true))
    }

    private fun buildRemote() {
        addRow(sToggle("منع أزرار التلفزيون", "يمنع أزرار TV / Guide / DVR من فتح شاشات أخرى", "block_keys", true))
        addRow(sToggle("زر القائمة يفتح الإعدادات", "زر Menu في الريموت", "menu_key", true))
        addHeader("الأزرار الملونة")
        addRow(keyRow("الأحمر", "key_red"))
        addRow(keyRow("الأخضر", "key_green"))
        addRow(keyRow("الأصفر", "key_yellow"))
        addRow(keyRow("الأزرق", "key_blue"))
    }

    private fun buildWeatherSection() {
        val c = city()
        val cityTv = tv(13f, true, accent).apply { text = c.name }
        addRow(sRow("المدينة", if (c.sub.isNotEmpty()) c.sub else "للطقس ومواقيت الصلاة", cityTv) { openCityPicker() })
        addRow(sRow("📍 تحديد موقعي تلقائياً", "عن طريق الشبكة (بدون GPS)", null) { autoLocate() })
        addRow(sToggle("إظهار الطقس", "في الصفحة الرئيسية", "show_weather", true))
        addRow(sToggle("إظهار مواقيت الصلاة", "في الصفحة الرئيسية", "show_prayers", true))
        addRow(sToggle("العد التنازلي للأذان", "تحت مواقيت الصلاة", "prayer_countdown", true))
        addRow(sRow("تحديث الآن", "جلب الطقس والمواقيت من جديد", null) { refreshFeeds() })
    }

    private fun buildMatchesSection() {
        addRow(sToggle("إظهار المباريات", "في الصفحة الرئيسية", "show_matches", true))
        addRow(sToggle("الشريط المتحرك", "ملخص المباريات فوق البطاقات", "show_ticker", true))
        addRow(sRow("تحديث المباريات", "جلب المباريات من جديد", null) { fetchMatches() })
        addHeader("البطولات")
        for (lg in Data.leagues) {
            addRow(sToggle(lg.name, if (lg.slug == "afc.champions") "أندية غرب آسيا فقط — بدون شرق آسيا" else "", "lg_" + lg.slug, lg.def, rebuild = false) {
                ui.removeCallbacks(matchesTask)
                ui.postDelayed(matchesTask, 700)
            })
        }
    }

    private fun buildHomeSection() {
        addRow(sChoice("صفحة البداية", "الصفحة اللي تفتح مع تشغيل الواجهة", "start_page", listOf("الرئيسية", "المباريات"), 0, rebuild = false))
        addRow(sToggle("درج جميع التطبيقات", "يفتح بالسهم لتحت من شريط المفضلة", "show_all_apps", true))
        addRow(sChoice("أعمدة المفضلة", "عدد التطبيقات الظاهرة في الشريط", "fav_cols", listOf("4", "5", "6"), 1))
        addRow(sChoice("أعمدة جميع التطبيقات", "عدد التطبيقات في كل صف", "grid_cols", listOf("4", "5", "6"), 1))
        addRow(sRow("إعادة ضبط المفضلة", "الترتيب الافتراضي", null) {
            prefs.edit().remove("fav_apps").apply()
            loadAppsAndDock()
        })
    }

    private fun buildAboutSection() {
        val brandTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(20f, true, Color.WHITE).apply { text = "WM Launcher" })
            addView(tv(11.5f, false, dim).apply { text = nd("الإصدار ${curVersionName()}  (${curVersionCode()})") })
        }
        val card = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = slateGlass(190, 20, Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        card.addView(WmLogo(this), LinearLayout.LayoutParams(dp(54), dp(54)))
        card.addView(brandTexts, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14) })
        addRow(card)

        val checkRow = sRow("التحقق من التحديث", if (updateMsg.isEmpty()) "اضغط للتحقق من وجود إصدار جديد" else updateMsg, null) { checkUpdate() }
        updateSubTv = checkRow.tag as? TextView
        addRow(checkRow)

        val info = updateInfo
        if (info != null && info.code > curVersionCode()) {
            addRow(sRow("تحميل وتثبيت ${info.name}", if (info.notes.isEmpty()) "تحديث جديد جاهز" else info.notes, null) { startUpdate() })
        }
    }

    /* ─────────── المباريات ─────────── */
    private fun fetchMatches() {
        thread {
            val r: List<Match>? = try { Data.matches(enabledLeagues()) } catch (e: Throwable) { null }
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
        for (m in list) {
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

    private fun miniLogo(name: String, spec: String): ImageView {
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(crest(name)) }
        if (spec.isNotEmpty()) loadLogo(spec, iv)
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

    // خلفية بطاقة المباراة: لون واحد شفاف خفيف + إطار رفيع (بدون طبقات داخلية)
    private fun miniCardBg(): Drawable = GradientDrawable().apply {
        setColor(Color.argb(26, 255, 255, 255))
        cornerRadius = dp(20).toFloat()
        setStroke(dp(1), Color.argb(48, 255, 255, 255))
    }

    private fun miniMatch(m: Match): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = miniCardBg()
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isFocusable = true
            isClickable = true
            elevation = 0f
            outlineProvider = null // بدون ظل: الظل كان يبان من خلف الزجاج كمربع داخلي
            setOnClickListener { showPage(1) }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(20) else miniCardBg()
                v.translationZ = if (f) dp(10).toFloat() else 0f
                v.animate()
                    .scaleX(if (f) focusScale() else 1f)
                    .scaleY(if (f) focusScale() else 1f)
                    .translationY(if (f) -dp(3).toFloat() else 0f)
                    .setDuration(140)
                    .start()
            }
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(9.5f, true, accent).apply { text = m.league; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        val st = tv(9f, true, if (m.live) gold else dim).apply { text = statusText(m); maxLines = 1 }
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
            outlineProvider = null
            setOnFocusChangeListener { v, f ->
                v.background = if (f) focusBg(22) else slateGlass(210, 22, Color.argb(60, 255, 255, 255), 1)
                v.translationZ = if (f) dp(16).toFloat() else 0f
                v.animate()
                    .scaleX(if (f) focusScale() else 1f)
                    .scaleY(if (f) focusScale() else 1f)
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
        // درع بسيط مؤقت لين يوصل الشعار الحقيقي
        val w = size.toFloat()
        val shield = Path().apply {
            moveTo(w * 0.5f, w * 0.06f)
            lineTo(w * 0.88f, w * 0.2f)
            lineTo(w * 0.84f, w * 0.58f)
            quadTo(w * 0.78f, w * 0.82f, w * 0.5f, w * 0.95f)
            quadTo(w * 0.22f, w * 0.82f, w * 0.16f, w * 0.58f)
            lineTo(w * 0.12f, w * 0.2f)
            close()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, w, Color.argb(200, Color.red(clubColor), Color.green(clubColor), Color.blue(clubColor)),
                Color.argb(140, Color.red(clubColor) / 2, Color.green(clubColor) / 2, Color.blue(clubColor) / 2), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(shield, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.045f
        p.color = Color.argb(150, 255, 255, 255)
        canvas.drawPath(shield, p)
        return BitmapDrawable(resources, bmp)
    }

    private val logoPool = java.util.concurrent.Executors.newFixedThreadPool(4)

    // نصغّر الشعار وقت فك الضغط (الصور الكبيرة كانت تملأ الذاكرة وتختفي الشعارات)
    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        val target = dp(96)
        var sample = 1
        while (o.outWidth / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun fetchBitmap(u: String): Bitmap? = try {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (conn.responseCode in 200..299) conn.inputStream.use { decodeSmall(it.readBytes()) } else null
    } catch (e: Throwable) { null }

    private fun logoFile(key: String) = java.io.File(java.io.File(filesDir, "logos").apply { mkdirs() },
        Integer.toHexString(key.hashCode()) + ".png")

    // الشعار: ESPN أولاً، بعدين رابط ESPN برقم النادي، وإذا ما فيه نبحث في TheSportsDB باسم النادي
    private fun loadLogo(spec: String, iv: ImageView) {
        val cached = logoCache[spec]
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        logoPool.execute {
            // من ذاكرة الجهاز أولاً (يبقى محفوظ بعد إعادة التشغيل)
            val disk = logoFile(spec)
            if (disk.exists()) {
                val b = try { BitmapFactory.decodeFile(disk.absolutePath) } catch (e: Throwable) { null }
                if (b != null) {
                    logoCache[spec] = b
                    ui.post { iv.setImageBitmap(b) }
                    return@execute
                }
            }
            val parts = spec.split("|").filter { it.isNotEmpty() }
            val urls = parts.filter { it.startsWith("http") }.toMutableList()
            val en = parts.firstOrNull { it.startsWith("@") }?.drop(1) ?: ""
            if (en.isNotEmpty()) prefs.getString("badge_$en", null)?.let { urls.add(0, it) }
            var bmp: Bitmap? = null
            for (u in urls.distinct()) { bmp = fetchBitmap(u); if (bmp != null) break }
            if (bmp == null && en.isNotEmpty()) {
                val badge = try { Data.badgeFor(en) } catch (e: Throwable) { null }
                if (badge != null) {
                    bmp = fetchBitmap("$badge/small") ?: fetchBitmap(badge)
                    if (bmp != null) prefs.edit().putString("badge_$en", badge).apply()
                }
            }
            val b = bmp ?: return@execute
            logoCache[spec] = b
            try { disk.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Throwable) { }
            ui.post { iv.setImageBitmap(b) }
        }
    }

    /* ─────────── المفضلة والتطبيقات ─────────── */
    private fun getSavedFavs(): MutableList<String> {
        val saved = prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        return if (saved.isNotEmpty()) saved.toMutableList()
        else allApps.take(6).map { it.activityInfo.packageName }.toMutableList()
    }

    private fun startReorderMode(pkg: String) {
        if (drawerOpen) closeDrawer(false)
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

    // كاش للاسم والأيقونة واللون (الحساب الثقيل يصير مرة وحدة لكل تطبيق بدل كل مرة)
    private class AppVis(val label: String, val icon: Drawable, val banner: Drawable?, val bg: Int, val glow: Int)
    private val labelCache = ConcurrentHashMap<String, String>()
    private val visCache = ConcurrentHashMap<String, AppVis>()
    private var appsSig = ""
    private var gridApps: List<ResolveInfo> = emptyList()
    private var gridGen = 0

    private fun appKey(app: ResolveInfo): String {
        val ai = app.activityInfo
        val stamp = try { java.io.File(ai.applicationInfo.sourceDir).lastModified() } catch (e: Throwable) { 0L }
        return ai.packageName + "@" + stamp
    }

    private fun labelOf(app: ResolveInfo): String {
        val k = appKey(app)
        labelCache[k]?.let { return it }
        val l = app.loadLabel(packageManager).toString()
        labelCache[k] = l
        return l
    }

    private fun visOf(app: ResolveInfo): AppVis {
        val k = appKey(app)
        visCache[k]?.let { return it }
        val pm = packageManager
        val pkg = app.activityInfo.packageName
        val label = labelOf(app)
        val icon = app.loadIcon(pm)
        val banner: Drawable? = try { app.activityInfo.loadBanner(pm) ?: app.activityInfo.applicationInfo.loadBanner(pm) } catch (e: Throwable) { null }
        val bg = if (banner != null) 0xFF141822.toInt() else brandBg(label, pkg, icon)
        val v = AppVis(label, icon, banner, bg, dominant(banner ?: icon))
        visCache[k] = v
        return v
    }

    // نسخة مستقلة من الأيقونة لكل خلية (الـ Drawable الواحد ما يصلح لأكثر من عرض)
    private fun fresh(d: Drawable): Drawable = try { d.constantState?.newDrawable(resources)?.mutate() ?: d } catch (e: Throwable) { d }

    private fun gridCellW(): Int {
        val gc = gridCols()
        return (resources.displayMetrics.widthPixels - dp(52) - dp(44) - dp(28) - gc * dp(20)) / gc
    }

    private fun startGridBuild() {
        val gen = ++gridGen
        allGrid.removeAllViews()
        allGrid.columnCount = gridCols()
        gridApps = allApps.toList()
        ui.postDelayed({ fillGrid(gen, 8) }, 500)
    }

    // يبني خلايا الدرج على دفعات صغيرة في الخلفية، فيفتح الدرج جاهز وبدون تعليق
    private fun fillGrid(gen: Int, batch: Int) {
        if (gen != gridGen || !::allGrid.isInitialized) return
        val gw = gridCellW()
        val from = allGrid.childCount
        val to = if (batch <= 0) gridApps.size else minOf(gridApps.size, from + batch)
        for (k in from until to) {
            allGrid.addView(createCell(gridApps[k], gw, false), GridLayout.LayoutParams().apply {
                width = -2
                height = -2
                setMargins(dp(10), dp(10), dp(10), dp(10))
            })
        }
        if (batch > 0 && to < gridApps.size) ui.postDelayed({ fillGrid(gen, batch) }, 16)
    }

    private fun ensureGrid() { fillGrid(gridGen, 0) }

    private fun loadAppsAndDock(focusPkg: String? = null, force: Boolean = true) {
        val pm = packageManager
        val raw = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
        val sig = raw.joinToString(",") { appKey(it) } + "|" + (prefs.getString("fav_apps", "") ?: "") + "|" +
            favCols() + "|" + gridCols() + "|" + (reorderingPkg ?: "")
        if (!force && sig == appsSig && ::favRow.isInitialized && favRow.childCount > 0) return
        appsSig = sig
        allApps = raw.sortedBy { labelOf(it) }.toMutableList()

        val favPkgs = if (reorderingPkg != null) reorderList else getSavedFavs()
        val sw = resources.displayMetrics.widthPixels
        val fc = favCols()

        favRow.removeAllViews()
        val favList = favPkgs.mapNotNull { pkg -> allApps.find { it.activityInfo.packageName == pkg } }
        val fw = (sw - dp(68) - dp(36) - dp(32) - fc * dp(16)) / fc
        var viewToFocus: View? = null

        for (a in favList) {
            val cell = createCell(a, fw, isDock = true)
            if (a.activityInfo.packageName == focusPkg) {
                viewToFocus = (cell as? ViewGroup)?.getChildAt(0)
            }
            favRow.addView(cell, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })
        }

        if (::drawerCountTv.isInitialized) drawerCountTv.text = nd("${allApps.size} تطبيق")
        startGridBuild()

        if (drawerOpen) {
            ensureGrid()
            val t = tileOf(allGrid, focusPkg) ?: tileOf(allGrid, lastDrawerPkg) ?: firstTile(allGrid)
            t?.post { t.requestFocus() }
        } else if (currentPage == 0) {
            val target = viewToFocus ?: tileOf(favRow, lastDockPkg) ?: firstTile(favRow)
            target?.post { target.requestFocus() }
        }

        // نجهّز الأيقونات والألوان في الخلفية عشان الدرج يفتح فوراً
        val snapshot = allApps.toList()
        thread {
            for (a in snapshot) try { visOf(a) } catch (e: Throwable) { }
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
        val vis = visOf(app)
        val pkg = app.activityInfo.packageName
        val label = vis.label
        val icon = vis.icon
        val banner = vis.banner
        val bg = vis.bg
        val glowC = vis.glow
        val th = (w * 0.54f).toInt()
        val isMovingThis = isDock && (reorderingPkg == pkg)

        val cell = LinearLayout(this).unclip().apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            tag = pkg
        }

        val titleTv = tv(11.5f, true, if (isMovingThis) gold else dim).apply {
            text = if (isMovingThis) "◀  $label  ▶" else label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), 0)
        }

        val idleB = solidCard(Color.TRANSPARENT, 20, Color.argb(45, 255, 255, 255), 1)
        val focB = solidCard(Color.TRANSPARENT, 20, focusRing(), 3)
        val movB = solidCard(Color.TRANSPARENT, 20, gold, 3)
        val borderOverlay = View(this).apply {
            background = if (isMovingThis) solidCard(Color.TRANSPARENT, 20, accent, 3) else idleB
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
                    if (!isDock && drawerOpen) ui.postDelayed({ closeDrawer(false) }, 600)
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
                if (f) { if (isDock) lastDockPkg = pkg else lastDrawerPkg = pkg }
                val movingNow = isDock && (reorderingPkg == pkg)
                borderOverlay.background = when {
                    movingNow -> movB
                    f -> focB
                    else -> idleB
                }
                v.elevation = if (f || movingNow) 14f else 0f
                cell.translationZ = if (f || movingNow) dp(12).toFloat() else 0f
                titleTv.setTextColor(
                    when {
                        movingNow -> gold
                        f -> Color.WHITE
                        else -> dim
                    }
                )
                titleTv.text = if (movingNow) "◀  $label  ▶" else label

                val targetScale = when {
                    movingNow -> focusScale() + 0.03f
                    f -> focusScale()
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
                setImageDrawable(fresh(banner))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val isz = (th * 0.64f).toInt()
            tile.addView(ImageView(this).apply {
                setImageDrawable(fresh(icon))
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

    /* ─────────── الساعة والطقس والصلاة ─────────── */
    private fun updateClock() {
        try {
            val now = LocalTime.now()
            val en = pInt("date_lang", 0) == 1
            greetingTv.text = if (now.hour in 4..11) (if (en) "Good morning" else "صباح الخير") else (if (en) "Good evening" else "مساء الخير")
            clockTv.text = nd(now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)))
            val day = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", dateLoc()))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", dateLoc()))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar)) } catch (e: Throwable) { "" }
            dateLineTv.text = nd(if (h.isNotEmpty()) "$day $m\n$h" else "$day $m")

            if (now.minute != lastMin) {
                lastMin = now.minute
                renderPrayers()
                for ((t, mt) in statusViews) t.text = statusText(mt)
            }
        } catch (e: Throwable) { }
        ui.removeCallbacks(clockTask)
        ui.postDelayed(clockTask, 10000)
    }

    private fun applyWeather() {
        if (!::tempTv.isInitialized) return
        tempTv.text = nd(wTemp)
        if (::weatherIcon.isInitialized) weatherIcon.setState(wCat, wDay)
        weatherStatusTv.text = wStatus
        weatherSubTv.text = nd(wSub)
    }

    private fun refreshFeeds() {
        val c = city()
        ui.removeCallbacks(feedsTask)
        ui.postDelayed(feedsTask, 15 * 60 * 1000L)
        thread {
            try {
                val w = Data.weather(c)
                ui.post {
                    if (Data.encode(c) != Data.encode(city())) return@post
                    wTemp = "${w.temp}°"
                    wStatus = "${w.status}"
                    wCat = w.cat
                    wDay = w.isDay
                    wSub = "المحسوسة ${w.feels}° • الرطوبة ${w.humidity}%\nالرياح ${w.wind} كم/س"
                    applyWeather()
                }
            } catch (e: Throwable) { }
            try {
                val p = Data.prayers(c)
                ui.post {
                    if (Data.encode(c) != Data.encode(city())) return@post
                    prayersData = p.list
                    prayerZone = try { if (p.tz.isNotEmpty()) ZoneId.of(p.tz) else null } catch (e: Throwable) { null }
                    renderPrayers()
                }
            } catch (e: Throwable) { }
        }
    }

    private fun renderPrayers() {
        if (prayersData.isEmpty() || !::prayerLayout.isInitialized) return
        // الوقت الحالي بتوقيت المدينة المختارة (مهم للمدن خارج السعودية)
        val now = prayerZone?.let { LocalTime.now(it) } ?: LocalTime.now()
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
                if (on) background = solidCard(Color.argb(50, 45, 212, 191), 8, accent, 1)
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
