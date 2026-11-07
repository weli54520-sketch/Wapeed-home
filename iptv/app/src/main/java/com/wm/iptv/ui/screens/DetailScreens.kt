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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.data.Episode
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Repo
import com.wm.iptv.data.SeriesInfo
import com.wm.iptv.data.VodInfo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.Art
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Chip
import com.wm.iptv.ui.Dot
import com.wm.iptv.ui.FocusBox
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Poster
import com.wm.iptv.ui.ProgressLine
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.formatDuration
import com.wm.iptv.ui.formatTime
import com.wm.iptv.ui.rememberFocusMemory
import com.wm.iptv.ui.remainingText
import com.wm.iptv.ui.track
import kotlinx.coroutines.delay

@Composable
private fun Backdrop(url: String, seed: Int) {
    Box(Modifier.fillMaxSize()) {
        Art(url, seed, "")
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to Color.Transparent, 0.35f to Color(0xB30A0A0C), 0.65f to Color(0xF20A0A0C), 1f to C.Bg,
            startX = 0f, endX = Float.POSITIVE_INFINITY)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to C.Bg)))
    }
}

// ───────────────────────── تفاصيل الفيلم ─────────────────────────

@Composable
fun MovieDetailScreen(nav: Navigator, id: Int) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val movie = catalog.movieById[id]
    val mem = rememberFocusMemory("movie_$id")
    var info by remember { mutableStateOf<VodInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(id) { info = Repo.vodInfo(id); loading = false }

    if (movie == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("الفيلم غير موجود", color = C.Text3) }; return }

    val prog = remember(version) { Repo.store.progress("m:$id") }
    val resumable = prog != null && !prog.finished && prog.position > 30_000
    val fav = remember(version) { Repo.isFavorite(Kind.MOVIE, id) }
    val similar = remember(catalog) { (catalog.moviesByCat[movie.categoryId] ?: emptyList()).filter { it.id != id }.sortedByDescending { it.added }.take(24) }
    val movieWithExt = if (movie.ext.isBlank() && !info?.ext.isNullOrBlank()) movie.copy(ext = info!!.ext) else movie

    LaunchedEffect(Unit) { delay(60); if (!mem.restore()) mem.focus("play") }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Backdrop(info?.backdrop?.takeIf { it.isNotBlank() } ?: info?.poster?.takeIf { it.isNotBlank() } ?: movie.icon, movie.id)

        Column(Modifier.padding(start = 64.dp, top = 56.dp).width(640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(movie.name, fontSize = 50.sp, fontWeight = FontWeight.Bold, lineHeight = 58.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val r = info?.rating?.takeIf { it.isNotBlank() } ?: movie.rating
                if (r.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(r, color = C.Star, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Dot()
                }
                info?.year?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp); Dot() }
                info?.genre?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false)); Dot() }
                info?.durationSecs?.takeIf { it > 0 }?.let { Text(formatDuration(it), color = C.Text2, fontSize = 15.sp) }
                if (movieWithExt.ext.isNotBlank()) Chip(movieWithExt.ext.uppercase())
            }
            when {
                loading -> CircularProgressIndicator(color = C.Text3, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                !info?.plot.isNullOrBlank() -> Text(info!!.plot, color = C.Text2, fontSize = 16.sp, lineHeight = 28.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                info?.director?.takeIf { it.isNotBlank() }?.let { Text("الإخراج: $it", color = C.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                info?.cast?.takeIf { it.isNotBlank() }?.let { Text("البطولة: $it", color = C.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }

            if (resumable) {
                Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ProgressLine(prog!!.fraction, height = 5.dp)
                    Text("شاهدت ${formatTime(prog.position)} من ${formatTime(prog.duration)} · ${remainingText(prog)}", color = C.Text3, fontSize = 13.sp)
                }
            }

            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (resumable) {
                    Pill("أكمل من ${formatTime(prog!!.position)}", icon = Ico.Play, big = true, modifier = Modifier.track(mem, "play"),
                        onClick = { PlayerLauncher.movie(ctx, movieWithExt, prog.position) })
                    Pill("من البداية", big = true, modifier = Modifier.track(mem, "start"), onClick = { PlayerLauncher.movie(ctx, movieWithExt, 0L) })
                } else {
                    Pill("تشغيل", icon = Ico.Play, big = true, modifier = Modifier.track(mem, "play"), onClick = { PlayerLauncher.movie(ctx, movieWithExt, 0L) })
                }
                Pill(if (fav) "في المفضلة" else "", icon = if (fav) Ico.Star else Ico.StarLine, big = true, modifier = Modifier.track(mem, "fav"),
                    onClick = { Repo.toggleFavorite(Kind.MOVIE, id) })
                if (prog != null) Pill(if (prog.finished) "إلغاء المشاهدة" else "تعليم كمشاهد", icon = Ico.Check, big = true, modifier = Modifier.track(mem, "watched"),
                    onClick = {
                        if (prog.finished) Repo.store.removeProgress("m:$id")
                        else Repo.store.saveProgress(prog.copy(position = prog.duration, updated = System.currentTimeMillis()))
                        Repo.bump()
                    })
            }
        }

        if (similar.isNotEmpty()) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 14.dp)) {
                Text("من نفس التصنيف", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Text2, modifier = Modifier.padding(start = 64.dp))
                LazyRow(contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(similar, key = { it.id }) { m ->
                        Poster(m.name, m.icon, m.id, width = 104.dp, focusMod = Modifier.track(mem, "sim_${m.id}"),
                            onClick = { nav.go(Route.MovieDetail(m.id)) })
                    }
                }
            }
        }
    }
}

// ───────────────────────── المسلسل والحلقات ─────────────────────────

@Composable
fun SeriesDetailScreen(nav: Navigator, id: Int) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val series = catalog.seriesById[id]
    val mem = rememberFocusMemory("series_$id")
    var info by remember { mutableStateOf(Repo.cachedSeriesInfo(id)) }
    var loading by remember { mutableStateOf(info == null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (info == null) { info = Repo.seriesInfo(id); failed = info == null; loading = false }
    }

    val name = series?.name ?: info?.name ?: ""
    val inf: SeriesInfo? = info
    val seasons = inf?.seasons ?: emptyList()
    val allProg = remember(version) { Repo.store.allProgress().associateBy { it.key } }
    val watched = remember(version) { Repo.store.watchedEpisodes() }

    // آخر حلقة اشتغلت في هذا المسلسل
    val lastEp: Episode? = remember(inf, version) {
        val eps = inf?.episodes?.values?.flatten() ?: emptyList()
        val withP = eps.mapNotNull { e -> allProg["e:${e.id}"]?.let { e to it } }.maxByOrNull { it.second.updated }
        when {
            withP == null -> eps.sortedWith(compareBy({ it.season }, { it.number })).firstOrNull()
            !withP.second.finished -> withP.first
            else -> {
                val sorted = eps.sortedWith(compareBy({ it.season }, { it.number }))
                val i = sorted.indexOfFirst { it.id == withP.first.id }
                sorted.getOrNull(i + 1) ?: withP.first
            }
        }
    }

    var season by rememberSaveable { mutableStateOf(-1) }
    if (season == -1 && seasons.isNotEmpty()) season = lastEp?.season ?: seasons.first()
    val episodes = inf?.episodes?.get(season) ?: emptyList()
    val epRow = rememberLazyListState()
    val fav = remember(version) { Repo.isFavorite(Kind.SERIES, id) }

    fun epState(e: Episode): Pair<Boolean, Float> {
        val p = allProg["e:${e.id}"]
        val done = e.id in watched || (p?.finished == true)
        return done to (if (!done) p?.fraction ?: 0f else 0f)
    }

    fun play(e: Episode, fromStart: Boolean = false) {
        val p = allProg["e:${e.id}"]
        val start = if (!fromStart && p != null && !p.finished) p.position else 0L
        PlayerLauncher.episode(ctx, id, e, name, inf?.cover?.ifBlank { series?.cover ?: "" } ?: series?.cover ?: "", start)
    }

    LaunchedEffect(inf) {
        if (inf == null) return@LaunchedEffect
        delay(60)
        if (!mem.restore()) {
            val idx = episodes.indexOfFirst { it.id == lastEp?.id }
            if (idx > 0) epRow.scrollToItem(idx)
            delay(30)
            if (!mem.focus("resume")) mem.focus("season_$season")
        }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Box(Modifier.fillMaxWidth().height(420.dp)) { Backdrop(inf?.backdrop?.ifBlank { null } ?: inf?.cover?.ifBlank { null } ?: series?.cover ?: "", id) }

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 64.dp, top = 48.dp).width(640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    inf?.rating?.takeIf { it.isNotBlank() }?.let {
                        Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(15.dp))
                        Text(it, color = C.Star, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Dot()
                    }
                    inf?.year?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp); Dot() }
                    inf?.genre?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp, maxLines = 1); Dot() }
                    if (seasons.isNotEmpty()) Text(if (seasons.size == 1) "موسم واحد" else "${seasons.size} مواسم", color = C.Text2, fontSize = 15.sp)
                }
                inf?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 15.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (lastEp != null) {
                        val st = epState(lastEp)
                        Pill(
                            (if (st.second > 0f) "أكمل " else "شغّل ") + "الموسم ${lastEp.season} · الحلقة ${lastEp.number}",
                            icon = Ico.Play, big = true, modifier = Modifier.track(mem, "resume"), onClick = { play(lastEp) })
                    }
                    Pill(if (fav) "في المفضلة" else "", icon = if (fav) Ico.Star else Ico.StarLine, big = true, modifier = Modifier.track(mem, "fav"),
                        onClick = { Repo.toggleFavorite(Kind.SERIES, id) })
                }
            }

            Spacer(Modifier.height(26.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Text3) }
                failed || seasons.isEmpty() -> Text(if (failed) "ما قدرنا نجيب الحلقات من السيرفر" else "ما فيه حلقات متاحة", color = C.Text3, modifier = Modifier.padding(start = 64.dp))
                else -> {
                    LazyRow(contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it }) { s ->
                            Pill("الموسم $s", selected = s == season, modifier = Modifier.track(mem, "season_$s"),
                                onClick = { season = s }, onFocus = { if (it) season = s })
                        }
                    }
                    LazyRow(
                        state = epRow,
                        contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 22.dp, bottom = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        items(episodes, key = { it.id }) { e ->
                            val (done, frac) = epState(e)
                            EpisodeCard(e, done, frac, inf?.cover?.ifBlank { null } ?: series?.cover ?: "",
                                Modifier.track(mem, "ep_${e.id}"),
                                onClick = { play(e) },
                                onLong = { Repo.store.setWatched(e.id, !done); if (done) Repo.store.removeProgress("e:${e.id}"); Repo.bump() })
                        }
                    }
                    Row(Modifier.padding(start = 64.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("✓ تمت مشاهدتها", fontSize = 12.sp, color = C.Muted)
                        Text("ضغطة مطوّلة على الحلقة: تعليم كمشاهدة / إلغاء", fontSize = 12.sp, color = C.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(e: Episode, done: Boolean, frac: Float, fallbackImg: String, mod: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(250.dp)) {
        FocusBox(mod.fillMaxWidth().height(141.dp), onClick = onClick, onLongClick = onLong, onFocus = { focused = it }) {
            Art(e.image.ifBlank { fallbackImg }, e.id, e.number.toString())
            if (done) {
                Box(Modifier.matchParentSize().background(Color(0x66000000)))
                Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(26.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Ico.Check, null, tint = C.Text, modifier = Modifier.size(15.dp))
                }
            }
            if (frac > 0.01f) ProgressLine(frac, Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp))
        }
        Spacer(Modifier.height(if (focused) 13.dp else 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الحلقة ${e.number}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color.White else C.Text2, modifier = Modifier.weight(1f))
            if (e.durationSecs > 0) Text(formatDuration(e.durationSecs), fontSize = 12.sp, color = C.Muted)
        }
        val sub = e.title.takeIf { it.isNotBlank() && !it.contains("E${e.number}", true) } ?: ""
        Text(
            when { frac > 0.01f -> "متوقف عندها"; done -> "تمت المشاهدة"; else -> sub },
            fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
