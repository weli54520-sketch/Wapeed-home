package com.riyadh.home

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val ar = Locale("ar", "SA")
    private val ui = Handler(Looper.getMainLooper())
    private val dim = Color.argb(200, 255, 255, 255)
    private lateinit var prefs: SharedPreferences
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var cityTv: TextView
    private lateinit var weatherTv: TextView
    private lateinit var weatherSub: TextView
    private lateinit var prayerBox: LinearLayout
    private lateinit var prayerTimer: TextView
    private lateinit var ticker: TextView
    private lateinit var favRow: LinearLayout
    private lateinit var favLabel: TextView
    private lateinit var matchesRow: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var pages: List<View>
    private lateinit var tabs: List<TextView>
    private var prayers: List<Prayer> = emptyList()
    private var apps: List<ResolveInfo> = emptyList()

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun tv(sp: Float, bold: Boolean = false, color: Int = Color.WHITE) = Ui.tv(this, sp, bold, color)
    private fun city() = Data.cities[prefs.getInt("city", 0).coerceIn(0, Data.cities.size - 1)]

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.glass(this@MainActivity, 140, 22)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("home", Context.MODE_PRIVATE)
        try {
            build()
            tick()
            loop()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply {
                text = "خطأ: $e"; textSize = 22f; setTextColor(Color.WHITE); setPadding(60, 60, 60, 60)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) loadApps()
    }

    @Deprecated("") override fun onBackPressed() {
        if (::pages.isInitialized) showPage(0)
    }

    private fun build() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(40), dp(22), dp(40), dp(22))
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF070B18.toInt(), 0xFF14224A.toInt(), 0xFF2A1F55.toInt())
        )

        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        val names = listOf("الرئيسية", "المباريات", "التطبيقات")
        tabs = names.mapIndexed { i, n ->
            val t = tv(17f, true).apply { text = n; setPadding(dp(20), dp(9), dp(20), dp(9)) }
            Ui.focusable(t, 18, 1.06f, { showPage(i) })
            header.addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            t
        }
        header.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        fun round(ico: Ico, act: () -> Unit) {
            val f = FrameLayout(this)
            f.addView(IconView(this, ico), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            Ui.focusable(f, 24, 1.1f, act)
            header.addView(f, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(10) })
        }
        round(Ico.WIFI) { go(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        round(Ico.GEAR) { openSettings() }

        val box = FrameLayout(this)
        pages = listOf(buildHome(), buildMatches(), buildApps())
        pages.forEach { box.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        root.addView(header)
        root.addView(box, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showPage(0)
    }

    private fun buildHome(): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(14), 0, 0)

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val clockBox = LinearLayout(this)
        clockBox.orientation = LinearLayout.VERTICAL
        clock = tv(64f, true)
        date = tv(15f, false, dim)
        clockBox.addView(clock)
        clockBox.addView(date)
        top.addView(clockBox, LinearLayout.LayoutParams(0, -2, 1f))

        val w = card()
        cityTv = tv(12f, true, Ui.ACCENT).apply { text = city().name }
        weatherTv = tv(34f, true).apply { text = "--°" }
        weatherSub = tv(12f, false, dim).apply { text = "جاري التحديث..."; setLineSpacing(0f, 1.2f) }
        w.addView(cityTv)
        w.addView(weatherTv)
        w.addView(weatherSub)
        top.addView(w, LinearLayout.LayoutParams(dp(200), -2).apply { marginStart = dp(16) })

        val p = card()
        p.addView(tv(12f, true, Ui.ACCENT).apply { text = "أوقات الصلاة" })
        prayerBox = LinearLayout(this)
        prayerBox.orientation = LinearLayout.VERTICAL
        prayerBox.setPadding(0, dp(4), 0, dp(2))
        prayerTimer = tv(11.5f, true, Ui.GOLD)
        p.addView(prayerBox)
        p.addView(prayerTimer)
        top.addView(p, LinearLayout.LayoutParams(dp(210), -2).apply { marginStart = dp(16) })
        col.addView(top)

        ticker = tv(14f, true, Ui.GOLD).apply { text = "⚽ جاري تحديث المباريات..."; setPadding(dp(4), dp(14), 0, 0) }
        col.addView(ticker)
        col.addView(tv(15f, true).apply { text = "المفضلة"; setPadding(dp(4), dp(12), 0, dp(6)) })

        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.VERTICAL
        dock.background = Ui.glass(this, 130, 24)
        dock.setPadding(dp(14), dp(12), dp(14), dp(8))
        dock.clipChildren = false
        favRow = LinearLayout(this)
        favRow.gravity = Gravity.CENTER_VERTICAL
        favRow.clipChildren = false
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipChildren = false
        hs.clipToPadding = false
        hs.addView(favRow)
        favLabel = tv(13f, true, dim).apply { setPadding(dp(4), dp(6), 0, 0) }
        dock.addView(hs)
        dock.addView(favLabel)
        col.addView(dock, LinearLayout.LayoutParams(-1, -2))
        return col
    }

    private fun buildMatches(): View {
        matchesRow = LinearLayout(this)
        matchesRow.gravity = Gravity.CENTER_VERTICAL
        matchesRow.clipChildren = false
        matchesRow.setPadding(dp(6), dp(20), dp(6), dp(20))
        matchesRow.addView(tv(16f, false, dim).apply { text = "جاري تحميل المباريات..." })
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipChildren = false
        hs.clipToPadding = false
        hs.addView(matchesRow)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(20f, true).apply { text = "مباريات اليوم"; setPadding(dp(4), dp(18), 0, 0) })
        col.addView(hs, LinearLayout.LayoutParams(-1, -2))
        return col
    }

    private fun buildApps(): View {
        grid = GridLayout(this)
        grid.layoutDirection = View.LAYOUT_DIRECTION_RTL
        grid.columnCount = 6
        grid.clipChildren = false
        grid.clipToPadding = false
        grid.setPadding(dp(6), dp(16), dp(6), dp(30))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.clipChildren = false
        sv.clipToPadding = false
        sv.addView(grid)
        return sv
    }    private fun showPage(i: Int) {
        pages.forEachIndexed { k, p -> p.visibility = if (k == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { k, t -> t.setTextColor(if (k == i) Ui.ACCENT else Color.WHITE) }
    }

    private fun go(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Throwable) { Toast.makeText(this, "غير متاح على هذا الجهاز", Toast.LENGTH_SHORT).show() }
    }

    private fun tick() {
        try {
            clock.text = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm", ar))
            val g = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ar))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", ar)) + " هـ  •  " } catch (e: Throwable) { "" }
            date.text = h + g
            renderPrayers()
        } catch (e: Throwable) { }
        ui.postDelayed({ tick() }, 20000)
    }

    private fun loop() {
        refresh()
        ui.postDelayed({ loop() }, 15 * 60 * 1000L)
    }

    private fun refresh() {
        val c = city()
        thread {
            try {
                val w = Data.weather(c)
                ui.post {
                    weatherTv.text = "${w.temp}°"
                    weatherSub.text = "${w.status}\nالمحسوسة ${w.feels}° • الرطوبة ${w.humidity}%\nالرياح ${w.wind} كم/س"
                }
            } catch (e: Throwable) { }
            try { val p = Data.prayers(c); ui.post { prayers = p; renderPrayers() } } catch (e: Throwable) { }
            try { val m = Data.matches(); ui.post { renderMatches(m) } } catch (e: Throwable) { }
        }
    }

    private fun renderPrayers() {
        if (prayers.isEmpty()) return
        prayerBox.removeAllViews()
        val now = LocalTime.now()
        var next = prayers.indexOfFirst { it.time.isAfter(now) }
        val wrap = next == -1
        if (wrap) next = 0
        val f = DateTimeFormatter.ofPattern("h:mm a", ar)
        for ((i, p) in prayers.withIndex()) {
            val on = i == next
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(6), dp(2), dp(6), dp(2))
            if (on) r.background = Ui.glass(this, 200, 8, 0, 0)
            r.addView(tv(12.5f, on, if (on) Ui.ACCENT else dim).apply { text = p.name }, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(tv(12.5f, on).apply { text = p.time.format(f) })
            prayerBox.addView(r)
        }
        val t = prayers[next].time
        val mins = if (wrap) Duration.between(now, LocalTime.MAX).toMinutes() + Duration.between(LocalTime.MIN, t).toMinutes()
        else Duration.between(now, t).toMinutes()
        val hh = mins / 60
        val mm = mins % 60
        prayerTimer.text = "${prayers[next].name} بعد ${if (hh > 0) "$hh س و " else ""}$mm د"
    }

    private fun team(name: String, logo: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val iv = ImageView(context)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        Ui.loadImage(logo, iv)
        addView(iv, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(tv(12f, true).apply { text = name; maxLines = 2; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    }

    private fun renderMatches(l: List<Match>) {
        matchesRow.removeAllViews()
        val first = l.firstOrNull { it.live } ?: l.firstOrNull()
        ticker.text = if (first == null) "⚽ لا توجد مباريات مهمة اليوم" else "⚽ ${first.home} × ${first.away}  •  ${first.center}"
        if (l.isEmpty()) {
            matchesRow.addView(tv(18f, false, dim).apply { text = "لا توجد مباريات مهمة اليوم" })
            return
        }
        for (m in l) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.setPadding(dp(18), dp(12), dp(18), dp(12))
            c.addView(tv(12f, true, Ui.ACCENT).apply { text = m.league; maxLines = 1 })
            val teams = LinearLayout(this)
            teams.gravity = Gravity.CENTER_VERTICAL
            teams.setPadding(0, dp(8), 0, dp(4))
            teams.addView(team(m.home, m.homeLogo), LinearLayout.LayoutParams(0, -2, 1f))
            teams.addView(tv(18f, true, Ui.GOLD).apply { text = m.center; gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) })
            teams.addView(team(m.away, m.awayLogo), LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(teams, LinearLayout.LayoutParams(-1, -2))
            if (m.note.isNotEmpty()) c.addView(tv(11.5f, false, dim).apply { text = (if (m.live) "🔴 " else "") + m.note })
            Ui.focusable(c, 20, 1.05f, { })
            matchesRow.addView(c, LinearLayout.LayoutParams(dp(300), -2).apply { marginEnd = dp(14) })
        }
    }

    private fun favs(): MutableList<String> {
        val s = prefs.getString("fav", null)
        return if (s == null) apps.take(6).map { it.activityInfo.packageName }.toMutableList()
        else s.split(",").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveFavs(l: List<String>) {
        prefs.edit().putString("fav", l.joinToString(",")).apply()
        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        apps = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
        favRow.removeAllViews()
        for (p in favs()) {
            val a = apps.find { it.activityInfo.packageName == p } ?: continue
            favRow.addView(tile(a, true), LinearLayout.LayoutParams(dp(120), dp(76)).apply { marginEnd = dp(12) })
        }
        grid.removeAllViews()
        val gw = ((resources.displayMetrics.widthPixels - dp(80)) / 6) - dp(12)
        for (a in apps) {
            grid.addView(tile(a, false), GridLayout.LayoutParams().apply {
                width = gw
                height = dp(92)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        if (pages[0].visibility == View.VISIBLE) favRow.getChildAt(0)?.requestFocus()
    }

    private fun tile(app: ResolveInfo, dock: Boolean): View {
        val pm = packageManager
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(pm).toString()
        val icon = app.loadIcon(pm)
        val color = Ui.avgColor(icon)
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(6), dp(8), dp(6))
        t.isFocusable = true
        t.isClickable = true
        t.background = Ui.solid(this, color, 16, 0x40FFFFFF, 1)
        t.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        if (!dock) t.addView(tv(11f, true).apply { text = label; maxLines = 1; gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
        t.setOnClickListener { go(Intent(Intent.ACTION_MAIN).setClassName(pkg, app.activityInfo.name)) }
        t.setOnLongClickListener { appMenu(app); true }
        t.setOnFocusChangeListener { v, f ->
            v.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(120).start()
            v.background = Ui.solid(this, color, 16, if (f) Color.WHITE else 0x40FFFFFF, if (f) 3 else 1)
            v.elevation = if (f) 20f else 0f
            if (f && dock) favLabel.text = label
        }
        return t
    }

    private fun appMenu(app: ResolveInfo) {
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(packageManager).toString()
        val f = favs()
        val inFav = f.contains(pkg)
        val opts = ArrayList<Pair<String, () -> Unit>>()
        opts.add((if (inFav) "إزالة من المفضلة" else "إضافة للمفضلة") to {
            if (inFav) f.remove(pkg) else f.add(pkg)
            saveFavs(f)
        })
        if (inFav && f.indexOf(pkg) > 0) opts.add("تقديم ▶" to {
            val i = f.indexOf(pkg); f.removeAt(i); f.add(i - 1, pkg); saveFavs(f)
        })
        if (inFav && f.indexOf(pkg) < f.size - 1) opts.add("تأخير ◀" to {
            val i = f.indexOf(pkg); f.removeAt(i); f.add(i + 1, pkg); saveFavs(f)
        })
        opts.add("معلومات التطبيق" to { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))) })
        opts.add("إلغاء التثبيت" to { go(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))) })
        sheet(label, opts)
    }

    private fun sheet(title: String, opts: List<Pair<String, () -> Unit>>) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.layoutDirection = View.LAYOUT_DIRECTION_RTL
        box.setPadding(dp(26), dp(22), dp(26), dp(22))
        box.background = Ui.solid(this, 0xF0121B30.toInt(), 26, 0x50FFFFFF, 1)
        box.addView(tv(19f, true, Ui.ACCENT).apply { text = title; setPadding(0, 0, 0, dp(14)) })
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.clipChildren = false
        for ((txt, act) in opts) {
            val r = tv(16f, true).apply { text = txt; setPadding(dp(20), dp(14), dp(20), dp(14)) }
            Ui.focusable(r, 16, 1.03f, { d.dismiss(); act() })
            list.addView(r, LinearLayout.LayoutParams(dp(380), -2).apply { bottomMargin = dp(10) })
        }
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list)
        box.addView(sv, LinearLayout.LayoutParams(dp(380), if (opts.size > 5) dp(340) else -2))
        d.setContentView(box)
        d.show()
    }

    private fun openSettings() {
        sheet("الإعدادات", listOf(
            "📍 المدينة: ${city().name}" to { pickCity() },
            "📶 الواي فاي والشبكة" to { go(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            "⚙️ إعدادات الجهاز" to { go(Intent(Settings.ACTION_SETTINGS)) },
            "⭐ إعادة ضبط المفضلة" to { prefs.edit().remove("fav").apply(); loadApps() }
        ))
    }

    private fun pickCity() {
        sheet("اختر المدينة", Data.cities.mapIndexed { i, c ->
            c.name to {
                prefs.edit().putInt("city", i).apply()
                cityTv.text = c.name
                refresh()
            }
        })
    }
}
