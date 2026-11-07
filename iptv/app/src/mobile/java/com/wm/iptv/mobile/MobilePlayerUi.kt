package com.wm.iptv.mobile

import android.content.Context
import android.media.AudioManager
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.wm.iptv.player.ChannelsPanel
import com.wm.iptv.player.Panel
import com.wm.iptv.player.PlayerVm
import com.wm.iptv.player.TracksPanel
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.clockText
import com.wm.iptv.ui.formatTime
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun MobilePlayerScreen(vm: PlayerVm, player: ExoPlayer, act: MobilePlayerActivity) {
    val audio = remember { act.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    // مؤشر الصوت / الإضاءة
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    // فقاعة الضغطتين
    var tapSeek by remember { mutableLongStateOf(0L) }
    var tapSeekShown by remember { mutableIntStateOf(0) }
    LaunchedEffect(tapSeekShown) { if (tapSeekShown > 0) { delay(800); tapSeek = 0L } }
    LaunchedEffect(gestureLabel) { if (gestureLabel != null) { delay(900); gestureLabel = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
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
                    sv.setApplyEmbeddedFontSizes(false)
                    sv.setStyle(CaptionStyleCompat(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
                    val f = when (vm.subtitleSize) { 0 -> 0.8f; 2 -> 1.3f; 3 -> 1.6f; else -> 1.05f }
                    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * f)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (act.inPip) return@Box

        // ───── طبقة اللمس ─────
        var dragMode by remember { mutableIntStateOf(0) } // 1 تقديم، 2 صوت، 3 إضاءة
        var dragStartPos by remember { mutableLongStateOf(0L) }
        var dragAcc by remember { mutableStateOf(Offset.Zero) }
        var startLevel by remember { mutableFloatStateOf(0f) }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .pointerInput(vm.isLive) {
                    detectTapGestures(
                        onTap = { vm.overlay = !vm.overlay; if (vm.isLive) { if (vm.overlay) act.showLiveBanner() else vm.banner = false }; act.keepOverlay() },
                        onDoubleTap = { o ->
                            if (vm.isLive) { act.togglePlayPause(); return@detectTapGestures }
                            // يسار الشاشة = رجوع، يمين = تقديم (بغض النظر عن اتجاه اللغة)
                            val dir = if (o.x < size.width / 2f) -1 else 1
                            val step = 5_000L * dir
                            act.seekBy(step)
                            tapSeek = if (tapSeek != 0L && (tapSeek > 0) == (dir > 0)) tapSeek + step else step
                            tapSeekShown++
                        },
                    )
                }
                .pointerInput(vm.isLive) {
                    detectDragGestures(
                        onDragStart = { o ->
                            dragMode = 0; dragAcc = Offset.Zero
                            dragStartPos = player.currentPosition
                            startLevel = if (o.x < size.width / 2f) act.window.attributes.screenBrightness.let { if (it < 0) 0.5f else it }
                            else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            dragStartX = o.x
                        },
                        onDragEnd = {
                            if (dragMode == 1) {
                                if (vm.isLive) { if (abs(dragAcc.x) > 120) act.zapBy(if (dragAcc.x < 0) 1 else -1) }
                                else act.commitSeekNow()
                            }
                            dragMode = 0
                        },
                        onDragCancel = { if (dragMode == 1 && !vm.isLive) act.cancelSeekNow(); dragMode = 0 },
                        onDrag = { ch, d ->
                            ch.consume()
                            dragAcc += d
                            if (dragMode == 0 && (abs(dragAcc.x) > 24 || abs(dragAcc.y) > 24)) {
                                dragMode = if (abs(dragAcc.x) > abs(dragAcc.y)) 1 else if (dragStartX < size.width / 2f) 3 else 2
                            }
                            when (dragMode) {
                                1 -> if (!vm.isLive) {
                                    // عرض الشاشة كامل = ٣ دقايق
                                    val ms = (dragAcc.x / size.width * 180_000).toLong()
                                    act.previewSeek(dragStartPos + ms)
                                }
                                2 -> {
                                    val v = (startLevel - dragAcc.y / (size.height * 0.8f)).coerceIn(0f, 1f)
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt(), 0)
                                    gestureLabel = "الصوت ${(v * 100).toInt()}٪"
                                }
                                3 -> {
                                    val v = (startLevel - dragAcc.y / (size.height * 0.8f)).coerceIn(0.02f, 1f)
                                    val lp = act.window.attributes; lp.screenBrightness = v; act.window.attributes = lp
                                    gestureLabel = "الإضاءة ${(v * 100).toInt()}٪"
                                }
                            }
                        },
                    )
                }
        ) {}

        if (vm.buffering && vm.seekTarget == null) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(44.dp))

        // ───── الأزرار ─────
        val visible = vm.overlay || vm.seekTarget != null || (!vm.playing && !vm.buffering)
        AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))))

                // فوق
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MIconButton(Ico.Back, "رجوع") { act.finish() }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        val title = if (vm.isLive) (vm.zapPreview ?: vm.channel)?.name ?: vm.title else vm.title
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (vm.subtitle.isNotEmpty()) Text(vm.subtitle, fontSize = 12.sp, color = C.Text3)
                        if (vm.isLive) {
                            val now = System.currentTimeMillis()
                            vm.epg.firstOrNull { it.start <= now && it.end > now }?.let { Text("${clockText(it.start)} · ${it.title}", fontSize = 12.sp, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    if (vm.isLive) {
                        MIconButton(if (vm.favorite) Ico.Star else Ico.StarLine, "مفضلة", tint = if (vm.favorite) C.Star else C.Text) { act.toggleFavoriteChannel() }
                        MIconButton(Ico.List, "القنوات") { vm.panel = Panel.CHANNELS; act.keepOverlay() }
                    }
                    MIconButton(Ico.Subs, "الصوت والترجمة") { vm.panel = Panel.TRACKS; act.keepOverlay() }
                    MIconButton(Ico.Expand, "أبعاد الصورة") { act.cycleResize() }
                    MIconButton(Ico.Pip, "نافذة صغيرة") { act.enterPip() }
                }

                // النص
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.isLive) {
                            Round(Ico.Rewind, "القناة السابقة", 50) { act.zapBy(-1) }
                            Round(if (vm.playing) Ico.Pause else Ico.Play, "تشغيل", 68) { act.togglePlayPause() }
                            Round(Ico.Forward, "القناة التالية", 50) { act.zapBy(1) }
                        } else {
                            SeekRound(false) { act.seekBy(-5_000) }
                            Round(if (vm.playing) Ico.Pause else Ico.Play, "تشغيل", 68) { act.togglePlayPause() }
                            SeekRound(true) { act.seekBy(5_000) }
                        }
                    }
                }

                // تحت
                if (!vm.isLive) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                            if (vm.nextEp != null && vm.countdown < 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    MPill("الحلقة التالية", icon = Ico.Next, onClick = { act.nextEpisodeNow() })
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            MSeekBar(vm, act)
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text(formatTime(vm.seekTarget ?: vm.position), fontSize = 12.sp, color = C.Text2)
                                Spacer(Modifier.weight(1f))
                                if (vm.duration > 0) Text("-" + formatTime(vm.duration - (vm.seekTarget ?: vm.position)), fontSize = 12.sp, color = C.Text2)
                            }
                        }
                    }
                }
            }
        }

        // فقاعة التقديم بالسحب
        val target = vm.seekTarget
        if (target != null) {
            val delta = target - vm.seekOrigin
            Bubble((if (delta >= 0) "+" else "-") + secs(abs(delta)), Modifier.align(Alignment.Center).offset(y = (-70).dp))
            if (vm.thumbsOn && !vm.overlay) {
                Box(Modifier.align(Alignment.Center).offset(y = 30.dp).size(width = 200.dp, height = 112.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1C1C1E)).border(2.dp, Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    vm.thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: CircularProgressIndicator(color = C.Text3, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }
        // فقاعة الضغطتين
        if (tapSeek != 0L) {
            Bubble((if (tapSeek > 0) "+" else "-") + secs(abs(tapSeek)),
                Modifier.align(if (tapSeek > 0) androidx.compose.ui.AbsoluteAlignment.CenterRight else androidx.compose.ui.AbsoluteAlignment.CenterLeft).padding(horizontal = 60.dp))
        }
        gestureLabel?.let { Bubble(it, Modifier.align(Alignment.TopCenter).padding(top = 70.dp)) }

        // الحلقة التالية (عد تنازلي)
        val next = vm.nextEp
        if (next != null && vm.countdown >= 0) {
            Column(
                Modifier.align(Alignment.BottomStart).padding(18.dp).padding(bottom = 60.dp)
                    .background(Color(0xF21C1C1E), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("الحلقة التالية خلال ${vm.countdown}", fontSize = 12.sp, color = C.Accent, fontWeight = FontWeight.SemiBold)
                Text("الموسم ${next.season} · الحلقة ${next.number}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MPill("شغّل الآن", primary = true, onClick = { act.nextEpisodeNow() })
                    MPill("إلغاء", onClick = { act.dismissNextEpisode() })
                }
            }
        }

        vm.message?.let { msg ->
            Column(Modifier.align(Alignment.Center).padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(msg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Color(0xCC1C1C1E), RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 8.dp))
                if (!vm.buffering) { Spacer(Modifier.height(8.dp)); MPill("إعادة المحاولة", icon = Ico.Refresh, onClick = { act.retryNow() }) }
            }
        }

        // اللوحات (نفس لوحات التلفزيون) + زر إغلاق
        when (vm.panel) {
            Panel.TRACKS -> { TracksPanel(vm, act); ClosePanel(vm) }
            Panel.CHANNELS -> { ChannelsPanel(vm, act); ClosePanel(vm) }
            null -> {}
        }
    }
}

private var dragStartX = 0f

private fun secs(ms: Long): String = if (ms < 60_000) "${ms / 1000} ث" else formatTime(ms)

@Composable
private fun Bubble(text: String, modifier: Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(Color(0x99000000), RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 8.dp))
}

@Composable
private fun Round(icon: ImageVector, desc: String, size: Int, label: String = "", onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0x59000000)).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
        if (label.isNotEmpty()) Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

/** زر تقديم/رجوع: سهم دائري والرقم ٥ داخله */
@Composable
private fun SeekRound(forward: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0x59000000)).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        com.wm.iptv.ui.SeekGlyph(forward, "5", 38.dp)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ClosePanel(vm: PlayerVm) {
    Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
        MIconButton(Ico.Close, "إغلاق", Modifier.background(Color(0xCC1C1C1E), CircleShape)) { vm.panel = null }
    }
}

/** شريط التقدم: تضغط عليه أو تسحب، وتطلع الصورة فوق مكان التقديم */
@Composable
private fun MSeekBar(vm: PlayerVm, act: MobilePlayerActivity) {
    val dur = vm.duration
    fun frac(v: Long) = if (dur > 0) (v.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val target = vm.seekTarget
    BoxWithConstraints(Modifier.fillMaxWidth().height(if (target != null && vm.thumbsOn) 150.dp else 28.dp)) {
        val w = maxWidth
        if (target != null && vm.thumbsOn) {
            val tw = 176.dp
            val x = (w * frac(target) - tw / 2).coerceIn(0.dp, (w - tw).coerceAtLeast(0.dp))
            Column(Modifier.offset(x = x).width(tw), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(tw).height(99.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E)).border(2.dp, Color.White, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    vm.thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: CircularProgressIndicator(color = C.Text3, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
                Text(formatTime(target), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(28.dp)
                .pointerInput(dur) {
                    detectTapGestures { o -> if (dur > 0) { act.previewSeek((o.x / size.width * dur).toLong()); act.commitSeekNow() } }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragEnd = { act.commitSeekNow() },
                        onDragCancel = { act.cancelSeekNow() },
                    ) { ch, _ ->
                        ch.consume()
                        if (dur > 0) act.previewSeek((ch.position.x / size.width * dur).toLong())
                    }
                }
        ) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac(vm.buffered)).background(Color(0x66FFFFFF)))
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac(target ?: vm.position)).background(Color.White))
            }
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * frac(target ?: vm.position) - 8.dp).size(16.dp)
                .background(if (target != null) C.Accent else Color.White, CircleShape))
        }
    }
}
