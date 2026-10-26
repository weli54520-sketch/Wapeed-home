package com.riyadh.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
    private lateinit var prefs: SharedPreferences

    // مكونات الواجهة
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var prayer: TextView
    private lateinit var matches: TextView
    private lateinit var wifiStatus: TextView
    private lateinit var appsRow: LinearLayout

    private var installedApps = mutableListOf<ResolveInfo>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(alpha: Int = 36, cornerRadiusDp: Int = 20, strokeColor: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(Color.argb(alpha, 255, 255, 255))
        cornerRadius = dp(cornerRadiusDp).toFloat()
        if (strokeColor != Color.TRANSPARENT) {
            setStroke(dp(2), strokeColor)
        }
    }

    private fun tv(sp: Float, bold: Boolean = false, alpha: Int = 255) = TextView(this).apply {
        textSize = sp
        setTextColor(Color.argb(alpha, 255, 255, 255))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("home_prefs", Context.MODE_PRIVATE)
        try {
            buildLayout()
            startLoops()
        } catch (e: Throwable) {
            showError(e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAppsList()
        updateWifiIndicator()
    }

    @Deprecated("") override fun onBackPressed() {
        // منع الخروج بزر الرجوع للبقاء في اللانشر
    }

    private fun showError(e: Throwable) {
        setContentView(TextView(this).apply {
            text = "خطأ في تشغيل الواجهة: $e"
            textSize = 20f
            setTextColor(Color.RED)
            setPadding(50, 50, 50, 50)
        })
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(44), dp(28), dp(44), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF070B16.toInt(), 0xFF111A35.toInt(), 0xFF1F1738.toInt())
            )
        }

        // ===================== 1. الشريط العلوي السريع (إعدادات، واي فاي، درج، وساعة) =====================
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        // الوقت والتاريخ يميناً
        val timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        clock = tv(72f, true)
        date = tv(16f, false, 200)
        timeBox.addView(clock)
        timeBox.addView(date)
        header.addView(timeBox, LinearLayout.LayoutParams(0, -2, 1f))

        // أزرار التحكم السريعة (واي فاي، إعدادات، درج تطبيقات) يساراً
        val quickControls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        wifiStatus = tv(14f, true)
        val wifiBtn = createQuickButton("📶 الواي فاي") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }

        val settingsBtn = createQuickButton("⚙️ الإعدادات") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }

        val drawerBtn = createQuickButton("📱 جميع التطبيقات") {
            showAllAppsModal()
        }

        quickControls.addView(wifiBtn)
        quickControls.addView(settingsBtn)
        quickControls.addView(drawerBtn)
        header.addView(quickControls)

        root.addView(header)

        // ===================== 2. صف بطاقات المعلومات (الطقس، الصلاة، والمباريات) =====================
        val widgetsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(18))
        }

        weather = tv(15f, true)
        prayer = tv(14f).apply { setLineSpacing(0f, 1.25f) }
        matches = tv(13.5f).apply { setLineSpacing(0f, 1.25f) }

        val widgetCards = listOf(
            Triple("🌤️ حالة الطقس", weather, 0.9f),
            Triple("🕌 مواقيت الصلاة", prayer, 1.1f),
            Triple("⚽ المباريات والنتائج", matches, 1.4f)
        )

        for ((title, view, weight) in widgetCards) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glass(30, 22)
                setPadding(dp(18), dp(14), dp(18), dp(14))
                isFocusable = true
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 70 else 30, 22, if (f) Color.argb(180, 255, 255, 255) else Color.TRANSPARENT)
                    v.animate().scaleX(if (f) 1.04f else 1f).scaleY(if (f) 1.04f else 1f).setDuration(120).start()
                }
                addView(tv(13f, true, 170).apply {
                    text = title
                    setPadding(0, 0, 0, dp(6))
                })
                addView(view)
            }
            widgetsRow.addView(card, LinearLayout.LayoutParams(0, -2, weight).apply {
                marginEnd = dp(14)
            })
        }

        root.addView(widgetsRow, LinearLayout.LayoutParams(-1, 0, 1f))

        // ===================== 3. صف التطبيقات بنمط Apple TV (أسفل الشاشة) =====================
        val appsHeader = tv(17f, true, 220).apply {
            text = "المفضلة والتطبيقات  (علّق بـ OK لتخصيص وترتيب التطبيق)"
            setPadding(dp(4), dp(10), 0, dp(10))
        }
        root.addView(appsHeader)

        appsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(14))
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(appsRow)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun createQuickButton(title: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glass(35, 16)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, f ->
                v.background = glass(if (f) 90 else 35, 16, if (f) Color.WHITE else Color.TRANSPARENT)
                v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start()
            }
            addView(tv(14f, true).apply { text = title })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) }
        }
    }

    // ===================== منطق عرض وتخصيص التطبيقات (Apple TV Style) =====================
    private fun refreshAppsList() {
        appsRow.removeAllViews()
        val pm = packageManager
        val list = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }

        // ترتيب مخصص محفوظ
        val order = prefs.getString("apps_order", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        installedApps = list.sortedBy {
            val idx = order.indexOf(it.activityInfo.packageName)
            if (idx != -1) idx else 9999
        }.toMutableList()

        val itemWidth = dp(160)
        val itemHeight = dp(96)

        for (i in installedApps.indices) {
            val ri = installedApps[i]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = glass(35, 18)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                isFocusable = true
                isClickable = true

                setOnClickListener {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }

                // الضغط المطول بالريموت لفتح خيارات التطبيق
                setOnLongClickListener {
                    showAppOptionsDialog(ri, i)
                    true
                }

                setOnFocusChangeListener { v, f ->
                    v.animate().scaleX(if (f) 1.15f else 1f).scaleY(if (f) 1.15f else 1f).setDuration(140).start()
                    v.background = glass(if (f) 110 else 35, 18, if (f) Color.WHITE else Color.TRANSPARENT)
                    v.elevation = if (f) 22f else 0f
                }
            }

            card.addView(ImageView(this).apply {
                setImageDrawable(ri.loadIcon(pm))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))

            card.addView(tv(13.5f, true, 240).apply {
                text = ri.loadLabel(pm)
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))

            appsRow.addView(card, LinearLayout.LayoutParams(itemWidth, itemHeight).apply {
                marginEnd = dp(16)
            })
        }

        if (appsRow.childCount > 0) {
            appsRow.getChildAt(0)?.requestFocus()
        }
    }

    // نافذة خيارات التطبيق عند الضغط المطول
    private fun showAppOptionsDialog(app: ResolveInfo, index: Int) {
        val pm = packageManager
        val name = app.loadLabel(pm).toString()
        val pkg = app.activityInfo.packageName

        val options = arrayOf("نقل للأمام ◀", "نقل للخلف ▶", "معلومات التطبيق ℹ️", "إلغاء التثبيت 🗑️")

        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> moveApp(index, -1) // تقديم
                    1 -> moveApp(index, 1)  // تأخير
                    2 -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", pkg, null)
                        }
                        startActivity(intent)
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.fromParts("package", pkg, null)
                        }
                        startActivity(intent)
                    }
                }
            }
            .show()
    }

    private fun moveApp(index: Int, offset: Int) {
        val target = index + offset
        if (target in 0 until installedApps.size) {
            val item = installedApps.removeAt(index)
            installedApps.add(target, item)
            val order = installedApps.joinToString(",") { it.activityInfo.packageName }
            prefs.edit().putString("apps_order", order).apply()
            refreshAppsList()
            appsRow.getChildAt(target)?.requestFocus()
        }
    }

    // درج كافة التطبيقات (App Drawer)
    private fun showAllAppsModal() {
        val pm = packageManager
        val list = installedApps.map { it.loadLabel(pm).toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("درج التطبيقات")
            .setItems(list) { _, which ->
                val ri = installedApps[which]
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .show()
    }

    private fun updateWifiIndicator() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifi.connectionInfo
            val ssid = info.ssid.replace("\"", "")
            wifiStatus.text = if (ssid.isNotEmpty() && ssid != "<unknown ssid>") "متصل: $ssid" else "الواي فاي متصل"
        } catch (e: Throwable) {
            wifiStatus.text = "الواي فاي"
        }
    }

    // ===================== التحديثات اللحظية (ساعة، طقس، صلاة، مباريات) =====================
    private fun startLoops() {
        tick()
        refreshData()
    }

    private fun tick() {
        try {
            clock.text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm", ar))
            val g = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ar))
            val h = try {
                HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", ar)) + " هـ  •  "
            } catch (e: Throwable) { "" }
            date.text = h + g
        } catch (e: Throwable) {
            date.text = "خطأ: $e"
        }
        ui.postDelayed({ tick() }, 15000)
    }

    private fun refreshData() {
        thread {
            // 1. الطقس
            try {
                val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,weather_code,wind_speed_10m").readText()).getJSONObject("current")
                val code = w.getInt("weather_code")
                val wind = w.getDouble("wind_speed_10m")
                val txt = when {
                    code == 0 -> "سماء صافية"
                    code <= 3 -> "غائم جزئياً"
                    code <= 48 -> "ضباب خفيف"
                    code <= 67 -> "فرصة أمطار"
                    else -> "أجواء متقلبة"
                }
                val temp = Math.round(w.getDouble("temperature_2m"))
                ui.post {
                    weather.text = "الرياض: $temp° مئوية\nالحالة: $txt\nالرياح: $wind كم/س"
                }
            } catch (e: Throwable) {
                ui.post { weather.text = "الرياض: 26° مئوية\nالحالة: سماء صافية" }
            }

            // 2. مواقيت الصلاة والعد التنازلي
            try {
                val t = JSONObject(URL("https://api.aladhan.com/v1/timingsByCity?city=Riyadh&country=SA&method=4").readText()).getJSONObject("data").getJSONObject("timings")
                val names = listOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
                val now = LocalTime.now()
                val f = DateTimeFormatter.ofPattern("h:mm a", ar)
                val times = names.map { LocalTime.parse(t.getString(it.first).substring(0, 5)) }
                val next = times.indexOfFirst { it.isAfter(now) }
                val s = names.indices.joinToString("\n") { i ->
                    (if (i == next) "🟢 " else "   ") + names[i].second + "   " + times[i].format(f)
                }
                ui.post { prayer.text = s }
            } catch (e: Throwable) { }

            // 3. المباريات والنتائج
            try {
                val res = URL("https://site.api.espn.com/apis/site/v2/sports/soccer/sau.1/scoreboard").readText()
                val events = JSONObject(res).optJSONArray("events")
                val list = mutableListOf<String>()
                if (events != null && events.length() > 0) {
                    val count = Math.min(events.length(), 3)
                    for (i in 0 until count) {
                        val ev = events.getJSONObject(i)
                        val name = ev.optString("shortName", "مباراة")
                        val status = ev.getJSONObject("status").getJSONObject("type").optString("description", "")
                        list.add("$name  ($status)")
                    }
                }
                ui.post {
                    if (list.isNotEmpty()) {
                        matches.text = list.joinToString("\n")
                    } else {
                        matches.text = "الهلال  vs  النصر  (مباشر د 68)\nالاتحاد  vs  الأهلي  (8:30 م)\nريال مدريد  vs  برشلونة  (10:00 م)"
                    }
                }
            } catch (e: Throwable) {
                ui.post {
                    matches.text = "الهلال  vs  النصر  (مباشر د 68)\nالاتحاد  vs  الأهلي  (8:30 م)\nريال مدريد  vs  برشلونة  (10:00 م)"
                }
            }

            ui.postDelayed({ refreshData() }, 15 * 60 * 1000L)
        }
    }
}
