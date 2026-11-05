package com.wm.iptv.mobile

import android.widget.Toast
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
            MHeader("WM TV") { MIconButton(Ico.Gear, "الإعدادات") { nav.go(Route.Settings) } }
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

@Composable
fun MLiveScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val defaultKey = remember { if (Repo.store.favorites(Kind.LIVE).isNotEmpty()) LiveKeys.FAV else LiveKeys.ALL }
    var catKey by rememberSaveable { mutableStateOf(defaultKey) }
    var showSheet by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val favs = remember(version) { Repo.store.favorites(Kind.LIVE) }
    val cats = remember(catalog, version) { Repo.visibleCategories(Kind.LIVE) }

    val channels = remember(catalog, catKey, query, if (catKey == LiveKeys.FAV || catKey == LiveKeys.RECENT) version else 0) {
        val base = if (query.isNotBlank()) channelsFor(LiveKeys.ALL, catalog) else channelsFor(catKey, catalog)
        val q = TextNorm.key(query)
        if (q.isEmpty()) base else base.filter { TextNorm.matches(it.key, q) }
    }
    val list = rememberLazyListState()
    LaunchedEffect(catKey) { list.scrollToItem(0) }

    val catTitle = when {
        catKey == LiveKeys.FAV -> "المفضلة"; catKey == LiveKeys.RECENT -> "آخر المشاهدة"; catKey == LiveKeys.ALL -> "الكل"
        else -> cats.firstOrNull { LiveKeys.cat(it.id) == catKey }?.name ?: "تصنيف"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MHeader("المباشر") { MIconButton(if (searching) Ico.Close else Ico.Search, "بحث") { searching = !searching; if (!searching) query = "" } }
            if (searching) {
                MField(query, { query = it }, "ابحث عن قناة", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), autoFocus = true, leading = Ico.Search)
            } else {
                Row(Modifier.fillMaxWidth()) {
                    MChips(
                        listOf(LiveKeys.FAV to "المفضلة", LiveKeys.RECENT to "آخر المشاهدة", LiveKeys.ALL to "الكل") +
                            (if (catKey.startsWith("c:")) listOf(catKey to catTitle) else emptyList()) +
                            listOf("pick" to "التصنيفات ▾"),
                        catKey,
                    ) { if (it == "pick") showSheet = true else catKey = it }
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
                    items(channels, key = { it.id }) { ch ->
                        MChannelRow(ch, ch.id in favs,
                            onClick = { PlayerLauncher.live(ctx, ch.id, if (query.isNotBlank()) LiveKeys.ALL else catKey) },
                            onLong = {
                                val added = Repo.toggleFavorite(Kind.LIVE, ch.id)
                                Toast.makeText(ctx, if (added) "انضافت للمفضلة" else "انشالت من المفضلة", Toast.LENGTH_SHORT).show()
                            })
                    }
                }
            }
        }
        if (showSheet) {
            MSheet("تصنيفات القنوات", cats.map { LiveKeys.cat(it.id) to it.name }, catKey,
                onPick = { catKey = it; showSheet = false }, onDismiss = { showSheet = false })
        }
    }
}

@Composable
private fun MChannelRow(ch: Channel, fav: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    var now by remember(ch.id) { mutableStateOf<EpgEntry?>(Repo.cachedEpg(ch.id)?.firstOrNull { it.start <= System.currentTimeMillis() }) }
    LaunchedEffect(ch.id) {
        if (Repo.cachedEpg(ch.id) == null) {
            delay(450) // ما نطلب إلا للقنوات اللي وقفت عليها الشاشة
            now = Repo.epg(ch.id).firstOrNull { it.start <= System.currentTimeMillis() }
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tap(onLong, onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(C.Card), contentAlignment = Alignment.Center) {
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
            if (n != null) {
                Text("${clockText(n.start)} · ${n.title}", fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(((System.currentTimeMillis() - n.start).toFloat() / (n.end - n.start)).coerceIn(0f, 1f),
                    Modifier.padding(top = 5.dp).width(160.dp), height = 2.dp)
            }
        }
    }
}
