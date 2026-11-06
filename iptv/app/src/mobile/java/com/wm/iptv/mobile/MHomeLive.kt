package com.wm.iptv.mobile

import android.content.Context
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wm.iptv.player.Players
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wm.iptv.data.Channel
import com.wm.iptv.data.EpgEntry
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Movie
import com.wm.iptv.data.Repo
import com.wm.iptv.data.TextNorm
import com.wm.iptv.data.VodInfo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.Art
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.ProgressLine
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.clockText
import com.wm.iptv.ui.remainingText
import com.wm.iptv.ui.screens.LiveKeys
import com.wm.iptv.ui.screens.channelsFor
import kotlinx.coroutines.delay

// ───────────────────────── الرئيسية ─────────────────────────

@Composable
fun MHomeScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current

    val hero: Movie? = remember(catalog) { catalog.recentMovies.firstOrNull { it.icon.isNotBlank() } ?: catalog.recentMovies.firstOrNull() }
    var heroInfo by remember { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(hero?.id) { heroInfo = hero?.let { Repo.vodInfo(it.id) } }

    val cont = remember(version) { Repo.store.allProgress().filter { !it.finished && it.fraction > 0.01f }.take(20) }
    val favCh = remember(version, catalog) { Repo.store.favoriteOrder(Kind.LIVE).mapNotNull { catalog.channelById[it] }.take(30) }
    val favMovies = remember(version, catalog) { Repo.store.favoriteOrder(Kind.MOVIE).mapNotNull { catalog.movieById[it] }.take(30) }
    val favSeries = remember(version, catalog) { Repo.store.favoriteOrder(Kind.SERIES).mapNotNull { catalog.seriesById[it] }.take(30) }
    val expiry = remember(version) {
        Repo.store.userInfo()?.expDate?.let { exp ->
            val days = ((exp * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
            if (days in 0..7) "اشتراكك ينتهي خلال $days ${if (days == 1) "يوم" else "أيام"}" else null
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBarSpace.current)) {
        item {
            MHeader("Waleed TV") { MRefreshButton(); MIconButton(Ico.Gear, "الإعدادات") { nav.go(Route.Settings) } }
        }
        if (expiry != null) item {
            Text(expiry, color = C.Star, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp))
        }
        if (hero != null) item {
            val p = remember(version) { Repo.store.progress("m:${hero.id}") }
            Box(
                Modifier.padding(horizontal = 18.dp, vertical = 8.dp).fillMaxWidth().aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(18.dp)).tap { nav.go(Route.MovieDetail(hero.id)) }
            ) {
                Art(heroInfo?.backdrop?.takeIf { it.isNotBlank() } ?: hero.icon, hero.id, "")
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF20A0A0C))))
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المضاف حديثاً", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Accent)
                    Text(hero.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(heroInfo?.year?.takeIf { it.isNotBlank() }, heroInfo?.genre?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, fontSize = 13.sp, color = C.Text2, maxLines = 1)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MPill(if (p != null && !p.finished && p.position > 0) "أكمل" else "تشغيل", icon = Ico.Play, primary = true,
                            onClick = { PlayerLauncher.movie(ctx, hero, if (p != null && !p.finished) p.position else 0L) })
                        MPill("التفاصيل", onClick = { nav.go(Route.MovieDetail(hero.id)) })
                    }
                }
            }
        }
        if (cont.isNotEmpty()) item {
            MSection("أكمل المشاهدة") {
                items(cont, key = { it.key }) { p ->
                    MLandscape(p.title, if (p.subtitle.isNotEmpty()) "${p.subtitle} · ${remainingText(p)}" else remainingText(p), p.image, p.key.hashCode(),
                        width = 230.dp, progress = p.fraction,
                        onLong = { Repo.store.removeProgress(p.key); Repo.bump(); Toast.makeText(ctx, "انشال من أكمل المشاهدة", Toast.LENGTH_SHORT).show() },
                        onClick = { PlayerLauncher.resume(ctx, p) })
                }
            }
        }
        if (favCh.isNotEmpty()) item {
            MSection("قنواتك المفضلة") {
                items(favCh, key = { it.id }) { ch ->
                    MLandscape(ch.name, "", ch.icon, ch.id, width = 160.dp, fit = true, onClick = { PlayerLauncher.live(ctx, ch.id, LiveKeys.FAV) })
                }
            }
        }
        if (catalog.recentMovies.isNotEmpty()) item {
            MSection("أفلام مضافة حديثاً") {
                items(catalog.recentMovies.take(40), key = { it.id }) { m ->
                    MPoster(m.name, m.icon, m.id, width = 116.dp, onClick = { nav.go(Route.MovieDetail(m.id)) })
                }
            }
        }
        if (catalog.recentSeries.isNotEmpty()) item {
            MSection("مسلسلات محدثة") {
                items(catalog.recentSeries.take(40), key = { it.id }) { s ->
                    MPoster(s.name, s.cover, s.id, width = 116.dp, onClick = { nav.go(Route.SeriesDetail(s.id)) })
                }
            }
        }
        if (favMovies.isNotEmpty()) item {
            MSection("أفلامي المفضلة") {
                items(favMovies, key = { it.id }) { m -> MPoster(m.name, m.icon, m.id, width = 116.dp, onClick = { nav.go(Route.MovieDetail(m.id)) }) }
            }
        }
        if (favSeries.isNotEmpty()) item {
            MSection("مسلسلاتي المفضلة") {
                items(favSeries, key = { it.id }) { s -> MPoster(s.name, s.cover, s.id, width = 116.dp, onClick = { nav.go(Route.SeriesDetail(s.id)) }) }
            }
        }
        if (catalog.isEmpty) item { MEmpty("جارٍ التحميل…") }
    }
}

// ───────────────────────── المباشر ─────────────────────────

/**
 * مشغل مثبّت فوق قائمة القنوات (مثل تطبيقات Apple TV):
 * تضغط قناة → تشتغل فوق والقائمة تبقى تحت تقلّب فيها براحتك.
 * تضغط نفس القناة مرة ثانية أو على الفيديو (أو تلف الجوال) → ملء الشاشة.
 * نستخدم مشغل واحد ونبدل الرابط بس، عشان التنقل بين القنوات يكون سريع.
 */
@OptIn(UnstableApi::class)
private class LiveDock {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var channel by mutableStateOf<Channel?>(null)
        private set
    var buffering by mutableStateOf(false)
    var error by mutableStateOf(false)
    /** القناة اللي نرجع نشغلها لما نرجع من ملء الشاشة أو من الخلفية */
    var resumeId = 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        override fun onPlayerError(e: PlaybackException) { error = true; buffering = false }
    }

    fun play(ctx: android.content.Context, ch: Channel) {
        val p = player ?: Players.create(ctx, live = true).also { it.addListener(listener); player = it }
        error = false; buffering = true
        channel = ch
        try {
            p.setMediaItem(Players.item(Repo.liveUrl(ch.id)))
            p.prepare()
            p.playWhenReady = true
            Repo.store.pushRecentChannel(ch.id)
        } catch (_: Exception) { error = true; buffering = false }
    }

    fun retry(ctx: android.content.Context) { channel?.let { play(ctx, it) } }

    /** نوقف المشغل (عشان ما ينحسب اتصالين مع ملء الشاشة) */
    fun release() {
        player?.let { try { it.removeListener(listener); it.stop(); it.release() } catch (_: Exception) {} }
        player = null
    }

    fun close() { release(); channel = null; resumeId = 0 }
}

@Composable
fun MLiveScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val defaultKey = remember { if (Repo.store.favorites(Kind.LIVE).isNotEmpty()) LiveKeys.FAV else LiveKeys.ALL }
    var catKey by rememberSaveable { mutableStateOf(defaultKey) }
    var showSheet by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val favs = remember(version) { Repo.store.favorites(Kind.LIVE) }
    val cats = remember(catalog, version) { Repo.visibleCategories(Kind.LIVE) }
    val dock = remember { LiveDock() }

    val channels = remember(catalog, catKey, query, if (catKey == LiveKeys.FAV || catKey == LiveKeys.RECENT) version else 0) {
        val base = if (query.isNotBlank()) channelsFor(LiveKeys.ALL, catalog) else channelsFor(catKey, catalog)
        val q = TextNorm.key(query)
        if (q.isEmpty()) base else base.filter { TextNorm.matches(it.key, q) }
    }
    val list = rememberLazyListState()
    var shownCat by rememberSaveable { mutableStateOf(catKey) }
    LaunchedEffect(catKey) { if (shownCat != catKey) { shownCat = catKey; list.scrollToItem(0) } }

    val listKey = if (query.isNotBlank()) LiveKeys.ALL else catKey
    fun fullscreen(ch: Channel) {
        dock.release()
        dock.resumeId = ch.id
        PlayerLauncher.live(ctx, ch.id, listKey)
    }

    // نوقف المشغل الصغير لما يطلع التطبيق للخلفية أو يفتح ملء الشاشة، ونرجعه لما نرجع
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> { dock.channel?.let { if (dock.player != null) dock.resumeId = it.id }; dock.release() }
                Lifecycle.Event.ON_START -> if (dock.resumeId != 0) {
                    // لو قلّبت قنوات في ملء الشاشة نكمل على آخر قناة شفتها
                    val c = Repo.catalog.value
                    val ch = c.channelById[Repo.store.lastChannel] ?: c.channelById[dock.resumeId]
                    dock.resumeId = 0
                    if (ch != null) dock.play(ctx, ch)
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); dock.close() }
    }

    val catTitle = when {
        catKey == LiveKeys.FAV -> "المفضلة"; catKey == LiveKeys.RECENT -> "آخر المشاهدة"; catKey == LiveKeys.ALL -> "الكل"
        else -> cats.firstOrNull { LiveKeys.cat(it.id) == catKey }?.name ?: "تصنيف"
    }
    val current = dock.channel

    // دوران الجهاز الفعلي (مو أبعاد النافذة): الفولد المفتوح شاشته شبه مربعة فالأبعاد تضلل
    val cfg = LocalConfiguration.current
    val rotated = remember(cfg) {
        @Suppress("DEPRECATION")
        val r = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        r == Surface.ROTATION_90 || r == Surface.ROTATION_270
    }
    val splitMode = Repo.store.liveSplit

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        // لما تلف الجهاز بالعرض: المشغل بجنب القائمة بدل ما يغطيها (ويمكن تغييرها من الإعدادات)
        val wide = screenW >= 480.dp && when (splitMode) { 1 -> true; 2 -> false; else -> rotated }
        // بالطول (جوال أو فولد): المشغل مثبّت فوق القائمة، مثل ما كان
        val docked = current != null && !wide

        val listPane: @Composable ColumnScope.() -> Unit = {
            if (searching) {
                Row(Modifier.padding(top = if (docked) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    MField(query, { query = it }, "ابحث عن قناة", Modifier.weight(1f).padding(start = 18.dp, end = if (docked) 4.dp else 18.dp, top = 4.dp, bottom = 4.dp),
                        autoFocus = true, leading = Ico.Search)
                    if (docked) MIconButton(Ico.Close, "إغلاق البحث", Modifier.padding(end = 8.dp)) { searching = false; query = "" }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = if (docked) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    MChips(
                        listOf(LiveKeys.FAV to "المفضلة", LiveKeys.RECENT to "آخر المشاهدة", LiveKeys.ALL to "الكل") +
                            (if (catKey.startsWith("c:")) listOf(catKey to catTitle) else emptyList()) +
                            listOf("pick" to "التصنيفات ▾"),
                        catKey, Modifier.weight(1f),
                    ) { if (it == "pick") showSheet = true else catKey = it }
                    if (docked) MIconButton(Ico.Search, "بحث", Modifier.padding(end = 6.dp)) { searching = true }
                }
            }
            if (channels.isEmpty()) {
                MEmpty(when {
                    query.isNotBlank() -> "ما لقينا قناة باسم \"$query\""
                    catKey == LiveKeys.FAV -> "اضغط مطوّلاً على أي قناة عشان تضيفها للمفضلة"
                    catKey == LiveKeys.RECENT -> "ما شغلت أي قناة للحين"
                    catalog.isEmpty -> "جارٍ التحميل…"
                    else -> "ما فيه قنوات هنا"
                })
            } else {
                LazyColumn(state = list, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp + LocalBarSpace.current)) {
                    if (current == null && Repo.store.livePreview) item {
                        Text(if (wide) "اضغط على القناة تشتغل بجنب القائمة، وكبّرها بزر التكبير"
                             else "اضغط على القناة تشتغل فوق والقائمة تبقى، واضغطها مرة ثانية لملء الشاشة",
                            fontSize = 12.sp, color = C.Dim, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    items(channels, key = { it.id }) { ch ->
                        MChannelRow(ch, ch.id in favs, playing = ch.id == current?.id,
                            onClick = {
                                when {
                                    !Repo.store.livePreview -> PlayerLauncher.live(ctx, ch.id, listKey)
                                    // الشاشة العريضة: التكبير بزر التكبير بس (ما يصير بالغلط)
                                    ch.id == dock.channel?.id -> if (!wide) fullscreen(ch)
                                    else -> dock.play(ctx, ch)
                                }
                            },
                            onLong = {
                                val added = Repo.toggleFavorite(Kind.LIVE, ch.id)
                                Toast.makeText(ctx, if (added) "انضافت للمفضلة" else "انشالت من المفضلة", Toast.LENGTH_SHORT).show()
                            })
                    }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width((screenW * 0.42f).coerceIn(300.dp, 400.dp)).fillMaxHeight()) {
                    MHeader("المباشر") { MRefreshButton(); MIconButton(if (searching) Ico.Close else Ico.Search, "بحث") { searching = !searching; if (!searching) query = "" } }
                    listPane()
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, end = 16.dp, top = 14.dp, bottom = 12.dp + LocalBarSpace.current)) {
                    LivePane(dock, current, onFullscreen = { current?.let { fullscreen(it) } }, onClose = { dock.close() })
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(current != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    val ch = current ?: dock.channel
                    if (ch != null) LiveDockView(dock, ch, onFullscreen = { fullscreen(ch) }, onClose = { dock.close() })
                }
                if (current == null) {
                    MHeader("المباشر") { MRefreshButton(); MIconButton(if (searching) Ico.Close else Ico.Search, "بحث") { searching = !searching; if (!searching) query = "" } }
                }
                listPane()
            }
        }
        if (showSheet) {
            MSheet("تصنيفات القنوات", cats.map { LiveKeys.cat(it.id) to it.name }, catKey,
                onPick = { catKey = it; showSheet = false }, onDismiss = { showSheet = false })
        }
    }
}

/** الجانب اللي فيه المشغل في الشاشة العريضة: الفيديو + اسم القناة + دليل البرامج */
@Composable
private fun LivePane(dock: LiveDock, ch: Channel?, onFullscreen: () -> Unit, onClose: () -> Unit) {
    if (ch == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Ico.Tv, null, tint = C.Dim, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(12.dp))
            Text("اختر قناة وتشتغل هنا", fontSize = 16.sp, color = C.Text3, fontWeight = FontWeight.SemiBold)
            Text("وتكبّرها بزر التكبير متى ما تبي", fontSize = 13.sp, color = C.Muted)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LiveDockView(dock, ch, onFullscreen, onClose, Modifier.clip(RoundedCornerShape(18.dp)), tapFullscreen = false)
        EpgBlock(ch)
    }
}

@Composable
private fun EpgBlock(ch: Channel) {
    var epg by remember(ch.id) { mutableStateOf(Repo.cachedEpg(ch.id) ?: emptyList()) }
    LaunchedEffect(ch.id) { epg = Repo.epg(ch.id) }
    val nowMs = System.currentTimeMillis()
    val now = epg.firstOrNull { it.start <= nowMs && it.end > nowMs }
    val next = epg.filter { it.start > nowMs }.take(3)
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${ch.num}  ·  ${ch.name}", fontSize = 13.sp, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (now != null) {
            Text("يعرض الآن · ${clockText(now.start)} – ${clockText(now.end)}", fontSize = 12.sp, color = C.Accent, fontWeight = FontWeight.SemiBold)
            Text(now.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressLine(((nowMs - now.start).toFloat() / (now.end - now.start)).coerceIn(0f, 1f), Modifier.padding(vertical = 4.dp), height = 3.dp)
            if (now.description.isNotBlank()) Text(now.description, fontSize = 13.sp, color = C.Text3, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        } else {
            Text("لا يوجد دليل برامج لهذي القناة", fontSize = 14.sp, color = C.Muted)
        }
        if (next.isNotEmpty()) {
            Text("التالي", fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(top = 10.dp))
            next.forEach { e ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(clockText(e.start), fontSize = 13.sp, color = C.Muted, modifier = Modifier.width(72.dp))
                    Text(e.title, fontSize = 13.sp, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveDockView(
    dock: LiveDock, ch: Channel, onFullscreen: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier, tapFullscreen: Boolean = true,
) {
    val ctx = LocalContext.current
    var now by remember(ch.id) { mutableStateOf<EpgEntry?>(Repo.cachedEpg(ch.id)?.firstOrNull { it.start <= System.currentTimeMillis() }) }
    LaunchedEffect(ch.id) { now = Repo.epg(ch.id).firstOrNull { it.start <= System.currentTimeMillis() && it.end > System.currentTimeMillis() } }

    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black).plainClick { if (dock.error) dock.retry(ctx) else if (tapFullscreen) onFullscreen() }) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.player = dock.player },
            onRelease = { it.player = null },
            modifier = Modifier.matchParentSize(),
        )
        when {
            dock.error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("القناة لا تستجيب حالياً", fontSize = 14.sp, color = C.Text2)
                Text("اضغط لإعادة المحاولة", fontSize = 12.sp, color = C.Muted)
            }
            dock.buffering -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(30.dp))
        }
        // تدرج تحت فيه اسم القناة والبرنامج الحالي
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(76.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.Live))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val n = now
                if (n != null) Text(n.title, fontSize = 12.sp, color = C.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MIconButton(Ico.Expand, "ملء الشاشة", Modifier.background(Color(0x66000000), CircleShape), size = 19.dp) { onFullscreen() }
        }
        MIconButton(Ico.Close, "إيقاف", Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0x66000000), CircleShape), size = 18.dp) { onClose() }
    }
}

@Composable
private fun MChannelRow(ch: Channel, fav: Boolean, playing: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    var now by remember(ch.id) { mutableStateOf<EpgEntry?>(Repo.cachedEpg(ch.id)?.firstOrNull { it.start <= System.currentTimeMillis() }) }
    LaunchedEffect(ch.id) {
        if (Repo.cachedEpg(ch.id) == null) {
            delay(450) // ما نطلب إلا للقنوات اللي وقفت عليها الشاشة
            now = Repo.epg(ch.id).firstOrNull { it.start <= System.currentTimeMillis() }
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (playing) C.Card else Color.Transparent)
            .tap(onLong, onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(if (playing) C.Line else C.Card), contentAlignment = Alignment.Center) {
            if (ch.icon.isNotBlank()) AsyncImage(ch.icon, null, contentScale = ContentScale.Fit, modifier = Modifier.size(46.dp))
            else Text(ch.name.take(2), fontSize = 13.sp, color = C.Muted)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.num.toString(), fontSize = 12.sp, color = C.Dim, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (fav) { Spacer(Modifier.width(6.dp)); Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(12.dp)) }
            }
            val n = now
            if (playing) {
                Text("يعرض الآن · اضغط مرة ثانية لملء الشاشة", fontSize = 12.sp, color = C.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (n != null) {
                Text("${clockText(n.start)} · ${n.title}", fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(((System.currentTimeMillis() - n.start).toFloat() / (n.end - n.start)).coerceIn(0f, 1f),
                    Modifier.padding(top = 5.dp).width(160.dp), height = 2.dp)
            }
        }
        if (playing) Icon(Ico.Expand, null, tint = C.Text3, modifier = Modifier.size(18.dp))
    }
}
