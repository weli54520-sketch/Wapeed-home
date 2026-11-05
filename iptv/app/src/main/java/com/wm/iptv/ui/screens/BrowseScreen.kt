package com.wm.iptv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.TopBar
import com.wm.iptv.data.Kind
import com.wm.iptv.data.Repo
import com.wm.iptv.data.TextNorm
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Poster
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.SideItem
import com.wm.iptv.ui.rememberFocusMemory
import com.wm.iptv.ui.track
import kotlinx.coroutines.delay

/** عنصر موحد للأفلام والمسلسلات */
private data class Item(val id: Int, val name: String, val image: String, val rating: String, val added: Long, val cat: String, val key: String)

private const val SEL_ALL = "all"
private const val SEL_RECENT = "recent"
private const val SEL_FAV = "fav"

@Composable
fun MoviesScreen(nav: Navigator) = BrowseScreen(nav, Kind.MOVIE)

@Composable
fun SeriesListScreen(nav: Navigator) = BrowseScreen(nav, Kind.SERIES)

@Composable
private fun BrowseScreen(nav: Navigator, kind: Kind) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val ctx = LocalContext.current
    val isMovie = kind == Kind.MOVIE
    val mem = rememberFocusMemory("browse_${kind.name}")

    var selection by rememberSaveable { mutableStateOf(SEL_ALL) }
    var pending by remember { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    // تغيير التصنيف لما توقف عليه شوي (بدون ما تضغط)
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        delay(320)
        selection = p
    }

    val cats = remember(catalog, version) { Repo.visibleCategories(kind) }
    val all: List<Item> = remember(catalog) {
        if (isMovie) catalog.movies.map { Item(it.id, it.name, it.icon, it.rating, it.added, it.categoryId, it.key) }
        else catalog.series.map { Item(it.id, it.name, it.cover, it.rating, it.updated, it.categoryId, it.key) }
    }
    val favs = remember(version) { Repo.store.favorites(kind) }
    val hidden = remember(version) { Repo.store.hiddenCategories(kind) }
    val prog = remember(version) { if (isMovie) Repo.store.allProgress().associateBy { it.key } else emptyMap() }

    val items: List<Item> = remember(all, selection, sort, query, favs, hidden) {
        val base = when {
            selection == SEL_ALL -> all.filter { it.cat !in hidden }
            selection == SEL_RECENT -> all.filter { it.cat !in hidden }.sortedByDescending { it.added }.take(300)
            selection == SEL_FAV -> {
                val order = Repo.store.favoriteOrder(kind)
                val byId = all.associateBy { it.id }
                order.mapNotNull { byId[it] }
            }
            selection.startsWith("c:") -> all.filter { it.cat == selection.removePrefix("c:") }
            else -> all
        }
        val q = TextNorm.key(query)
        val filtered = if (q.isEmpty()) base else base.filter { TextNorm.matches(it.key, q) }
        if (selection == SEL_RECENT || selection == SEL_FAV) filtered
        else when (sort) {
            1 -> filtered.sortedBy { it.name.lowercase() }
            2 -> filtered.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
            else -> filtered.sortedByDescending { it.added }
        }
    }

    val grid = rememberLazyGridState()
    var shownSel by rememberSaveable { mutableStateOf(selection) }
    LaunchedEffect(selection, sort, query) {
        if (selection != shownSel) { shownSel = selection; grid.scrollToItem(0) }
    }

    LaunchedEffect(Unit) {
        delay(60)
        if (!mem.restore()) mem.focus("s_$selection")
    }

    val weekAgo = remember { System.currentTimeMillis() / 1000 - 7 * 86400 }
    val selTitle = when {
        selection == SEL_ALL -> "الكل"
        selection == SEL_RECENT -> "المضاف حديثاً"
        selection == SEL_FAV -> "المفضلة"
        else -> cats.firstOrNull { "c:${it.id}" == selection }?.name ?: ""
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar(nav, if (isMovie) Route.Movies else Route.SeriesList)
        Row(Modifier.fillMaxSize()) {
            // ───── القائمة الجانبية ─────
            val side = rememberLazyListState()
            LazyColumn(
                state = side,
                modifier = Modifier.width(272.dp).fillMaxHeight().background(C.Panel),
                contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { Text(if (isMovie) "الأفلام" else "المسلسلات", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, bottom = 14.dp)) }
                item(key = "s_all") {
                    SideItem("الكل", Modifier.track(mem, "s_$SEL_ALL"), icon = Ico.Grid, selected = selection == SEL_ALL,
                        onFocus = { if (it) pending = SEL_ALL }, onClick = { selection = SEL_ALL; mem.focus("g_first") })
                }
                item(key = "s_recent") {
                    SideItem("المضاف حديثاً", Modifier.track(mem, "s_$SEL_RECENT"), icon = Ico.Sparkle, selected = selection == SEL_RECENT,
                        onFocus = { if (it) pending = SEL_RECENT }, onClick = { selection = SEL_RECENT })
                }
                item(key = "s_fav") {
                    SideItem("المفضلة", Modifier.track(mem, "s_$SEL_FAV"), icon = Ico.Star, selected = selection == SEL_FAV,
                        trailing = if (favs.isNotEmpty()) favs.size.toString() else "",
                        onFocus = { if (it) pending = SEL_FAV }, onClick = { selection = SEL_FAV })
                }
                item {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(C.Line))
                        Text("التصنيفات", fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 6.dp))
                    }
                }
                items(cats, key = { "c_${it.id}" }) { c ->
                    val k = "c:${c.id}"
                    SideItem(c.name, Modifier.track(mem, "s_$k"), selected = selection == k,
                        onFocus = { if (it) pending = k }, onClick = { selection = k })
                }
            }

            // ───── الشبكة ─────
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp, end = 40.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(14.dp))
                    Text("${items.size} ${if (isMovie) "فيلم" else "مسلسل"}", fontSize = 14.sp, color = C.Muted)
                    Spacer(Modifier.weight(1f))
                    if (selection != SEL_RECENT && selection != SEL_FAV) {
                        Pill("الترتيب: " + when (sort) { 1 -> "الاسم"; 2 -> "التقييم"; else -> "الأحدث" },
                            onClick = { sort = (sort + 1) % 3 })
                        Spacer(Modifier.width(8.dp))
                    }
                    SearchField(query, { query = it }, if (isMovie) "ابحث في الأفلام" else "ابحث في المسلسلات",
                        onDone = { if (items.isNotEmpty()) mem.focus("g_first") })
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Pill("مسح", onClick = { query = "" })
                    }
                }

                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                catalog.isEmpty -> "جارٍ التحميل…"
                                query.isNotEmpty() -> "ما لقينا شي باسم \"$query\" هنا"
                                selection == SEL_FAV -> "اضغط مطوّلاً على أي ${if (isMovie) "فيلم" else "مسلسل"} عشان تضيفه للمفضلة"
                                else -> "القائمة فاضية"
                            }, color = C.Text3, fontSize = 17.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        state = grid,
                        contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalArrangement = Arrangement.spacedBy(26.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(items, key = { _, x -> x.id }) { idx, it0 ->
                            val p = prog["m:${it0.id}"]
                            val fm = Modifier.track(mem, if (idx == 0) "g_first" else "g_${it0.id}")
                            Poster(
                                it0.name, it0.image, it0.id, width = Dp.Unspecified,
                                badge = if (it0.added > weekAgo && selection != SEL_RECENT) "جديد" else null,
                                progress = p?.takeIf { !it.finished }?.fraction ?: 0f,
                                favorite = it0.id in favs,
                                focusMod = fm,
                                onClick = { nav.go(if (isMovie) Route.MovieDetail(it0.id) else Route.SeriesDetail(it0.id)) },
                                onLongClick = {
                                    val added = Repo.toggleFavorite(kind, it0.id)
                                    Toast.makeText(ctx, if (added) "انضاف للمفضلة" else "انشال من المفضلة", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
