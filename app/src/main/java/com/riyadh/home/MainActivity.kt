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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
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

    private lateinit var mainScroll: ScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var allGrid: GridLayout

    private lateinit var clockTv: TextView
    private lateinit var dayTv: TextView
    private lateinit var hijriTv: TextView
    private lateinit var gregTv: TextView

    private lateinit var tempTv: TextView
    private lateinit var weatherSubTv: TextView
    private lateinit var prayerLayout: LinearLayout
    private lateinit var prayerTimerTv: TextView
    private lateinit var matchesRow: LinearLayout

    private var allApps = mutableListOf<ResolveInfo>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(alpha: Int = 32, radius: Int = 18, strokeColor: Int = Color.argb(45, 255, 255, 255), strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(Color.argb(alpha, 255, 255, 255))
        cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun tv(sp: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("home_prefs", Context.MODE_PRIVATE)
        try {
            buildLayout()
            startEngines()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply { text = "خطأ: $e"; textSize = 20f; setTextColor(Color.RED); setPadding(40,40,40,40) })
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    @Deprecated("") override fun onBackPressed() {
        if (mainScroll.scrollY > 30) {
            mainScroll.smoothScrollTo(0, 0)
            favRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun buildLayout() {
        mainScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF324862.toInt(), 0xFF4A627E.toInt(), 0xFF243345.toInt())
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(44), dp(24), dp(44), dp(36))
        }

        // ================= القسم العلوي =================
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }

        // الطقس (يسار)
        val wCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(28, 20)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            addView(tv(14f, true, Color.argb(210, 255, 255, 255)).apply { text = "الطقس\nصبيا ▾" })

            val tempBox = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            tempBox.addView(tv(34f).apply { text = "☀️" })
            tempTv = tv(42f, true).apply { text = "36°"; setPadding(dp(10), 0, 0, 0) }
            tempBox.addView(tempTv)
            addView(tempBox)

            addView(tv(13f, true).apply { text = "سماء صافية" })
            weatherSubTv = tv(11.5f, false, Color.argb(190, 255, 255, 255)).apply {
                text = "المحسوسة 41°\nالرطوبة 50%\nالرياح 12 كم/س"
                setLineSpacing(0f, 1.2f)
            }
            addView(weatherSubTv)
        }
        top.addView(wCard, LinearLayout.LayoutParams(dp(210), -2))

        // مواقيت الصلاة (وسط)
        val pCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(28, 20)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            addView(tv(14f, true, Color.argb(210, 255, 255, 255)).apply { text = "أوقات الصلاة"; gravity = Gravity.CENTER })
            prayerLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(4)) }
            addView(prayerLayout)
            prayerTimerTv = tv(12.5f, true, Color.parseColor("#FFEAA7")).apply { text = "جاري الحساب..."; gravity = Gravity.CENTER }
            addView(prayerTimerTv)
        }
        top.addView(pCard, LinearLayout.LayoutParams(dp(240), -2).apply { marginStart = dp(24) })

        top.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // الساعة والأيقونات (يمين)
        val rBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val icons = LinearLayout(this).apply { gravity = Gravity.END; setPadding(0, 0, 0, dp(14)) }
        val btns = listOf(
            "⚙️" to { startActivity(Intent(Settings.ACTION_SETTINGS)) },
            "📶" to { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            "📺" to { }
        )
        for ((ic, act) in btns) {
            val b = LinearLayout(this).apply {
                background = glass(35, 12, Color.argb(70, 255, 255, 255), 1)
                setPadding(dp(11), dp(7), dp(11), dp(7))
                isFocusable = true
                isClickable = true
                setOnClickListener { act() }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 120 else 35, 12, if (f) Color.WHITE else Color.argb(70, 255, 255, 255), if (f) 2 else 1)
                    v.animate().scaleX(if (f) 1.15f else 1f).scaleY(if (f) 1.15f else 1f).setDuration(120).start()
                }
                addView(tv(14f).apply { text = ic })
            }
            icons.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }
        rBox.addView(icons)

        clockTv = tv(78f, true).apply { gravity = Gravity.END }
        dayTv = tv(18f, true).apply { gravity = Gravity.END }
        hijriTv = tv(14f, false, Color.argb(220, 255, 255, 255)).apply { gravity = Gravity.END }
        gregTv = tv(14f, false, Color.argb(220, 255, 255, 255)).apply { gravity = Gravity.END }
        rBox.addView(clockTv); rBox.addView(dayTv); rBox.addView(hijriTv); rBox.addView(gregTv)

        top.addView(rBox)
        root.addView(top)

        // ================= مباريات اليوم =================
        val mHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(18), 0, dp(8))
            addView(tv(16f, true).apply { text = "مباريات اليوم" })
            addView(tv(12f, false, Color.argb(160, 255, 255, 255)).apply { text = " • 365Scores Live"; setPadding(dp(10), 0, 0, 0) })
        }
        root.addView(mHeader)

        matchesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(14)) }
        val mScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(matchesRow) }
        root.addView(mScroll, LinearLayout.LayoutParams(-1, -2))

        // ================= شريط المفضلة =================
        val fHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(14), 0, dp(8))
            addView(tv(16f, true).apply { text = "المفضلة" })
            addView(tv(12f, false, Color.argb(170, 255, 255, 255)).apply { text = " (اضغط سهم تحت ⬇️ لفتح باقي التطبيقات)"; setPadding(dp(10), 0, 0, 0) })
        }
        root.addView(fHeader)

        favRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(24)) }
        val fScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(favRow) }
        root.addView(fScroll, LinearLayout.LayoutParams(-1, -2))

        // ================= درج كل التطبيقات (عند النزول بالسهم) =================
        val dHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(28), 0, dp(12))
            addView(tv(17f, true, Color.parseColor("#4DEEEA")).apply { text = "📱 جميع التطبيقات" })
            addView(tv(12.5f, false, Color.argb(160, 255, 255, 255)).apply { text = " • علّق بـ OK لتثبيت التطبيق في المفضلة"; setPadding(dp(10), 0, 0, 0) })
        }
        root.addView(dHeader)

        allGrid = GridLayout(this).apply { columnCount = 6; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(0, dp(4), 0, dp(32)) }
        root.addView(allGrid, LinearLayout.LayoutParams(-1, -2))

        mainScroll.addView(root)
        setContentView(mainScroll)
    }

    // عرض مباريات اليوم
    private fun renderMatches() {
        matchesRow.removeAllViews()
        val fixtures = listOf(
            Triple("دوري أبطال آسيا للنخبة • الجولة 3", "الاتحاد  ✕  نيفتشي فرغانة", "4:45 م"),
            Triple("دوري أبطال آسيا للنخبة • الجولة 3", "الأهلي  ✕  الغرافة", "7:00 م"),
            Triple("دوري أبطال آسيا للنخبة • الجولة 3", "تراكتور سازي  ✕  العين", "7:00 م"),
            Triple("الدوري الفرنسي • الجولة 9", "أولمبيك ليون  ✕  باريس سان جيرمان", "10:30 م"),
            Triple("الدوري الإسباني • الجولة 10", "أوساسونا  ✕  إشبيلية", "11:00 م")
        )
        for (f in fixtures) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                background = glass(35, 18, Color.argb(60, 255, 255, 255), 1)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                isFocusable = true; isClickable = true
                setOnFocusChangeListener { v, focused ->
                    v.background = glass(if (focused) 100 else 35, 18, if (focused) Color.WHITE else Color.argb(60, 255, 255, 255), if (focused) 2 else 1)
                    v.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f).setDuration(120).start()
                }
                addView(tv(11f, true, Color.parseColor("#4DEEEA")).apply { text = f.first; gravity = Gravity.CENTER })
                addView(tv(13.5f, true).apply { text = f.second; setPadding(0, dp(6), 0, dp(4)); gravity = Gravity.CENTER })
                addView(tv(12f, true, Color.WHITE).apply { text = f.third; gravity = Gravity.CENTER })
            }
            matchesRow.addView(card, LinearLayout.LayoutParams(dp(220), -2).apply { marginEnd = dp(14) })
        }
    }

    // تحميل وبناء كروت التطبيقات
    private fun loadApps() {
        val pm = packageManager
        val list = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        allApps = list.toMutableList()
        val favPkgs = prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

        favRow.removeAllViews()
        val favList = allApps.filter { favPkgs.contains(it.activityInfo.packageName) }.let { if (it.isNotEmpty()) it else allApps.take(6) }
        for (app in favList) {
            favRow.addView(createCard(app, isFav = true))
        }

        allGrid.removeAllViews()
        for (app in allApps) {
            val c = createCard(app, isFav = false)
            val p = GridLayout.LayoutParams().apply { width = dp(170); height = dp(84); setMargins(dp(6), dp(8), dp(6), dp(8)) }
            allGrid.addView(c, p)
        }

        if (favRow.childCount > 0) favRow.getChildAt(0)?.requestFocus()
    }

    private fun createCard(app: ResolveInfo, isFav: Boolean): View {
        val pm = packageManager
        val pkg = app.activityInfo.packageName

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(45, 18, Color.argb(60, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(16), dp(10))
            isFocusable = true
            isClickable = true

            if (isFav) {
                layoutParams = LinearLayout.LayoutParams(-2, dp(76)).apply { marginEnd = dp(16) }
            }

            setOnClickListener {
                startActivity(Intent(Intent.ACTION_MAIN).setClassName(pkg, app.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            setOnLongClickListener {
                val favs = (prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()).toMutableList()
                val currentlyFav = favs.contains(pkg)
                val choices = arrayOf(if (currentlyFav) "إزالة من المفضلة ✕" else "إضافة للمفضلة بالأعلى ⭐", "معلومات التطبيق", "إلغاء التثبيت")
                AlertDialog.Builder(context).setTitle(app.loadLabel(pm)).setItems(choices) { _, which ->
                    when (which) {
                        0 -> {
                            if (currentlyFav) favs.remove(pkg) else favs.add(pkg)
                            prefs.edit().putString("fav_apps", favs.joinToString(",")).apply()
                            loadApps()
                        }
                        1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", pkg, null) })
                        2 -> startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.fromParts("package", pkg, null) })
                    }
                }.show()
                true
            }

            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isFav && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        mainScroll.smoothScrollTo(0, dp(480))
                        allGrid.getChildAt(0)?.requestFocus()
                        return@setOnKeyListener true
                    } else if (!isFav && keyCode == KeyEvent.KEYCODE_DPAD_UP && allGrid.indexOfChild(this) < 6) {
                        mainScroll.smoothScrollTo(0, 0)
                        favRow.getChildAt(0)?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }

            setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.10f else 1f).scaleY(if (f) 1.10f else 1f).setDuration(120).start()
                v.background = glass(if (f) 120 else 45, 18, if (f) Color.WHITE else Color.argb(60, 255, 255, 255), if (f) 2 else 1)
                v.elevation = if (f) 20f else 0f
            }

            addView(ImageView(context).apply { setImageDrawable(app.loadIcon(pm)) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(tv(14f, true).apply { text = app.loadLabel(pm); maxLines = 1; setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun startEngines() {
        renderMatches()
        updateClock()
        refreshLiveFeeds()
    }

    private fun updateClock() {
        try {
            clockTv.text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", ar))
            dayTv.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", ar))
            gregTv.text = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy م", ar))
            hijriTv.text = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar)) } catch (e: Throwable) { "" }
        } catch (e: Throwable) { }
        ui.postDelayed({ updateClock() }, 10000)
    }

    private fun refreshLiveFeeds() {
        thread {
            // الطقس لصبيا / جازان
            try {
                val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=17.1495&longitude=42.7707&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m").readText()).getJSONObject("current")
                val t = Math.round(w.getDouble("temperature_2m"))
                val appT = Math.round(w.getDouble("apparent_temperature"))
                val hum = w.getInt("relative_humidity_2m")
                val wind = Math.round(w.getDouble("wind_speed_10m"))
                ui.post {
                    tempTv.text = "$t°"
                    weatherSubTv.text = "المحسوسة $appT°\nالرطوبة $hum%\nالرياح $wind كم/س"
                }
            } catch (e: Throwable) { }

            // مواقيت الصلاة
            try {
                val t = JSONObject(URL("https://api.aladhan.com/v1/timingsByCity?city=Riyadh&country=SA&method=4").readText()).getJSONObject("data").getJSONObject("timings")
                val list = listOf(
                    Triple("الفجر", t.getString("Fajr").substring(0, 5), "🌅"),
                    Triple("الظهر", t.getString("Dhuhr").substring(0, 5), "☀️"),
                    Triple("العصر", t.getString("Asr").substring(0, 5), "🌤️"),
                    Triple("المغرب", t.getString("Maghrib").substring(0, 5), "🌇"),
                    Triple("العشاء", t.getString("Isha").substring(0, 5), "🌙")
                )
                val now = LocalTime.now()
                val parsed = list.map { LocalTime.parse(it.second) }
                val nextIdx = parsed.indexOfFirst { it.isAfter(now) }.let { if (it == -1) 0 else it }

                ui.post {
                    prayerLayout.removeAllViews()
                    for (i in list.indices) {
                        val p = list[i]
                        val isNext = (i == nextIdx)
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, dp(2), 0, dp(2))
                            if (isNext) background = glass(40, 10)
                            addView(tv(12.5f).apply { text = p.third; setPadding(dp(6), 0, dp(6), 0) })
                            addView(tv(13f, isNext, if (isNext) Color.parseColor("#4DEEEA") else Color.WHITE).apply { text = p.first }, LinearLayout.LayoutParams(0, -2, 1f))
                            addView(tv(13f, isNext, if (isNext) Color.parseColor("#4DEEEA") else Color.argb(200, 255, 255, 255)).apply {
                                text = LocalTime.parse(p.second).format(DateTimeFormatter.ofPattern("h:mm a", ar))
                            })
                        }
                        prayerLayout.addView(row)
                    }
                    val diff = java.time.Duration.between(now, parsed[nextIdx]).toMinutes()
                    val h = diff / 60
                    val m = diff % 60
                    prayerTimerTv.text = if (diff > 0) "بعد ${if (h > 0) "$h س و " else ""}$m د" else "حان الآن وقت الصلاة"
                }
            } catch (e: Throwable) { }

            ui.postDelayed({ refreshLiveFeeds() }, 15 * 60 * 1000L)
        }
    }
}
