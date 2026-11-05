package com.wm.iptv.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.TopBar
import com.wm.iptv.data.Kind
import com.wm.iptv.data.LoadState
import com.wm.iptv.data.Repo
import com.wm.iptv.ui.C
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.SideItem
import com.wm.iptv.ui.rememberFocusMemory
import com.wm.iptv.ui.track
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
private fun date(sec: Long) = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).format(dateFmt)

@Composable
fun SettingsScreen(nav: Navigator) {
    val version by Repo.version.collectAsState()
    val state by Repo.state.collectAsState()
    val account by Repo.account.collectAsState()
    val catalog by Repo.catalog.collectAsState()
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val mem = rememberFocusMemory("settings")
    val st = Repo.store
    var tick by remember { mutableIntStateOf(0) } // يحدث القيم بعد كل تغيير
    val info = remember(version) { st.userInfo() }

    LaunchedEffect(Unit) { delay(60); if (!mem.restore()) mem.focus("refresh") }

    fun changed() { tick++ }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar(nav, Route.Settings)
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp)) {
            // ───── الاشتراك ─────
            Column(Modifier.width(400.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الاشتراك", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().background(C.Panel, RoundedCornerShape(18.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(account?.name ?: "", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    InfoLine("المستخدم", account?.username ?: "")
                    InfoLine("السيرفر", account?.server?.removePrefix("http://")?.removePrefix("https://") ?: "")
                    if (info != null) {
                        InfoLine("الحالة", when (info.status.lowercase()) { "active" -> "فعّال"; "expired" -> "منتهي"; else -> info.status } + if (info.isTrial) " (تجريبي)" else "")
                        InfoLine("ينتهي في", info.expDate?.let { date(it) } ?: "غير محدود")
                        info.expDate?.let {
                            val days = ((it * 1000 - System.currentTimeMillis()) / 86_400_000L)
                            InfoLine("المتبقي", if (days >= 0) "$days يوم" else "منتهي")
                        }
                        InfoLine("الاتصالات", "${info.activeConnections} من ${info.maxConnections}")
                    }
                    InfoLine("المحتوى", "${catalog.channels.size} قناة · ${catalog.movies.size} فيلم · ${catalog.series.size} مسلسل")
                    if (st.lastRefresh > 0) InfoLine("آخر تحديث", date(st.lastRefresh / 1000))
                }
                Spacer(Modifier.height(4.dp))
                Pill(if (state is LoadState.Loading) (state as LoadState.Loading).step else "تحديث القوائم الآن", icon = Ico.Refresh, big = true,
                    modifier = Modifier.track(mem, "refresh"), onClick = { Repo.refresh() })
                Pill("الحسابات (${st.accounts().size})", icon = Ico.User, big = true, modifier = Modifier.track(mem, "accounts"), onClick = { nav.go(Route.Accounts) })
            }

            Spacer(Modifier.width(48.dp))

            // ───── الخيارات ─────
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 10.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item { Header("المشغل") }
                item(key = "thumb") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("صورة اللقطة أثناء التقديم", Modifier.track(mem, "thumb"),
                        trailing = when (st.seekPreviewMode) { 1 -> "دايماً"; 2 -> "إيقاف"; else -> "تلقائي (حسب الاتصالات)" },
                        onClick = { st.seekPreviewMode = (st.seekPreviewMode + 1) % 3; changed() })
                }
                item(key = "auto") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("تشغيل الحلقة التالية تلقائياً", Modifier.track(mem, "auto"), trailing = if (st.autoNextEpisode) "تشغيل" else "إيقاف",
                        onClick = { st.autoNextEpisode = !st.autoNextEpisode; changed() })
                }
                item(key = "audio") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("لغة الصوت المفضلة", Modifier.track(mem, "audio"), trailing = langLabel(st.preferredAudio, "الأصلية"),
                        onClick = { st.preferredAudio = nextLang(st.preferredAudio); changed() })
                }
                item(key = "subl") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("لغة الترجمة المفضلة", Modifier.track(mem, "subl"), trailing = langLabel(st.preferredSubtitle, "بدون"),
                        onClick = { st.preferredSubtitle = nextLang(st.preferredSubtitle); changed() })
                }
                item(key = "subs") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("حجم الترجمة", Modifier.track(mem, "subs"),
                        trailing = when (st.subtitleSize) { 0 -> "صغير"; 2 -> "كبير"; 3 -> "كبير جداً"; else -> "متوسط" },
                        onClick = { st.subtitleSize = (st.subtitleSize + 1) % 4; changed() })
                }

                item { Header("المباشر") }
                item(key = "prev") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("معاينة القناة في القائمة", Modifier.track(mem, "prev"), trailing = if (st.livePreview) "تشغيل" else "إيقاف",
                        onClick = { st.livePreview = !st.livePreview; changed() })
                }
                item(key = "fmt") {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    SideItem("صيغة البث المباشر", Modifier.track(mem, "fmt"), trailing = if (st.liveHls) "HLS (m3u8)" else "TS (أسرع وأثبت)",
                        onClick = { st.liveHls = !st.liveHls; changed() })
                }

                item { Header("التصنيفات") }
                item { SideItem("إخفاء تصنيفات القنوات", Modifier.track(mem, "hl"), icon = Ico.EyeOff, onClick = { nav.go(Route.HiddenCats(Kind.LIVE)) }) }
                item { SideItem("إخفاء تصنيفات الأفلام", Modifier.track(mem, "hm"), icon = Ico.EyeOff, onClick = { nav.go(Route.HiddenCats(Kind.MOVIE)) }) }
                item { SideItem("إخفاء تصنيفات المسلسلات", Modifier.track(mem, "hs"), icon = Ico.EyeOff, onClick = { nav.go(Route.HiddenCats(Kind.SERIES)) }) }

                item { Header("التطبيق") }
                item {
                    val upd by com.wm.iptv.update.Updater.state.collectAsState()
                    SideItem("التحقق من التحديثات", Modifier.track(mem, "update"), icon = Ico.Refresh,
                        trailing = if (upd is com.wm.iptv.update.UpdateState.Checking) "جارٍ الفحص…" else "الإصدار الحالي 1.${com.wm.iptv.update.Updater.currentVersion}",
                        onClick = { com.wm.iptv.update.manualCheck(ctx, scope) })
                }

                item { Header("السجل") }
                item {
                    SideItem("مسح سجل المشاهدة", Modifier.track(mem, "clear"), icon = Ico.Trash, onClick = {
                        st.clearHistory(); Repo.bump()
                        Toast.makeText(ctx, "انمسح السجل", Toast.LENGTH_SHORT).show()
                    })
                }
                item {
                    Text("الإصدار ${appVersion(ctx)}", fontSize = 12.sp, color = C.Dim, modifier = Modifier.padding(start = 14.dp, top = 20.dp))
                }
            }
        }
    }
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (_: Exception) { "" }

private val langs = listOf("ar", "en", "")
private fun nextLang(cur: String): String = langs[(langs.indexOf(cur).coerceAtLeast(0) + 1) % langs.size]
private fun langLabel(code: String, empty: String) = when (code) { "ar" -> "العربية"; "en" -> "الإنجليزية"; "" -> empty; else -> code }

@Composable
private fun Header(t: String) {
    Text(t, fontSize = 13.sp, color = C.Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, fontSize = 14.sp, color = C.Muted, modifier = Modifier.width(110.dp))
        Text(v, fontSize = 14.sp, color = C.Text2, maxLines = 1)
    }
}

// ───────────────────────── إخفاء التصنيفات ─────────────────────────

@Composable
fun HiddenCategoriesScreen(nav: Navigator, kind: Kind) {
    val catalog by Repo.catalog.collectAsState()
    val version by Repo.version.collectAsState()
    val mem = rememberFocusMemory("hidden_${kind.name}")
    val cats = remember(catalog) { Repo.allCategories(kind) }
    val hidden = remember(version) { Repo.store.hiddenCategories(kind) }
    LaunchedEffect(Unit) { delay(60); if (!mem.restore()) mem.focus("first") }

    Column(Modifier.fillMaxSize().background(C.Bg).padding(horizontal = 64.dp, vertical = 36.dp)) {
        Text(when (kind) { Kind.LIVE -> "تصنيفات القنوات"; Kind.MOVIE -> "تصنيفات الأفلام"; Kind.SERIES -> "تصنيفات المسلسلات" },
            fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("اضغط على التصنيف عشان تخفيه أو تظهره. المخفي ما يطلع في القوائم ولا في \"الكل\".", fontSize = 14.sp, color = C.Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("إظهار الكل", onClick = { cats.forEach { Repo.store.setCategoryHidden(kind, it.id, false) }; Repo.bump() })
            Pill("إخفاء الكل", onClick = { cats.forEach { Repo.store.setCategoryHidden(kind, it.id, true) }; Repo.bump() })
        }
        LazyColumn(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(cats, key = { it.id }) { c ->
                val h = c.id in hidden
                SideItem(c.name, Modifier.track(mem, if (c == cats.first()) "first" else "c_${c.id}"), icon = if (h) Ico.EyeOff else Ico.Eye,
                    trailing = if (h) "مخفي" else "ظاهر",
                    onClick = { Repo.store.setCategoryHidden(kind, c.id, !h); Repo.bump() })
            }
        }
    }
}

// ───────────────────────── الحسابات ─────────────────────────

@Composable
fun AccountsScreen(nav: Navigator) {
    val version by Repo.version.collectAsState()
    val account by Repo.account.collectAsState()
    val ctx = LocalContext.current
    val mem = rememberFocusMemory("accounts")
    val list = remember(version, account) { Repo.store.accounts() }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { delay(60); if (!mem.restore()) mem.focus("add") }

    Column(Modifier.fillMaxSize().background(C.Bg).padding(horizontal = 64.dp, vertical = 36.dp)) {
        Text("الحسابات", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("اضغط على الحساب للتبديل له. ضغطة مطوّلة للحذف.", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.forEach { a ->
                val cur = a.id == account?.id
                SideItem(
                    "${a.name}  ·  ${a.username}", Modifier.track(mem, "a_${a.id}"), icon = Ico.User, selected = cur,
                    trailing = if (confirmDelete == a.id) "اضغط مطوّلاً مرة ثانية للحذف" else if (cur) "الحالي" else "",
                    onClick = { if (!cur) { Repo.switchAccount(a.id); nav.go(Route.Home) } },
                    onLongClick = {
                        if (confirmDelete == a.id) {
                            Repo.deleteAccount(a.id); confirmDelete = null
                            Toast.makeText(ctx, "انحذف الحساب", Toast.LENGTH_SHORT).show()
                        } else confirmDelete = a.id
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Box { Pill("إضافة حساب", icon = Ico.Plus, big = true, modifier = Modifier.track(mem, "add"), onClick = { nav.go(Route.AddAccount) }) }
        }
    }
}
