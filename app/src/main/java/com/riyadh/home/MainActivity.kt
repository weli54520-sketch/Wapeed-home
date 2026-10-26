package com.riyadh.home

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val ar = Locale("ar", "SA")
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var prayer: TextView
    private lateinit var row: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(a: Int, r: Int) = GradientDrawable().apply {
        setColor(Color.argb(a, 255, 255, 255)); cornerRadius = dp(r).toFloat()
    }

    private fun tv(sp: Float, bold: Boolean = false, alpha: Int = 255) = TextView(this).apply {
        textSize = sp; setTextColor(Color.argb(alpha, 255, 255, 255))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun showError(e: Throwable) {
        setContentView(TextView(this).apply {
            text = "خطأ: $e"; textSize = 24f; setTextColor(Color.WHITE); setPadding(60, 60, 60, 60)
        })
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { build() } catch (e: Throwable) { showError(e) }
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(56), dp(40), dp(56), dp(32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0B1026.toInt(), 0xFF1B2A5C.toInt(), 0xFF3B2A6B.toInt())
            )
        }
        val top = LinearLayout(this).apply { gravity = Gravity.TOP }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = tv(88f, true); date = tv(20f, false, 200)
        left.addView(clock); left.addView(date)
        top.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        weather = tv(22f, true)
        prayer = tv(17f).apply { setLineSpacing(0f, 1.25f) }
        for (v in listOf(weather, prayer)) {
            val card = LinearLayout(this).apply {
                background = glass(36, 28); setPadding(dp(24), dp(16), dp(24), dp(16))
            }
            card.addView(v)
            top.addView(card, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(20) })
        }
        root.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
        row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        val sv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(row) }
        root.addView(sv, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        tick(); refresh()
    }

    override fun onResume() {
        super.onResume()
        try { loadApps() } catch (e: Throwable) { if (::date.isInitialized) date.text = "خطأ: $e" }
    }

    @Deprecated("") override fun onBackPressed() {}

    private fun tick() {
        try {
            clock.text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm", ar))
            val g = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ar))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", ar)) + " هـ  •  " } catch (e: Throwable) { "" }
            date.text = h + g
        } catch (e: Throwable) { date.text = "خطأ: $e" }
        ui.postDelayed({ tick() }, 20000)
    }

    private fun refresh(
