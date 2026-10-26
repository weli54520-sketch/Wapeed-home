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
    private lateinit var datesTv: TextView

    private lateinit var tempTv: TextView
    private lateinit var weatherStatusTv: TextView
    private lateinit var weatherSubTv: TextView
    private lateinit var prayerLayout: LinearLayout
    private lateinit var prayerTimerTv: TextView
    private lateinit var matchesRow: LinearLayout

    private var allApps = mutableListOf<ResolveInfo>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(alpha: Int = 36, radius: Int = 22, strokeColor: Int = Color.argb(55, 255, 255, 255), strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(Color.argb(alpha, 255, 255, 255))
        cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun circleDrawable(bgColor: Int, strokeColor: Int = Color.WHITE) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(bgColor)
        setStroke(dp(1), strokeColor)
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
            buildCinematicTvLayout()
            startEngines()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply { text = "خطأ: $e"; textSize = 20f; setTextColor(Color.RED); setPadding(40, 40, 40, 40) })
        }
    }

    override fun onResume() {
        super.onResume()
        loadAppsShelf()
    }

    @Deprecated("") override fun onBackPressed() {
        if (mainScroll.scrollY > 30) {
            mainScroll.smoothScrollTo(0, 0)
            favRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun buildCinematicTvLayout() {
        mainScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            // خلفية سينمائية عميقة بتدرج راقي ومريح لإضاءة البروجكتر
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0F1A2C.toInt(), 0xFF1C2C42.toInt(), 0xFF121A29.toInt())
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(44), dp(24), dp(44), dp(36))
        }

        // ==========================================
        // 1. القسم العلوي (الطقس والصلاة موحدة المقاس تماماً، والساعة يميناً)
        // ==========================================
        val topSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val cardW = dp(230)
        val cardH = dp(146)

        // --- أ: بطاقة الطقس (الرياض) ---
        val wCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(32, 22)
            setPadding(dp(18), dp(12), dp(18), dp(12))

            addView(tv(13f, true, Color.argb(200, 255, 255, 255)).apply { text = "الطقس • الرياض ▾" })

            val tempBox = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            tempBox.addView(tv(32f).apply { text = "☀️" })
            tempTv = tv(38f, true).apply { text = "28°"; setPadding(dp(10), 0, 0, 0) }
            tempBox.addView(tempTv)
            addView(tempBox)

            weatherStatusTv = tv(13f, true).apply { text = "سماء صافية" }
            weatherSubTv = tv(11f, false, Color.argb(185, 255, 255, 255)).apply {
                text = "المحسوسة 29° • الرطوبة 22%\nالرياح 14 كم/س"
                setLineSpacing(0f, 1.2f)
            }
            addView(weatherStatusTv)
            addView(weatherSubTv)
        }
        topSection.addView(wCard, LinearLayout.LayoutParams(cardW, cardH))

        // --- ب: بطاقة أوقات الصلاة (الرياض - نفس المقاس تماماً) ---
        val pCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(32, 22)
            setPadding(dp(18), dp(10), dp(18), dp(10))

            addView(tv(13f, true, Color.argb(200, 255, 255, 255)).apply {
                text = "أوقات الصلاة • الرياض"
                gravity = Gravity.CENTER
            })

            prayerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            addView(prayerLayout)

            prayerTimerTv = tv(12f, true, Color.parseColor("#4DEEEA")).apply {
                text = "جاري الحساب..."
                gravity = Gravity.CENTER
            }
            addView(prayerTimerTv)
        }
        topSection.addView(pCard, LinearLayout.LayoutParams(cardW, cardH).apply { marginStart = dp(20) })

        // فاصل مرن يدفع الساعة والأيقونات إلى اليمين
        topSection.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // --- ج: الوقت والتاريخ والأزرار الزجاجية العلوية ---
        val rBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        // أزرار النظام بتصميم كبسولات زجاجية ناعمة ومفرغة
        val iconsRow = LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, 0, 0, dp(12))
        }

        val topBtns = listOf(
            "⚙️" to { startActivity(Intent(Settings.ACTION_SETTINGS)) },
            "📶" to { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            "📱" to { mainScroll.smoothScrollTo(0, dp(450)); allGrid.getChildAt(0)?.requestFocus() }
        )

        for ((ic, act) in topBtns) {
            val b = LinearLayout(this).apply {
                background = glass(40, 14, Color.argb(60, 255, 255, 255), 1)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                isFocusable = true
                isClickable = true
                setOnClickListener { act() }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 120 else 40, 14, if (f) Color.WHITE else Color.argb(60, 255, 255, 255), if (f) 2 else 1)
                    v.animate().scaleX(if (f) 1.14f else 1f).scaleY(if (f) 1.14f else 1f).setDuration(120).start()
                }
                addView(tv(14f).apply { text = ic })
            }
            iconsRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }
        rBox.addView(iconsRow)

        clockTv = tv(74f, true).apply { gravity = Gravity.END; text = "12:30 م" }
        dayTv = tv(17f, true).apply { gravity = Gravity.END; text = "الإثنين" }
        datesTv = tv(13f, false, Color.argb(200, 255, 255, 255)).apply { gravity = Gravity.END }

        rBox.addView(clockTv)
        rBox.addView(dayTv)
        rBox.addView(datesTv)

        topSection.addView(rBox)
        root.addView(topSection)

        // ==========================================
        // 2. مباريات اليوم (مقاسات موحدة وشعارات أندية متناسقة)
        // ==========================================
        val mHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(16), 0, dp(8))
            addView(tv(15.5f, true).apply { text = "مباريات اليوم" })
            addView(tv(12f, false, Color.argb(160, 255, 255, 255)).apply {
                text = " • 365Scores Live"
                setPadding(dp(8), 0, 0, 0)
            })
        }
        root.addView(mHeader)

        matchesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(12))
        }
        val mScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(matchesRow)
        }
        root.addView(mScroll, LinearLayout.LayoutParams(-1, -2))

        // ==========================================
        // 3. شريط المفضلة بنمط Apple TV 4K (أيقونات دائرية كبيرة وبطاقات موحدة)
        // ==========================================
        val favHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), 0, dp(8))
            addView(tv(16f, true).apply { text = "المفضلة" })
            addView(tv(12f, false, Color.argb(170, 255, 255, 255)).apply {
                text = " (اضغط سهم تحت ⬇️ لفتح باقي التطبيقات)"
                setPadding(dp(10), 0, 0, 0)
            })
        }
        root.addView(favHeader)

        favRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(20))
        }
        val favScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(favRow)
        }
        root.addView(favScroll, LinearLayout.LayoutParams(-1, -2))

        // ==========================================
        // 4. درج التطبيقات الكامل (بالنزول لأسفل)
        // ==========================================
        val dHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(24), 0, dp(10))
            addView(tv(17f, true, Color.parseColor("#4DEEEA")).apply { text = "📱 جميع التطبيقات" })
            addView(tv(12f, false, Color.argb(160, 255, 255, 255)).apply {
                text = " • علّق بـ OK لتثبيت التطبيق في المفضلة"
                setPadding(dp(10), 0, 0, 0)
            })
        }
        root.addView(dHeader)

        allGrid = GridLayout(this).apply {
            columnCount = 6
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(4), 0, dp(32))
        }
        root.addView(allGrid, LinearLayout.LayoutParams(-1, -2))

        mainScroll.addView(root)
        setContentView(mainScroll)
    }

    // ==========================================
    // بطاقات المباريات بمقاسات هندسية موحدة وشعارات أندية واضحة
    // ==========================================
    private fun renderMatchesShelf() {
        matchesRow.removeAllViews()

        val fixtures = listOf(
            MatchInfo("دوري أبطال آسيا للنخبة • الجولة 3", "الاتحاد", 0xFFE6A100.toInt(), "نيفتشي", 0xFF1B5E20.toInt(), "4:45 م"),
            MatchInfo("دوري أبطال آسيا للنخبة • الجولة 3", "الأهلي", 0xFF0D6838.toInt(), "الغرافة", 0xFFFBC02D.toInt(), "7:00 م"),
            MatchInfo("دوري أبطال آسيا للنخبة • الجولة 3", "تراكتور", 0xFFC62828.toInt(), "العين", 0xFF4A148C.toInt(), "7:00 م"),
            MatchInfo("الدوري الفرنسي • الجولة 9", "ليون", 0xFF1565C0.toInt(), "باريس سان جيرمان", 0xFF0D1B2A.toInt(), "10:30 م"),
            MatchInfo("الدوري الإسباني • الجولة 10", "أوساسونا", 0xFFB71C1C.toInt(), "إشبيلية", 0xFFD32F2F.toInt(), "11:00 م")
        )

        val itemWidth = dp(220)
        val itemHeight = dp(105)

        for (f in fixtures) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = glass(35, 18, Color.argb(60, 255, 255, 255), 1)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, focused ->
                    v.background = glass(if (focused) 100 else 35, 18, if (focused) Color.WHITE else Color.argb(60, 255, 255, 255), if (focused) 2 else 1)
                    v.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f).setDuration(120).start()
                }

                // عنوان البطولة
                addView(tv(10.5f, true, Color.parseColor("#4DEEEA")).apply {
                    text = f.tournament
                    gravity = Gravity.CENTER
                    maxLines = 1
                })

                // صف المواجهة مع شعار دائري ملون لكل نادي
                val vsBox = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(4))

                    // نادي الأرض
                    val hBadge = View(context).apply { background = circleDrawable(f.hColor) }
                    addView(hBadge, LinearLayout.LayoutParams(dp(16), dp(16)))
                    addView(tv(12f, true).apply { text = " " + f.home; maxLines = 1 })

                    addView(tv(11f, true, Color.argb(140, 255, 255, 255)).apply {
                        text = " ✕ "
                        setPadding(dp(6), 0, dp(6), 0)
                    })

                    // نادي الضيف
                    addView(tv(12f, true).apply { text = f.away + " "; maxLines = 1 })
                    val aBadge = View(context).apply { background = circleDrawable(f.aColor) }
                    addView(aBadge, LinearLayout.LayoutParams(dp(16), dp(16)))
                }
                addView(vsBox)

                // التوقيت بنمط واضح
                addView(tv(11.5f, true, Color.WHITE).apply {
                    text = f.time
                    gravity = Gravity.CENTER
                })
            }

            matchesRow.addView(card, LinearLayout.LayoutParams(itemWidth, itemHeight).apply {
                marginEnd = dp(14)
            })
        }
    }

    data class MatchInfo(val tournament: String, val home: String, val hColor: Int, val away: String, val aColor: Int, val time: String)

    // ==========================================
    // بطاقات التطبيقات بتصميم Apple TV 4K الموحد والأنيق
    // ==========================================
    private fun loadAppsShelf() {
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
            favRow.addView(createAppleStyleCard(app, isFav = true))
        }

        allGrid.removeAllViews()
        for (app in allApps) {
            val c = createAppleStyleCard(app, isFav = false)
            val p = GridLayout.LayoutParams().apply {
                width = dp(170)
                height = dp(84)
                setMargins(dp(6), dp(8), dp(6), dp(8))
            }
            allGrid.addView(c, p)
        }

        if (favRow.childCount > 0) favRow.getChildAt(0)?.requestFocus()
    }

    private fun createAppleStyleCard(app: ResolveInfo, isFav: Boolean): View {
        val pm = packageManager
        val pkg = app.activityInfo.packageName

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(45, 18, Color.argb(60, 255, 255, 255), 1)
            setPadding(dp(14), dp(8), dp(16), dp(8))
            isFocusable = true
            isClickable = true

            if (isFav) {
                // مقاس عريض وموحد لبطاقة المفضلة
                layoutParams = LinearLayout.LayoutParams(dp(185), dp(78)).apply { marginEnd = dp(16) }
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
                            loadAppsShelf()
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
                        mainScroll.smoothScrollTo(0, dp(450))
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
                v.animate().scaleX(if (f) 1.12f else 1f).scaleY(if (f) 1.12f else 1f).setDuration(120).start()
                v.background = glass(if (f) 130 else 45, 18, if (f) Color.WHITE else Color.argb(60, 255, 255, 255), if (f) 3 else 1)
                v.elevation = if (f) 24f else 0f
            }

            // أيقونة التطبيق كبيرة ودائرية متناسقة
            addView(ImageView(context).apply {
                setImageDrawable(app.loadIcon(pm))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))

            addView(tv(14f, true).apply {
                text = app.loadLabel(pm)
                maxLines = 1
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    // ==========================================
    // تحديث البيانات المباشرة (الرياض)
    // ==========================================
    private fun startEngines() {
        renderMatchesShelf()
        updateClockEngine()
        refreshRiyadhData()
    }

    private fun updateClockEngine() {
        try {
            clockTv.text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", ar))
            dayTv.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", ar))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy م", ar))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar)) } catch (e: Throwable) { "" }
            datesTv.text = if (h.isNotEmpty()) "$h\n$m" else m
        } catch (e: Throwable) { }
        ui.postDelayed({ updateClockEngine() }, 10000)
    }

    private fun refreshRiyadhData() {
        thread {
            // طقس الرياض
            try {
                val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m").readText()).getJSONObject("current")
                val t = Math.round(w.getDouble("temperature_2m"))
                val appT = Math.round(w.getDouble("apparent_temperature"))
                val hum = w.getInt("relative_humidity_2m")
                val wind = Math.round(w.getDouble("wind_speed_10m"))
                ui.post {
                    tempTv.text = "$t°"
                    weatherSubTv.text = "المحسوسة $appT° • الرطوبة $hum%\nالرياح $wind كم/س"
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
                    prayerLayout.removeAllViews()
                    for (i in list.indices) {
                        val p = list[i]
                        val isNext = (i == nextIdx)
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, dp(1), 0, dp(1))
                            if (isNext) background = glass(40, 8)
                            addView(tv(11.5f, isNext, if (isNext) Color.parseColor("#4DEEEA") else Color.argb(180, 255, 255, 255)).apply {
                                text = p.first
                            }, LinearLayout.LayoutParams(0, -2, 1f))
                            addView(tv(12f, isNext, if (isNext) Color.WHITE else Color.argb(200, 255, 255, 255)).apply {
                                val tp = LocalTime.parse(p.second)
                                text = tp.format(DateTimeFormatter.ofPattern("h:mm a", ar))
                            })
                        }
                        prayerLayout.addView(row)
                    }
                    val diff = java.time.Duration.between(now, parsed[nextIdx]).toMinutes()
                    val h = diff / 60
                    val m = diff % 60
                    prayerTimerTv.text = if (diff > 0) "الأذان القادم بعد ${if (h > 0) "$h س و " else ""}$m د" else "حان الآن وقت الصلاة"
                }
            } catch (e: Throwable) { }

            ui.postDelayed({ refreshRiyadhData() }, 15 * 60 * 1000L)
        }
    }
}
