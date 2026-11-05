package com.wm.iptv.ui.screens

import androidx.compose.foundation.background
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

    LaunchedEffect(Unit) {
        delay(60)
        if (!mem.restore()) mem.focus("hero_play")
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        // خلفية الفيلم المميز
        Box(Modifier.fillMaxWidth().height(500.dp)) {
            val bg = heroInfo?.backdrop?.takeIf { it.isNotBlank() } ?: hero?.icon ?: ""
            Art(bg, hero?.id ?: 0, "")
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF00A0A0C), Color(0x990A0A0C), Color.Transparent), startX = Float.POSITIVE_INFINITY, endX = 0f)))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to C.Bg)))
        }

        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item(key = "top") { TopBar(nav, Route.Home) }
            item(key = "hero") {
                Column(Modifier.padding(start = 64.dp, top = 40.dp, bottom = 36.dp).width(600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

