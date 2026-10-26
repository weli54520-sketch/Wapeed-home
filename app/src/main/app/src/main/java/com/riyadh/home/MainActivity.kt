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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
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

    override fun onResume() { super.onResume(); loadApps() }
    @Deprecated("") override fun onBackPressed() {}

    private fun tick() {
        clock.text = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm", ar))
        val g = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ar))
        val h = HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", ar))
        date.text = "$h هـ  •  $g"
        ui.postDelayed({ tick() }, 20000)
    }

    private fun refresh() {
        thread {
            try {
                val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code").readText()).getJSONObject("current")
                val code = w.getInt("weather_code")
                val txt = when { code == 0 -> "صافي"; code <= 3 -> "غائم جزئياً"; code <= 48 -> "ضباب"; code <= 67 -> "أمطار"; code <= 82 -> "زخات"; else -> "عواصف" }
                ui.post { weather.text = "الرياض  ${Math.round(w.getDouble("temperature_2m"))}°  $txt" }
            } catch (e: Exception) { }
            try {
                val t = JSONObject(URL("https://api.aladhan.com/v1/timingsByCity?city=Riyadh&country=SA&method=4").readText()).getJSONObject("data").getJSONObject("timings")
                val names = listOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
                val now = LocalTime.now()
                val f = DateTimeFormatter.ofPattern("h:mm a", ar)
                val times = names.map { LocalTime.parse(t.getString(it.first).substring(0, 5)) }
                val next = times.indexOfFirst { it.isAfter(now) }
                val s = names.indices.joinToString("\n") { i -> (if (i == next) "◂ " else "   ") + names[i].second + "   " + times[i].format(f) }
                ui.post { prayer.text = s }
            } catch (e: Exception) { }
            ui.postDelayed({ refresh() }, 30 * 60 * 1000L)
        }
    }

    private fun loadApps() {
        row.removeAllViews()
        val pm = packageManager
        val list = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
        for (ri in list) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = glass(30, 26); setPadding(dp(14), dp(18), dp(14), dp(14))
                isFocusable = true; isClickable = true
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_MAIN).setClassName(ri.activityInfo.packageName, ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                setOnFocusChangeListener { v, f ->
                    v.animate().scaleX(if (f) 1.14f else 1f).scaleY(if (f) 1.14f else 1f).setDuration(160).start()
                    v.background = glass(if (f) 90 else 30, 26)
                    v.elevation = if (f) 24f else 0f
                }
            }
            card.addView(ImageView(this).apply { setImageDrawable(ri.loadIcon(pm)) }, LinearLayout.LayoutParams(dp(84), dp(84)))
            card.addView(tv(14f, false, 230).apply { text = ri.loadLabel(pm); maxLines = 1; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) },
                LinearLayout.LayoutParams(dp(110), -2))
            row.addView(card, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(22); marginStart = dp(6) })
        }
        row.getChildAt(0)?.requestFocus()
    }
}
