package com.wm.iptv.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.wm.iptv.TopBar
import com.wm.iptv.data.Channel
import com.wm.iptv.data.EpgEntry
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Repo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.player.Players
import com.wm.iptv.ui.C
import com.wm.iptv.ui.FocusBox
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.ProgressLine
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.SideItem
import com.wm.iptv.ui.SmoothFocusScroll
import com.wm.iptv.ui.clockText
import com.wm.iptv.ui.rememberFocusMemory
import com.wm.iptv.ui.track
import kotlinx.coroutines.delay

/** مشغل المعاينة الصغير — نوقفه قبل فتح ملء الشاشة عشان ما ينحسب اتصالين */
class PreviewController {
    /** مشغل واحد نعيد استخدامه لكل القنوات (أسرع وأقل ضغط على السيرفر) */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    fun obtain(ctx: android.content.Context): ExoPlayer = player ?: Players.create(ctx, live = true).also { player = it }

    /** يوقف البث بدون ما يحذف المشغل */
    fun pause() { try { player?.stop() } catch (_: Exception) {} }

    fun stop() { player?.let { try { it.stop(); it.release() } catch (_: Exception) {} }; player = null }
}

@Composable
fun LiveScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val mem = rememberFocusMemory("live")
    val preview = remember { PreviewController() }

    val defaultKey = remember {
        when {
            Repo.store.favorites(Kind.LIVE).isNotEmpty() -> LiveKeys.FAV
            else -> LiveKeys.ALL
        }
    }
    var catKey by rememberSaveable { mutableStateOf(defaultKey) }
    var pendingCat by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingCat) { val p = pendingCat ?: return@LaunchedEffect; delay(320); catKey = p }

    val cats = remember(catalog, version) { Repo.visibleCategories(Kind.LIVE) }
    val favs = remember(version) { Repo.store.favorites(Kind.LIVE) }
    val channels = remember(catalog, catKey, if (catKey == LiveKeys.FAV || catKey == LiveKeys.RECENT) version else 0) { channelsFor(catKey, catalog) }

    var focusedCh by remember { mutableStateOf<Channel?>(null) }
    var previewCh by remember { mutableStateOf<Channel?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var epgFor by remember { mutableStateOf(0) }

    // دليل البرامج للقناة المحددة
    LaunchedEffect(focusedCh?.id) {
        val ch = focusedCh ?: return@LaunchedEffect
        Repo.cachedEpg(ch.id)?.let { epg = it; epgFor = ch.id }
        delay(300)
        epg = Repo.epg(ch.id); epgFor = ch.id
    }
    // تشغيل المعاينة بعد ما توقف على القناة شوي
    LaunchedEffect(focusedCh?.id) {
        val ch = focusedCh ?: return@LaunchedEffect
        if (!Repo.store.livePreview) return@LaunchedEffect
        delay(900)
        previewCh = ch
    }

    val list = rememberLazyListState()
    var shownCat by rememberSaveable { mutableStateOf(catKey) }
    LaunchedEffect(catKey) { if (catKey != shownCat) { shownCat = catKey; list.scrollToItem(0) } }

    // بعد الرجوع من المشغل: نروح لآخر قناة اشتغلت (لو قلّبت هناك)
    var lastSeen by remember { mutableStateOf(Repo.store.lastChannel) }
    LaunchedEffect(version) {
        val last = Repo.store.lastChannel
        if (last != 0 && last != lastSeen) {
            lastSeen = last
            val idx = channels.indexOfFirst { it.id == last }
            if (idx >= 0) { list.scrollToItem((idx - 3).coerceAtLeast(0)); delay(50); mem.focus("ch_$last") }
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        if (!mem.restore()) {
            val last = Repo.store.lastChannel
            val idx = channels.indexOfFirst { it.id == last }
            if (idx >= 0) { list.scrollToItem((idx - 3).coerceAtLeast(0)); delay(50); if (mem.focus("ch_$last")) return@LaunchedEffect }
            if (!mem.focus("ch_first")) mem.focus("cat_$catKey")
        }
    }

    // إيقاف المعاينة لما يطلع التطبيق للخلفية
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { preview.stop(); previewCh = null } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); preview.stop() }
    }

    fun open(ch: Channel) {
        preview.stop(); previewCh = null
        PlayerLauncher.live(ctx, ch.id, catKey)
    }

    // كتابة رقم القناة بالريموت
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1300)
        val n = digits.toIntOrNull()
        digits = ""
        val ch = channels.firstOrNull { it.num == n } ?: catalog.channels.firstOrNull { it.num == n }
        if (ch != null) open(ch) else Toast.makeText(ctx, "ما فيه قناة رقم $n", Toast.LENGTH_SHORT).show()
    }

    Box(
        Modifier.fillMaxSize().background(C.Bg).onPreviewKeyEvent { e ->
            val c = e.nativeKeyEvent.keyCode
            if (c in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                if (e.type == KeyEventType.KeyDown) digits = (digits + (c - KeyEvent.KEYCODE_0)).take(5)
                true
            } else false
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(nav, Route.Live)
            Row(Modifier.fillMaxSize()) {
                // ───── التصنيفات ─────
                SmoothFocusScroll { LazyColumn(
                    modifier = Modifier.width(230.dp).fillMaxHeight().background(C.Panel),
                    contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(key = "k_fav") {
                        SideItem("المفضلة", Modifier.track(mem, "cat_${LiveKeys.FAV}"), icon = Ico.Star, selected = catKey == LiveKeys.FAV,
                            trailing = if (favs.isNotEmpty()) favs.size.toString() else "",
                            onFocus = { if (it) pendingCat = LiveKeys.FAV }, onClick = { catKey = LiveKeys.FAV })
                    }
                    item(key = "k_recent") {
                        SideItem("آخر المشاهدة", Modifier.track(mem, "cat_${LiveKeys.RECENT}"), icon = Ico.Clock, selected = catKey == LiveKeys.RECENT,
                            onFocus = { if (it) pendingCat = LiveKeys.RECENT }, onClick = { catKey = LiveKeys.RECENT })
                    }
                    item(key = "k_all") {
                        SideItem("الكل", Modifier.track(mem, "cat_${LiveKeys.ALL}"), icon = Ico.Grid, selected = catKey == LiveKeys.ALL,
                            onFocus = { if (it) pendingCat = LiveKeys.ALL }, onClick = { catKey = LiveKeys.ALL })
                    }
                    item { Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).height(1.dp).background(C.Line)) }
                    items(cats, key = { "k_${it.id}" }) { c ->
                        val k = LiveKeys.cat(c.id)
                        SideItem(c.name, Modifier.track(mem, "cat_$k"), selected = catKey == k,
                            onFocus = { if (it) pendingCat = k }, onClick = { catKey = k })
                    }
                } }

                // ───── القنوات ─────
                Column(Modifier.width(450.dp).fillMaxHeight().padding(horizontal = 14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(liveKeyTitle(catKey, catalog), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${channels.size} قناة", fontSize = 12.sp, color = C.Muted)
                    }
                    if (channels.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                when (catKey) {
                                    LiveKeys.FAV -> "اضغط مطوّلاً على أي قناة لإضافتها للمفضلة"
                                    LiveKeys.RECENT -> "ما شغلت أي قناة للحين"
                                    else -> if (catalog.isEmpty) "جارٍ التحميل…" else "ما فيه قنوات هنا"
                                }, color = C.Text3, fontSize = 15.sp
                            )
                        }
                    } else {
                        SmoothFocusScroll { LazyColumn(state = list, contentPadding = PaddingValues(top = 4.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(channels, key = { _, ch -> ch.id }) { i, ch ->
                                ChannelRow(
                                    ch, ch.id in favs, if (epgFor == ch.id) epg.firstOrNull() else Repo.cachedEpg(ch.id)?.firstOrNull(),
                                    Modifier.track(mem, if (i == 0) "ch_first" else "ch_${ch.id}").let { m -> if (i == 0) m.track(mem, "ch_${ch.id}") else m },
                                    onFocus = { if (it) focusedCh = ch },
                                    onClick = { open(ch) },
                                    onLong = {
                                        val added = Repo.toggleFavorite(Kind.LIVE, ch.id)
                                        Toast.makeText(ctx, if (added) "انضافت للمفضلة" else "انشالت من المفضلة", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        } }
                    }
                }

                // ───── المعاينة ودليل البرامج ─────
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 26.dp, end = 40.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF15181C)), contentAlignment = Alignment.Center) {
                        val pc = previewCh
                        if (pc != null && pc.id == focusedCh?.id) {
                            PreviewPlayer(pc, preview)
                        } else {
                            val fc = focusedCh
                            if (fc != null && fc.icon.isNotBlank()) AsyncImage(fc.icon, null, contentScale = ContentScale.Fit, modifier = Modifier.size(120.dp))
                            else Icon(Ico.Tv, null, tint = C.Dim, modifier = Modifier.size(48.dp))
                        }
                        Row(Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0x8C000000), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(C.Live, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("مباشر", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val fc = focusedCh
                    if (fc != null) {
                        val now = if (epgFor == fc.id) epg.firstOrNull { it.start <= System.currentTimeMillis() } else null
                        val next = if (epgFor == fc.id) epg.filter { it != now }.take(3) else emptyList()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${fc.num}  ·  ${fc.name}", fontSize = 13.sp, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (now != null) {
                                Text("يعرض الآن · ${clockText(now.start)} – ${clockText(now.end)}", fontSize = 12.sp, color = C.Accent, fontWeight = FontWeight.SemiBold)
                                Text(now.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val f = ((System.currentTimeMillis() - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f)
                                ProgressLine(f, Modifier.padding(vertical = 4.dp), height = 3.dp)
                                if (now.description.isNotBlank()) Text(now.description, fontSize = 13.sp, color = C.Text3, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("لا يوجد دليل برامج لهذي القناة", fontSize = 14.sp, color = C.Muted)
                            }
                        }
                        if (next.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth()) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                                Text("التالي · بتوقيت جهازك", fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                                next.forEach { e ->
                                    Row(Modifier.padding(vertical = 5.dp)) {
                                        Text(clockText(e.start), fontSize = 14.sp, color = C.Muted, modifier = Modifier.width(80.dp))
                                        Text(e.title, fontSize = 14.sp, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (digits.isNotEmpty()) {
            Text(digits, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 50.dp)
                    .background(Color(0xE61C1C1E), RoundedCornerShape(16.dp)).padding(horizontal = 26.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun ChannelRow(ch: Channel, fav: Boolean, now: EpgEntry?, mod: Modifier, onFocus: (Boolean) -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    FocusBox(mod.fillMaxWidth(), onClick = onClick, onLongClick = onLong, shape = RoundedCornerShape(14.dp), focusScale = 1.03f,
        onFocus = { focused = it; onFocus(it) }, elevate = false) {
        Row(
            Modifier.fillMaxWidth().background(if (focused) C.Text else Color.Transparent).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(ch.num.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color(0xFF3A3A3C) else C.Dim, modifier = Modifier.width(38.dp))
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFFE5E5EA) else C.Card), contentAlignment = Alignment.Center) {
                if (ch.icon.isNotBlank()) AsyncImage(ch.icon, null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
                else Text(ch.name.take(2), fontSize = 12.sp, color = C.Muted)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (focused) C.Bg else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (fav) { Spacer(Modifier.width(6.dp)); Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(12.dp)) }
                }
                if (now != null) {
                    Text(now.title, fontSize = 12.sp, color = if (focused) Color(0xFF3A3A3C) else C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val f = ((System.currentTimeMillis() - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f)
                    ProgressLine(f, Modifier.padding(top = 4.dp), color = if (focused) C.Bg else C.Accent, height = 3.dp)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPlayer(ch: Channel, ctrl: PreviewController) {
    val ctx = LocalContext.current
    var error by remember(ch.id) { mutableStateOf(false) }
    var attempt by remember(ch.id) { mutableStateOf(0) }
    var ready by remember(ch.id) { mutableStateOf(false) }

    // تشغيل القناة (ولو السيرفر رفض أول مرة — غالباً لأن الاتصال السابق ما انقفل — نعيد المحاولة)
    LaunchedEffect(ch.id, attempt) {
        if (attempt > 0) delay(1200L * attempt)
        val p = ctrl.obtain(ctx)
        error = false
        try {
            p.setMediaItem(Players.item(Repo.liveUrl(ch.id)))
            p.prepare()
            p.playWhenReady = true
        } catch (_: Exception) { error = true }
    }
    val player = ctrl.player
    DisposableEffect(player, ch.id) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { if (attempt < 3) attempt++ else error = true }
            override fun onRenderedFirstFrame() { ready = true }
        }
        player?.addListener(l)
        onDispose { try { player?.removeListener(l) } catch (_: Exception) {} }
    }
    // لما تنتقل لقناة ثانية نوقف البث بس، والمشغل يبقى جاهز
    DisposableEffect(Unit) { onDispose { ctrl.pause() } }

    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isFocusable = false
                isFocusableInTouchMode = false
            }
        },
        update = { it.player = ctrl.player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
    when {
        error -> Text("القناة لا تستجيب حالياً", fontSize = 13.sp, color = C.Text3)
        !ready -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

