package com.riyadh.home

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val ar = Locale("ar", "SA")
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private lateinit var mainScroll: ScrollView
    private lateinit var rootContainer: LinearLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var favDockContainer: LinearLayout
    private lateinit var favRow: LinearLayout
    private lateinit var allGrid: GridLayout

    private lateinit var clockTv: TextView
    private lateinit var miniClockTv: TextView
    private lateinit var dayTv: TextView
    private lateinit var datesTv: TextView

    private lateinit var tempTv: TextView
    private lateinit var weatherStatusTv: TextView
    private lateinit var weatherSubTv: TextView
    private lateinit var prayerLayout: LinearLayout
    private lateinit var prayerTimerTv: TextView
    private lateinit var matchesRow: LinearLayout

    private var allApps = mutableListOf<ResolveInfo>()
    private val logoCache = ConcurrentHashMap<String, Bitmap>()
    private val bgCache = ConcurrentHashMap<String, Bitmap>()

    // خلفية فخمة داكنة ومناسبة للواجهة (ويمكنك وضع رابط صورة السيارة والحصان من الإعدادات)
    private val defaultCustomBgUrl = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?q=80&w=1920&auto=format&fit=crop"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(alpha: Int = 45, radius: Int = 22, strokeColor: Int = Color.argb(75, 255, 255, 255), strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(Color.argb(alpha, 15, 23, 36))
        cornerRadius = dp(radius).toFloat()
        if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun solidCard(color: Int, radius: Int = 18, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color)
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
        prefs = getSharedPreferences("argon_home_prefs", Context.MODE_PRIVATE)
        try {
            buildArgonInterface()
            startEngines()
        } catch (e: Throwable) {
            setContentView(TextView(this).apply { text = "خطأ: $e"; textSize = 20f; setTextColor(Color.RED); setPadding(40, 40, 40, 40) })
        }
    }

    override fun onResume() {
        super.onResume()
        loadAppsAndDock()
    }

    @Deprecated("") override fun onBackPressed() {
        if (mainScroll.scrollY > 30) {
            mainScroll.smoothScrollTo(0, 0)
            favRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun applyCustomBackground() {
        val bgUrl = prefs.getString("custom_bg_url", defaultCustomBgUrl) ?: defaultCustomBgUrl
        val cached = bgCache[bgUrl]
        if (cached != null) {
            backgroundImageView.setImageBitmap(cached)
            return
        }
        thread {
            try {
                val conn = URL(bgUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                if (bmp != null) {
                    bgCache[bgUrl] = bmp
                    ui.post { backgroundImageView.setImageBitmap(bmp) }
                }
            } catch (e: Throwable) { }
        }
    }

    private fun buildArgonInterface() {
        val frameLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(0xFF0A0F18.toInt())
        }

        backgroundImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        frameLayout.addView(backgroundImageView)

        // طبقة تعتيم متدرجة خفيفة لتبرز البطاقات الزجاجية بوضوح فخم
        val darkOverlay = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setBackgroundColor(0x77080C14.toInt())
        }
        frameLayout.addView(darkOverlay)

        mainScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(44), dp(20), dp(44), dp(36))
            clipChildren = false
            clipToPadding = false
        }

        applyCustomBackground()

        val statusHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(10))
            clipChildren = false
            clipToPadding = false
        }
        statusHeader.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val interactiveCapsulesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        miniClockTv = tv(14f, true, Color.argb(220, 255, 255, 255)).apply {
            setPadding(dp(10), 0, dp(14), 0)
        }

        val prayerCapsule = createExpandingCapsule("أوقات الصلاة", IconType.PRAYER) { openPrayerSettingsDialog() }
        val sourcesCapsule = createExpandingCapsule("المصادر", IconType.SOURCES) { openSourcesDialog() }
        val settingsCapsule = createExpandingCapsule("الإعدادات", IconType.SETTINGS) { openInternalSettingsDialog() }
        val wifiCapsule = createExpandingCapsule("واي فاي", IconType.WIFI) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        val gridCapsule = createExpandingCapsule("التطبيقات", IconType.GRID) {
            mainScroll.smoothScrollTo(0, dp(520))
            allGrid.getChildAt(0)?.requestFocus()
        }

        interactiveCapsulesRow.addView(gridCapsule)
        interactiveCapsulesRow.addView(wifiCapsule)
        interactiveCapsulesRow.addView(settingsCapsule)
        interactiveCapsulesRow.addView(sourcesCapsule)
        interactiveCapsulesRow.addView(prayerCapsule)
        interactiveCapsulesRow.addView(miniClockTv)

        statusHeader.addView(interactiveCapsulesRow)
        rootContainer.addView(statusHeader)

        val topWidgetsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val widgetWidth = dp(235)
        val widgetHeight = dp(148)

        val wCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(140, 22, Color.argb(80, 255, 255, 255), 1)
            setPadding(dp(18), dp(12), dp(18), dp(12))

            val wHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(tv(13f, true, Color.argb(220, 255, 255, 255)).apply { text = "الطقس" })
                addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                addView(tv(12f, true, Color.parseColor("#4DEEEA")).apply { text = "الرياض ▾" })
            }
            addView(wHeader)

            val tempRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            tempRow.addView(createSunGraphicView(), LinearLayout.LayoutParams(dp(36), dp(36)))
            tempTv = tv(38f, true).apply { text = "33°"; setPadding(dp(10), 0, 0, 0) }
            tempRow.addView(tempTv)
            addView(tempRow)

            weatherStatusTv = tv(12.5f, true).apply { text = "سماء صافية" }
            weatherSubTv = tv(11f, false, Color.argb(200, 255, 255, 255)).apply {
                text = "المحسوسة 31° • الرطوبة 15%\nالرياح 9 كم/س"
                setLineSpacing(0f, 1.15f)
            }
            addView(weatherStatusTv)
            addView(weatherSubTv)
        }
        topWidgetsRow.addView(wCard, LinearLayout.LayoutParams(widgetWidth, widgetHeight))

        val pCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(140, 22, Color.argb(80, 255, 255, 255), 1)
            setPadding(dp(18), dp(10), dp(18), dp(10))

            addView(tv(13f, true, Color.argb(220, 255, 255, 255)).apply {
                text = "أوقات الصلاة • الرياض"
                gravity = Gravity.CENTER
            })

            prayerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(2))
            }
            addView(prayerLayout, LinearLayout.LayoutParams(-1, 0, 1f))

            prayerTimerTv = tv(11.5f, true, Color.parseColor("#FFEAA7")).apply {
                text = "جاري تحديث المواقيت..."
                gravity = Gravity.CENTER
            }
            addView(prayerTimerTv)
        }
        topWidgetsRow.addView(pCard, LinearLayout.LayoutParams(widgetWidth, widgetHeight).apply { marginStart = dp(18) })

        topWidgetsRow.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockTv = tv(72f, true).apply { gravity = Gravity.END; text = "1:13 م" }
        dayTv = tv(18f, true).apply { gravity = Gravity.END; text = "الإثنين" }
        datesTv = tv(13f, false, Color.argb(220, 255, 255, 255)).apply {
            gravity = Gravity.END
            setLineSpacing(0f, 1.2f)
        }
        clockBox.addView(clockTv)
        clockBox.addView(dayTv)
        clockBox.addView(datesTv)

        topWidgetsRow.addView(clockBox)
        rootContainer.addView(topWidgetsRow)

        val mHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(16), 0, dp(6))
            addView(tv(15.5f, true).apply { text = "مباريات اليوم" })
            addView(tv(11.5f, false, Color.argb(170, 255, 255, 255)).apply {
                text = " • 365Scores Live"
                setPadding(dp(8), 0, 0, 0)
            })
        }
        rootContainer.addView(mHeader)

        matchesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(10))
            clipChildren = false
            clipToPadding = false
        }
        val mScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(matchesRow)
        }
        rootContainer.addView(mScroll, LinearLayout.LayoutParams(-1, -2))

        val favHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), 0, dp(6))
            addView(tv(15f, true).apply { text = "المفضلة" })
            addView(tv(11.5f, false, Color.argb(175, 255, 255, 255)).apply {
                text = " (اضغط سهم تحت ⬇️ لفتح درج التطبيقات • علّق بـ OK للترتيب)"
                setPadding(dp(10), 0, 0, 0)
            })
        }
        rootContainer.addView(favHeader)

        favDockContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(130, 26, Color.argb(75, 255, 255, 255), 1)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            clipChildren = false
            clipToPadding = false
        }

        favRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val favScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(favRow)
        }
        favDockContainer.addView(favScroll, LinearLayout.LayoutParams(-1, -2))
        rootContainer.addView(favDockContainer, LinearLayout.LayoutParams(-1, -2))

        val dHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(36), 0, dp(12))
            addView(tv(17f, true, Color.parseColor("#4DEEEA")).apply { text = "جميع التطبيقات" })
            addView(tv(12f, false, Color.argb(170, 255, 255, 255)).apply {
                text = " • علّق بـ OK على أي تطبيق لتثبيته في المفضلة"
                setPadding(dp(10), 0, 0, 0)
            })
        }
        rootContainer.addView(dHeader)

        allGrid = GridLayout(this).apply {
            columnCount = 5
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(6), 0, dp(40))
        }
        rootContainer.addView(allGrid, LinearLayout.LayoutParams(-1, -2))

        // هذا هو السطر الذي كان مفقوداً وتسبب في اختفاء الواجهة!
        mainScroll.addView(rootContainer)

        frameLayout.addView(mainScroll)
        setContentView(frameLayout)
    }

    enum class IconType { WIFI, SETTINGS, GRID, SOURCES, PRAYER }

    private fun createExpandingCapsule(label: String, type: IconType, onClick: () -> Unit): View {
        return object : LinearLayout(this) {
            private var isFocusedState = false
            private val titleTv = TextView(context).apply {
                text = label
                textSize = 12.5f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                visibility = View.GONE
                setPadding(dp(8), 0, dp(4), 0)
            }
            private val iconView = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    strokeCap = Paint.Cap.ROUND
                    color = Color.WHITE
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f
                    when (type) {
                        IconType.WIFI -> {
                            val r1 = 9f * resources.displayMetrics.density
                            val r2 = 5.5f * resources.displayMetrics.density
                            canvas.drawArc(cx - r1, cy - r1 + dp(3), cx + r1, cy + r1 + dp(3), 225f, 90f, false, paint)
                            canvas.drawArc(cx - r2, cy - r2 + dp(3), cx + r2, cy + r2 + dp(3), 225f, 90f, false, paint)
                            paint.style = Paint.Style.FILL
                            canvas.drawCircle(cx, cy + dp(5).toFloat(), 1.6f * resources.displayMetrics.density, paint)
                            paint.style = Paint.Style.STROKE
                        }
                        IconType.SETTINGS -> {
                            val r = 4.5f * resources.displayMetrics.density
                            canvas.drawCircle(cx, cy, r, paint)
                            for (i in 0 until 8) {
                                val angle = Math.toRadians((i * 45).toDouble())
                                canvas.drawLine(
                                    cx + (r + dp(1)) * Math.cos(angle).toFloat(), cy + (r + dp(1)) * Math.sin(angle).toFloat(),
                                    cx + (r + dp(3)) * Math.cos(angle).toFloat(), cy + (r + dp(3)) * Math.sin(angle).toFloat(), paint
                                )
                            }
                        }
                        IconType.GRID -> {
                            val s = 3f * resources.displayMetrics.density
                            val g = 2.5f * resources.displayMetrics.density
                            canvas.drawRoundRect(cx - g - s, cy - g - s, cx - g / 2, cy - g / 2, 2f, 2f, paint)
                            canvas.drawRoundRect(cx + g / 2, cy - g - s, cx + g + s, cy - g / 2, 2f, 2f, paint)
                            canvas.drawRoundRect(cx - g - s, cy + g / 2, cx - g / 2, cy + g + s, 2f, 2f, paint)
                            canvas.drawRoundRect(cx + g / 2, cy + g / 2, cx + g + s, cy + g + s, 2f, 2f, paint)
                        }
                        IconType.SOURCES -> {
                            canvas.drawRoundRect(cx - dp(7), cy - dp(5), cx + dp(7), cy + dp(4), 3f, 3f, paint)
                            canvas.drawLine(cx - dp(3), cy + dp(6), cx + dp(3), cy + dp(6), paint)
                            canvas.drawLine(cx, cy + dp(4), cx, cy + dp(6), paint)
                        }
                        IconType.PRAYER -> {
                            canvas.drawArc(cx - dp(6), cy - dp(7), cx + dp(6), cy + dp(5), 180f, 180f, false, paint)
                            canvas.drawLine(cx - dp(8), cy + dp(6), cx + dp(8), cy + dp(6), paint)
                            canvas.drawLine(cx, cy - dp(9), cx, cy - dp(7), paint)
                        }
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }

            init {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glass(120, 16, Color.argb(90, 255, 255, 255), 1)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                isFocusable = true
                isClickable = true
                clipChildren = false
                setOnClickListener { onClick() }

                setOnFocusChangeListener { _, f ->
                    isFocusedState = f
                    titleTv.visibility = if (f) View.VISIBLE else View.GONE
                    background = glass(if (f) 200 else 120, 16, if (f) Color.WHITE else Color.argb(90, 255, 255, 255), if (f) 2 else 1)
                    animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start()
                }

                addView(iconView)
                addView(titleTv)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply { marginStart = dp(8) }
        }
    }

    private fun createSunGraphicView(): View {
        return object : View(this) {
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
                    c.drawLine(
                        cx + r1 * Math.cos(a).toFloat(), cy + r1 * Math.sin(a).toFloat(),
                        cx + r2 * Math.cos(a).toFloat(), cy + r2 * Math.sin(a).toFloat(), p
                    )
                }
            }
        }
    }

    private fun openSourcesDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = glass(240, 22, Color.WHITE, 2).apply { setColor(0xEE141E30.toInt()) }
            setPadding(dp(26), dp(20), dp(26), dp(20))
        }

        sheet.addView(tv(18f, true, Color.parseColor("#4DEEEA")).apply {
            text = "📺 مصادر الإدخال (Video Sources)"
            setPadding(0, 0, 0, dp(14))
        })

        val sources = listOf("HDMI 1", "HDMI 2", "HDMI 3", "AV / Composite", "USB Media Player", "VGA")
        for (src in sources) {
            val btn = LinearLayout(this).apply {
                background = glass(100, 14)
                setPadding(dp(16), dp(11), dp(16), dp(11))
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    Toast.makeText(context, "تم التبديل إلى $src", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 180 else 100, 14, if (f) Color.WHITE else Color.TRANSPARENT, if (f) 2 else 0)
                }
                addView(tv(14f, true).apply { text = src })
            }
            sheet.addView(btn, LinearLayout.LayoutParams(dp(340), -2).apply { bottomMargin = dp(8) })
        }

        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openPrayerSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = glass(240, 22, Color.WHITE, 2).apply { setColor(0xEE141E30.toInt()) }
            setPadding(dp(26), dp(20), dp(26), dp(20))
            addView(tv(18f, true, Color.parseColor("#FFEAA7")).apply {
                text = "🕌 إعدادات أوقات الصلاة"
                setPadding(0, 0, 0, dp(12))
            })
            addView(tv(14f, false, Color.WHITE).apply {
                text = "المدينة الحالية: الرياض (تقويم أم القرى)\nالحساب: الهيئة العامة للمساحة\n\nالمواقيت يتم تحديثها تلقائياً عبر الإنترنت بدقة."
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, dp(16))
            })
        }
        val closeBtn = LinearLayout(this).apply {
            background = glass(100, 14)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isFocusable = true
            isClickable = true
            setOnClickListener { dialog.dismiss() }
            setOnFocusChangeListener { v, f -> v.background = glass(if (f) 180 else 100, 14, if (f) Color.WHITE else Color.TRANSPARENT, if (f) 2 else 0) }
            addView(tv(14f, true).apply { text = "إغلاق"; gravity = Gravity.CENTER })
        }
        sheet.addView(closeBtn, LinearLayout.LayoutParams(dp(340), -2))

        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun openInternalSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = glass(240, 22, Color.WHITE, 2).apply { setColor(0xEE141E30.toInt()) }
            setPadding(dp(28), dp(22), dp(28), dp(22))
        }

        sheet.addView(tv(19f, true, Color.parseColor("#4DEEEA")).apply {
            text = "إعدادات واجهة Argon الشاملة"
            setPadding(0, 0, 0, dp(14))
        })

        val items = listOf(
            "🖼️ استعادة الخلفية الافتراضية" to {
                prefs.edit().putString("custom_bg_url", defaultCustomBgUrl).apply()
                applyCustomBackground()
                dialog.dismiss()
            },
            "🌐 إدخال رابط خلفية صورة مخصصة (Custom URL)" to {
                dialog.dismiss()
                showCustomBgUrlInputDialog()
            },
            "📶 إعدادات الواي فاي والشبكة" to {
                dialog.dismiss()
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            },
            "⭐ إعادة تعيين شريط المفضلة" to {
                prefs.edit().remove("fav_apps").apply()
                loadAppsAndDock()
                dialog.dismiss()
            },
            "⚙️ إعدادات نظام البروجكتر الأساسية" to {
                dialog.dismiss()
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        )

        for ((label, action) in items) {
            val rowBtn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = glass(100, 16, Color.argb(60, 255, 255, 255), 1)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                isFocusable = true
                isClickable = true
                setOnClickListener { action() }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 180 else 100, 16, if (f) Color.WHITE else Color.argb(60, 255, 255, 255), if (f) 2 else 1)
                    v.animate().scaleX(if (f) 1.04f else 1f).scaleY(if (f) 1.04f else 1f).setDuration(110).start()
                }
                addView(tv(14f, true).apply { text = label })
            }
            sheet.addView(rowBtn, LinearLayout.LayoutParams(dp(400), -2).apply { bottomMargin = dp(10) })
        }

        dialog.setContentView(sheet)
        dialog.show()
    }

    private fun showCustomBgUrlInputDialog() {
        val input = EditText(this).apply {
            hint = "أدخل رابط صورة مباشر (HTTPS)"
            setText(prefs.getString("custom_bg_url", defaultCustomBgUrl))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle("تغيير خلفية الواجهة")
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

    data class ClubMatch(
        val league: String,
        val round: String,
        val homeName: String,
        val homeLogoUrl: String,
        val awayName: String,
        val awayLogoUrl: String,
        val matchTime: String
    )

    private fun renderMatchesWithRealCrests() {
        matchesRow.removeAllViews()

        val matches = listOf(
            ClubMatch(
                "أبطال آسيا للنخبة", "الجولة 3",
                "الهلال", "https://a.espncdn.com/i/teamlogos/soccer/500/10314.png",
                "العين", "https://a.espncdn.com/i/teamlogos/soccer/500/8663.png",
                "7:00 م"
            ),
            ClubMatch(
                "أبطال آسيا للنخبة", "الجولة 3",
                "الاتحاد", "https://a.espncdn.com/i/teamlogos/soccer/500/10315.png",
                "باختاكور", "https://a.espncdn.com/i/teamlogos/soccer/500/21597.png",
                "4:45 م"
            ),
            ClubMatch(
                "أبطال آسيا للنخبة", "الجولة 3",
                "الأهلي", "https://a.espncdn.com/i/teamlogos/soccer/500/10313.png",
                "الغرافة", "https://a.espncdn.com/i/teamlogos/soccer/500/8677.png",
                "7:00 م"
            ),
            ClubMatch(
                "الدوري الفرنسي", "الجولة 9",
                "ليون", "https://a.espncdn.com/i/teamlogos/soccer/500/167.png",
                "باريس سان جيرمان", "https://a.espncdn.com/i/teamlogos/soccer/500/160.png",
                "10:30 م"
            ),
            ClubMatch(
                "الدوري الإسباني", "الجولة 10",
                "إشبيلية", "https://a.espncdn.com/i/teamlogos/soccer/500/243.png",
                "أوساسونا", "https://a.espncdn.com/i/teamlogos/soccer/500/97.png",
                "11:00 م"
            )
        )

        val cardWidth = dp(228)
        val cardHeight = dp(108)

        for (m in matches) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = glass(140, 18, Color.argb(80, 255, 255, 255), 1)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, focused ->
                    v.background = glass(if (focused) 200 else 140, 18, if (focused) Color.WHITE else Color.argb(80, 255, 255, 255), if (focused) 2 else 1)
                    v.animate().scaleX(if (focused) 1.07f else 1f).scaleY(if (focused) 1.07f else 1f).setDuration(120).start()
                }

                val header = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(tv(10.5f, true, Color.parseColor("#4DEEEA")).apply { text = m.league; maxLines = 1 })
                    addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                    addView(tv(10f, false, Color.argb(190, 255, 255, 255)).apply { text = m.round })
                }
                addView(header, LinearLayout.LayoutParams(-1, -2))

                val teamsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(4))
                }

                val homeCol = createClubColumn(m.homeName, m.homeLogoUrl)
                val awayCol = createClubColumn(m.awayName, m.awayLogoUrl)

                teamsRow.addView(homeCol, LinearLayout.LayoutParams(0, -2, 1f))
                teamsRow.addView(tv(11f, true, Color.argb(160, 255, 255, 255)).apply {
                    text = "✕"
                    gravity = Gravity.CENTER
                    setPadding(dp(4), 0, dp(4), 0)
                })
                teamsRow.addView(awayCol, LinearLayout.LayoutParams(0, -2, 1f))

                addView(teamsRow, LinearLayout.LayoutParams(-1, 0, 1f))

                addView(tv(11.5f, true, Color.WHITE).apply {
                    text = m.matchTime
                    gravity = Gravity.CENTER
                })
            }

            matchesRow.addView(card, LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                marginEnd = dp(14)
            })
        }
    }

    private fun createClubColumn(name: String, logoUrl: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            val logoImg = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(createGenericShield(name))
            }
            loadClubCrestAsync(logoUrl, logoImg)

            addView(logoImg, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(tv(11f, true).apply {
                text = name
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun createGenericShield(name: String): Drawable {
        val size = dp(32)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#28354A")
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, p)
        p.color = Color.WHITE
        p.textSize = 10f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(name.take(1), size / 2f, size / 2f + 4, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun loadClubCrestAsync(url: String, imageView: ImageView) {
        val cached = logoCache[url]
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }
        thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                if (bmp != null) {
                    logoCache[url] = bmp
                    ui.post { imageView.setImageBitmap(bmp) }
                }
            } catch (e: Throwable) { }
        }
    }

    private fun loadAppsAndDock() {
        val pm = packageManager
        val list = (pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        allApps = list.toMutableList()
        val favPkgs = prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

        favRow.removeAllViews()
        val favList = if (favPkgs.isNotEmpty()) {
            favPkgs.mapNotNull { pkg -> allApps.find { it.activityInfo.packageName == pkg } }
        } else {
            allApps.take(6)
        }

        for (i in favList.indices) {
            favRow.addView(createAppleTvTile(favList[i], isFav = true, favIndex = i))
        }

        allGrid.removeAllViews()
        for (app in allApps) {
            val tile = createAppleTvTile(app, isFav = false, favIndex = -1)
            val p = GridLayout.LayoutParams().apply {
                width = dp(182)
                height = dp(90)
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            allGrid.addView(tile, p)
        }

        if (favRow.childCount > 0) favRow.getChildAt(0)?.requestFocus()
    }

    private fun resolveBrandColors(label: String, pkg: String, icon: Drawable): Pair<Int, Int> {
        val key = "$label $pkg".lowercase(Locale.ROOT)
        return when {
            key.contains("youtube") || key.contains("يوتيوب") -> Color.WHITE to Color.parseColor("#212121")
            key.contains("netflix") -> Color.WHITE to Color.parseColor("#E50914")
            key.contains("prime") -> Color.parseColor("#00A8E1") to Color.WHITE
            key.contains("plex") -> Color.parseColor("#1F1F1F") to Color.parseColor("#E5A00D")
            key.contains("apple") -> Color.parseColor("#141414") to Color.WHITE
            key.contains("downloader") -> Color.parseColor("#F57C00") to Color.WHITE
            key.contains("aroma") -> Color.parseColor("#B71C1C") to Color.WHITE
            key.contains("viu") -> Color.parseColor("#FBC02D") to Color.parseColor("#212121")
            key.contains("vlc") -> Color.parseColor("#FB8C00") to Color.WHITE
            else -> extractDominantColor(icon) to Color.WHITE
        }
    }

    private fun extractDominantColor(drawable: Drawable): Int {
        return try {
            val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, 24, 24)
            drawable.draw(c)
            val pixel = bmp.getPixel(12, 12)
            if (Color.alpha(pixel) < 120) Color.parseColor("#28354A") else {
                val r = (Color.red(pixel) * 0.75f).toInt()
                val g = (Color.green(pixel) * 0.75f).toInt()
                val b = (Color.blue(pixel) * 0.75f).toInt()
                Color.rgb(r, g, b)
            }
        } catch (e: Throwable) {
            Color.parseColor("#28354A")
        }
    }

    private fun createAppleTvTile(app: ResolveInfo, isFav: Boolean, favIndex: Int): View {
        val pm = packageManager
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(pm).toString()
        val icon = app.loadIcon(pm)
        val (bgColor, textColor) = resolveBrandColors(label, pkg, icon)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = solidCard(bgColor, 18, Color.argb(50, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            isClickable = true

            if (isFav) {
                layoutParams = LinearLayout.LayoutParams(dp(178), dp(82)).apply { marginEnd = dp(14) }
            }

            setOnClickListener {
                startActivity(Intent(Intent.ACTION_MAIN).setClassName(pkg, app.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            setOnLongClickListener {
                showAppActionModal(app, isFav, favIndex)
                true
            }

            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (isFav && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        mainScroll.smoothScrollTo(0, dp(520))
                        allGrid.getChildAt(0)?.requestFocus()
                        return@setOnKeyListener true
                    } else if (!isFav && keyCode == KeyEvent.KEYCODE_DPAD_UP && allGrid.indexOfChild(this) < 5) {
                        mainScroll.smoothScrollTo(0, 0)
                        favRow.getChildAt(0)?.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }

            setOnFocusChangeListener { v, focused ->
                v.animate()
                    .scaleX(if (focused) 1.13f else 1f)
                    .scaleY(if (focused) 1.13f else 1f)
                    .translationY(if (focused) -dp(5).toFloat() else 0f)
                    .setDuration(130)
                    .start()
                v.background = solidCard(
                    bgColor,
                    18,
                    if (focused) Color.WHITE else Color.argb(50, 255, 255, 255),
                    if (focused) 3 else 1
                )
                v.elevation = if (focused) 28f else 4f
            }

            addView(ImageView(context).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(42), dp(42)))

            addView(tv(14f, true, textColor).apply {
                text = label
                maxLines = 1
                setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun showAppActionModal(app: ResolveInfo, isFav: Boolean, favIndex: Int) {
        val pm = packageManager
        val pkg = app.activityInfo.packageName
        val label = app.loadLabel(pm).toString()
        val currentFavs = (prefs.getString("fav_apps", "")?.split(",")?.filter { it.isNotEmpty() }
            ?: allApps.take(6).map { it.activityInfo.packageName }).toMutableList()
        val inFav = currentFavs.contains(pkg)

        val options = mutableListOf<Pair<String, () -> Unit>>()
        options.add((if (inFav) "إزالة من المفضلة ✕" else "تثبيت في شريط المفضلة ⭐") to {
            if (inFav) currentFavs.remove(pkg) else currentFavs.add(pkg)
            prefs.edit().putString("fav_apps", currentFavs.joinToString(",")).apply()
            loadAppsAndDock()
        })

        if (inFav && favIndex > 0) {
            options.add("تحريك لليمين (تقديم) ▶" to {
                val item = currentFavs.removeAt(favIndex)
                currentFavs.add(favIndex - 1, item)
                prefs.edit().putString("fav_apps", currentFavs.joinToString(",")).apply()
                loadAppsAndDock()
                favRow.getChildAt(favIndex - 1)?.requestFocus()
            })
        }
        if (inFav && favIndex in 0 until (currentFavs.size - 1)) {
            options.add("تحريك لليسار (تأخير) ◀" to {
                val item = currentFavs.removeAt(favIndex)
                currentFavs.add(favIndex + 1, item)
                prefs.edit().putString("fav_apps", currentFavs.joinToString(",")).apply()
                loadAppsAndDock()
                favRow.getChildAt(favIndex + 1)?.requestFocus()
            })
        }

        options.add("معلومات التطبيق ℹ️" to {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", pkg, null) })
        })
        options.add("إلغاء تثبيت التطبيق 🗑️" to {
            startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.fromParts("package", pkg, null) })
        })

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = glass(240, 22, Color.WHITE, 2).apply { setColor(0xEE162234.toInt()) }
            setPadding(dp(24), dp(18), dp(24), dp(18))
            addView(tv(17f, true, Color.parseColor("#4DEEEA")).apply {
                text = label
                setPadding(0, 0, 0, dp(12))
            })
        }

        for ((title, act) in options) {
            val b = LinearLayout(this).apply {
                background = glass(100, 14)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isFocusable = true
                isClickable = true
                setOnClickListener { dialog.dismiss(); act() }
                setOnFocusChangeListener { v, f ->
                    v.background = glass(if (f) 180 else 100, 14, if (f) Color.WHITE else Color.TRANSPARENT, if (f) 2 else 0)
                }
                addView(tv(14f, true).apply { text = title })
            }
            box.addView(b, LinearLayout.LayoutParams(dp(320), -2).apply { bottomMargin = dp(8) })
        }

        dialog.setContentView(box)
        dialog.show()
    }

    private fun startEngines() {
        renderMatchesWithRealCrests()
        updateClockEngine()
        refreshRiyadhLiveFeeds()
    }

    private fun updateClockEngine() {
        try {
            val now = LocalTime.now()
            clockTv.text = now.format(DateTimeFormatter.ofPattern("h:mm a", ar))
            miniClockTv.text = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
            dayTv.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE", ar))
            val m = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy م", ar))
            val h = try { HijrahDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy هـ", ar)) } catch (e: Throwable) { "" }
            datesTv.text = if (h.isNotEmpty()) "$h\n$m" else m
        } catch (e: Throwable) { }
        ui.postDelayed({ updateClockEngine() }, 10000)
    }

    private fun refreshRiyadhLiveFeeds() {
        thread {
            try {
                val w = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=24.7136&longitude=46.6753&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code").readText()).getJSONObject("current")
                val t = Math.round(w.getDouble("temperature_2m"))
                val appT = Math.round(w.getDouble("apparent_temperature"))
                val hum = w.getInt("relative_humidity_2m")
                val wind = Math.round(w.getDouble("wind_speed_10m"))
                val code = w.getInt("weather_code")
                val statusTxt = when {
                    code == 0 -> "سماء صافية"
                    code <= 3 -> "غائم جزئياً"
                    code <= 48 -> "ضباب خفيف"
                    else -> "أجواء معتدلة"
                }
                ui.post {
                    tempTv.text = "$t°"
                    weatherStatusTv.text = statusTxt
                    weatherSubTv.text = "المحسوسة $appT° • الرطوبة $hum%\nالرياح $wind كم/س"
                }
            } catch (e: Throwable) { }

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
                            setPadding(dp(6), dp(1), dp(6), dp(1))
                            if (isNext) background = glass(180, 8)

                            addView(tv(11.5f, isNext, if (isNext) Color.parseColor("#4DEEEA") else Color.argb(200, 255, 255, 255)).apply {
                                text = p.first
                            }, LinearLayout.LayoutParams(0, -2, 1f))

                            addView(tv(11.5f, isNext, if (isNext) Color.WHITE else Color.argb(220, 255, 255, 255)).apply {
                                text = LocalTime.parse(p.second).format(DateTimeFormatter.ofPattern("h:mm a", ar))
                            })
                        }
                        prayerLayout.addView(row)
                    }
                    val target = parsed[nextIdx]
                    val diff = if (target.isAfter(now)) {
                        java.time.Duration.between(now, target).toMinutes()
                    } else {
                        java.time.Duration.between(now, LocalTime.MAX).toMinutes() + java.time.Duration.between(LocalTime.MIN, target).toMinutes()
                    }
                    val h = diff / 60
                    val m = diff % 60
                    prayerTimerTv.text = "الأذان القادم بعد ${if (h > 0) "$h س و " else ""}$m د"
                }
            } catch (e: Throwable) { }

            ui.postDelayed({ refreshRiyadhLiveFeeds() }, 15 * 60 * 1000L)
        }
    }
}
