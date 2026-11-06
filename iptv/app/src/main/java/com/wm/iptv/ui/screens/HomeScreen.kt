package com.wm.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import com.wm.iptv.data.Sports
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.TopBar
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Movie
import com.wm.iptv.data.Repo
import com.wm.iptv.data.VodInfo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.Art
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Chip
import com.wm.iptv.ui.Dot
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
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val mem = rememberFocusMemory("home")

    // الواجهة تتبدل بين أحدث الأفلام كل كم ثانية (وتوقف لما تكون واقف على أزرارها)
    val heroes: List<Movie> = remember(catalog) {
        catalog.recentMovies.filter { it.icon.isNotBlank() }.take(8).ifEmpty { catalog.recentMovies.take(1) }
    }
    var heroIdx by rememberSaveable { mutableIntStateOf(0) }
    val hero: Movie? = heroes.getOrNull(heroIdx % heroes.size.coerceAtLeast(1))
    var heroInfo by remember { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(hero?.id) {
        heroInfo = Repo.cachedVodInfo(hero?.id ?: 0)
        heroInfo = hero?.let { Repo.vodInfo(it.id) }
        // نجهز اللي بعده عشان التبديل يكون على طول
        heroes.getOrNull((heroIdx + 1) % heroes.size.coerceAtLeast(1))?.let { Repo.vodInfo(it.id) }
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

    // مباريات اليوم: نجيبها من أول ما تفتح الرئيسية ونحدثها كل دقيقتين
    var matches by remember { mutableStateOf(Sports.cached()) }
    LaunchedEffect(Unit) { while (true) { matches = Sports.today(); delay(120_000) } }
    val navFr = remember { FocusRequester() }

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

    LaunchedEffect(Unit) {
        delay(60)
        // أول ما يفتح التطبيق: التركيز على الشريط العلوي
        if (!mem.restore()) { try { navFr.requestFocus() } catch (_: Exception) { mem.focus("hero_play") } }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        // خلفية الفيلم المميز
        Box(Modifier.fillMaxWidth().height(500.dp)) {
            val bg = heroInfo?.backdrop?.takeIf { it.isNotBlank() } ?: hero?.icon ?: ""
            Crossfade(targetState = if (showMatches) "matches" else bg, animationSpec = tween(600), label = "herobg") { b ->
                Box(Modifier.fillMaxSize()) {
                    if (b == "matches") MatchesBackdrop() else Art(b, hero?.id ?: 0, "")
                }
            }
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF00A0A0C), Color(0x990A0A0C), Color.Transparent), startX = Float.POSITIVE_INFINITY, endX = 0f)))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to C.Bg)))
        }

        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item(key = "top") { TopBar(nav, Route.Home, currentFocus = navFr, onNavFocus = { r, f -> if (f) navFocus = r else if (navFocus == r) navFocus = null }) }
            item(key = "hero") {
              // ارتفاع ثابت: الفيلم يتبدل والمباريات تطلع بدون ما تتحرك الصفوف اللي تحت
              Box(Modifier.fillMaxWidth().height(460.dp)) {
                if (showMatches) MatchesShowcase(matches, Modifier.padding(start = 64.dp, end = 64.dp, top = 8.dp, bottom = 12.dp))
                Column(Modifier.padding(start = 64.dp, top = 40.dp, bottom = 36.dp).width(600.dp)
                    .graphicsLayer { alpha = if (showMatches) 0f else 1f }
                    .onFocusChanged { heroFocused = it.hasFocus },
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (expiry != null) Text(expiry, color = C.Star, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("المضاف حديثاً", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent)
                    Text(hero?.name ?: "أهلاً فيك", fontSize = 50.sp, fontWeight = FontWeight.Bold, lineHeight = 58.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        heroInfo?.year?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp); Dot() }
                        heroInfo?.genre?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp, maxLines = 1); Dot() }
                        heroInfo?.durationSecs?.takeIf { it > 0 }?.let { Text(formatDuration(it), color = C.Text2, fontSize = 15.sp) }
                        hero?.rating?.takeIf { it.isNotBlank() }?.let { Chip("★ $it") }
                    }
                    val plot = heroInfo?.plot ?: ""
                    if (plot.isNotBlank()) Text(plot, color = C.Text2, fontSize = 16.sp, lineHeight = 27.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (hero != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val p = Repo.store.progress("m:${hero.id}")
                        Pill(if (p != null && !p.finished && p.position > 0) "أكمل" else "تشغيل", icon = Ico.Play, big = true,
                            modifier = Modifier.track(mem, "hero_play"),
                            onClick = { PlayerLauncher.movie(ctx, hero, if (p != null && !p.finished) p.position else 0L) })
                        Pill("التفاصيل", big = true, modifier = Modifier.track(mem, "hero_info"), onClick = { nav.go(Route.MovieDetail(hero.id)) })
                    }
                    // نقاط تبين ترتيب الفيلم المعروض
                    if (heroes.size > 1) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        heroes.indices.forEach { i ->
                            Box(Modifier.size(width = if (i == heroIdx % heroes.size) 18.dp else 6.dp, height = 6.dp)
                                .background(if (i == heroIdx % heroes.size) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp)))
                        }
                    }
                }
              }
            }

            if (cont.isNotEmpty()) item(key = "cont") {
                Row0("أكمل المشاهدة") {
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

            if (favCh.isNotEmpty()) item(key = "favch") {
                Row0("قنواتك المفضلة") {
                    items(favCh, key = { it.id }) { ch ->
                        Landscape(ch.name, "", ch.icon, ch.id, width = 200.dp, focusMod = Modifier.track(mem, "fc_${ch.id}"),
                            onClick = { PlayerLauncher.live(ctx, ch.id, LiveKeys.FAV) })
                    }
                }
            }

            if (catalog.recentMovies.isNotEmpty()) item(key = "rm") {
                Row0("أفلام مضافة حديثاً") {
                    items(catalog.recentMovies.take(40), key = { it.id }) { m ->
                        Poster(m.name, m.icon, m.id, badge = null, focusMod = Modifier.track(mem, "rm_${m.id}"),
                            onClick = { nav.go(Route.MovieDetail(m.id)) })
                    }
                }
            }

            if (catalog.recentSeries.isNotEmpty()) item(key = "rs") {
                Row0("مسلسلات محدثة") {
                    items(catalog.recentSeries.take(40), key = { it.id }) { s ->
                        Poster(s.name, s.cover, s.id, focusMod = Modifier.track(mem, "rs_${s.id}"),
                            onClick = { nav.go(Route.SeriesDetail(s.id)) })
                    }
                }
            }

            if (favMovies.isNotEmpty()) item(key = "fm") {
                Row0("أفلامي المفضلة") {
                    items(favMovies, key = { it.id }) { m ->
                        Poster(m.name, m.icon, m.id, focusMod = Modifier.track(mem, "fm_${m.id}"), onClick = { nav.go(Route.MovieDetail(m.id)) })
                    }
                }
            }

            if (favSeries.isNotEmpty()) item(key = "fs") {
                Row0("مسلسلاتي المفضلة") {
                    items(favSeries, key = { it.id }) { s ->
                        Poster(s.name, s.cover, s.id, focusMod = Modifier.track(mem, "fs_${s.id}"), onClick = { nav.go(Route.SeriesDetail(s.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Row0(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        SectionTitle(title, Modifier.padding(start = 64.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 18.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            content = content,
        )
    }
}

