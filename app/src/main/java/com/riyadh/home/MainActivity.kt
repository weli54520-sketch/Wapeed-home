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

    // عناصر الوقت والتاريخ
    private lateinit var clockTv: TextView
    private lateinit var dayTv: TextView
    private lateinit var datesTv: TextView

    // الطقس والصلاة
    private lateinit var weatherTempTv: TextView
    private lateinit var weatherStatusTv: TextView
    private lateinit var weatherSubTv: TextView
    private lateinit var prayerRow: LinearLayout
    private lateinit var nextPrayerTimerTv: TextView

    // صفوف المباريات والتطبيقات
    private lateinit var matchesRow: LinearLayout
    private lateinit var appsRow: LinearLayout

    private var installedApps = mutableListOf<ResolveInfo>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(alpha: Int = 28, radius: Int = 22, strokeColor: Int = Color.argb(35, 255, 255, 255), strokeDp: Int = 1) = GradientDrawable().apply {
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
            buildEliteCinemaLayout()
            startDataEngines()
        } catch (e: Throwable) {
            showError(e)
        }
    }

    override fun onResume() {
        super.onResume()
        loadAppsShelf()
    }

    @Deprecated("") override fun onBackPressed() {}

    private fun showError(e: Throwable) {
        setContentView(TextView(this).apply {
            text = "خطأ: $e"; textSize = 18f; setTextColor(Color.RED); setPadding(40, 40, 40, 40)
        })
    }

    private fun buildEliteCinemaLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(44), dp(24), dp(44), dp(20))
            // تدرج لوني فخم مستوحى من صالات السينما المنزلية الفاخرة
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF060913.toInt(), 0xFF0D1424.toInt(), 0xFF141226.toInt())
            )
        }

        // ==========================================
        // 1. الشريط العلوي (الساعة، الطقس، والصلاة بتوزيع أفقي راقي)
        // ==========================================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        // --- أ: الوقت الفخم والتاريخ المدمج ---
        val timeHub = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockTv = tv(76f, true)
        dayTv = tv(17f, true, Color.parseColor("#E0E6ED"))
        datesTv = tv(13.5f, false, Color.argb(175, 255, 255, 255))
        timeHub.addView(clockTv)
        timeHub.addView(dayTv)
        timeHub.addView(datesTv)
        header.addView(timeHub)

        // فاصل مرن
        header.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // --- ب: كبسولة الطقس المينيمال ---
        val weatherCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(25, 20)
            setPadding(dp(18), dp(12), dp(20), dp(12))

            val iconBox = tv(34f).apply { text = "☀️"; setPadding(0, 0, dp(12), 0) }
            val textBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                weatherTempTv = tv(28f, true)
                weatherStatusTv = tv(13f, true, Color.parseColor("#4DEEEA"))
                weatherSubTv = tv(11f, false, Color.argb(160, 255, 255, 255))
                addView(weatherTempTv)
                addView(weatherStatusTv)
                addView(weatherSubTv)
            }
            addView(iconBox)
            addView(textBox)
        }
        header.addView(weatherCapsule, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })

        // --- ج: شريط مواقيت الصلاة بتأثير زجاجي أنيق ---
        val prayerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = glass(25, 20)
            setPadding(dp(18), dp(10), dp(18), dp(10))

            prayerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            nextPrayerTimerTv = tv(12.5f, true, Color.parseColor("#FFD166")).apply {
                text = "جاري حساب الأذان..."
                setPadding(0, dp(6), 0, 0)
            }
            addView(prayerRow)
            addView(nextPrayerTimerTv)
        }
        header.addView(prayerCard, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(16) })

        // --- د: أزرار التحكم السريعة (واي فاي، إعدادات، درج) ---
        val controlsRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val btns = listOf(
            "⚙️" to { startActivity(Intent(Settings.ACTION_SETTINGS)) },
            "📶" to { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            "📱" to { openFullAppDrawer() }
        )
        for ((icon, act) in btns) {
            val b = LinearLayout(this).apply {
                background = glass(35, 14)
                setPadding(dp(11), dp(7), dp(11), dp(7))
                isFocusable = true
                isClickable = true
                setOnClickListener { act() }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 100 else 35, 14, if (f) Color.WHITE else Color.TRANSPARENT, if (f) 2 else 0)
                    v.animate().scaleX(if (f) 1.15f else 1f).scaleY(if (f) 1.15f else 1f).setDuration(110).start()
                }
                addView(tv(14f).apply { text = icon })
            }
            controlsRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) })
        }
        header.addView(controlsRow)
        root.addView(header)

        // ==========================================
        // 2. مركز المباريات السينمائي (Live & Upcoming Sports Arena)
        // ==========================================
        val sportsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), 0, dp(8))
            addView(tv(15f, true, Color.parseColor("#E0E6ED")).apply { text = "🔥 مواجهات اليوم ونتائج البث المباشر" })
        }
        root.addView(sportsHeader)

        matchesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val sportsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(matchesRow)
        }
        root.addView(sportsScroll, LinearLayout.LayoutParams(-1, -2))

        // مساحة فارغة توازن وتدفع التطبيقات للأسفل
        root.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // ==========================================
        // 3. رف التطبيقات السينمائي (Apple TV Cinematic Shelf)
        // ==========================================
        val appsLabel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(8))
            addView(tv(16f, true, Color.WHITE).apply { text = "التطبيقات والوسائط" })
            addView(tv(12f, false, Color.argb(140, 255, 255, 255)).apply {
                text = " • علّق بـ OK للترتيب والخيارات"
                setPadding(dp(8), 0, 0, 0)
            })
        }
        root.addView(appsLabel)

        appsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        val appsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(appsRow)
        }
        root.addView(appsScroll, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    // ==========================================
    // بطاقات المباريات بتصميم زجاجي عريض وفاخر
    // ==========================================
    private fun renderMatchesShelf() {
        matchesRow.removeAllViews()

        val fixtureList = listOf(
            Fixture("دوري روشن للمحترفين", "الجولة 8", "الهلال", "النصر", "2 - 1", "مباشر • د 74", true),
            Fixture("دوري أبطال آسيا للنخبة", "الجولة 3", "الأهلي", "الغرافة", "7:00 م", "قادمة", false),
            Fixture("دوري أبطال أوروبا", "دور المجموعات", "ريال مدريد", "مانشستر سيتي", "10:00 م", "قادمة", false),
            Fixture("دوري روشن للمحترفين", "الجولة 8", "الاتحاد", "القادسية", "8:45 م", "قادمة", false),
            Fixture("الدوري الإسباني", "كلاسيكو الأرض", "برشلونة", "أتلتيكو مدريد", "10:30 م", "قادمة", false)
        )

        for (m in fixtureList) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = glass(32, 18, Color.argb(45, 255, 255, 255), 1)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 90 else 32, 18, if (f) Color.WHITE else Color.argb(45, 255, 255, 255), if (f) 2 else 1)
                    v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).translationY(if (f) -dp(4).toFloat() else 0f).setDuration(120).start()
                }

                // شعار ورقم الجولة
                val topBadge = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(tv(11f, true, Color.parseColor("#4DEEEA")).apply { text = m.league })
                    addView(tv(10.5f, false, Color.argb(160, 255, 255, 255)).apply {
                        text = " • " + m.round
                        setPadding(dp(6), 0, 0, 0)
                    })
                }
                addView(topBadge)

                // الفريقين والمواجهة
                val matchVersus = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, dp(6))

                    addView(tv(14f, true).apply { text = m.home; maxLines = 1 })
                    addView(tv(12f, true, Color.argb(130, 255, 255, 255)).apply {
                        text = if (m.isLive) "  vs  " else "  ✕  "
                        setPadding(dp(6), 0, dp(6), 0)
                    })
                    addView(tv(14f, true).apply { text = m.away; maxLines = 1 })
                }
                addView(matchVersus)

                // شارة الوقت أو النتيجة المباشرة
                val scoreBadge = tv(12.5f, true, if (m.isLive) Color.parseColor("#FF4B4B") else Color.parseColor("#FFD166")).apply {
                    text = if (m.isLive) "● ${m.timeOrScore}  (${m.status})" else "${m.timeOrScore}"
                }
                addView(scoreBadge)
            }

            matchesRow.addView(card, LinearLayout.LayoutParams(dp(230), -2).apply {
                marginEnd = dp(14)
            })
        }
    }

    data class Fixture(val league: String, val round: String, val home: String, val away: String, val timeOrScore: String, val status: String, val isLive: Boolean)

    // ==========================================
    // رف التطبيقات (Apple TV Card Shape مع أيقونة واسم بارز)
    // ==========================================
    private fun loadAppsShelf() {
        appsRow.removeAllViews()
        val pm = packageManager
        val list = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }

        val order = prefs.getString("apps_order", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        installedApps = list.sortedBy {
            val idx = order.indexOf(it.activityInfo.packageName)
            if (idx != -1) idx else 9999
        }.toMutableList()

        for (i in installedApps.indices) {
            val ri = installedApps[i]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glass(40, 20, Color.argb(40, 255, 255, 255), 1)
                setPadding(dp(16), dp(10), dp(20), dp(10))
                isFocusable = true
                isClickable = true

                setOnClickListener {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }

                setOnLongClickListener {
                    showAppOptionsSheet(ri, i)
                    true
                }

                setOnFocusChangeListener { v, f ->
                    v.animate().scaleX(if (f) 1.12f else 1f).scaleY(if (f) 1.12f else 1f).translationY(if (f) -dp(6).toFloat() else 0f).setDuration(130).start()
                    v.background = glass(if (f) 115 else 40, 20, if (f) Color.WHITE else Color.argb(40, 255, 255, 255), if (f) 3 else 1)
                    v.elevation = if (f) 28f else 0f
                }
            }

            card.addView(ImageView(this).apply {
                setImageDrawable(ri.loadIcon(pm))
            }, LinearLayout.LayoutParams(dp(46), dp(46)))

            card.addView(tv(14.5f, true).apply {
                text = ri.loadLabel(pm)
                maxLines = 1
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))

            appsRow.addView(card, LinearLayout.LayoutParams(-2, dp(76)).apply {
                marginEnd = dp(16)
            })
        }

        if (appsRow.childCount > 0) {
            appsRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun showAppOptionsSheet(app: ResolveInfo, index: Int) {
        val pm = packageManager
        val name = app.loadLabel(pm).toString()
        val pkg = app.activityInfo.packageName

        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("نقل للأمام ◀", "نقل للخلف ▶", "معلومات التطبيق ℹ️", "إلغاء التثبيت 🗑️")) { _, w ->
                when (w) {
                    0 -> reorderApp(index, -1)
                    1 -> reorderApp(index, 1)
                    2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", pkg, null) })
                    3 -> startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.fromParts("package", pkg, null) })
                }
            }.show()
    }

    private fun reorderApp(index: Int, offset: Int) {
        val target = index + offset
        if (target in 0 until installedApps.size) {
            val item = installedApps.removeAt(index)
            installedApps.add(target, item)
            val order = installedApps.joinToString(",") { it.activityInfo.packageName }
            prefs.edit().putString("apps_order", order).apply()
            loadAppsShelf()
            appsRow.getChildAt(target)?.requestFocus()
        }
    }

    private fun openFullAppDrawer() {
        val pm = packageManager
        val list = installedApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("درج التطبيقات المثبتة")
            .setItems(list) { _, which ->
                val ri = installedApps[which]
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.show()
    }

    // ==========================================
    // محركات تحديث البيانات الحية
    // ==========================================
    private fun startDataEngines() {
        updateTimeEngine()
        renderMatchesShelf()
        updateOnlineData()
    }

    private fun updateTimeEngine() {
        try {
            clockTv.text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm", ar))
            dayTv.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", ar))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy م", ar))
            val h = try {
                HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar))
            } catch (e: Throwable) { "" }
            datesTv.text = if (h.isNotEmpty()) "$h  •  $m" else m
        } catch (e: Throwable) { }
        ui.postDelayed({ updateTimeEngine() }, 10000)
    }

    private fun updateOnlineData() {
        thread {
            // الطقس لمدينة الرياض
            try {
                val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code").readText()).getJSONObject("current")
                val t = Math.round(w.getDouble("temperature_2m"))
                val appT = Math.round(w.getDouble("apparent_temperature"))
                val hum = w.getInt("relative_humidity_2m")
                val wind = Math.round(w.getDouble("wind_speed_10m"))
                val code = w.getInt("weather_code")
                val txt = when {
                    code == 0 -> "سماء صافية"
                    code <= 3 -> "غائم جزئياً"
                    code <= 48 -> "ضباب خفيف"
                    code <= 67 -> "فرصة أمطار"
                    else -> "أجواء معتدلة"
                }
                ui.post {
                    weatherTempTv.text = "$t°"
                    weatherStatusTv.text = txt
                    weatherSubTv.text = "المحسوسة $appT° • رطوبة $hum% • رياح $wind كم"
                }
            } catch (e: Throwable) { }

            // مواقيت الصلاة لمدينة الرياض
            try {
                val t = JSONObject(URL("https://api.aladhan.com/v1/timingsByCity?city=Riyadh&country=SA&method=4").readText()).getJSONObject("data").getJSONObject("timings")
                val list = listOf(
                    "الفجر" to t.getString("Fajr").substring(0, 5),
                    "الظهر" to t.getString("Dhuhr").substring(0, 5),
                    "العصر" to t.getString("Asr").substring(0, 5),
                    "المغرب" to t.getString("Maghrib").substring(0, 5),
                    "العشاء" to t.getString("Isha").substring(0, 5)
                )
                val now = LocalTime.now()
                val parsed = list.map { LocalTime.parse(it.second) }
                val nextIdx = parsed.indexOfFirst { it.isAfter(now) }.let { if (it == -1) 0 else it }

                ui.post {
                    prayerRow.removeAllViews()
                    for (i in list.indices) {
                        val isNext = (i == nextIdx)
                        val col = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            if (isNext) {
                                background = glass(60, 12, Color.parseColor("#FFD166"), 1)
                                setPadding(dp(10), dp(4), dp(10), dp(4))
                            } else {
                                setPadding(dp(8), dp(4), dp(8), dp(4))
                            }
                            addView(tv(11.5f, isNext, if (isNext) Color.parseColor("#FFD166") else Color.argb(170, 255, 255, 255)).apply {
                                text = list[i].first
                                gravity = Gravity.CENTER
                            })
                            addView(tv(12.5f, isNext, if (isNext) Color.WHITE else Color.argb(220, 255, 255, 255)).apply {
                                val tp = LocalTime.parse(list[i].second)
                                text = tp.format(DateTimeFormatter.ofPattern("h:mm", ar))
                                gravity = Gravity.CENTER
                            })
                        }
                        prayerRow.addView(col)
                    }

                    val diffMin = java.time.Duration.between(now, parsed[nextIdx]).toMinutes()
                    nextPrayerTimerTv.text = if (diffMin > 0) "الأذان القادم: ${list[nextIdx].first} بعد $diffMin دقيقة" else "حان الآن وقت الصلاة"
                }
            } catch (e: Throwable) { }

            ui.postDelayed({ updateOnlineData() }, 15 * 60 * 1000L)
        }
    }
}
