package com.wm.iptv

import android.os.Bundle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import com.wm.iptv.ui.onSelect
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wm.iptv.data.LoadState
import com.wm.iptv.data.Repo
import com.wm.iptv.ui.AppTheme
import com.wm.iptv.ui.C
import com.wm.iptv.ui.CenterMessage
import com.wm.iptv.ui.Ico
import com.wm.iptv.ui.Navigator
import com.wm.iptv.ui.Pill
import com.wm.iptv.ui.Route
import com.wm.iptv.ui.clockText
import com.wm.iptv.ui.screens.AccountsScreen
import com.wm.iptv.ui.screens.HiddenCategoriesScreen
import com.wm.iptv.ui.screens.HomeScreen
import com.wm.iptv.ui.screens.LiveScreen
import com.wm.iptv.ui.screens.LoginScreen
import com.wm.iptv.ui.screens.MovieDetailScreen
import com.wm.iptv.ui.screens.MoviesScreen
import com.wm.iptv.ui.screens.SearchScreen
import com.wm.iptv.ui.screens.SeriesDetailScreen
import com.wm.iptv.ui.screens.SeriesListScreen
import com.wm.iptv.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val nav = Navigator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Repo.start()
        setContent { AppTheme { Root(nav, onExit = { finish() }) } }
    }

    override fun onResume() {
        super.onResume()
        Repo.bump() // يحدث "أكمل المشاهدة" بعد الرجوع من المشغل
    }
}

@Composable
private fun Root(nav: Navigator, onExit: () -> Unit) {
    val account by Repo.account.collectAsState()
    val state by Repo.state.collectAsState()
    val catalog by Repo.catalog.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when {
            account == null -> LoginScreen(onDone = { nav.stack.clear(); nav.stack.add(Route.Home) })
            state is LoadState.Loading && (state as LoadState.Loading).firstTime -> LoadingScreen((state as LoadState.Loading).step)
            // شاشة الحسابات تشتغل حتى لو صار خطأ في التحميل
            state is LoadState.Error && catalog.isEmpty && nav.top != Route.Accounts && nav.top != Route.AddAccount ->
                CenterMessage("ما قدرنا نحمّل القوائم", (state as LoadState.Error).message) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("إعادة المحاولة", icon = Ico.Refresh, big = true, onClick = { Repo.refresh() })
                        Pill("الحسابات", icon = Ico.User, big = true, onClick = { nav.go(Route.Accounts) })
                    }
                }
            else -> MainArea(nav)
        }
    }

    // يشيك على التحديثات بهدوء أول ما يفتح التطبيق
    LaunchedEffect(Unit) { com.wm.iptv.update.Updater.check(quiet = true) }
    com.wm.iptv.ui.TvUpdateDialog()

    var lastBack by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    BackHandler {
        if (!nav.back()) {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2200) onExit()
            else { lastBack = now; Toast.makeText(ctx, "اضغط رجوع مرة ثانية للخروج", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
private fun LoadingScreen(step: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Waleed TV", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = C.Text, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(step, color = C.Text3, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text("أول مرة ياخذ شوي، بعدها يفتح على طول", color = C.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MainArea(nav: Navigator) {
    val holder = rememberSaveableStateHolder()
    val route = nav.top
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            contentKey = { it.key },
            label = "nav",
        ) { r ->
            holder.SaveableStateProvider(r.key) {
                Box(Modifier.fillMaxSize()) {
                    when (r) {
                        Route.Home -> HomeScreen(nav)
                        Route.Live -> LiveScreen(nav)
                        Route.Movies -> MoviesScreen(nav)
                        Route.SeriesList -> SeriesListScreen(nav)
                        Route.Search -> SearchScreen(nav)
                        Route.Settings -> SettingsScreen(nav)
                        is Route.MovieDetail -> MovieDetailScreen(nav, r.id)
                        is Route.SeriesDetail -> SeriesDetailScreen(nav, r.id)
                        is Route.HiddenCats -> HiddenCategoriesScreen(nav, r.kind)
                        Route.Accounts -> AccountsScreen(nav)
                        Route.AddAccount -> LoginScreen(onDone = { nav.go(Route.Home) }, onCancel = { nav.back() })
                    }
                }
            }
        }
        val state by Repo.state.collectAsState()
        if (state is LoadState.Loading) {
            Text((state as LoadState.Loading).step, fontSize = 12.sp, color = C.Text3,
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
                    .background(Color(0xCC1C1C1E), RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

/**
 * شريط التنقل العلوي بستايل Apple TV.
 * currentFocus: لو انعطى، الزر الحالي ياخذ هذا الـ FocusRequester (عشان التطبيق يفتح والتركيز على الشريط).
 * onNavFocus: يبلغ الشاشة أي زر واقف عليه (الرئيسية تعرض المباريات لما تقف على "المباشر").
 */
@Composable
fun TopBar(
    nav: Navigator, current: Route, modifier: Modifier = Modifier,
    currentFocus: androidx.compose.ui.focus.FocusRequester? = null,
    onNavFocus: ((Route, Boolean) -> Unit)? = null,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Row(modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Waleed TV", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        GlassNavBar(nav, current, currentFocus, onNavFocus)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.wm.iptv.ui.TvRefreshButton()
            Spacer(Modifier.width(14.dp))
            Text(clockText(now), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = C.Text2, modifier = Modifier.width(90.dp))
        }
    }
}

private data class BarTab(val route: Route, val label: String, val icon: ImageVector)

private val barTabs = listOf(
    BarTab(Route.Home, "الرئيسية", Ico.Home),
    BarTab(Route.Live, "المباشر", Ico.Tv),
    BarTab(Route.Movies, "الأفلام", Ico.Film),
    BarTab(Route.SeriesList, "المسلسلات", Ico.Stack),
    BarTab(Route.Search, "بحث", Ico.Search),
    BarTab(Route.Settings, "الإعدادات", Ico.Gear),
)

/**
 * نفس شريط الجوال: كبسولة زجاجية فيها فقاعة تنزلق بنعومة تحت القسم.
 * على التلفزيون الفقاعة تمشي مع الريموت (التركيز)، ولما يطلع التركيز من الشريط ترجع تقعد على القسم الحالي.
 */
@Composable
private fun GlassNavBar(
    nav: Navigator, current: Route,
    currentFocus: androidx.compose.ui.focus.FocusRequester?,
    onNavFocus: ((Route, Boolean) -> Unit)?,
) {
    val shape = RoundedCornerShape(999.dp)
    val itemW = 104.dp
    val itemH = 56.dp
    val selected = barTabs.indexOfFirst { it.route == current }.coerceAtLeast(0)
    var focusedIdx by remember { mutableStateOf<Int?>(null) }
    val target = focusedIdx ?: selected
    val bubbleX by animateDpAsState(itemW * target, spring(dampingRatio = 0.78f, stiffness = 420f), label = "bubble")
    val hot by animateFloatAsState(if (focusedIdx != null) 1f else 0f, tween(140), label = "hot")

    Box(
        Modifier
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            // زجاج: طبقة غامقة شفافة + لمعة خفيفة من فوق + إطار رفيع لامع (نفس الجوال)
            .background(Color(0x700A0A0E))
            .background(Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF))))
            .border(0.8.dp, Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x0DFFFFFF))), shape)
            .padding(5.dp)
    ) {
        // فقاعة التحديد: هادئة لو على القسم الحالي، وأوضح وأكبر شوي لما التركيز عليها
        Box(
            Modifier.offset(x = bubbleX).width(itemW).height(itemH)
                .graphicsLayer { scaleX = 1f + 0.06f * hot; scaleY = 1f + 0.06f * hot }
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f + 0.24f * hot), Color.White.copy(alpha = 0.06f + 0.10f * hot))))
                .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f + 0.35f * hot), Color.White.copy(alpha = 0.08f + 0.10f * hot))), shape)
        )
        Row {
            barTabs.forEachIndexed { i, t ->
                val on = i == (focusedIdx ?: selected)
                val color by animateColorAsState(if (on) Color.White else Color(0xFFB8B8BE), tween(140), label = "c")
                Column(
                    Modifier.width(itemW).height(itemH)
                        .then(if (t.route == current && currentFocus != null) Modifier.focusRequester(currentFocus) else Modifier)
                        .onFocusChanged {
                            if (it.isFocused) { focusedIdx = i; onNavFocus?.invoke(t.route, true) }
                            else if (focusedIdx == i) { focusedIdx = null; onNavFocus?.invoke(t.route, false) }
                        }
                        .onSelect({ nav.go(t.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(t.icon, t.label, tint = color, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, fontSize = 11.sp, color = color, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
