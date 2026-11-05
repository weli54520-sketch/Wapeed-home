package com.wm.iptv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.R

object C {
    val Bg = Color(0xFF0A0A0C)
    val Panel = Color(0xFF111114)
    val Card = Color(0xFF1C1C1E)
    val Line = Color(0xFF2C2C2E)
    val Text = Color(0xFFF5F5F7)
    val Text2 = Color(0xFFD1D1D6)
    val Text3 = Color(0xFFAEAEB2)
    val Muted = Color(0xFF8E8E93)
    val Dim = Color(0xFF636366)
    val Accent = Color(0xFF64D2FF)
    val Star = Color(0xFFFFD60A)
    val Live = Color(0xFFFF453A)
    val Glass = Color(0x24FFFFFF)
    val Scrim = Color(0xCC0A0A0C)

    /** ألوان هادئة للبطاقات قبل ما تتحمل الصورة */
    private val tones = listOf(
        0xFF2B2230, 0xFF1F2D2A, 0xFF33291F, 0xFF1E2636, 0xFF2E1F22,
        0xFF262A1E, 0xFF22252E, 0xFF30262A, 0xFF1D2B33, 0xFF332E1F,
    ).map { Color(it) }

    fun tone(seed: Int): Color = tones[Math.floorMod(seed, tones.size)]
}

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

@Composable
fun AppTheme(scaleToTv: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Text, onPrimary = C.Bg, background = C.Bg, surface = C.Panel,
            onSurface = C.Text, onBackground = C.Text, secondary = C.Accent,
        )
    ) {
        // التصميم مبني على شاشة 1280×720؛ نكبّر أو نصغّر كل شي بنفس النسبة
        // عشان يطلع نفس الشكل على أي تلفزيون أو بروجكتر مهما كانت كثافة الشاشة.
        val cfg = LocalConfiguration.current
        val dm = LocalContext.current.resources.displayMetrics
        val scaled = remember(cfg.screenWidthDp, cfg.screenHeightDp, dm.widthPixels, dm.heightPixels) {
            val w = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
            val h = minOf(dm.widthPixels, dm.heightPixels).toFloat()
            Density(density = minOf(w / 1280f, h / 720f), fontScale = 1f)
        }
        val density = if (scaleToTv) scaled else LocalDensity.current
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides density) {
            ProvideTextStyle(TextStyle(fontFamily = Plex, color = C.Text, fontSize = 16.sp)) {
                content()
            }
        }
    }
}

/** أيقونات خطية بسيطة (بدل مكتبة الأيقونات الكبيرة) */
object Ico {
    private fun icon(name: String, vararg paths: String, stroke: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            if (stroke) b.addPath(addPathNodes(p), stroke = SolidColor(Color.White), strokeLineWidth = 2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round)
            else b.addPath(addPathNodes(p), fill = SolidColor(Color.White))
        }
        return b.build()
    }

    val Play = icon("play", "M7,4v16l13,-8z")
    val Pause = icon("pause", "M6,4h4v16h-4z", "M14,4h4v16h-4z")
    val Search = icon("search", "M11,4a7,7 0,1 0,0.01 0z", "M20,20l-3.5,-3.5", stroke = true)
    val Gear = icon("gear", "M12,9a3,3 0,1 0,0.01 0z", "M12,2v3M12,19v3M4.9,4.9l2.1,2.1M17,17l2.1,2.1M2,12h3M19,12h3M4.9,19.1l2.1,-2.1M17,7l2.1,-2.1", stroke = true)
    val Star = icon("star", "M12,3l2.7,5.6l6.1,0.9l-4.4,4.3l1,6.1l-5.4,-2.9l-5.4,2.9l1,-6.1l-4.4,-4.3l6.1,-0.9z")
    val StarLine = icon("starline", "M12,3l2.7,5.6l6.1,0.9l-4.4,4.3l1,6.1l-5.4,-2.9l-5.4,2.9l1,-6.1l-4.4,-4.3l6.1,-0.9z", stroke = true)
    val Check = icon("check", "M5,12l5,5l9,-10", stroke = true)
    val Grid = icon("grid", "M4,4h6v6h-6z", "M14,4h6v6h-6z", "M4,14h6v6h-6z", "M14,14h6v6h-6z", stroke = true)
    val Sparkle = icon("new", "M12,3v4M12,17v4M3,12h4M17,12h4M5.6,5.6l2.8,2.8M15.6,15.6l2.8,2.8M5.6,18.4l2.8,-2.8M15.6,8.4l2.8,-2.8", stroke = true)
    val Clock = icon("clock", "M12,3a9,9 0,1 0,0.01 0z", "M12,7v5l3,2", stroke = true)
    val Mic = icon("mic", "M12,3a3,3 0,0 0,-3 3v6a3,3 0,0 0,6 0v-6a3,3 0,0 0,-3 -3z", "M5,11a7,7 0,0 0,14 0M12,18v3", stroke = true)
    val Forward = icon("ff", "M4,5v14l8,-7z", "M12,5v14l8,-7z")
    val Rewind = icon("rw", "M20,5v14l-8,-7z", "M12,5v14l-8,-7z")
    val Next = icon("next", "M5,5v14l10,-7z", "M17,5h2v14h-2z")
    val Subs = icon("subs", "M3,5h18v14h-18z", "M7,13h4M13,13h4M7,16h10", stroke = true)
    val Audio = icon("audio", "M4,9h4l5,-4v14l-5,-4h-4z", "M16,9a4,4 0,0 1,0 6", stroke = true)
    val Refresh = icon("refresh", "M20,12a8,8 0,1 1,-2.3 -5.7", "M20,4v5h-5", stroke = true)
    val User = icon("user", "M12,4a4,4 0,1 0,0.01 0z", "M4,20a8,8 0,0 1,16 0", stroke = true)
    val Plus = icon("plus", "M12,5v14M5,12h14", stroke = true)
    val Trash = icon("trash", "M4,7h16M9,7v-3h6v3M6,7l1,13h10l1,-13", stroke = true)
    val Eye = icon("eye", "M2,12c3,-5 7,-7 10,-7s7,2 10,7c-3,5 -7,7 -10,7s-7,-2 -10,-7z", "M12,9a3,3 0,1 0,0.01 0z", stroke = true)
    val EyeOff = icon("eyeoff", "M2,12c3,-5 7,-7 10,-7s7,2 10,7c-3,5 -7,7 -10,7s-7,-2 -10,-7z", "M4,4l16,16", stroke = true)
    val Back = icon("back", "M9,6l6,6l-6,6", stroke = true)
    val List = icon("list", "M8,6h12M8,12h12M8,18h12M4,6h0.01M4,12h0.01M4,18h0.01", stroke = true)
    val Tv = icon("tv", "M3,6h18v12h-18z", "M8,21h8", stroke = true)
    val Close = icon("close", "M6,6l12,12M18,6l-12,12", stroke = true)
    val Pip = icon("pip", "M3,5h18v14h-18z", "M12,12h7v5h-7z", stroke = true)
    val Expand = icon("expand", "M4,9v-5h5M20,9v-5h-5M4,15v5h5M20,15v5h-5", stroke = true)
    val Home = icon("home", "M3,11l9,-7l9,7v9h-6v-6h-6v6h-6z", stroke = true)
    val Film = icon("film", "M4,4h16v16h-16z", "M8,4v16M16,4v16M4,9h4M4,15h4M16,9h4M16,15h4", stroke = true)
    val Stack = icon("stack", "M4,8h16v12h-16z", "M7,5h10M9,2h6", stroke = true)
    val Sort = icon("sort", "M4,7h16M7,12h10M10,17h4", stroke = true)
    val Lock = icon("lock", "M6,11h12v9h-12z", "M8,11v-3a4,4 0,0 1,8 0v3", stroke = true)
    val Plus10 = icon("plus5", "M12,5a7,7 0,1 1,-7 7", "M12,2v6l4,-3z")
    val Minus10 = icon("minus5", "M12,5a7,7 0,1 0,7 7", "M12,2v6l-4,-3z")
}
