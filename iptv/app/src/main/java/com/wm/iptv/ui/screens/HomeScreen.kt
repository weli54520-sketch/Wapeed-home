package com.wm.iptv.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.wm.iptv.TopBar
import com.wm.iptv.data.Catalog
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Movie
import com.wm.iptv.data.Repo
import com.wm.iptv.data.Series
import com.wm.iptv.data.Sports
import com.wm.iptv.data.VodInfo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.Art
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Chip
import com.wm.iptv.ui.Dot
import com.wm.iptv.ui.FocusMemory
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Landscape
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Poster
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.SectionTitle
import com.wm.iptv.ui.formatDuration
import com.wm.iptv.ui.rememberFocusMemory
import com.wm.iptv.ui.remainingText
import com.wm.iptv.ui.track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** مقاسات موحدة للرئيسية كلها */
private val PadH = 64.dp          // الهامش الجانبي
private val HeroH = 520.dp        // ارتفاع الواجهة (يشمل الشريط العلوي)
private val RowGap = 22.dp        // المسافة بين بطاقات الصف
private val RowPadV = 14.dp       // مسافة الصف فوق وتحت (تكفي لتكبير البطاقة بدون قص)

/**
 * الرئيسية: واجهة (فيلم مميز) ثم صفوف مرتبة بحسب أهميتها لك.
 * الواجهة قسم مستقل بحالته الخاصة، فلما يتبدل الفيلم ما تنعاد رسمة بقية الصفحة (هذا اللي كان يسبب التهنيق).
 */
@Composable
fun HomeScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val mem = rememberFocusMemory("home")
    val navFr = remember { FocusRequester() }

    val cont = remember(version) { Repo.store.allProgress().filter { !it.finished && it.fraction > 0.01f }.take(20) }
    val favCh = remember(version, catalog) { Repo.store.favoriteOrder(Kind.LIVE).mapNotNull { catalog.channelById[it] }.take(30) }
    val lastCh = remember(version, catalog) { catalog.channelById[Repo.store.lastChannel] }
    val favMovies = remember(version, catalog) { Repo.store.favoriteOrder(Kind.MOVIE).mapNotNull { catalog.movieById[it] }.take(30) }
    val favSeries = remember(version, catalog) { Repo.store.favoriteOrder(Kind.SERIES).mapNotNull { catalog.seriesById[it] }.take(30) }
    // الأعلى تقييماً: فرز آلاف العناصر يصير في خيط خلفي عشان ما يوقف الواجهة
    val tops by produceState(Pair<List<Movie>, List<Series>>(emptyList(), emptyList()), catalog) {
        value = withContext(Dispatchers.Default) { catalog.topMovies to catalog.topSeries }
    }
    val weekAgo = remember { System.currentTimeMillis() / 1000 - 7 * 86400 }

    LaunchedEffect(Unit) {
        delay(60)
        // أول ما يفتح التطبيق: التركيز على الشريط العلوي
        if (!mem.restore()) { try { navFr.requestFocus() } catch (_: Exception) { mem.focus("hero_play") } }
    }

    LazyColumn(Modifier.fillMaxSize().background(C.Bg), contentPadding = PaddingValues(bottom = 60.dp)) {
        item(key = "hero") { HeroSection(nav, catalog, version, mem, navFr) }

        if (cont.isNotEmpty()) item(key = "cont") {
            Section("أكمل المشاهدة") {
                items(cont, key = { it.key }) { p ->
                    Landscape(
                        p.title, if (p.subtitle.isNotEmpty()) "${p.subtitle} · ${remainingText(p)}" else remainingText(p),
                        p.image, p.key.hashCode(), progress = p.fraction,
                        focusMod = Modifier.track(mem, "c_${p.key}"),
                        onClick = { PlayerLauncher.resume(ctx, p) },
                        onLongClick = { Repo.store.removeProgress(p.key); Repo.bump() },
                    )
                }
            }
        }

        item(key = "matches") { MatchesRow(nav, mem) }

        if (favCh.isNotEmpty() || lastCh != null) item(key = "live") {
            Section("قنواتك") {
                // اختصار: آخر قناة شاهدتها أول بطاقة
                if (lastCh != null) item(key = "last_${lastCh.id}") {
                    Landscape(lastCh.name, "آخر قناة شاهدتها", lastCh.icon, lastCh.id, width = 220.dp,
                        focusMod = Modifier.track(mem, "lc_${lastCh.id}"),
                        onClick = { PlayerLauncher.live(ctx, lastCh.id, com.wm.iptv.ui.screens.LiveKeys.RECENT) })
                }
                items(favCh.filter { it.id != lastCh?.id }, key = { it.id }) { ch ->
                    Landscape(ch.name, "", ch.icon, ch.id, width = 220.dp, focusMod = Modifier.track(mem, "fc_${ch.id}"),
                        onClick = { PlayerLauncher.live(ctx, ch.id, com.wm.iptv.ui.screens.LiveKeys.FAV) })
                }
            }
        }

        if (catalog.recentMovies.isNotEmpty()) item(key = "rm") {
            Section("أفلام مضافة حديثاً") {
                items(catalog.recentMovies.take(40), key = { it.id }) { m ->
                    Poster(m.name, m.icon, m.id, badge = if (m.added > weekAgo) "جديد" else null,
                        focusMod = Modifier.track(mem, "rm_${m.id}"), onClick = { nav.go(Route.MovieDetail(m.id)) })
                }
            }
        }

        if (catalog.recentSeries.isNotEmpty()) item(key = "rs") {
            Section("مسلسلات محدثة") {
                items(catalog.recentSeries.take(40), key = { it.id }) { s ->
                    Poster(s.name, s.cover, s.id, badge = if (s.updated > weekAgo) "حلقات جديدة" else null,
                        focusMod = Modifier.track(mem, "rs_${s.id}"), onClick = { nav.go(Route.SeriesDetail(s.id)) })
                }
            }
        }

        // أفضل 10 (بحسب تقييم المزوّد): أرقام كبيرة بجنب البوسترات
        if (tops.second.isNotEmpty()) item(key = "ts") {
            Section("أفضل 10 مسلسلات تقييماً") {
                itemsIndexed(tops.second.take(10), key = { _, s -> s.id }) { i, s ->
                    Ranked(i + 1) {
                        Poster(s.name, s.cover, s.id, badge = "★ ${s.rating}", focusMod = Modifier.track(mem, "ts_${s.id}"),
                            onClick = { nav.go(Route.SeriesDetail(s.id)) })
                    }
                }
            }
        }

        if (tops.first.isNotEmpty()) item(key = "tm") {
            Section("أفضل 10 أفلام تقييماً") {
                itemsIndexed(tops.first.take(10), key = { _, m -> m.id }) { i, m ->
                    Ranked(i + 1) {
                        Poster(m.name, m.icon, m.id, badge = "★ ${m.rating}", focusMod = Modifier.track(mem, "tm_${m.id}"),
                            onClick = { nav.go(Route.MovieDetail(m.id)) })
                    }
                }
            }
        }

        if (favMovies.isNotEmpty()) item(key = "fm") {
            Section("أفلامي المفضلة") {
                items(favMovies, key = { it.id }) { m ->
                    Poster(m.name, m.icon, m.id, focusMod = Modifier.track(mem, "fm_${m.id}"), onClick = { nav.go(Route.MovieDetail(m.id)) })
                }
            }
        }

        if (favSeries.isNotEmpty()) item(key = "fs") {
            Section("مسلسلاتي المفضلة") {
                items(favSeries, key = { it.id }) { s ->
                    Poster(s.name, s.cover, s.id, focusMod = Modifier.track(mem, "fs_${s.id}"), onClick = { nav.go(Route.SeriesDetail(s.id)) })
                }
            }
        }
    }
}

/** صف بعنوان ومسافات موحدة */
@Composable
private fun Section(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        SectionTitle(title, Modifier.padding(start = PadH))
        LazyRow(
            contentPadding = PaddingValues(start = PadH, end = PadH, top = RowPadV, bottom = RowPadV),
            horizontalArrangement = Arrangement.spacedBy(RowGap),
            content = content,
        )
    }
}

/** رقم الترتيب الكبير بجنب البطاقة (نص ثابت فقط = خفيف) */
@Composable
private fun Ranked(rank: Int, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "$rank", fontSize = 104.sp, fontWeight = FontWeight.Bold, lineHeight = 104.sp, letterSpacing = (-6).sp,
            color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(end = 2.dp, bottom = 26.dp),
        )
        content()
    }
}

/** مباريات اليوم كصف بطاقات (تفتح المباشر). يختفي لو ما فيه مباريات. */
@Composable
private fun MatchesRow(nav: Navigator, mem: FocusMemory) {
    var matches by remember { mutableStateOf(Sports.cached()) }
    LaunchedEffect(Unit) { while (true) { matches = Sports.today(); delay(120_000) } }
    if (matches.isEmpty()) return
    Section("مباريات اليوم") {
        items(matches.take(12), key = { it.id }) { m ->
            MatchCard(m, Modifier.track(mem, "mt_${m.id}")) { nav.go(Route.Live) }
        }
    }
}

/**
 * الواجهة: صورة الفيلم خلف الشريط العلوي والنص، وتتبدل بين أحدث الأفلام كل 9 ثواني.
 * كل حالتها (الفيلم الحالي، معلوماته، المباريات) داخلها، فتغييرها ما يعيد رسم الصفحة كلها.
 */
@Composable
private fun HeroSection(nav: Navigator, catalog: Catalog, version: Int, mem: FocusMemory, navFr: FocusRequester) {
    val ctx = LocalContext.current
    val heroes: List<Movie> = remember(catalog) {
        catalog.recentMovies.filter { it.icon.isNotBlank() }.take(8).ifEmpty { catalog.recentMovies.take(1) }
    }
    val count = heroes.size.coerceAtLeast(1)
    var heroIdx by rememberSaveable { mutableIntStateOf(0) }
    val hero: Movie? = heroes.getOrNull(heroIdx % count)
    var heroInfo by remember { mutableStateOf<VodInfo?>(null) }

    LaunchedEffect(hero?.id) {
        heroInfo = Repo.cachedVodInfo(hero?.id ?: 0)
        heroInfo = hero?.let { Repo.vodInfo(it.id) }
        // نجهز اللي بعده (المعلومات + صورة الخلفية في الكاش) عشان التبديل يكون فوري وبدون تقطيع
        heroes.getOrNull((heroIdx + 1) % count)?.let { next ->
            val url = Repo.vodInfo(next.id)?.backdrop?.takeIf { it.isNotBlank() } ?: next.icon
            if (url.isNotBlank()) ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    var heroFocused by remember { mutableStateOf(false) }
    var navFocus by remember { mutableStateOf<Route?>(null) }
    val showMatches = navFocus == Route.Live
    LaunchedEffect(heroes, heroFocused, showMatches) {
        if (heroes.size < 2) return@LaunchedEffect
        while (true) {
            delay(9_000)
            if (!heroFocused && !showMatches) heroIdx = (heroIdx + 1) % heroes.size
        }
    }

    var matches by remember { mutableStateOf(Sports.cached()) }
    LaunchedEffect(Unit) { while (true) { matches = Sports.today(); delay(120_000) } }

    val expiry = remember(version) {
        Repo.store.userInfo()?.expDate?.let { exp ->
            val days = ((exp * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
            if (days in 0..7) "اشتراكك ينتهي خلال $days ${if (days == 1) "يوم" else "أيام"}" else null
        }
    }

    Box(Modifier.fillMaxWidth().height(HeroH)) {
        // الخلفية: صورة واحدة + تدرجين (طبقات قليلة = أخف على التلفزيون)
        val bg = heroInfo?.backdrop?.takeIf { it.isNotBlank() } ?: hero?.icon ?: ""
        Crossfade(targetState = if (showMatches) "matches" else bg, animationSpec = tween(500), label = "herobg") { b ->
            Box(Modifier.fillMaxSize()) {
                if (b == "matches") MatchesBackdrop() else Art(b, hero?.id ?: 0, "")
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF00A0A0C), Color(0x990A0A0C), Color.Transparent), startX = Float.POSITIVE_INFINITY, endX = 0f)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to C.Bg)))

        Column(Modifier.fillMaxSize()) {
            TopBar(nav, Route.Home, currentFocus = navFr, onNavFocus = { r, f -> if (f) navFocus = r else if (navFocus == r) navFocus = null })

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (showMatches) MatchesShowcase(matches, Modifier.padding(horizontal = PadH, vertical = 4.dp))
                // النص مثبّت من تحت: الأزرار ما تتحرك من مكانها لو العنوان سطر أو سطرين
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = PadH, bottom = 30.dp).width(620.dp)
                        .graphicsLayer { alpha = if (showMatches) 0f else 1f }
                        .onFocusChanged { heroFocused = it.hasFocus },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (expiry != null) Text(expiry, color = C.Star, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("المضاف حديثاً", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent)
                    Text(hero?.name ?: "أهلاً فيك", fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        heroInfo?.year?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp); Dot() }
                        heroInfo?.genre?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp, maxLines = 1); Dot() }
                        heroInfo?.durationSecs?.takeIf { it > 0 }?.let { Text(formatDuration(it), color = C.Text2, fontSize = 15.sp) }
                        hero?.rating?.takeIf { it.isNotBlank() }?.let { Chip("★ $it") }
                    }
                    val plot = heroInfo?.plot ?: ""
                    if (plot.isNotBlank()) Text(plot, color = C.Text2, fontSize = 16.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (hero != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val p = Repo.store.progress("m:${hero.id}")
                        Pill(if (p != null && !p.finished && p.position > 0) "أكمل" else "تشغيل", icon = Ico.Play, big = true,
                            modifier = Modifier.track(mem, "hero_play"),
                            onClick = { PlayerLauncher.movie(ctx, hero, if (p != null && !p.finished) p.position else 0L) })
                        Pill("التفاصيل", big = true, modifier = Modifier.track(mem, "hero_info"), onClick = { nav.go(Route.MovieDetail(hero.id)) })
                    }
                    // نقاط تبين ترتيب الفيلم المعروض
                    if (heroes.size > 1) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        heroes.indices.forEach { i ->
                            val on = i == heroIdx % count
                            Box(Modifier.size(width = if (on) 18.dp else 6.dp, height = 6.dp)
                                .background(if (on) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
    }
}
