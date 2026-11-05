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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.wm.iptv.data.TextNorm
import com.wm.iptv.data.VodInfo
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.Art
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.ProgressLine
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.formatDuration
import com.wm.iptv.ui.formatTime
import com.wm.iptv.ui.remainingText

private data class MItem(val id: Int, val name: String, val image: String, val rating: String, val added: Long, val cat: String, val key: String)

// ───────────────────────── قائمة الأفلام / المسلسلات ─────────────────────────

@Composable
fun MBrowseScreen(nav: Navigator, kind: Kind) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val isMovie = kind == Kind.MOVIE

    var selection by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }

    val cats = remember(catalog, version) { Repo.visibleCategories(kind) }
    val all = remember(catalog) {
        if (isMovie) catalog.movies.map { MItem(it.id, it.name, it.icon, it.rating, it.added, it.categoryId, it.key) }
        else catalog.series.map { MItem(it.id, it.name, it.cover, it.rating, it.updated, it.categoryId, it.key) }
    }
    val favs = remember(version) { Repo.store.favorites(kind) }
    val hidden = remember(version) { Repo.store.hiddenCategories(kind) }
    val prog = remember(version) { if (isMovie) Repo.store.allProgress().associateBy { it.key } else emptyMap() }

    val list = remember(all, selection, sort, query, favs, hidden) {
        val base = when {
            selection == "all" -> all.filter { it.cat !in hidden }
            selection == "recent" -> all.filter { it.cat !in hidden }.sortedByDescending { it.added }.take(300)
            selection == "fav" -> { val byId = all.associateBy { it.id }; Repo.store.favoriteOrder(kind).mapNotNull { byId[it] } }
            selection.startsWith("c:") -> all.filter { it.cat == selection.removePrefix("c:") }
            else -> all
        }
        val q = TextNorm.key(query)
        val f = if (q.isEmpty()) base else base.filter { TextNorm.matches(it.key, q) }
        if (selection == "recent" || selection == "fav") f
        else when (sort) {
            1 -> f.sortedBy { it.name.lowercase() }
            2 -> f.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
            else -> f.sortedByDescending { it.added }
        }
    }
    val grid = rememberLazyGridState()
    var shown by rememberSaveable { mutableStateOf(selection) }
    LaunchedEffect(selection) { if (shown != selection) { shown = selection; grid.scrollToItem(0) } }

    val catTitle = cats.firstOrNull { "c:${it.id}" == selection }?.name ?: "تصنيف"
    val weekAgo = remember { System.currentTimeMillis() / 1000 - 7 * 86400 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MHeader(if (isMovie) "الأفلام" else "المسلسلات") {
                if (selection != "recent" && selection != "fav")
                    MIconButton(Ico.Sort, "الترتيب") {
                        sort = (sort + 1) % 3
                        Toast.makeText(ctx, "الترتيب: " + when (sort) { 1 -> "الاسم"; 2 -> "التقييم"; else -> "الأحدث" }, Toast.LENGTH_SHORT).show()
                    }
                MIconButton(if (searching) Ico.Close else Ico.Search, "بحث") { searching = !searching; if (!searching) query = "" }
            }
            if (searching) {
                MField(query, { query = it }, if (isMovie) "ابحث في الأفلام" else "ابحث في المسلسلات",
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp), autoFocus = true, leading = Ico.Search)
            }
            MChips(
                listOf("all" to "الكل", "recent" to "المضاف حديثاً", "fav" to "المفضلة") +
                    (if (selection.startsWith("c:")) listOf(selection to catTitle) else emptyList()) +
                    listOf("pick" to "التصنيفات ▾"),
                selection,
            ) { if (it == "pick") sheet = true else selection = it }

            if (list.isEmpty()) {
                MEmpty(when {
                    catalog.isEmpty -> "جارٍ التحميل…"
                    query.isNotEmpty() -> "ما لقينا شي باسم \"$query\""
                    selection == "fav" -> "اضغط مطوّلاً على أي ${if (isMovie) "فيلم" else "مسلسل"} عشان تضيفه للمفضلة"
                    else -> "القائمة فاضية"
                })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp), state = grid,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("${list.size} ${if (isMovie) "فيلم" else "مسلسل"}", fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(start = 4.dp))
                    }
                    items(list, key = { it.id }) { it0 ->
                        MPoster(
                            it0.name, it0.image, it0.id,
                            badge = if (it0.added > weekAgo && selection != "recent") "جديد" else null,
                            progress = prog["m:${it0.id}"]?.takeIf { !it.finished }?.fraction ?: 0f,
                            favorite = it0.id in favs,
                            onLong = {
                                val added = Repo.toggleFavorite(kind, it0.id)
                                Toast.makeText(ctx, if (added) "انضاف للمفضلة" else "انشال من المفضلة", Toast.LENGTH_SHORT).show()
                            },
                            onClick = { nav.go(if (isMovie) Route.MovieDetail(it0.id) else Route.SeriesDetail(it0.id)) },
                        )
                    }
                }
            }
        }
        if (sheet) {
            MSheet(if (isMovie) "تصنيفات الأفلام" else "تصنيفات المسلسلات", cats.map { "c:${it.id}" to it.name }, selection,
                onPick = { selection = it; sheet = false }, onDismiss = { sheet = false })
        }
    }
}

// ───────────────────────── ترويسة التفاصيل ─────────────────────────

@Composable
private fun DetailHeader(image: String, seed: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        Art(image, seed, "")
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to C.Bg)))
        MIconButton(Ico.Back, "رجوع", Modifier.padding(8.dp).background(Color(0x80000000), CircleShape)) { onBack() }
    }
}

// ───────────────────────── تفاصيل الفيلم ─────────────────────────

@Composable
fun MMovieDetail(nav: Navigator, id: Int) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val movie = catalog.movieById[id]
    var info by remember { mutableStateOf<VodInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(id) { info = Repo.vodInfo(id); loading = false }
    if (movie == null) { MEmpty("الفيلم غير موجود"); return }

    val prog = remember(version) { Repo.store.progress("m:$id") }
    val resumable = prog != null && !prog.finished && prog.position > 30_000
    val fav = remember(version) { Repo.isFavorite(Kind.MOVIE, id) }
    val similar = remember(catalog) { (catalog.moviesByCat[movie.categoryId] ?: emptyList()).filter { it.id != id }.sortedByDescending { it.added }.take(24) }
    val m2 = if (movie.ext.isBlank() && !info?.ext.isNullOrBlank()) movie.copy(ext = info!!.ext) else movie

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { DetailHeader(info?.backdrop?.takeIf { it.isNotBlank() } ?: info?.poster?.takeIf { it.isNotBlank() } ?: movie.icon, movie.id) { nav.back() } }
        item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(movie.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val r = info?.rating?.takeIf { it.isNotBlank() } ?: movie.rating
                    if (r.isNotBlank()) { Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(14.dp)); Text(r, color = C.Star, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    info?.year?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 13.sp) }
                    info?.durationSecs?.takeIf { it > 0 }?.let { Text(formatDuration(it), color = C.Text2, fontSize = 13.sp) }
                    if (m2.ext.isNotBlank()) MInfoChip(m2.ext.uppercase())
                }
                info?.genre?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Muted, fontSize = 13.sp) }
                if (resumable) {
                    ProgressLine(prog!!.fraction, height = 4.dp)
                    Text("شاهدت ${formatTime(prog.position)} من ${formatTime(prog.duration)} · ${remainingText(prog)}", color = C.Text3, fontSize = 12.sp)
                    MBigButton("أكمل من ${formatTime(prog.position)}", Ico.Play) { PlayerLauncher.movie(ctx, m2, prog.position) }
                    MBigButton("من البداية", null, primary = false) { PlayerLauncher.movie(ctx, m2, 0L) }
                } else {
                    MBigButton("تشغيل", Ico.Play) { PlayerLauncher.movie(ctx, m2, 0L) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MPill(if (fav) "في المفضلة" else "المفضلة", icon = if (fav) Ico.Star else Ico.StarLine, onClick = { Repo.toggleFavorite(Kind.MOVIE, id) })
                    if (prog != null) MPill(if (prog.finished) "إلغاء المشاهدة" else "تعليم كمشاهد", icon = Ico.Check, onClick = {
                        if (prog.finished) Repo.store.removeProgress("m:$id") else Repo.store.saveProgress(prog.copy(position = prog.duration, updated = System.currentTimeMillis()))
                        Repo.bump()
                    })
                }
                when {
                    loading -> CircularProgressIndicator(color = C.Text3, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    !info?.plot.isNullOrBlank() -> Text(info!!.plot, color = C.Text2, fontSize = 15.sp, lineHeight = 24.sp)
                }
                info?.director?.takeIf { it.isNotBlank() }?.let { Text("الإخراج: $it", color = C.Muted, fontSize = 13.sp) }
                info?.cast?.takeIf { it.isNotBlank() }?.let { Text("البطولة: $it", color = C.Muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (similar.isNotEmpty()) item {
            MSection("من نفس التصنيف") {
                items(similar, key = { it.id }) { s -> MPoster(s.name, s.icon, s.id, width = 104.dp, onClick = { nav.go(Route.MovieDetail(s.id)) }) }
            }
        }
    }
}

// ───────────────────────── المسلسل ─────────────────────────

@Composable
fun MSeriesDetail(nav: Navigator, id: Int) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val series = catalog.seriesById[id]
    var info by remember { mutableStateOf(Repo.cachedSeriesInfo(id)) }
    var loading by remember { mutableStateOf(info == null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(id) { if (info == null) { info = Repo.seriesInfo(id); failed = info == null; loading = false } }

    val inf: SeriesInfo? = info
    val name = series?.name ?: inf?.name ?: ""
    val cover = inf?.cover?.ifBlank { null } ?: series?.cover ?: ""
    val seasons = inf?.seasons ?: emptyList()
    val allProg = remember(version) { Repo.store.allProgress().associateBy { it.key } }
    val watched = remember(version) { Repo.store.watchedEpisodes() }
    val fav = remember(version) { Repo.isFavorite(Kind.SERIES, id) }

    val lastEp: Episode? = remember(inf, version) {
        val eps = (inf?.episodes?.values?.flatten() ?: emptyList()).sortedWith(compareBy({ it.season }, { it.number }))
        val withP = eps.mapNotNull { e -> allProg["e:${e.id}"]?.let { e to it } }.maxByOrNull { it.second.updated }
        when {
            withP == null -> eps.firstOrNull()
            !withP.second.finished -> withP.first
            else -> eps.getOrNull(eps.indexOfFirst { it.id == withP.first.id } + 1) ?: withP.first
        }
    }
    var season by rememberSaveable { mutableStateOf(-1) }
    if (season == -1 && seasons.isNotEmpty()) season = lastEp?.season ?: seasons.first()
    val episodes = inf?.episodes?.get(season) ?: emptyList()

    fun play(e: Episode) {
        val p = allProg["e:${e.id}"]
        PlayerLauncher.episode(ctx, id, e, name, cover, if (p != null && !p.finished) p.position else 0L)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item { DetailHeader(inf?.backdrop?.ifBlank { null } ?: cover, id) { nav.back() } }
        item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                val meta = listOfNotNull(inf?.year?.takeIf { it.isNotBlank() }, inf?.genre?.takeIf { it.isNotBlank() },
                    if (seasons.isNotEmpty()) (if (seasons.size == 1) "موسم واحد" else "${seasons.size} مواسم") else null).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inf?.rating?.takeIf { it.isNotBlank() }?.let { Icon(Ico.Star, null, tint = C.Star, modifier = Modifier.size(14.dp)); Text(it, color = C.Star, fontSize = 13.sp) }
                    if (meta.isNotEmpty()) Text(meta, color = C.Text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (lastEp != null) {
                    val p = allProg["e:${lastEp.id}"]
                    MBigButton((if (p != null && !p.finished) "أكمل " else "شغّل ") + "م${lastEp.season} · ح${lastEp.number}", Ico.Play) { play(lastEp) }
                }
                MPill(if (fav) "في المفضلة" else "المفضلة", icon = if (fav) Ico.Star else Ico.StarLine, onClick = { Repo.toggleFavorite(Kind.SERIES, id) })
                inf?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = C.Text2, fontSize = 14.sp, lineHeight = 23.sp, maxLines = 5, overflow = TextOverflow.Ellipsis) }
            }
        }
        when {
            loading -> item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Text3) } }
            failed || seasons.isEmpty() -> item { MEmpty(if (failed) "ما قدرنا نجيب الحلقات من السيرفر" else "ما فيه حلقات متاحة") }
            else -> {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it }) { s -> MPill("الموسم $s", selected = s == season, onClick = { season = s }) }
                    }
                }
                items(episodes, key = { it.id }) { e ->
                    val p = allProg["e:${e.id}"]
                    val done = e.id in watched || p?.finished == true
                    val frac = if (!done) p?.fraction ?: 0f else 0f
                    Row(
                        Modifier.fillMaxWidth().tap(onLong = {
                            Repo.store.setWatched(e.id, !done); if (done) Repo.store.removeProgress("e:${e.id}"); Repo.bump()
                        }) { play(e) }.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                            Art(e.image.ifBlank { cover }, e.id, e.number.toString())
                            if (done) {
                                Box(Modifier.matchParentSize().background(Color(0x66000000)))
                                Icon(Ico.Check, null, tint = C.Text, modifier = Modifier.align(Alignment.Center).size(22.dp))
                            }
                            if (frac > 0.01f) ProgressLine(frac, Modifier.align(Alignment.BottomCenter).padding(6.dp), height = 3.dp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("الحلقة ${e.number}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val sub = when { frac > 0.01f -> "متوقف عندها"; done -> "تمت المشاهدة"; else -> e.title.takeIf { it.isNotBlank() && !it.contains("E${e.number}", true) } ?: "" }
                            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (e.durationSecs > 0) Text(formatDuration(e.durationSecs), fontSize = 12.sp, color = C.Dim)
                        }
                    }
                }
                item { Text("ضغطة مطوّلة على الحلقة: تعليم كمشاهدة / إلغاء", fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(18.dp)) }
            }
        }
    }
}
