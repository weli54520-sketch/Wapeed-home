package com.wm.iptv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
import com.wm.iptv.data.Channel
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Repo
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.ProgressLine
import com.wm.iptv.ui.SideItem
import com.wm.iptv.ui.clockText
import com.wm.iptv.ui.formatTime
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: PlayerVm, player: ExoPlayer, act: PlayerActivity) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = { v ->
                v.resizeMode = vm.resizeMode
                v.subtitleView?.let { sv ->
                    sv.setApplyEmbeddedStyles(true)
                    sv.setApplyEmbeddedFontSizes(false)
                    sv.setStyle(CaptionStyleCompat(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
                    val f = when (vm.subtitleSize) { 0 -> 0.8f; 2 -> 1.3f; 3 -> 1.6f; else -> 1.05f }
                    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * f)
                    sv.setBottomPaddingFraction(0.07f)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (vm.buffering && vm.seekTarget == null) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(46.dp))
        }

        if (vm.isLive) LiveOverlay(vm) else VodOverlay(vm)

        vm.message?.let { msg ->
            Text(msg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).padding(top = 110.dp)
                    .background(Color(0xCC1C1C1E), RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
        }

        when (vm.panel) {
            Panel.TRACKS -> TracksPanel(vm, act)
            Panel.CHANNELS -> ChannelsPanel(vm, act)
            null -> {}
        }
    }
}

// ───────────────────────── الأفلام والمسلسلات ─────────────────────────

@Composable
private fun VodOverlay(vm: PlayerVm) {
    val visible = vm.overlay || vm.seekTarget != null || (!vm.playing && !vm.buffering)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(260))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(330.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))

                // العنوان والمسارات
                Row(Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 30.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(vm.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (vm.subtitle.isNotEmpty()) Text(vm.subtitle, fontSize = 15.sp, color = C.Text3)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrackChip("الصوت: " + currentLabel(vm.tracks, MC.TRACK_TYPE_AUDIO, "تلقائي"))
                        TrackChip("الترجمة: " + currentLabel(vm.tracks, MC.TRACK_TYPE_TEXT, "بدون"))
                    }
                }

                // شريط التقدم (من اليسار لليمين عشان زر اليمين = تقديم)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 56.dp).padding(bottom = 24.dp)) {
                        SeekBar(vm)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(formatTime(vm.seekTarget ?: vm.position), fontSize = 14.sp, color = C.Text2)
                            Spacer(Modifier.weight(1f))
                            if (vm.duration > 0) Text("-" + formatTime(vm.duration - (vm.seekTarget ?: vm.position)), fontSize = 14.sp, color = C.Text2)
                        }
                        Spacer(Modifier.height(14.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)) {
                                if (vm.barMode) {
                                    Hint("◀ ▶", "٥ دقايق")
                                    Hint("OK", if (vm.seekTarget != null) "تأكيد" else "تشغيل / إيقاف")
                                    Hint("▲", "رجوع للتقديم بالثواني")
                                } else {
                                    Hint("◀ ▶", "ضغطة = ٥ ثواني")
                                    Hint("ضغط مستمر", "تسريع ×٢ ×٤ ×٨")
                                    Hint("▼", "شريط التقدم (٥ دقايق)")
                                    Hint("▲", "الصوت والترجمة")
                                }
                            }
                        }
                    }
                }
            }
        }

        // فقاعة التقديم في النص
        val target = vm.seekTarget
        if (target != null) {
            val delta = target - vm.seekOrigin
            val sign = if (delta >= 0) "+" else "-"
            val abs = kotlin.math.abs(delta)
            val txt = if (abs < 60_000) "$sign${abs / 1000} ث" else "$sign${formatTime(abs)}"
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.align(Alignment.Center).background(Color(0x8C000000), RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val step = if (vm.barMode) "5د" else "5"
                    if (delta < 0) com.wm.iptv.ui.SeekGlyph(false, step, 40.dp)
                    Text(txt, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (vm.speedLabel.isNotEmpty()) Text(vm.speedLabel, fontSize = 15.sp, color = C.Accent, fontWeight = FontWeight.Bold)
                    if (delta >= 0) com.wm.iptv.ui.SeekGlyph(true, step, 40.dp)
                }
            }
        } else if (!vm.playing && !vm.buffering && vm.message == null) {
            Box(Modifier.align(Alignment.Center).size(84.dp).background(Color(0x8C000000), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ico.Pause, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        // الحلقة التالية
        val next = vm.nextEp
        if (next != null && vm.countdown >= 0) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 150.dp)
                    .background(Color(0xF21C1C1E), RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("الحلقة التالية خلال ${vm.countdown}", fontSize = 13.sp, color = C.Accent, fontWeight = FontWeight.SemiBold)
                Text("الموسم ${next.season} · الحلقة ${next.number}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("OK تشغيل الآن  ·  رجوع إلغاء", fontSize = 13.sp, color = C.Text3)
            }
        }
    }
}

@Composable
private fun SeekBar(vm: PlayerVm) {
    val dur = vm.duration
    val pos = vm.position
    val target = vm.seekTarget
    BoxWithConstraints(Modifier.fillMaxWidth().height(if (target != null && vm.thumbsOn) 200.dp else 24.dp)) {
        val w = maxWidth
        fun frac(v: Long) = if (dur > 0) (v.toFloat() / dur).coerceIn(0f, 1f) else 0f
        // الصورة فوق مكان التقديم
        if (target != null && vm.thumbsOn) {
            val tw = 256.dp
            val x = (w * frac(target) - tw / 2).coerceIn(0.dp, (w - tw).coerceAtLeast(0.dp))
            Column(Modifier.offset(x = x).width(tw), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(tw).height(144.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val img = vm.thumb
                    if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else CircularProgressIndicator(color = C.Text3, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(formatTime(target), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        val bar = vm.barMode
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(24.dp)) {
            // لما يكون الشريط محدد: أعرض ومحاط بإطار
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(if (bar) 10.dp else 6.dp).clip(CircleShape).background(Color(0x38FFFFFF))
                .then(if (bar) Modifier.border(1.5.dp, Color(0x99FFFFFF), CircleShape) else Modifier)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac(vm.buffered)).background(Color(0x5CFFFFFF)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac(pos)).clip(CircleShape).background(Color.White))
            }
            if (target != null) {
                Box(Modifier.align(Alignment.CenterStart).offset(x = w * frac(target) - 9.dp).size(18.dp).background(C.Accent, CircleShape)
                    .border(3.dp, Color(0x59000000), CircleShape))
            } else if (bar) {
                Box(Modifier.align(Alignment.CenterStart).offset(x = w * frac(pos) - 10.dp).size(20.dp).background(Color.White, CircleShape)
                    .border(3.dp, Color(0x59000000), CircleShape))
            }
        }
    }
}

@Composable
private fun TrackChip(text: String) {
    Text(text, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.background(Color(0x24FFFFFF), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 7.dp))
}

@Composable
private fun Hint(key: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(key, fontSize = 12.sp, color = C.Text, modifier = Modifier.border(1.dp, C.Dim, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 13.sp, color = C.Text3)
    }
}

// ───────────────────────── المباشر ─────────────────────────

@Composable
private fun LiveOverlay(vm: PlayerVm) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(10_000) } }
    val ch = vm.zapPreview ?: vm.channel
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(vm.banner || vm.zapPreview != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))))) {
                if (ch != null) {
                    val epgNow = vm.epg.firstOrNull { it.start <= now && it.end > now }
                    val epgNext = vm.epg.firstOrNull { it.start >= (epgNow?.end ?: now) && it != epgNow }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 56.dp, vertical = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x24FFFFFF)), contentAlignment = Alignment.Center) {
                            if (ch.icon.isNotBlank()) AsyncImage(ch.icon, null, contentScale = ContentScale.Fit, modifier = Modifier.size(80.dp))
                            else Text(ch.name.take(2), fontSize = 22.sp, color = C.Text3)
                        }
                        Spacer(Modifier.width(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.num.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Accent)
                                Spacer(Modifier.width(14.dp))
                                Text(ch.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                if (ch.id == vm.channel?.id && vm.favorite) { Spacer(Modifier.width(10.dp)); Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(18.dp)) }
                                if (vm.zapPreview != null) { Spacer(Modifier.width(14.dp)); Text("OK للتشغيل الآن", fontSize = 13.sp, color = C.Text3) }
                            }
                            if (epgNow != null) {
                                Row {
                                    Text("${clockText(epgNow.start)} – ${clockText(epgNow.end)}", fontSize = 14.sp, color = C.Text3)
                                    Spacer(Modifier.width(12.dp))
                                    Text(epgNow.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                ProgressLine(((now - epgNow.start).toFloat() / (epgNow.end - epgNow.start)).coerceIn(0f, 1f),
                                    Modifier.width(520.dp).padding(vertical = 4.dp), color = Color.White, height = 3.dp)
                                if (epgNext != null) Text("التالي ${clockText(epgNext.start)} · ${epgNext.title}", fontSize = 14.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("لا يوجد دليل برامج", fontSize = 14.sp, color = C.Muted)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(clockText(now), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text(vm.listTitle, fontSize = 13.sp, color = C.Text3)
                            Text("▲▼ تقليب · OK القائمة · زر القائمة: مفضلة", fontSize = 12.sp, color = C.Muted)
                        }
                    }
                }
            }
        }
        if (vm.digits.isNotEmpty()) {
            Text(vm.digits, fontSize = 52.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)
                    .background(Color(0xE61C1C1E), RoundedCornerShape(18.dp)).padding(horizontal = 30.dp, vertical = 8.dp))
        }
    }
}

@Composable
internal fun ChannelsPanel(vm: PlayerVm, act: PlayerActivity) {
    val list = vm.channels
    val cur = vm.channel
    val state = rememberLazyListState()
    val fr = remember { FocusRequester() }
    val curIndex = list.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0)
    val prev = remember { Repo.catalog.value.channelById[Repo.store.previousChannel] }
    LaunchedEffect(Unit) {
        state.scrollToItem((curIndex - 3).coerceAtLeast(0))
        delay(40)
        try { fr.requestFocus() } catch (_: Exception) {}
    }
    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
        Column(Modifier.align(Alignment.CenterStart).width(470.dp).fillMaxHeight().background(Color(0xF2111114)).padding(horizontal = 16.dp, vertical = 24.dp)) {
            Text(vm.listTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 12.dp))
            com.wm.iptv.ui.SmoothFocusScroll { LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                if (prev != null && prev.id != cur?.id) {
                    item(key = "prev") {
                        SideItem("القناة السابقة: ${prev.name}", icon = Ico.Back, onClick = { act.previousChannel() })
                    }
                }
                itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    PanelChannel(c, c.id == cur?.id, if (i == curIndex) Modifier.focusRequester(fr) else Modifier,
                        onClick = { act.selectChannel(c) },
                        onLong = { Repo.toggleFavorite(Kind.LIVE, c.id); if (c.id == cur?.id) vm.favorite = Repo.isFavorite(Kind.LIVE, c.id) })
                }
            } }
        }
    }
}

@Composable
private fun PanelChannel(c: Channel, playing: Boolean, mod: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    SideItem(
        "${c.num}   ${c.name}", mod, selected = playing, trailing = if (playing) "يعرض الآن" else "",
        onClick = onClick, onLongClick = onLong,
    )
}

// ───────────────────────── الصوت والترجمة ─────────────────────────

@Composable
internal fun TracksPanel(vm: PlayerVm, act: PlayerActivity) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); try { fr.requestFocus() } catch (_: Exception) {} }
    val audio = vm.tracks.groups.filter { it.type == MC.TRACK_TYPE_AUDIO }
    val text = vm.tracks.groups.filter { it.type == MC.TRACK_TYPE_TEXT }
    var first = true
    fun firstMod(): Modifier = if (first) { first = false; Modifier.focusRequester(fr) } else Modifier

    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
        LazyColumn(
            Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight().background(Color(0xF2111114)),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // اختصار: الحلقة التالية في أي وقت (للمسلسلات اللي بعدها حلقة)
            if (vm.nextEp != null) item {
                SideItem("الحلقة التالية", icon = Ico.Next, onClick = { vm.panel = null; act.nextEpisodeNow() })
            }
            item { PanelHeader("الصوت") }
            if (audio.isEmpty()) item { Text("مسار واحد", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(14.dp)) }
            audio.forEach { g ->
                for (i in 0 until g.length) {
                    if (!g.isTrackSupported(i)) continue
                    val m = firstMod()
                    item { SideItem(trackName(g.getTrackFormat(i), i), m, icon = Ico.Audio, selected = g.isTrackSelected(i), onClick = { act.selectTrack(g, i) }) }
                }
            }
            item { PanelHeader("الترجمة") }
            val anyText = text.any { it.isSelected }
            val offMod = firstMod()
            item { SideItem("بدون ترجمة", offMod, icon = Ico.Subs, selected = !anyText, onClick = { act.disableSubtitles() }) }
            text.forEach { g ->
                for (i in 0 until g.length) {
                    if (!g.isTrackSupported(i)) continue
                    item { SideItem(trackName(g.getTrackFormat(i), i), icon = Ico.Subs, selected = g.isTrackSelected(i), onClick = { act.selectTrack(g, i) }) }
                }
            }
            item { PanelHeader("العرض") }
            item {
                SideItem("حجم الترجمة: " + when (vm.subtitleSize) { 0 -> "صغير"; 2 -> "كبير"; 3 -> "كبير جداً"; else -> "متوسط" },
                    onClick = { act.cycleSubtitleSize() })
            }
            item {
                SideItem("أبعاد الصورة: " + when (vm.resizeMode) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "تكبير (بدون حواف)"
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "تمديد"
                    else -> "أصلي"
                }, onClick = { act.cycleResize() })
            }
        }
    }
}

@Composable
private fun PanelHeader(t: String) {
    Text(t, fontSize = 13.sp, color = C.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp))
}

private val arLocale = Locale("ar")

private fun langName(code: String?): String {
    if (code.isNullOrBlank() || code == "und") return ""
    return try {
        val l = Locale.forLanguageTag(code)
        l.getDisplayLanguage(arLocale).ifBlank { code }
    } catch (_: Exception) { code }
}

private fun trackName(f: Format, i: Int): String {
    val parts = ArrayList<String>()
    val lang = langName(f.language)
    val label = f.label?.takeIf { it.isNotBlank() }
    parts.add(label ?: lang.ifBlank { "مسار ${i + 1}" })
    if (label != null && lang.isNotBlank() && !label.contains(lang)) parts.add(lang)
    when {
        f.channelCount >= 8 -> parts.add("7.1")
        f.channelCount >= 6 -> parts.add("5.1")
        f.channelCount == 2 -> parts.add("ستيريو")
    }
    f.sampleMimeType?.let { m ->
        val codec = when {
            m.contains("eac3") -> "E-AC3"
            m.contains("ac3") -> "AC3"
            m.contains("dts") -> "DTS"
            m.contains("aac") || m.contains("mp4a") -> "AAC"
            m.contains("opus") -> "Opus"
            m.contains("subrip") -> "SRT"
            m.contains("pgs") -> "PGS"
            m.contains("ass") || m.contains("ssa") -> "ASS"
            else -> ""
        }
        if (codec.isNotEmpty()) parts.add(codec)
    }
    return parts.joinToString(" · ")
}

private fun currentLabel(tracks: Tracks, type: Int, none: String): String {
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) if (g.isTrackSelected(i)) {
            val f = g.getTrackFormat(i)
            return langName(f.language).ifBlank { f.label ?: none }
        }
    }
    return none
}
