package com.wm.iptv.mobile

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.data.Channel
import com.wm.iptv.data.Kind
import com.wm.iptv.data.LoadState
import com.wm.iptv.data.Movie
import com.wm.iptv.data.Repo
import com.wm.iptv.data.Series
import com.wm.iptv.data.TextNorm
import com.wm.iptv.player.PlayerLauncher
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.screens.LiveKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ───────────────────────── البحث ─────────────────────────

private class MResults(val channels: List<Channel>, val movies: List<Movie>, val series: List<Series>) {
    val empty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

@Composable
fun MSearchScreen(nav: Navigator) {
    val catalog by Repo.catalog.collectAsState()
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf(Repo.store.searchHistory()) }
    fun save(q: String) { if (q.trim().length >= 2) { Repo.store.addSearch(q.trim()); history = Repo.store.searchHistory() } }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it; save(it) }
    }
    val results by produceState(MResults(emptyList(), emptyList(), emptyList()), query, catalog) {
        val q = TextNorm.key(query)
        if (q.length < 2) { value = MResults(emptyList(), emptyList(), emptyList()); return@produceState }
        delay(180)
        value = withContext(Dispatchers.Default) {
            MResults(
                catalog.channels.filter { TextNorm.matches(it.key, q) }.take(40),
                catalog.movies.filter { TextNorm.matches(it.key, q) }.sortedByDescending { it.added }.take(60),
                catalog.series.filter { TextNorm.matches(it.key, q) }.sortedByDescending { it.updated }.take(60),
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBarSpace.current)) {
        item { MHeader("بحث") }
        item {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                MField(query, { query = it }, "قناة، فيلم، مسلسل…", Modifier.weight(1f), leading = Ico.Search, onDone = { save(query) })
                Spacer(Modifier.width(8.dp))
                MIconButton(Ico.Mic, "بحث صوتي") {
                    try {
                        voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA"))
                    } catch (_: Exception) { Toast.makeText(ctx, "البحث الصوتي غير متوفر", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        if (TextNorm.key(query).length < 2) {
            if (history.isNotEmpty()) item {
                Column(Modifier.padding(top = 18.dp)) {
                    Text("آخر عمليات البحث", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(horizontal = 18.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history) { h -> MPill(h, onClick = { query = h }) }
                    }
                }
            } else item { MEmpty("البحث يتجاهل الهمزات والتشكيل، ويقبل عربي وإنجليزي.") }
        } else if (results.empty) {
            item { MEmpty("ما لقينا شي باسم \"$query\"") }
        } else {
            if (results.channels.isNotEmpty()) item {
                MSection("القنوات (${results.channels.size})") {
                    items(results.channels, key = { it.id }) { c -> MLandscape(c.name, "${c.num}", c.icon, c.id, width = 150.dp, fit = true, onClick = { PlayerLauncher.live(ctx, c.id, LiveKeys.ALL) }) }
                }
            }
            if (results.movies.isNotEmpty()) item {
                MSection("الأفلام (${results.movies.size})") {
                    items(results.movies, key = { it.id }) { m -> MPoster(m.name, m.icon, m.id, width = 108.dp, onClick = { save(query); nav.go(Route.MovieDetail(m.id)) }) }
                }
            }
            if (results.series.isNotEmpty()) item {
                MSection("المسلسلات (${results.series.size})") {
                    items(results.series, key = { it.id }) { s -> MPoster(s.name, s.cover, s.id, width = 108.dp, onClick = { save(query); nav.go(Route.SeriesDetail(s.id)) }) }
                }
            }
        }
    }
}

// ───────────────────────── الإعدادات ─────────────────────────

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
private fun date(sec: Long) = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).format(dateFmt)

@Composable
private fun MRow(title: String, value: String = "", icon: ImageVector? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tap(onLong, onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = C.Text3, modifier = Modifier.size(20.dp))
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, fontSize = 13.sp, color = C.Muted, maxLines = 1)
    }
}

@Composable
private fun MGroup(title: String) {
    Text(title, fontSize = 13.sp, color = C.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 18.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
fun MSettingsScreen(nav: Navigator) {
    val version by Repo.version.collectAsState()
    val state by Repo.state.collectAsState()
    val account by Repo.account.collectAsState()
    val catalog by Repo.catalog.collectAsState()
    val ctx = LocalContext.current
    val st = Repo.store
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val info = remember(version) { st.userInfo() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp + LocalBarSpace.current)) {
        item { MHeader("الإعدادات") }
        item {
            Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().background(C.Panel, RoundedCornerShape(16.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(account?.name ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                KV("المستخدم", account?.username ?: "")
                if (info != null) {
                    KV("الحالة", when (info.status.lowercase()) { "active" -> "فعّال"; "expired" -> "منتهي"; else -> info.status })
                    KV("ينتهي في", info.expDate?.let { date(it) } ?: "غير محدود")
                    KV("الاتصالات", "${info.activeConnections} من ${info.maxConnections}")
                }
                KV("المحتوى", "${catalog.channels.size} قناة · ${catalog.movies.size} فيلم · ${catalog.series.size} مسلسل")
            }
        }
        item { MRow(if (state is LoadState.Loading) (state as LoadState.Loading).step else "تحديث القوائم الآن", icon = Ico.Refresh) { Repo.refresh() } }
        item { MRow("الحسابات", "${st.accounts().size}", Ico.User) { nav.go(Route.Accounts) } }

        item { MGroup("القوائم") }
        item { tick.let { }; MRow("تحديث القوائم عند فتح التطبيق", if (st.refreshOnOpen) "تشغيل" else "إيقاف") { st.refreshOnOpen = !st.refreshOnOpen; tick++ } }

        item { MGroup("العرض") }
        item { tick.let { }; MRow("تفاصيل الأفلام والمسلسلات بالعربي", if (st.arabicDetails) "تشغيل" else "إيقاف") { st.arabicDetails = !st.arabicDetails; Repo.clearDetailsCache(); tick++ } }
        item { tick.let { }; MRow("تشغيل القناة فوق القائمة أولاً", if (st.livePreview) "تشغيل" else "إيقاف") { st.livePreview = !st.livePreview; tick++ } }

        item { MGroup("المشغل") }
        item { tick.let { }; MRow("صورة اللقطة أثناء التقديم", when (st.seekPreviewMode) { 1 -> "دايماً"; 2 -> "إيقاف"; else -> "تلقائي" }) { st.seekPreviewMode = (st.seekPreviewMode + 1) % 3; tick++ } }
        item { tick.let { }; MRow("الحلقة التالية تلقائياً", if (st.autoNextEpisode) "تشغيل" else "إيقاف") { st.autoNextEpisode = !st.autoNextEpisode; tick++ } }
        item { tick.let { }; MRow("لغة الصوت المفضلة", lang(st.preferredAudio, "الأصلية")) { st.preferredAudio = nextLang(st.preferredAudio); tick++ } }
        item { tick.let { }; MRow("لغة الترجمة المفضلة", lang(st.preferredSubtitle, "بدون")) { st.preferredSubtitle = nextLang(st.preferredSubtitle); tick++ } }
        item { tick.let { }; MRow("حجم الترجمة", when (st.subtitleSize) { 0 -> "صغير"; 2 -> "كبير"; 3 -> "كبير جداً"; else -> "متوسط" }) { st.subtitleSize = (st.subtitleSize + 1) % 4; tick++ } }
        item { tick.let { }; MRow("صيغة البث المباشر", if (st.liveHls) "HLS" else "TS") { st.liveHls = !st.liveHls; tick++ } }

        item { MGroup("التصنيفات") }
        item { MRow("إخفاء تصنيفات القنوات", icon = Ico.EyeOff) { nav.go(Route.HiddenCats(Kind.LIVE)) } }
        item { MRow("إخفاء تصنيفات الأفلام", icon = Ico.EyeOff) { nav.go(Route.HiddenCats(Kind.MOVIE)) } }
        item { MRow("إخفاء تصنيفات المسلسلات", icon = Ico.EyeOff) { nav.go(Route.HiddenCats(Kind.SERIES)) } }

        item { MGroup("التطبيق") }
        item {
            val upd by com.wm.iptv.update.Updater.state.collectAsState()
            MRow("التحقق من التحديثات", if (upd is com.wm.iptv.update.UpdateState.Checking) "جارٍ الفحص…" else "1.${com.wm.iptv.update.Updater.currentVersion}", Ico.Refresh) {
                com.wm.iptv.update.manualCheck(ctx, scope)
            }
        }

        item { MGroup("السجل") }
        item { MRow("مسح سجل المشاهدة", icon = Ico.Trash) { st.clearHistory(); Repo.bump(); Toast.makeText(ctx, "انمسح السجل", Toast.LENGTH_SHORT).show() } }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row { Text(k, fontSize = 13.sp, color = C.Muted, modifier = Modifier.width(96.dp)); Text(v, fontSize = 13.sp, color = C.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private val langs = listOf("ar", "en", "")
private fun nextLang(c: String) = langs[(langs.indexOf(c).coerceAtLeast(0) + 1) % langs.size]
private fun lang(c: String, empty: String) = when (c) { "ar" -> "العربية"; "en" -> "الإنجليزية"; "" -> empty; else -> c }

@Composable
fun MHiddenScreen(nav: Navigator, kind: Kind) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val cats = remember(catalog) { Repo.allCategories(kind) }
    val hidden = remember(version) { Repo.store.hiddenCategories(kind) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MIconButton(Ico.Back, "رجوع", Modifier.padding(start = 6.dp)) { nav.back() }
                Text(when (kind) { Kind.LIVE -> "تصنيفات القنوات"; Kind.MOVIE -> "تصنيفات الأفلام"; Kind.SERIES -> "تصنيفات المسلسلات" },
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MPill("إظهار الكل", onClick = { cats.forEach { Repo.store.setCategoryHidden(kind, it.id, false) }; Repo.bump() })
                MPill("إخفاء الكل", onClick = { cats.forEach { Repo.store.setCategoryHidden(kind, it.id, true) }; Repo.bump() })
            }
        }
        items(cats, key = { it.id }) { c ->
            val h = c.id in hidden
            MRow(c.name, if (h) "مخفي" else "ظاهر", if (h) Ico.EyeOff else Ico.Eye) { Repo.store.setCategoryHidden(kind, c.id, !h); Repo.bump() }
        }
    }
}

@Composable
fun MAccountsScreen(nav: Navigator) {
    val version by Repo.version.collectAsState()
    val account by Repo.account.collectAsState()
    val ctx = LocalContext.current
    val list = remember(version, account) { Repo.store.accounts() }
    var confirm by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MIconButton(Ico.Back, "رجوع", Modifier.padding(start = 6.dp)) { nav.back() }
            Text("الحسابات", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("اضغط للتبديل، وضغطة مطوّلة للحذف.", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        list.forEach { a ->
            val cur = a.id == account?.id
            MRow("${a.name} · ${a.username}", if (confirm == a.id) "اضغط مطوّلاً مرة ثانية للحذف" else if (cur) "الحالي" else "", Ico.User,
                onLong = {
                    if (confirm == a.id) { Repo.deleteAccount(a.id); confirm = null; Toast.makeText(ctx, "انحذف الحساب", Toast.LENGTH_SHORT).show() }
                    else confirm = a.id
                }) { if (!cur) { Repo.switchAccount(a.id); nav.go(Route.Home) } }
        }
        Spacer(Modifier.height(12.dp))
        MBigButton("إضافة حساب", Ico.Plus, Modifier.padding(horizontal = 18.dp)) { nav.go(Route.AddAccount) }
    }
}

// ───────────────────────── تسجيل الدخول ─────────────────────────

@Composable
fun MLoginScreen(onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf("أروما") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var ua by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { Repo.login(name, server, user, pass, ua); onDone() }
            catch (e: Exception) { error = e.message ?: "تعذر الدخول" }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("WM", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Text("أدخل بيانات اشتراك Xtream", fontSize = 15.sp, color = C.Text3)
        Spacer(Modifier.height(4.dp))
        Label("اسم الحساب"); MField(name, { name = it }, "اسم الحساب")
        Label("رابط السيرفر"); MField(server, { server = it }, "http://example.com:8080", ltr = true, type = KeyboardType.Uri)
        Label("اسم المستخدم"); MField(user, { user = it }, "اسم المستخدم", ltr = true)
        Label("كلمة المرور"); MField(pass, { pass = it }, "كلمة المرور", ltr = true, password = true)
        Label("User-Agent (اختياري)"); MField(ua, { ua = it }, "اتركه فاضي إلا إذا طلبه المزود", ltr = true)
        error?.let { Text(it, color = Color(0xFFFF6961), fontSize = 14.sp) }
        Spacer(Modifier.height(4.dp))
        if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Text, strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) }
        else MBigButton("دخول", null) { submit() }
        if (onCancel != null) MBigButton("إلغاء", null, primary = false) { onCancel() }
        Text("البيانات تنحفظ في الجهاز فقط.", fontSize = 12.sp, color = C.Dim)
    }
}

@Composable
private fun Label(t: String) { Text(t, fontSize = 13.sp, color = C.Muted) }

