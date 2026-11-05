@file:OptIn(ExperimentalFoundationApi::class)

package com.wm.iptv.ui

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ───────────────────────── الضغط بالريموت ─────────────────────────

/**
 * ضغطة OK = onClick، ضغط مطوّل أو زر القائمة = onLongClick.
 * ما ينفذ الضغطة إلا إذا شاف KeyDown بنفسه — عشان ضغطة من الشاشة
 * السابقة ما تنتقل للزر الجديد بالغلط.
 */
fun Modifier.onSelect(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    var downSeen by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    this
        .onPreviewKeyEvent { ev ->
            val ne = ev.nativeKeyEvent
            val code = ne.keyCode
            val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
            when {
                ok && ev.type == KeyEventType.KeyDown -> {
                    if (ne.repeatCount == 0) { downSeen = true; longDone = false }
                    else if (downSeen && !longDone && onLongClick != null && (ne.isLongPress || ne.repeatCount >= 6)) {
                        longDone = true; onLongClick()
                    }
                    true
                }
                ok && ev.type == KeyEventType.KeyUp -> {
                    if (downSeen && !longDone) onClick()
                    downSeen = false; longDone = false
                    true
                }
                code == KeyEvent.KEYCODE_MENU && onLongClick != null -> {
                    if (ev.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                else -> false
            }
        }
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

// ───────────────────────── تذكر مكان التركيز ─────────────────────────

class FocusMemory(private val saved: MutableState<String?>) {
    private val map = HashMap<String, FocusRequester>()
    var last: String?
        get() = saved.value
        set(v) { saved.value = v }

    fun req(key: String): FocusRequester = map.getOrPut(key) { FocusRequester() }

    fun restore(): Boolean {
        val k = last ?: return false
        val r = map[k] ?: return false
        return try { r.requestFocus(); true } catch (_: Exception) { false }
    }

    fun focus(key: String): Boolean = try { req(key).requestFocus(); true } catch (_: Exception) { false }
}

@Composable
fun rememberFocusMemory(id: String): FocusMemory {
    val saved = rememberSaveable(id) { mutableStateOf<String?>(null) }
    return remember(id) { FocusMemory(saved) }
}

fun Modifier.track(mem: FocusMemory, key: String): Modifier =
    this.focusRequester(mem.req(key)).onFocusChanged { if (it.isFocused) mem.last = key }

// ───────────────────────── بطاقة بتأثير Apple TV ─────────────────────────

@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = 1.08f,
    onFocus: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(170), label = "s")
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(170), label = "l")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow((22 * lift).dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .onFocusChanged { focused = it.isFocused; onFocus?.invoke(it.isFocused) }
            .onSelect(onClick, onLongClick)
            .clip(shape)
    ) {
        content(focused)
        if (lift > 0.01f) {
            Box(Modifier.matchParentSize().border(1.5.dp, Color.White.copy(alpha = 0.28f * lift), shape))
        }
    }
}

@Composable
fun Poster(
    title: String,
    image: String,
    seed: Int,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    badge: String? = null,
    progress: Float = 0f,
    favorite: Boolean = false,
    focusMod: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(if (width == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusBox(
            modifier = focusMod.fillMaxWidth().aspectRatio(2f / 3f),
            onClick = onClick, onLongClick = onLongClick, focusScale = 1.1f,
            onFocus = { focused = it },
        ) {
            Art(image, seed, title)
            if (badge != null) {
                Text(
                    badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Bg,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(C.Accent, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
            if (favorite) {
                Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp))
            }
            if (progress > 0.01f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        Spacer(Modifier.height(if (focused) 14.dp else 8.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            fontSize = 13.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) Color.White else C.Muted, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Landscape(
    title: String,
    sub: String,
    image: String,
    seed: Int,
    modifier: Modifier = Modifier,
    width: Dp = 236.dp,
    progress: Float = 0f,
    focusMod: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        FocusBox(
            modifier = focusMod.fillMaxWidth().aspectRatio(16f / 9f),
            onClick = onClick, onLongClick = onLongClick, onFocus = { focused = it },
        ) {
            Art(image, seed, title)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
            if (progress > 0.01f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(if (focused) 12.dp else 7.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.White else C.Text2)
        if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = C.Muted)
    }
}

/** صورة مع لون هادئ وحرف أول لو ما فيه صورة أو ما تحملت */
@Composable
fun BoxScope.Art(url: String, seed: Int, title: String, contentScale: ContentScale = ContentScale.Crop) {
    Box(Modifier.matchParentSize().background(C.tone(seed)), contentAlignment = Alignment.Center) {
        Text(title.take(1), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.18f))
    }
    if (url.isNotBlank()) {
        AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = C.Accent, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

/** زر حبة: يصير أبيض لما يتحدد */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    selected: Boolean = false,
    big: Boolean = false,
    onClick: () -> Unit,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (big) 12.dp else 999.dp)
    FocusBox(modifier, onClick = onClick, shape = shape, focusScale = if (big) 1.06f else 1.04f,
        onFocus = { focused = it; onFocus?.invoke(it) }) {
        val bg = when { focused -> C.Text; selected -> Color.White.copy(alpha = 0.22f); primary -> Color.White.copy(alpha = 0.2f); else -> C.Glass }
        val fg = if (focused) C.Bg else C.Text
        Row(
            Modifier.background(bg).padding(horizontal = if (big) 26.dp else 18.dp, vertical = if (big) 13.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (big) 18.dp else 16.dp))
            if (text.isNotEmpty()) Text(text, color = fg, fontSize = if (big) 17.sp else 15.sp,
                fontWeight = if (focused || primary || selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}

/** عنصر قائمة جانبية */
@Composable
fun SideItem(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    trailing: String = "",
    onFocus: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    FocusBox(modifier.fillMaxWidth(), onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(12.dp), focusScale = 1.03f,
        onFocus = { focused = it; onFocus?.invoke(it) }) {
        val bg = when { focused -> C.Text; selected -> Color.White.copy(alpha = 0.12f); else -> Color.Transparent }
        val fg = when { focused -> C.Bg; selected -> Color.White; else -> C.Text3 }
        Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Text(text, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (trailing.isNotEmpty()) Text(trailing, color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Text2,
        modifier = modifier.border(1.dp, C.Muted, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 0.dp))
}

@Composable
fun Dot() { Text("·", color = C.Dim, fontSize = 15.sp) }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun CenterMessage(title: String, sub: String = "", content: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(sub, fontSize = 15.sp, color = C.Text3, textAlign = TextAlign.Center, modifier = Modifier.width(560.dp))
        }
        Spacer(Modifier.height(22.dp))
        content()
    }
}

// ───────────────────────── تنسيق ─────────────────────────

fun formatTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.US, "%d:%02d", m, s)
}

fun formatDuration(secs: Int): String {
    if (secs <= 0) return ""
    val h = secs / 3600; val m = (secs % 3600) / 60
    return when { h > 0 && m > 0 -> "$h س $m د"; h > 0 -> "$h س"; else -> "$m د" }
}

fun remainingText(p: com.wm.iptv.data.Progress): String {
    val left = ((p.duration - p.position) / 60000).toInt()
    return when { left <= 0 -> "قارب على الانتهاء"; left >= 60 -> "متبقي ${left / 60} س ${left % 60} د"; else -> "متبقي $left د" }
}

private val clockFmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale("ar"))
fun clockText(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(clockFmt)
