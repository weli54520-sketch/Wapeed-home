package com.wm.iptv.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.TopBar
import com.wm.iptv.data.Channel
import com.wm.iptv.data.Movie
import com.wm.iptv.data.Repo
import com.wm.iptv.data.Series
import com.wm.iptv.data.TextNorm
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Landscape
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Poster
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.SectionTitle
import com.wm.iptv.ui.rememberFocusMemory
import com.wm.iptv.ui.track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class Results(val channels: List<Channel>, val movies: List<Movie>, val series: List<Series>) {
    val empty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** بحث شامل: قنوات + أفلام + مسلسلات */
@Composable
fun SearchScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val ctx = LocalContext.current
    val mem = rememberFocusMemory("search")
    var query by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf(loadHistory()) }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; saveHistory(it); history = loadHistory() }
        }
    }

    val results by produceState(Results(emptyList(), emptyList(), emptyList()), query, catalog) {
        val q = TextNorm.key(query)
        if (q.length < 2) { value = Results(emptyList(), emptyList(), emptyList()); return@produceState }
        delay(180) // ننتظر لين يخلص كتابة
        value = withContext(Dispatchers.Default) {
            Results(
                catalog.channels.filter { TextNorm.matches(it.key, q) }.take(40),
                catalog.movies.filter { TextNorm.matches(it.key, q) }.sortedByDescending { it.added }.take(60),
                catalog.series.filter { TextNorm.matches(it.key, q) }.sortedByDescending { it.updated }.take(60),
            )
        }
    }

    LaunchedEffect(Unit) { delay(60); if (!mem.restore()) mem.focus("field") }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar(nav, Route.Search)
        Row(Modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, "ابحث عن قناة أو فيلم أو مسلسل", Modifier.track(mem, "field"), width = 560.dp,
                onDone = { if (query.isNotBlank()) { saveHistory(query); history = loadHistory() } })
            Spacer(Modifier.width(10.dp))
            Pill("", icon = Ico.Mic, onClick = {
                try {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "قل اسم اللي تبحث عنه"))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(ctx, "البحث الصوتي غير متوفر في هذا الجهاز", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
            if (query.isNotEmpty()) { Spacer(Modifier.width(10.dp)); Pill("مسح", onClick = { query = "" }) }
        }

        if (TextNorm.key(query).length < 2) {
            Column(Modifier.padding(horizontal = 64.dp, vertical = 24.dp)) {
                if (history.isNotEmpty()) {
                    Text("آخر عمليات البحث", fontSize = 15.sp, color = C.Muted)
                    LazyRow(contentPadding = PaddingValues(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(history) { h -> Pill(h, modifier = Modifier.track(mem, "h_$h"), onClick = { query = h }) }
                    }
                } else {
                    Text("اكتب حرفين على الأقل. البحث يتجاهل الهمزات والتشكيل، ويقبل عربي وإنجليزي.", fontSize = 15.sp, color = C.Muted)
                }
            }
        } else if (results.empty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ما لقينا شي باسم \"$query\"", color = C.Text3, fontSize = 17.sp) }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 60.dp)) {
                if (results.channels.isNotEmpty()) item(key = "ch") {
                    Group("القنوات (${results.channels.size})") {
                        items(results.channels, key = { it.id }) { c ->
                            Landscape(c.name, "${c.num}", c.icon, c.id, width = 200.dp, focusMod = Modifier.track(mem, "sc_${c.id}"),
                                onClick = { PlayerLauncher.live(ctx, c.id, LiveKeys.ALL) })
                        }
                    }
                }
                if (results.movies.isNotEmpty()) item(key = "mv") {
                    Group("الأفلام (${results.movies.size})") {
                        items(results.movies, key = { it.id }) { m ->
                            Poster(m.name, m.icon, m.id, focusMod = Modifier.track(mem, "sm_${m.id}"), onClick = { saveHistory(query); nav.go(Route.MovieDetail(m.id)) })
                        }
                    }
                }
                if (results.series.isNotEmpty()) item(key = "sr") {
                    Group("المسلسلات (${results.series.size})") {
                        items(results.series, key = { it.id }) { s ->
                            Poster(s.name, s.cover, s.id, focusMod = Modifier.track(mem, "ss_${s.id}"), onClick = { saveHistory(query); nav.go(Route.SeriesDetail(s.id)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        SectionTitle(title, Modifier.padding(start = 64.dp))
        LazyRow(contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 18.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), content = content)
    }
}

private fun loadHistory(): List<String> =
    Repo.store.let { it.searchHistory() }

private fun saveHistory(q: String) {
    val t = q.trim()
    if (t.length < 2) return
    Repo.store.addSearch(t)
}
